import React, { useState, useEffect, useRef } from "react";
import { Line } from "react-chartjs-2";
import { FaUndo } from "react-icons/fa";
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
import type { ChartOptions } from "chart.js";

// Register necessary components for Chart.js
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

interface MagellanChartProps {
  fuelType: string;
}

const DATA_OPTIONS = [
  { value: "System Inventory", label: "System Inventory" },
  { value: "MPL Racks Only", label: "MPL Racks Only" },
  {
    value: "Offlines and MPL Racks 7-Day Average",
    label: "Offlines & MPL Racks 7-Day Avg",
  },
  { value: "Receipts 7-Day Average", label: "Receipts 7-Day Avg" },
];

const MagellanChart: React.FC<MagellanChartProps> = ({ fuelType }) => {
  const [chartData, setChartData] = useState<Map<string, Map<string, number>>>(
    new Map()
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedDataOption, setSelectedDataOption] = useState(
    DATA_OPTIONS[0].value
  );
  const chartRef = useRef<ChartJS<"line"> | null>(null);

  useEffect(() => {
    fetchMagellanData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fuelType, selectedDataOption]);

const fetchMagellanData = async () => {
  setIsLoading(true);
  setError(null);
  try {
    const response = await fetch(
      `http://localhost:8080/getMagellanData?fuel=${fuelType}&data=${encodeURIComponent(
        selectedDataOption
      )}`
    );
    if (!response.ok) {
      throw new Error("Failed to fetch Magellan inventory data");
    }
    const data = await response.json();

    const yearMap: Map<string, Map<string, number>> = new Map();

    // Transform the new API structure
    Object.entries(data).forEach(([date, yearValues]) => {
      Object.entries(yearValues as Record<string, number>).forEach(
        ([year, value]) => {
          if (!yearMap.has(year)) {
            yearMap.set(year, new Map());
          }
          yearMap.get(year)!.set(date, value);
        }
      );
    });

    setChartData(yearMap);
  } catch (err) {
    setError(err instanceof Error ? err.message : "An unknown error occurred");
    setChartData(new Map());
  } finally {
    setIsLoading(false);
  }
};


  const getAllDates = (): string[] => {
    const allDatesSet = new Set<string>();
    chartData.forEach((dateMap) => {
      dateMap.forEach((_, date) => allDatesSet.add(date));
    });
    return Array.from(allDatesSet).sort((a, b) => {
      const [aMonth, aDay] = a.split("/").map(Number);
      const [bMonth, bDay] = b.split("/").map(Number);
      return (
        new Date(2000, aMonth - 1, aDay).getTime() -
        new Date(2000, bMonth - 1, bDay).getTime()
      );
    });
  };

  const calculateMinMaxRange = () => {
    const allDates = getAllDates();
    const validYears = Array.from(chartData.keys()).filter(
      (year) =>
        !["5YEARAVG", "10YEARAVG", "MAX", "MIN", "10-YEAR-RANGE"].includes(year)
    );

    const min: Array<number | null> = [];
    const max: Array<number | null> = [];

    allDates.forEach((date) => {
      const values = validYears
        .map((year) => chartData.get(year)?.get(date))
        .filter((v): v is number => typeof v === "number");
      if (values.length) {
        min.push(Math.min(...values));
        max.push(Math.max(...values));
      } else {
        min.push(null);
        max.push(null);
      }
    });

    return { min, max };
  };

  const getYearColor = (year: string, opacity = 1): string => {
    const colors: Record<string, string> = {
      "2020": `rgba(128, 0, 128, ${opacity})`, // Purple
      "2021": `rgba(128, 128, 128, ${opacity})`, // Gray
      "2022": `rgba(0, 128, 0, ${opacity})`, // Green
      "2023": `rgba(0, 0, 255, ${opacity})`, // Blue
      "2024": `rgba(255, 165, 0, ${opacity})`, // Orange
      "2025": `rgba(255, 140, 0, ${opacity})`, // Darker Orange
      "5YEARAVG": `rgba(255, 0, 0, ${opacity})`, // Red
      "10YEARAVG": `rgba(0, 0, 0, ${opacity})`, // Red
      MAX: `rgba(255, 0, 0, ${opacity})`, // Red
      MIN: `rgba(255, 0, 0, ${opacity})`, // Red
    };
    return colors[year] || `rgba(0, 0, 0, ${opacity})`;
  };

  const allDates = getAllDates();
  const rangeData = calculateMinMaxRange();

  const handleResetZoom = () => {
    if (chartRef.current) {
      chartRef.current.resetZoom();
    }
  };

    const data = {
      labels: getAllDates(),
      datasets: [
        // Range fill
        {
          label: "Value Range",
          data: calculateMinMaxRange().max,
          backgroundColor: "rgba(200, 200, 200, 0.2)",
          borderColor: "rgba(200, 200, 200, 0)",
          borderWidth: 0,
          pointRadius: 0,
          fill: "+1",
        },
        {
          label: "",
          data: calculateMinMaxRange().min,
          backgroundColor: "rgba(200, 200, 200, 0.2)",
          borderColor: "rgba(200, 200, 200, 0)",
          borderWidth: 0,
          pointRadius: 0,
        },
        // Years 2020-2024 (dotted lines)
        ...Array.from(chartData.entries())
          .filter(([year]) => parseInt(year) >= 2020 && parseInt(year) <= 2024)
          .map(([year, dateMap]) => ({
            label: year,
            data: getAllDates().map((date) => dateMap.get(date) ?? null),
            borderColor: getYearColor(year),
            backgroundColor: getYearColor(year, 0.5),
            borderWidth: 1,
            borderDash: [5, 5],
            tension: 0.1,
            pointRadius: 0,
          })),
        // 2025 (bold line)
        ...(chartData.has("2025")
          ? [
              {
                label: "2025",
                data: getAllDates().map(
                  (date) => chartData.get("2025")?.get(date) ?? null
                ),
                borderColor: getYearColor("2025"),
                backgroundColor: getYearColor("2025", 0.5),
                borderWidth: 3,
                tension: 0.1,
                pointRadius: 0,
              },
            ]
          : []),
        // 5-Year Avg
        ...(chartData.has("5YEARAVG")
          ? [
              {
                label: "5-Year Average",
                data: getAllDates().map(
                  (date) => chartData.get("5YEARAVG")?.get(date) ?? null
                ),
                borderColor: getYearColor("5YEARAVG"),
                backgroundColor: getYearColor("5YEARAVG", 0.5),
                borderWidth: 3,
                tension: 0.1,
                pointRadius: 0,
              },
            ]
          : []),
        // 10-Year Avg
        ...(chartData.has("10YEARAVG")
          ? [
              {
                label: "10-Year Average",
                data: getAllDates().map(
                  (date) => chartData.get("10YEARAVG")?.get(date) ?? null
                ),
                borderColor: getYearColor("10YEARAVG"),
                backgroundColor: getYearColor("10YEARAVG", 0.5),
                borderWidth: 3,
                tension: 0.1,
                pointRadius: 0,
              },
            ]
          : []),
      ],
    };

  const options: ChartOptions<"line"> = {
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
        text: `${fuelType} ${selectedDataOption}`,
        font: {
          size: 18,
          weight: "bold",
          family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
        },
      },
      tooltip: {
        callbacks: {
          label: (context: any) => {
            const label = context.dataset.label || "";
            const value = context.parsed.y;
            return `${label}: ${value?.toFixed(2) ?? "N/A"}`;
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
          text: "Inventory (Barrels)",
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
  // Add this new function to your existing MagellanChart component
  const get30DatesWith2025Data = () => {
    const allDatesWith2025Data = allDates
      .filter((date) => {
        return (
          chartData.get("2025")?.get(date) !== null &&
          chartData.get("2025")?.get(date) !== undefined
        );
      })
      .reverse();

    if (allDatesWith2025Data.length >= 30) {
      return allDatesWith2025Data.slice(0, 30);
    } else if (allDatesWith2025Data.length > 0) {
      return allDatesWith2025Data;
    } else {
      return allDates.slice(-30).reverse();
    }
  };

  const datesToDisplay = get30DatesWith2025Data();

  // Update your return statement to include the table
  return (
    <div
      className="graph-wrapper"
      style={{
        marginTop: "20px",
        display: "flex",
        flexDirection: "column",
        gap: "20px",
      }}
    >
      {/* Add the dropdown selector */}
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          gap: "20px",
          alignItems: "center",
        }}
      >
        <label htmlFor="data-select" style={{ fontWeight: "bold" }}>
          Data View:
        </label>
        <select
          id="data-select"
          value={selectedDataOption}
          onChange={(e) => setSelectedDataOption(e.target.value)}
          style={{
            padding: "8px 12px",
            borderRadius: "4px",
            border: "1px solid #ccc",
            backgroundColor: "#fff",
            fontSize: "16px",
            minWidth: "250px",
          }}
        >
          {DATA_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="graph-container" style={{ height: "800px" }}>
        {isLoading ? (
          <p style={{ textAlign: "center" }}>Loading inventory data...</p>
        ) : error ? (
          <p style={{ textAlign: "center", color: "red" }}>{error}</p>
        ) : chartData.size > 0 ? (
          <>
            <button
              onClick={handleResetZoom}
              style={{
                position: "absolute",
                right: "20px",
                top: "10px",
                zIndex: 100,
                padding: "5px 10px",
                backgroundColor: "#3498db",
                color: "white",
                border: "none",
                borderRadius: "4px",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "5px",
              }}
            >
              <FaUndo />
              Reset Zoom
            </button>
            <Line ref={chartRef} data={data} options={options} />
          </>
        ) : (
          <p style={{ textAlign: "center" }}>No inventory data available</p>
        )}
      </div>

      {/* Add this table section */}
      {chartData.size > 0 && (
        // Update the table container styling
        <div
          style={{
            marginTop: "20px",
            backgroundColor: "#fff",
            borderRadius: "8px",
            boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
            padding: "20px",
            overflowX: "auto",
            width: "100%",
            maxWidth: "1200px", // Increased from 1000px
            marginLeft: "auto",
            marginRight: "auto",
          }}
        >
          <h2
            style={{
              color: "#2c3e50",
              fontSize: "clamp(1rem, 2vw, 1.5rem)", // Responsive font size
              marginBottom: "15px",
              fontWeight: "bold",
              textAlign: "center", // Center the heading
            }}
          >
            Last 30 Days Data - 2025
          </h2>
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "clamp(0.8rem, 1.5vw, 1rem)", // Responsive font size
            }}
          >
            <thead>
              <tr style={{ backgroundColor: "#f8f9fa" }}>
                <th
                  style={{
                    padding: "clamp(8px, 1.5vw, 12px)", // Responsive padding
                    textAlign: "left",
                    borderBottom: "1px solid #ddd",
                    fontWeight: "bold",
                    fontSize: "clamp(0.9rem, 1.7vw, 1.1rem)", // Slightly larger for headers
                  }}
                >
                  Date
                </th>

                {Array.from(chartData.keys())
                  .filter(
                    (year) =>
                      ![
                        "2015",
                        "2016",
                        "2017",
                        "2018",
                        "2019",
                        "MAX",
                        "MIN",
                        "10-YEAR-RANGE",
                      ].includes(year)
                  )
                  .map((year) => (
                    <th
                      key={year}
                      style={{
                        padding: "clamp(8px, 1.5vw, 12px)",
                        textAlign: "right",
                        borderBottom: "1px solid #ddd",
                        fontWeight: "bold",
                        fontSize: "clamp(0.9rem, 1.7vw, 1.1rem)",
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
                    chartData.get("2025")?.get(date) !== undefined;
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
                          padding: "clamp(8px, 1.5vw, 12px)",
                          borderBottom: "1px solid #eee",
                          fontWeight: has2025Data ? "bold" : "500",
                          color: has2025Data ? "#2c3e50" : "inherit",
                        }}
                      >
                        {date}
                      </td>

                      {Array.from(chartData.keys())
                        .filter(
                          (year) =>
                            ![
                              "2015",
                              "2016",
                              "2017",
                              "2018",
                              "2019",
                              "MAX",
                              "MIN",
                              "10-YEAR-RANGE",
                            ].includes(year)
                        )
                        .map((year) => {
                          const value = chartData.get(year)?.get(date);
                          return (
                            <td
                              key={year}
                              style={{
                                padding: "clamp(8px, 1.5vw, 12px)",
                                textAlign: "right",
                                borderBottom: "1px solid #eee",
                                fontFamily: "'Courier New', Courier, monospace",
                                fontWeight: year === "2025" ? "bold" : "normal",
                                color: year === "2025" ? "#2c3e50" : "inherit",
                              }}
                            >
                              {value?.toFixed(2) ?? "N/A"}
                            </td>
                          );
                        })}
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td
                    colSpan={chartData.size + 1}
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

export default MagellanChart;
