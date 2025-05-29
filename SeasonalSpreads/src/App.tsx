import React, { useState, useEffect } from "react";
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

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
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
        `http://localhost:3232/getSpread?product=${activeTab}&startMonth=${startMonth}&endMonth=${endMonth}`
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
  if (spreadData.size === 0) return [];

  // Collect all dates from all datasets
  const allDates = new Set<string>();
  spreadData.forEach((yearData) => {
    yearData.forEach((_, date) => allDates.add(date));
  });

  // Convert to array and sort chronologically
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

  const chartData = {
    labels: allDates,
    datasets: [
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

  const chartOptions = {
    responsive: true,
    plugins: {
      legend: {
        position: "top" as const,
      },
      title: {
        display: true,
        text: `${activeTab} Spread Analysis (${startMonth}-${endMonth})`,
      },
      tooltip: {
        callbacks: {
          label: (context: any) => {
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
        },
        min: allDates[0], // Set first date as min
        max: allDates[allDates.length - 1], // Set last date as max
        ticks: {
          autoSkip: true,
          maxRotation: 0,
          maxTicksLimit: 12, // Show about one tick per month
          callback: (value: string | number) => {
            if (typeof value === "string") return value;
            // Only show every nth label to prevent crowding
            const index = Number(value);
            if (
              allDates.length <= 12 ||
              index % Math.ceil(allDates.length / 12) === 0
            ) {
              return allDates[index];
            }
            return "";
          },
        },
      },
      y: {
        title: {
          display: true,
          text: "Spread ($/gal)",
        },
      },
    },
    elements: {
      line: {
        tension: 0.1,
        spanGaps: true,
      },
      point: {
        radius: 0, // Hide points
      },
    },
  };
  return (
    <div className="app">
      <h1>Energy Futures Dashboard</h1>

      <div className="tabs">
        {["RBOB", "HO"].map((tab) => (
          <button
            key={tab}
            className={`tab ${activeTab === tab ? "active" : ""}`}
            onClick={() => handleTabChange(tab as ProductType)}
          >
            {tab}
          </button>
        ))}
      </div>

      <div className="month-selector">
        <div className="month-dropdown">
          <label>Start month: </label>
          <select
            value={startMonth}
            onChange={(e) => setStartMonth(e.target.value as MonthCode | "")}
          >
            <option value="">-- Select --</option>
            {monthOptions.map((month) => (
              <option key={`start-${month}`} value={month}>
                {month}
              </option>
            ))}
          </select>
        </div>

        <div className="month-dropdown">
          <label>End month: </label>
          <select
            value={endMonth}
            onChange={(e) => setEndMonth(e.target.value as MonthCode | "")}
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

      <div className="graph-container">
        {isLoading ? (
          <p>Loading data...</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : startMonth && endMonth ? (
          spreadData.size > 0 ? (
            <Line data={chartData} options={chartOptions} />
          ) : (
            <p>No data available for the selected months</p>
          )
        ) : (
          <p className="prompt">
            Please select both start and end months to view data
          </p>
        )}
      </div>
    </div>
  );
};

export default App;
