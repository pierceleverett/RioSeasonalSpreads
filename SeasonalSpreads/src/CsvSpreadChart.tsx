import React, { useEffect, useState, useRef } from "react";
import { Line } from "react-chartjs-2";
import { Chart as ChartJS } from "chart.js";
import type { ChartOptions } from "chart.js";
import { FaUndo } from "react-icons/fa";

interface CsvSpreadChartProps {
  type: "AtoNap" | "DtoA";
}

const CsvSpreadChart: React.FC<CsvSpreadChartProps> = ({ type }) => {
  const [dataMap, setDataMap] = useState<Map<string, Map<string, number>>>(
    new Map()
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chartRef = useRef<ChartJS<"line"> | null>(null);

  const colorPalette = [
    "rgba(0, 123, 255, OPACITY)",
    "rgba(40, 167, 69, OPACITY)",
    "rgba(255, 193, 7, OPACITY)",
    "rgba(220, 53, 69, OPACITY)",
    "rgba(23, 162, 184, OPACITY)",
    "rgba(108, 117, 125, OPACITY)",
  ];

  const yearColorMap = new Map<string, string>();
  const getYearColor = (year: string, opacity = 1): string => {
    if (year === "5YEARAVG") return `rgba(255, 0, 0, ${opacity})`;
    if (!yearColorMap.has(year)) {
      const index = yearColorMap.size % colorPalette.length;
      const color = colorPalette[index].replace("OPACITY", opacity.toString());
      yearColorMap.set(year, color);
    }
    return yearColorMap.get(year)!;
  };

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const res = await fetch(
          `https://rioseasonalspreads-production.up.railway.app/getBetweenSpreads?type=${type}`
        );
        const json = await res.json();
        const parsed = new Map<string, Map<string, number>>(
          Object.entries(json).map(([year, entries]) => [
            year,
            new Map(Object.entries(entries as Record<string, number>)),
          ])
        );
        setDataMap(parsed);
      } catch (err) {
        setError("Failed to load data");
      } finally {
        setIsLoading(false);
      }
    };
    fetchData();
  }, [type]);

  const getAllDates = () => {
    const allDates = new Set<string>();
    dataMap.forEach((yearMap) => {
      yearMap.forEach((_val, date) => allDates.add(date));
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

  const allDates = getAllDates();

  const calculateMinMaxRange = () => {
    const min: (number | null)[] = [];
    const max: (number | null)[] = [];
    allDates.forEach((date) => {
      const values = Array.from(dataMap.entries())
        .filter(([year]) => year !== "5YEARAVG")
        .map(([_, yearMap]) => yearMap.get(date))
        .filter((v): v is number => v !== undefined && v !== null);
      if (values.length > 0) {
        min.push(Math.min(...values));
        max.push(Math.max(...values));
      } else {
        min.push(null);
        max.push(null);
      }
    });
    return { min, max };
  };

  const rangeData = calculateMinMaxRange();

   const get30DatesWith2025Data = (): string[] => {
     const datesWith2025 = allDates.filter((date) =>
       dataMap.get("2025")?.has(date)
     );
     const recent =
       datesWith2025.length > 0
         ? datesWith2025.slice(-30)
         : allDates.slice(-30);
     return recent.reverse();
   };

   const datesToDisplay = get30DatesWith2025Data();

  const chartData = {
    labels: allDates,
    datasets: [
      {
        label: "Value Range Max",
        data: rangeData.max,
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        borderWidth: 0,
        pointRadius: 0,
        fill: "+1",
      },
      {
        label: "Value Range Min",
        data: rangeData.min,
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        borderWidth: 0,
        pointRadius: 0,
      },
      ...["2020", "2021", "2022", "2023", "2024", "2025"].map((year) => ({
        label: year,
        data: allDates.map((date) => dataMap.get(year)?.get(date) ?? null),
        borderColor: getYearColor(year),
        backgroundColor: getYearColor(year, 0.5),
        borderWidth: year === "2025" ? 3 : 1,
        borderDash: year === "2025" ? [] : [5, 5],
        tension: 0.1,
        pointRadius: 0,
      })),
      ...(dataMap.has("5YEARAVG")
        ? [
            {
              label: "5-Year Average",
              data: allDates.map(
                (date) => dataMap.get("5YEARAVG")?.get(date) ?? null
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
        text: `${type} Spread Chart`,
        font: {
          size: 18,
          weight: "bold",
          family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
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
    <div
      style={{
        marginTop: "20px",
        display: "flex",
        flexDirection: "column",
        gap: "20px",
        alignItems: "center",
        width: "100%",
        maxWidth: "1400px",
        margin: "0 auto",
      }}
    >
      <div
        style={{
          position: "relative",
          width: "100%",
          height: "90vh", // Increased height
          backgroundColor: "#fff",
          borderRadius: "8px",
          boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
          padding: "20px",
        }}
      >
        <button
          onClick={() => chartRef.current?.resetZoom()}
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
            fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          }}
        >
          <FaUndo />
          Reset Zoom
        </button>
        {isLoading ? (
          <p style={{ textAlign: "center" }}>Loading chart data...</p>
        ) : error ? (
          <p style={{ textAlign: "center", color: "red" }}>{error}</p>
        ) : (
          <Line ref={chartRef} data={chartData} options={chartOptions} />
        )}
      </div>

      {/* Restored Table Section */}
      {!isLoading && !error && dataMap.size > 0 && (
        <div
          style={{
            width: "100%",
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
              textAlign: "center",
              fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
            }}
          >
            Last 30 Days Data - 2025
          </h2>
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "1rem",
              fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
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
                {[
                  "2020",
                  "2021",
                  "2022",
                  "2023",
                  "2024",
                  "2025",
                  "5YEARAVG",
                ].map((year) => (
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
              {datesToDisplay.map((date, index) => (
                <tr
                  key={index}
                  style={{
                    borderBottom: "1px solid #eee",
                    backgroundColor: index % 2 === 0 ? "#fff" : "#f8f9fa",
                  }}
                >
                  <td
                    style={{
                      padding: "12px",
                      borderBottom: "1px solid #eee",
                      fontWeight: dataMap.get("2025")?.has(date)
                        ? "bold"
                        : "normal",
                    }}
                  >
                    {date}
                  </td>
                  {[
                    "2020",
                    "2021",
                    "2022",
                    "2023",
                    "2024",
                    "2025",
                    "5YEARAVG",
                  ].map((year) => {
                    const value = dataMap.get(year)?.get(date);
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
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default CsvSpreadChart;
