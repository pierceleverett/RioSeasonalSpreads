import React, { useEffect, useState, useRef } from "react";
import { Line } from "react-chartjs-2";
import { Chart as ChartJS } from "chart.js";
import type { ChartOptions } from "chart.js";
import { FaUndo } from "react-icons/fa";
import { useUser } from "@clerk/clerk-react"; 

interface CsvSpreadChartProps {
  type: "91Chi" | "ChiCBOB";
}

const CsvSpreadChart: React.FC<CsvSpreadChartProps> = ({ type }) => {
  const [dataMap, setDataMap] = useState<Map<string, Map<string, number>>>(
    new Map()
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tariffConstant, setTariffConstant] = useState(24.319);
  const [isSaving, setIsSaving] = useState(false);
  const chartRef = useRef<ChartJS<"line"> | null>(null);
  const { user } = useUser(); 

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
  const yearList = prevYears.filter((year) => !year.includes("AVG"));

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

  const [refreshStatus, setRefreshStatus] = useState<{
    message: string;
    color: string;
  }>({
    message: "Checking...",
    color: "gray",
  });

  const checkDataFreshness = () => {
    if (!dataMap || dataMap.size === 0) {
      setRefreshStatus({ message: "No Data Available", color: "gray" });
      return;
    }

    const currentYearData = dataMap.get(currentYear);
    if (!currentYearData || currentYearData.size === 0) {
      setRefreshStatus({ message: "No Current Data", color: "gray" });
      return;
    }

    const sortedDates = Array.from(currentYearData.keys())
      .map((d) => d.replace("/", "-"))
      .sort((a, b) => {
        const [aMonth, aDay] = a.split("-").map(Number);
        const [bMonth, bDay] = b.split("-").map(Number);
        return (
          new Date(2000, aMonth - 1, aDay).getTime() -
          new Date(2000, bMonth - 1, bDay).getTime()
        );
      });

    const lastDateStr = sortedDates.pop();
    if (!lastDateStr) {
      setRefreshStatus({ message: "No Dates Found", color: "gray" });
      return;
    }

    const [month, day] = lastDateStr.split("-").map(Number);
    const lastDataDate = new Date(new Date().getFullYear(), month - 1, day);
    lastDataDate.setHours(0, 0, 0, 0);

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);

    if (lastDataDate.getTime() === today.getTime()) {
      setRefreshStatus({ message: "Data Is Up-To-Date", color: "green" });
    } else if (lastDataDate.getTime() === yesterday.getTime()) {
      setRefreshStatus({
        message: "Data Is Up-To-Date (Yesterday)",
        color: "green",
      });
    } else {
      setRefreshStatus({
        message: `Data Stale (Last: ${lastDateStr})`,
        color: "red",
      });
    }
  };
  

  


useEffect(() => {
  if (type === "91Chi" && user?.unsafeMetadata?.tariffConstant91Chi) {
    setTariffConstant(Number(user.unsafeMetadata.tariffConstant91Chi));
  }
}, [user, type]);

const saveTariffConstant = async () => {
  if (!user) return;
  setIsSaving(true);
  try {
    await user?.update({
      unsafeMetadata: {
        tariffConstant91Chi: tariffConstant,
      },
    });
    
    
    alert("Tariff constant saved!");
  } catch (err) {
    console.error("Failed to save tariff constant:", err);
    alert("Failed to save.");
  } finally {
    setIsSaving(false);
  }
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
        checkDataFreshness();
      } catch (err) {
        setError("Failed to load data");
      } finally {
        setIsLoading(false);
      }
    };
    fetchData();
  }, [type]);

  useEffect(() => {
    if (dataMap.size > 0) {
      checkDataFreshness();
    }
  }, [dataMap]);
  

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
        .map(([_, yearMap]) => {
          const val = yearMap.get(date);
          return val !== undefined && val !== null && type === "91Chi"
            ? val + adjustment
            : val;
        })
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



  const get30DatesWith2025Data = (): string[] => {
    const datesWith2025 = allDates.filter((date) =>
      dataMap.get(currentYear)?.has(date)
    );
    const recent =
      datesWith2025.length > 0 ? datesWith2025.slice(-30) : allDates.slice(-30);
    return recent.reverse();
  };

  const datesToDisplay = get30DatesWith2025Data();

  const adjustment = type === "91Chi" ? 24.319 - tariffConstant : 0;

  const rangeData = React.useMemo(
    () => calculateMinMaxRange(),
    [dataMap, allDates, type, adjustment]
  );

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
      ...yearList.map((year) => ({
        label: year,
        data: allDates.map((date) => {
          const val = dataMap.get(year)?.get(date) ?? null;
          return val !== null && type === "91Chi" ? val + adjustment : val;
        }),
        borderColor: getYearColor(year),
        backgroundColor: getYearColor(year, 0.5),
        borderWidth: year === currentYear ? 3 : 1,
        borderDash: year === currentYear ? [] : [5, 5],
        tension: 0.1,
        pointRadius: 0,
      })),
      ...(dataMap.has("5YEARAVG")
        ? [
            {
              label: "5-Year Average",
              data: allDates.map((date) => {
                const val = dataMap.get("5YEARAVG")?.get(date) ?? null;
                return val !== null && type === "91Chi"
                  ? val + adjustment
                  : val;
              }),
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
        text:
          type === "91Chi"
            ? "91 Chi Less USGC 93 + Transport"
            : type === "ChiCBOB"
            ? "BCX RBOB - BCX CBOB"
            : "Spread Chart",
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
          text: "Spread (cents/gal)",
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
    <>
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          gap: "20px",
          marginBottom: "20px",
        }}
      >
        <button
          onClick={() => {
            setIsLoading(true);
            fetch(
              `https://rioseasonalspreads-production.up.railway.app/updateGC`
            )
              .then((res) => res.json())
              .then((json) => {
                const parsed = new Map<string, Map<string, number>>(
                  Object.entries(json).map(([year, entries]) => [
                    year,
                    new Map(Object.entries(entries as Record<string, number>)),
                  ])
                );
                setDataMap(parsed);
                checkDataFreshness();
              })
              .catch(() => setError("Failed to refresh data"))
              .finally(() => setIsLoading(false));
          }}
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
        <span
          style={{
            color: refreshStatus.color,
            fontWeight: "bold",
            fontSize: "clamp(12px, 1.2vw, 16px)",
          }}
        >
          {refreshStatus.message}
        </span>
      </div>

      <div className="graph-container">
        <button
          className="reset-zoom-button"
          onClick={() => chartRef.current?.resetZoom()}
        >
          <FaUndo /> Reset Zoom
        </button>

        {isLoading ? (
          <p style={{ textAlign: "center" }}>Loading chart data...</p>
        ) : error ? (
          <p style={{ textAlign: "center", color: "red" }}>{error}</p>
        ) : (
          <Line ref={chartRef} data={chartData} options={chartOptions} />
        )}
      </div>
      {type === "91Chi" && (
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            marginBottom: "10px",
            width: "100%",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "10px",
            }}
          >
            <label>
              Input Tariff and Fee Constant (cents/gal):
              <input
                type="number"
                value={tariffConstant}
                onChange={(e) => setTariffConstant(parseFloat(e.target.value))}
                step="0.001"
                style={{ marginLeft: "10px" }}
              />
            </label>
            <button onClick={saveTariffConstant} disabled={isSaving}>
              {isSaving ? "Saving..." : "Save"}
            </button>
          </div>
        </div>
      )}

      {!isLoading && !error && dataMap.size > 0 && (
        <div className="table-container">
          <h3>Last 30 Days Data - {currentYear}</h3>
          <table>
            <thead>
              <tr>
                <th>Date</th>
                {[...yearList, "5YEARAVG"].map((year) => (
                  <th key={year}>{year}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {datesToDisplay.map((date, index) => (
                <tr key={index}>
                  <td>{date}</td>
                  {[...yearList, "5YEARAVG"].map((year) => {
                    const value = dataMap.get(year)?.get(date);
                    const adjusted =
                      value !== undefined && value !== null && type === "91Chi"
                        ? value + adjustment
                        : value;
                    return (
                      <td
                        key={year}
                        style={{
                          fontFamily: "'Courier New', Courier, monospace",
                          fontWeight: year === currentYear ? "bold" : "normal",
                          color: year === currentYear ? "#2c3e50" : "inherit",
                        }}
                      >
                        {adjusted?.toFixed(4) ?? "N/A"}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
};

export default CsvSpreadChart;
