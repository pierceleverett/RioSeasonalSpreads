import { FaUndo } from "react-icons/fa";
import React, { useState, useEffect, useRef } from "react";
import "./styles/main.css";
import { Line } from "react-chartjs-2";
import {
  SignedIn,
  SignedOut,
  SignInButton,
  UserButton,
} from "@clerk/clerk-react";
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

import MagellanInventory from "./MagellanInventory";

import CsvSpreadChart from "./CsvSpreadChart";


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


type ProductType =
  | "RBOB Spreads"
  | "HO Spreads"
  | "Magellan Inventory"
  | "A to Nap"
  | "D to A";

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
  const [activeTab, setActiveTab] = useState<ProductType>("RBOB Spreads");
  const [startMonth, setStartMonth] = useState<MonthCode>("N");
  const [endMonth, setEndMonth] = useState<MonthCode>("Q");
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
    setStartMonth("N");
    setEndMonth("Q");
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

  const commodityParam = activeTab.split(" ")[0]; // This will return "RBOB", "HO", or "Magellan"

  try {
    const response = await fetch(
      `https://rioseasonalspreads-production.up.railway.app/getSpread?commodity=${commodityParam}&startMonth=${startMonth}&endMonth=${endMonth}`
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
    setError(err instanceof Error ? err.message : "An unknown error occurred");
    setSpreadData(new Map());
  } finally {
    setIsLoading(false);
  }
};

const getHistoricalYears = () => {
  const years = Array.from(spreadData.keys())
    .filter((key) => !["5YEARAVG", "10YEARAVG"].includes(key))
    .sort()
    .map((year) => parseInt(year));

  // Remove the last year (current year) and return the rest
  return years.length > 1 ? years.slice(0, -1) : [];
};

const getCurrentYear = () => {
  const years = Array.from(spreadData.keys())
    .filter((key) => !["5YEARAVG", "10YEARAVG"].includes(key))
    .sort()
    .map((year) => parseInt(year));

  return years.length > 0 ? years[years.length - 1].toString() : null;
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

const yearColorMap = new Map<string, string>();
const colorPalette = [
  "rgba(0, 123, 255, OPACITY)",
  "rgba(40, 167, 69, OPACITY)",
  "rgba(255, 193, 7, OPACITY)",
  "rgba(220, 53, 69, OPACITY)",
  "rgba(23, 162, 184, OPACITY)",
  "rgba(108, 117, 125, OPACITY)",
];

const getYearColor = (year: string, opacity = 1): string => {
  if (year === "5YEARAVG") return `rgba(255, 0, 0, ${opacity})`;
  if (year === "10YEARAVG") return `rgba(0, 0, 0, ${opacity})`;

  if (!yearColorMap.has(year)) {
    const index = yearColorMap.size % colorPalette.length;
    const color = colorPalette[index].replace("OPACITY", opacity.toString());
    yearColorMap.set(year, color);
  }

  return yearColorMap.get(year)!;
};


  const allDates = getAllDates();
  const rangeData = calculateMinMaxRange();

  const get30DatesWith2025Data = () => {
    const allDatesWith2025Data = allDates
      .filter((date) => {
        return (
          spreadData.get("2025")?.get(date) !== null &&
          spreadData.get("2025")?.get(date) !== undefined
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

const chartData: ChartData<"line"> = {
  labels: allDates,
  datasets: [
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
      label: "Min Value Range", // <-- fixed from empty string
      data: rangeData.min,
      backgroundColor: "rgba(200, 200, 200, 0.2)",
      borderColor: "rgba(200, 200, 200, 0)",
      borderWidth: 0,
      pointRadius: 0,
    },
    ...getHistoricalYears().map((year) => {
      const yearStr = year.toString();
      return {
        label: yearStr,
        data: allDates.map(
          (date) => spreadData.get(yearStr)?.get(date) ?? null
        ),
        borderColor: getYearColor(yearStr),
        backgroundColor: getYearColor(yearStr, 0.5),
        borderWidth: 1,
        borderDash: [5, 5],
        tension: 0.1,
        pointRadius: 0,
      };
    }),
    ...(getCurrentYear()
      ? [
          {
            label: getCurrentYear()!,
            data: allDates.map(
              (date) => spreadData.get(getCurrentYear()!)?.get(date) ?? null
            ),
            borderColor: getYearColor(getCurrentYear()!),
            backgroundColor: getYearColor(getCurrentYear()!, 0.5),
            borderWidth: 3,
            tension: 0.1,
            pointRadius: 0,
          },
        ]
      : []),
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

  const currentYear = getCurrentYear();

  return (
    <header>
      <SignedOut>
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            minHeight: "100vh",
            background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
            fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          }}
        >
          <div
            style={{
              backgroundColor: "white",
              padding: "40px",
              borderRadius: "10px",
              boxShadow: "0 4px 20px rgba(0, 0, 0, 0.1)",
              textAlign: "center",
              maxWidth: "500px",
              width: "100%",
            }}
          >
            <h1
              style={{
                color: "#2c3e50",
                fontSize: "2.2rem",
                marginBottom: "30px",
                fontWeight: "bold",
              }}
            >
              Energy Futures Dashboard
            </h1>
            <p
              style={{
                color: "#7f8c8d",
                marginBottom: "30px",
                fontSize: "1.1rem",
                lineHeight: "1.6",
              }}
            >
              Please sign in
            </p>
            <div
              style={{
                display: "inline-block",
                padding: "12px 24px",
                backgroundColor: "#3498db",
                color: "white",
                borderRadius: "4px",
                fontWeight: "bold",
                cursor: "pointer",
                transition: "all 0.3s ease",
                boxShadow: "0 2px 10px rgba(18, 127, 200, 0.3)",
              }}
            >
              <SignInButton />
            </div>
          </div>
        </div>
      </SignedOut>
      <SignedIn>
        <div
          className="app"
          style={{
            maxWidth: "2000px",
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
            {(
              [
                "RBOB Spreads",
                "HO Spreads",
                "Magellan Inventory",
                "A to Nap",
                "D to A",
              ] as ProductType[]
            ).map((tab) => (
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

          {activeTab === "Magellan Inventory" ? (
            <MagellanInventory />
          ) : activeTab === "A to Nap" || activeTab === "D to A" ? (
            <CsvSpreadChart
              type={activeTab === "A to Nap" ? "AtoNap" : "DtoA"}
            />
          ) : (
            <>
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
                    onChange={(e) =>
                      setStartMonth(e.target.value as MonthCode | "N")
                    }
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
                    onChange={(e) =>
                      setEndMonth(e.target.value as MonthCode | "Q")
                    }
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

              <div className="graph-wrapper">
                <div className="graph-container">
                  {isLoading ? (
                    <p style={{ textAlign: "center" }}>Loading data...</p>
                  ) : error ? (
                    <p className="error-message">{error}</p>
                  ) : startMonth && endMonth ? (
                    spreadData.size > 0 ? (
                      <>
                        <button
                          onClick={handleResetZoom}
                          className="reset-zoom-button"
                        >
                          <FaUndo />
                          Reset Zoom
                        </button>
                        <Line
                          ref={chartRef}
                          data={chartData}
                          options={chartOptions}
                        />
                      </>
                    ) : (
                      <p className="message">
                        No data available for the selected months
                      </p>
                    )
                  ) : (
                    <p className="message">
                      Please select both start and end months to view data
                    </p>
                  )}
                </div>
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
                    Last 30 Days Data - {currentYear || "Current Year"}
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
                                backgroundColor:
                                  index % 2 === 0 ? "#fff" : "#f8f9fa",
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
                                      fontFamily:
                                        "'Courier New', Courier, monospace",
                                      fontWeight:
                                        year === "2025" ? "bold" : "normal",
                                      color:
                                        year === "2025" ? "#2c3e50" : "inherit",
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
            </>
          )}
          <div style={{ position: "fixed", top: "20px", right: "20px" }}>
            <UserButton />
          </div>
        </div>
      </SignedIn>
    </header>
  );
};

export default App;
