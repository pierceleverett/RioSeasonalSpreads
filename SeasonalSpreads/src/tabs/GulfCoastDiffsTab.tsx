import React, { useEffect, useState, useRef } from "react";
import { FaUndo } from "react-icons/fa";
import { Line } from "react-chartjs-2";
import { Chart as ChartJS } from "chart.js";
import type { ChartOptions, ChartData } from "chart.js";

const GulfCoastDiffsTab: React.FC = () => {
  const [code1, setCode1] = useState("D");
  const [code2, setCode2] = useState("A");
  const [dataMap, setDataMap] = useState<Map<string, Map<string, number>>>(
    new Map()
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chartRef = useRef<ChartJS<"line"> | null>(null);
    const currentYear = new Date().getFullYear().toString();
    const prevYears = [
      currentYear,
      (parseInt(currentYear) - 1).toString(),
      (parseInt(currentYear) - 2).toString(),
      (parseInt(currentYear) - 3).toString(),
      (parseInt(currentYear) - 4).toString(),
      (parseInt(currentYear) - 5).toString(),
      "5YEARAVG",
      "10YEARAVG",
    ];

  const codeOptions = ["A", "D", "F", "M", "H", "Nap"];

const handleRefresh = async () => {
  try {
    setIsLoading(true);
    const response = await fetch(
      "https://rioseasonalspreads-production.up.railway.app/updateGC",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
      }
    );

    if (!response.ok) {
      throw new Error("Failed to update spread data");
    }

    console.log("Spread data updated successfully");
    // Refresh the data after updating
    await fetchGCSpreads();
  } catch (error) {
    console.error("Error updating spread data:", error);
    setError(error instanceof Error ? error.message : "Failed to refresh data");
  } finally {
    setIsLoading(false);
  }
};

    

  useEffect(() => {
    fetchGCSpreads();
  }, [code1, code2]);

  const fetchGCSpreads = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetch(
        `https://rioseasonalspreads-production.up.railway.app/getGCSpreads?code1=${code1}&code2=${code2}`
      );
      if (!response.ok)
        throw new Error("Failed to fetch Gulf Coast spread data");
      const data = await response.json();
      const parsed = new Map<string, Map<string, number>>(
        Object.entries(data).map(([year, entries]) => [
          year,
          new Map(Object.entries(entries as Record<string, number>)),
        ])
      );
      setDataMap(parsed);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "An unknown error occurred"
      );
      setDataMap(new Map());
    } finally {
      setIsLoading(false);
    }
  };

  const getAllDates = (): string[] => {
    const allDates = new Set<string>();
    dataMap.forEach((yearData) => {
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

const chartData: ChartData<"line"> = {
  labels: allDates,
  datasets: [
    {
      label: "Range Min",
      data: allDates.map((d) =>
        Math.min(
          ...Array.from(dataMap.values()).map((m) => m.get(d) ?? Infinity)
        )
      ),
      backgroundColor: "rgba(200, 200, 200, 0.2)",
      borderColor: "rgba(200, 200, 200, 0)",
      pointRadius: 0,
      fill: "+1", // Fill to the next dataset (Range Max)
    },
    {
      label: "Range Max",
      data: allDates.map((d) =>
        Math.max(
          ...Array.from(dataMap.values()).map((m) => m.get(d) ?? -Infinity)
        )
      ),
      backgroundColor: "rgba(200, 200, 200, 0.2)",
      borderColor: "rgba(200, 200, 200, 0)",
      pointRadius: 0,
      fill: false, // No fill above
    },
    ...Array.from(dataMap.entries()).map(([year, yearMap]) => ({
      label: year,
      data: allDates.map((date) => yearMap.get(date) ?? null),
      borderColor: year === currentYear ? "orange" : getYearColor(year),
      backgroundColor: getYearColor(year, 0.5),
      borderWidth: year === currentYear || year.includes("AVG") ? 3 : 1,
      borderDash: year === currentYear || year.includes("AVG") ? [] : [5, 5],
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
      text: `Gulf Coast Diff: ${code1} to ${code2}`,
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


  const getLast30Dates = () => {
    const all = getAllDates();
    const valid = all.filter((d) => dataMap.get(currentYear)?.has(d));
    return (valid.length >= 30 ? valid.slice(-30) : all.slice(-30)).reverse();
  };

  const last30Days = getLast30Dates();

const selectedYears = Array.from(dataMap.keys()).filter((year) =>
  prevYears.includes(year)
);


return (
  <div>
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        gap: "20px",
        marginBottom: "20px",
      }}
    >
      <label>
        <select value={code1} onChange={(e) => setCode1(e.target.value)}>
          {codeOptions.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </label>
      To
      <label>
        <select value={code2} onChange={(e) => setCode2(e.target.value)}>
          {codeOptions.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </label>
      <button
        onClick={handleRefresh}
        disabled={isLoading}
        style={{
          padding: "8px 16px",
          backgroundColor: "#4CAF50",
          color: "white",
          border: "none",
          borderRadius: "4px",
          cursor: "pointer",
        }}
      >
        {isLoading ? "Refreshing..." : "Refresh Data"}
      </button>
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

        <div className="table-container" style={{ marginTop: "40px" }}>
          <h3 style={{ textAlign: "center", marginBottom: "20px" }}>
            Last 30 Days Data
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
                {selectedYears.map((year) => (
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
              {last30Days.map((date, index) => (
                <tr
                  key={date}
                  style={{
                    backgroundColor: index % 2 === 0 ? "#fff" : "#f8f9fa",
                  }}
                >
                  <td style={{ padding: "10px", fontWeight: "bold" }}>
                    {date}
                  </td>
                  {selectedYears.map((year) => (
                    <td
                      key={year}
                      style={{
                        padding: "10px",
                        textAlign: "right",
                        fontFamily: "Courier New, monospace",
                        fontWeight: year === currentYear ? "bold" : "normal",
                        color: year === currentYear ? "#2c3e50" : "inherit",
                      }}
                    >
                      {dataMap.get(year)?.get(date)?.toFixed(4) ?? "N/A"}
                    </td>
                  ))}
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

export default GulfCoastDiffsTab;
