import { FaUndo} from "react-icons/fa"
import React, { useState, useEffect, useRef} from "react";
import "./styles/main.css";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from "chart.js";
import zoomPlugin from "chartjs-plugin-zoom";

import type { ChartOptions, ChartData } from "chart.js";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
  zoomPlugin
);

type ProductType = "RBOB" | "HO";
type MonthCode =
  | "F"
  | "G"
  | "H"
  | "J"
  | "K"
  | "M"
  | "N"
  | "Q"
  | "U"
  | "V"
  | "X"
  | "Z";

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ProductType>("RBOB");
  const [startMonth, setStartMonth] = useState<MonthCode | "">("");
  const [endMonth, setEndMonth] = useState<MonthCode | "">("");
  const [spreadData, setSpreadData] = useState<
    Map<string, Map<string, number>>
  >(new Map());
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chartRef = useRef<ChartJS<"line"> | null>(null);

  const monthOptions: MonthCode[] = [
    "F",
    "G",
    "H",
    "J",
    "K",
    "M",
    "N",
    "Q",
    "U",
    "V",
    "X",
    "Z",
  ];

  const handleTabChange = (tab: ProductType) => {
    setActiveTab(tab);
    setStartMonth("");
    setEndMonth("");
    setSpreadData(new Map());
  };

    const handleResetZoom = () => {
      if (chartRef.current) {
        chartRef.current.resetZoom();
      }
    };

  useEffect(() => {
    if (startMonth && endMonth) {
      fetchSpreadData();
    }
  }, [startMonth, endMonth, activeTab]);

  const fetchSpreadData = async () => {
    setIsLoading(true);
    setError(null);
    try {
    const response = await fetch(
      `http://localhost:3232/getSpread?commodity=${activeTab}&startMonth=${startMonth}&endMonth=${endMonth}`
    );
      if (!response.ok) {
        throw new Error("Failed to fetch spread data");
      }
      const data = await response.json();
      const mapData = new Map<string, Map<string, number>>(
        Object.entries(data).map(([year, entries]) => [
          year,
          new Map(Object.entries(entries as Record<string, number>)),
        ])
      );
      setSpreadData(mapData);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "An unknown error occurred"
      );
      setSpreadData(new Map());
    } finally {
      setIsLoading(false);
    }
  };

  const getAllDates = (): string[] => {
    const allDates = new Set<string>();
    spreadData.forEach((yearData) => {
      yearData.forEach((_val, date) => allDates.add(date));
    });
    return Array.from(allDates).sort((a, b) => {
      const [aMonth, aDay] = a.split("/").map(Number);
      const [bMonth, bDay] = b.split("/").map(Number);
      return (
        new Date(2000, aMonth - 1, aDay).getTime() -
        new Date(2000, bMonth - 1, bDay).getTime()
      );
    });
  };

  const calculateMinMaxRange = () => {
    const rangeData: { min: Array<number | null>; max: Array<number | null> } =
      { min: [], max: [] };
    const allDates = getAllDates();

    allDates.forEach((date) => {
      const values = Array.from(spreadData.entries())
        .filter(([year]) => year !== "5YEARAVG")
        .map(([_, yearData]) => yearData.get(date))
        .filter((val): val is number => val !== null && val !== undefined);

      if (values.length > 0) {
        rangeData.min.push(Math.min(...values));
        rangeData.max.push(Math.max(...values));
      } else {
        rangeData.min.push(null);
        rangeData.max.push(null);
      }
    });

    return rangeData;
  };

  const getYearColor = (year: string, opacity = 1): string => {
    const colors: Record<string, string> = {
      "2020": `rgba(128, 0, 128, ${opacity})`,
      "2021": `rgba(128, 128, 128, ${opacity})`,
      "2022": `rgba(0, 128, 0, ${opacity})`,
      "2023": `rgba(0, 0, 255, ${opacity})`,
      "2024": `rgba(255, 165, 0, ${opacity})`,
      "2025": `rgba(255, 140, 0, ${opacity})`,
      "5YEARAVG": `rgba(255, 0, 0, ${opacity})`,
    };
    return colors[year] || `rgba(0, 0, 0, ${opacity})`;
  };


  const allDates = getAllDates();
  const rangeData = calculateMinMaxRange();

const get30DatesWith2025Data = () => {
  // Get ALL dates with 2025 data, sorted newest to oldest
  const allDatesWith2025Data = allDates
    .filter((date) => {
      return (
        spreadData.get("2025")?.get(date) !== null &&
        spreadData.get("2025")?.get(date) !== undefined
      );
    })
    .reverse();

  // If we have at least 30 dates with data, take the most recent 30
  if (allDatesWith2025Data.length >= 30) {
    return allDatesWith2025Data.slice(0, 30);
  }
  // If we have some but less than 30, take all available
  else if (allDatesWith2025Data.length > 0) {
    return allDatesWith2025Data;
  }
  // If no 2025 data exists at all, fall back to last 30 days regardless
  else {
    return allDates.slice(-30).reverse();
  }
};

const datesToDisplay = get30DatesWith2025Data();

// Update the table header logic
const tableHeaderText = spreadData.has("2025")
  ? datesToDisplay.some((date) => spreadData.get("2025")?.get(date))
    ? `Last ${datesToDisplay.length} Days (With 2025 Data)`
    : "Last 30 Days (No 2025 Data)"
  : "Last 30 Days Data";

  const chartData: ChartData<"line"> = {
    labels: allDates,
    datasets: [
      // Grey shading between min and max
      {
        label: "Value Range",
        data: rangeData.max,
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        borderWidth: 0,
        pointRadius: 0,
        fill: "+1",
      },
      {
        label: "",
        data: rangeData.min,
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        borderWidth: 0,
        pointRadius: 0,
      },
      // Individual years (2020-2024) - thin dotted lines
      ...Array.from(spreadData.entries())
        .filter(([year]) => parseInt(year) >= 2020 && parseInt(year) <= 2024)
        .map(([year, yearData]) => ({
          label: year,
          data: allDates.map((date) => yearData.get(date) ?? null),
          borderColor: getYearColor(year),
          backgroundColor: getYearColor(year, 0.5),
          borderWidth: 1,
          borderDash: [5, 5],
          tension: 0.1,
          pointRadius: 0,
        })),
      // 2025 - bold solid line
      ...(spreadData.has("2025")
        ? [
            {
              label: "2025",
              data: allDates.map(
                (date) => spreadData.get("2025")?.get(date) ?? null
              ),
              borderColor: getYearColor("2025"),
              backgroundColor: getYearColor("2025", 0.5),
              borderWidth: 3,
              tension: 0.1,
              pointRadius: 0,
            },
          ]
        : []),
      // 5YEARAVG - bold solid line
      ...(spreadData.has("5YEARAVG")
        ? [
            {
              label: "5-Year Average",
              data: allDates.map(
                (date) => spreadData.get("5YEARAVG")?.get(date) ?? null
              ),
              borderColor: getYearColor("5YEARAVG"),
              backgroundColor: getYearColor("5YEARAVG", 0.5),
              borderWidth: 3,
              tension: 0.1,
              pointRadius: 0,
            },
          ]
        : []),
    ],
  };

  const chartOptions: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      zoom: {
        zoom: {
          wheel: { enabled: true },
          pinch: { enabled: true },
          mode: "xy",
        },
        pan: {
          enabled: true,
          mode: "xy",
        },
      },
      legend: {
        position: "top",
        labels: {
          font: {
            size: 14,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
      title: {
        display: true,
        text: `${activeTab} Spread Analysis (${startMonth}-${endMonth})`,
        font: {
          size: 18,
          weight: "bold",
          family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
        },
      },
      tooltip: {
        callbacks: {
          label: (context) => {
            const label = context.dataset.label || "";
            const value = context.parsed.y;
            return `${label}: ${value?.toFixed(4) ?? "N/A"}`;
          },
        },
      },
    },
    scales: {
      x: {
        title: {
          display: true,
          text: "Date (MM/DD)",
          font: {
            size: 14,
            weight: "bold",
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
        ticks: {
          font: {
            size: 12,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
      y: {
        title: {
          display: true,
          text: "Spread ($/gal)",
          font: {
            size: 14,
            weight: "bold",
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
        ticks: {
          font: {
            size: 12,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
    },
    elements: {
      line: {
        tension: 0.1,
        spanGaps: true,
      },
      point: {
        radius: 0,
      },
    },
  };

  return (
    <div
      className="app"
      style={{
        maxWidth: "1400px",
        margin: "0 auto",
        padding: "20px",
        fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
      }}
    >
      <h1
        style={{
          textAlign: "center",
          color: "#2c3e50",
          fontSize: "2.2rem",
          marginBottom: "20px",
          fontWeight: "bold",
          textShadow: "1px 1px 2px rgba(0,0,0,0.1)",
        }}
      >
        Energy Futures Dashboard
      </h1>

      <div
        className="tabs"
        style={{
          display: "flex",
          justifyContent: "center",
          gap: "10px",
          marginBottom: "20px",
        }}
      >
        {(["RBOB", "HO"] as ProductType[]).map((tab) => (
          <button
            key={tab}
            style={{
              padding: "10px 20px",
              border: "none",
              borderRadius: "4px",
              background: activeTab === tab ? "#3498db" : "#ecf0f1",
              color: activeTab === tab ? "white" : "#2c3e50",
              cursor: "pointer",
              fontWeight: "bold",
              transition: "all 0.3s ease",
            }}
            onClick={() => handleTabChange(tab)}
          >
            {tab}
          </button>
        ))}
      </div>

      <div
        className="month-selector"
        style={{
          display: "flex",
          justifyContent: "center",
          gap: "20px",
          marginBottom: "20px",
          flexWrap: "wrap",
        }}
      >
        <div className="month-dropdown" style={{ minWidth: "200px" }}>
          <label
            style={{
              display: "block",
              marginBottom: "8px",
              fontWeight: "bold",
              color: "#2c3e50",
            }}
          >
            Start month:
          </label>
          <select
            value={startMonth}
            onChange={(e) => setStartMonth(e.target.value as MonthCode | "")}
            style={{
              width: "100%",
              padding: "8px",
              borderRadius: "4px",
              border: "1px solid #bdc3c7",
            }}
          >
            <option value="">-- Select --</option>
            {monthOptions.map((month) => (
              <option key={`start-${month}`} value={month}>
                {month}
              </option>
            ))}
          </select>
        </div>

        <div className="month-dropdown" style={{ minWidth: "200px" }}>
          <label
            style={{
              display: "block",
              marginBottom: "8px",
              fontWeight: "bold",
              color: "#2c3e50",
            }}
          >
            End month:
          </label>
          <select
            value={endMonth}
            onChange={(e) => setEndMonth(e.target.value as MonthCode | "")}
            style={{
              width: "100%",
              padding: "8px",
              borderRadius: "4px",
              border: "1px solid #bdc3c7",
            }}
          >
            <option value="">-- Select --</option>
            {monthOptions.map((month) => (
              <option key={`end-${month}`} value={month}>
                {month}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div
        className="graph-container"
        style={{
          height: "500px",
          width: "100%",
          marginBottom: "40px",
          backgroundColor: "#fff",
          borderRadius: "8px",
          boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
          padding: "20px",
          position: "relative", // Add this for positioning the reset button
        }}
      >
        {isLoading ? (
          <p style={{ textAlign: "center" }}>Loading data...</p>
        ) : error ? (
          <p
            style={{
              textAlign: "center",
              color: "#e74c3c",
              fontWeight: "bold",
            }}
          >
            {error}
          </p>
        ) : startMonth && endMonth ? (
          spreadData.size > 0 ? (
            <>
              <button
                onClick={handleResetZoom}
                style={{
                  position: "absolute",
                  top: "30px",
                  right: "30px",
                  zIndex: 100,
                  background: "#3498db",
                  color: "white",
                  border: "none",
                  borderRadius: "4px",
                  padding: "8px 12px",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: "5px",
                  fontSize: "14px",
                  boxShadow: "0 2px 5px rgba(0,0,0,0.1)",
                  transition: "all 0.2s ease",
                }}
                onMouseOver={(e) => {
                  e.currentTarget.style.background = "#2980b9";
                }}
                onMouseOut={(e) => {
                  e.currentTarget.style.background = "#3498db";
                }}
              >
                <FaUndo />
                Reset Zoom
              </button>
              <Line ref={chartRef} data={chartData} options={chartOptions} />
            </>
          ) : (
            <p style={{ textAlign: "center" }}>
              No data available for the selected months
            </p>
          )
        ) : (
          <p
            style={{
              textAlign: "center",
              color: "#7f8c8d",
              fontStyle: "italic",
            }}
          >
            Please select both start and end months to view data
          </p>
        )}
      </div>
      {spreadData.size > 0 && (
        <div
          className="data-table"
          style={{
            marginTop: "20px",
            backgroundColor: "#fff",
            borderRadius: "8px",
            boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
            padding: "20px",
            overflowX: "auto",
          }}
        >
          <h2
            style={{
              color: "#2c3e50",
              fontSize: "1.5rem",
              marginBottom: "15px",
              fontWeight: "bold",
            }}
          >
            Last 30 Days Data -2025
          </h2>
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "14px",
            }}
          >
            <thead>
              <tr style={{ backgroundColor: "#f8f9fa" }}>
                <th
                  style={{
                    padding: "12px",
                    textAlign: "left",
                    borderBottom: "1px solid #ddd",
                    fontWeight: "bold",
                  }}
                >
                  Date
                </th>
                {Array.from(spreadData.keys()).map((year) => (
                  <th
                    key={year}
                    style={{
                      padding: "12px",
                      textAlign: "right",
                      borderBottom: "1px solid #ddd",
                      fontWeight: "bold",
                    }}
                  >
                    {year}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {datesToDisplay.length > 0 ? (
                datesToDisplay.map((date, index) => {
                  const has2025Data =
                    spreadData.get("2025")?.get(date) !== undefined;
                  return (
                    <tr
                      key={index}
                      style={{
                        borderBottom: "1px solid #eee",
                        backgroundColor: index % 2 === 0 ? "#fff" : "#f8f9fa",
                        ...(has2025Data ? { fontWeight: "bold" } : {}),
                      }}
                    >
                      <td
                        style={{
                          padding: "12px",
                          borderBottom: "1px solid #eee",
                          fontWeight: has2025Data ? "bold" : "500",
                          color: has2025Data ? "#2c3e50" : "inherit",
                        }}
                      >
                        {date}
                      </td>
                      {Array.from(spreadData.keys()).map((year) => {
                        const value = spreadData.get(year)?.get(date);
                        return (
                          <td
                            key={year}
                            style={{
                              padding: "12px",
                              textAlign: "right",
                              borderBottom: "1px solid #eee",
                              fontFamily: "'Courier New', Courier, monospace",
                              fontWeight: year === "2025" ? "bold" : "normal",
                              color: year === "2025" ? "#2c3e50" : "inherit",
                            }}
                          >
                            {value?.toFixed(4) ?? "N/A"}
                          </td>
                        );
                      })}
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td
                    colSpan={spreadData.size + 1}
                    style={{
                      textAlign: "center",
                      padding: "20px",
                      fontStyle: "italic",
                      color: "#7f8c8d",
                    }}
                  >
                    No data available for the selected criteria
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default App;
