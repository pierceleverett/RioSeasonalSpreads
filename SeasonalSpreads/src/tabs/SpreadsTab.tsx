import React, { useEffect, useState, useRef } from "react";
import { FaUndo } from "react-icons/fa";
import { Line } from "react-chartjs-2";
import zoomPlugin from "chartjs-plugin-zoom";

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

import type { ChartOptions, ChartData } from "chart.js";

// Register only what you use
ChartJS.register(
  zoomPlugin,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);


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

const SpreadsTab: React.FC = () => {
  const [commodity, setCommodity] = useState<"RBOB" | "HO">("RBOB");
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

  useEffect(() => {
    fetchSpreadData();
  }, [commodity, startMonth, endMonth]);

  const fetchSpreadData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetch(
        `https://rioseasonalspreads-production.up.railway.app/getSpread?commodity=${commodity}&startMonth=${startMonth}&endMonth=${endMonth}`
      );
      if (!response.ok) throw new Error("Failed to fetch spread data");
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

  const getLast30Dates = () => {
    const all = getAllDates();
    const valid = all.filter((d) => spreadData.get("2025")?.has(d));
    return (valid.length >= 30 ? valid.slice(-30) : all.slice(-30)).reverse();
  };

  const getYearColor = (year: string, opacity = 1): string => {
    const palette = [
      "rgba(0, 123, 255, OPACITY)",
      "rgba(40, 167, 69, OPACITY)",
      "rgba(255, 193, 7, OPACITY)",
      "rgba(220, 53, 69, OPACITY)",
      "rgba(23, 162, 184, OPACITY)",
      "rgba(108, 117, 125, OPACITY)",
    ];
    if (year === "5YEARAVG") return `rgba(255, 0, 0, ${opacity})`;
    const index = parseInt(year) % palette.length;
    return palette[index].replace("OPACITY", opacity.toString());
  };

  const allDates = getAllDates();
  const last30Dates = getLast30Dates();

  const chartData: ChartData<"line"> = {
    labels: allDates,
    datasets: [
      {
        label: "Range Max",
        data: allDates.map((d) =>
          Math.max(
            ...Array.from(spreadData.values()).map((m) => m.get(d) ?? -Infinity)
          )
        ),
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        pointRadius: 0,
        fill: "+1",
      },
      {
        label: "Range Min",
        data: allDates.map((d) =>
          Math.min(
            ...Array.from(spreadData.values()).map((m) => m.get(d) ?? Infinity)
          )
        ),
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        pointRadius: 0,
      },
      ...Array.from(spreadData.entries()).map(([year, yearMap]) => ({
        label: year,
        data: allDates.map((date) => yearMap.get(date) ?? null),
        borderColor: year === "2025" ? "orange" : getYearColor(year),
        backgroundColor: getYearColor(year, 0.5),
        borderWidth: year === "2025" || year.includes("AVG") ? 3 : 1,
        borderDash: year === "2025" || year.includes("AVG") ? [] : [5, 5],
        tension: 0.1,
        pointRadius: 0,
      })),
    ],
  };

  const chartOptions: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: "top",
        labels: {
          font: {
            size: 12,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
      title: {
        display: true,
        text: `${commodity} Spread (${startMonth}-${endMonth})`,
        font: {
          size: 18,
          weight: "bold",
          family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
        },
      },
      zoom: {
        pan: {
          enabled: true,
          mode: "xy",
        },
        zoom: {
          wheel: {
            enabled: true,
          },
          pinch: {
            enabled: true,
          },
          mode: "xy",
        },
      },
    },
    scales: {
      x: {
        title: {
          display: true,
          text: "Date (MM/DD)",
        },
        ticks: {
          maxRotation: 45,
          minRotation: 45,
          autoSkip: true,
          maxTicksLimit: 15,
          padding: 10,
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
        },
        ticks: {
          font: {
            size: 12,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
    },
  };

  return (
    <div style={{ padding: "20px", fontFamily: "Segoe UI" }}>
      <h2 style={{ textAlign: "center" }}>Spreads</h2>

      <div
        style={{
          display: "flex",
          justifyContent: "center",
          gap: "10px",
          marginBottom: "20px",
        }}
      >
        <select
          value={commodity}
          onChange={(e) => setCommodity(e.target.value as "RBOB" | "HO")}
        >
          <option value="RBOB">RBOB</option>
          <option value="HO">HO</option>
        </select>
        <select
          value={startMonth}
          onChange={(e) => setStartMonth(e.target.value as MonthCode)}
        >
          {monthOptions.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <select
          value={endMonth}
          onChange={(e) => setEndMonth(e.target.value as MonthCode)}
        >
          {monthOptions.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <p style={{ textAlign: "center" }}>Loading...</p>
      ) : error ? (
        <p style={{ textAlign: "center", color: "red" }}>{error}</p>
      ) : (
        <>
          <div className="graph-container">
            <button
              className="reset-zoom-button"
              onClick={() => chartRef.current?.resetZoom()}
            >
              <FaUndo /> Reset Zoom
            </button>
            <Line ref={chartRef} data={chartData} options={chartOptions} />
          </div>

          <div
            className="table-container"
            style={{
              marginTop: "40px",
              backgroundColor: "#fff",
              borderRadius: "8px",
              boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
              padding: "20px",
              overflowX: "auto",
            }}
          >
            <h3 style={{ textAlign: "center", marginBottom: "20px" }}>
              Last 30 Days Data - 2025
            </h3>
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                fontSize: "14px",
                fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
              }}
            >
              <thead>
                <tr style={{ backgroundColor: "#f8f9fa" }}>
                  <th
                    style={{
                      padding: "10px",
                      textAlign: "left",
                      borderBottom: "1px solid #ddd",
                    }}
                  >
                    Date
                  </th>
                  {Array.from(spreadData.keys())
                    .filter((year) =>
                      [
                        "2020",
                        "2021",
                        "2022",
                        "2023",
                        "2024",
                        "2025",
                        "5YEARAVG",
                        "10YEARAVG",
                      ].includes(year)
                    )
                    .map((year) => (
                      <th
                        key={year}
                        style={{
                          padding: "10px",
                          textAlign: "right",
                          borderBottom: "1px solid #ddd",
                        }}
                      >
                        {year}
                      </th>
                    ))}
                </tr>
              </thead>
              <tbody>
                {last30Dates.map((date, index) => (
                  <tr
                    key={index}
                    style={{
                      backgroundColor: index % 2 === 0 ? "#fff" : "#f8f9fa",
                    }}
                  >
                    <td style={{ padding: "10px", fontWeight: "bold" }}>
                      {date}
                    </td>
                    {Array.from(spreadData.keys())
                      .filter((year) =>
                        [
                          "2020",
                          "2021",
                          "2022",
                          "2023",
                          "2024",
                          "2025",
                          "5YEARAVG",
                          "10YEARAVG",
                        ].includes(year)
                      )
                      .map((year) => {
                        const value = spreadData.get(year)?.get(date);
                        return (
                          <td
                            key={year}
                            style={{
                              padding: "10px",
                              textAlign: "right",
                              fontFamily: "Courier New, monospace",
                              fontWeight: year === "2025" ? "bold" : "normal",
                              color: year === "2025" ? "#2c3e50" : "inherit",
                            }}
                          >
                            {value?.toFixed(4) ?? "N/A"}
                          </td>
                        );
                      })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
};

export default SpreadsTab;
