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
} from "chart.js";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
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

interface SpreadData {
  [date: string]: number;
}

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ProductType>("RBOB");
  const [startMonth, setStartMonth] = useState<MonthCode | "">("");
  const [endMonth, setEndMonth] = useState<MonthCode | "">("");
  const [spreadData, setSpreadData] = useState<SpreadData>({});
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
    setSpreadData({});
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
      setSpreadData(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "An unknown error occurred"
      );
      setSpreadData({});
    } finally {
      setIsLoading(false);
    }
  };

  const formatDate = (dateStr: string) => {
    // Parse as UTC to prevent timezone-related day shifts
    const date = new Date(dateStr + "T00:00:00Z");
    return `${date.getUTCMonth() + 1}/${date.getUTCDate()}`;
  };

  const sortedDates = Object.keys(spreadData).sort();

  const chartData = {
    labels: sortedDates.map(formatDate),
    datasets: [
      {
        label: `${activeTab} Spread (${startMonth}-${endMonth})`,
        data: sortedDates.map((date) => spreadData[date]),
        borderColor: "rgb(75, 192, 192)",
        backgroundColor: "rgba(75, 192, 192, 0.5)",
        tension: 0.1,
      },
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
            const dateLabel = sortedDates[context.dataIndex];
            const value = context.parsed.y;
            return `${dateLabel}: ${value.toFixed(4)}`;
          },
        },
      },
    },
    scales: {
      x: {
        title: {
          display: true,
          text: "Date",
        },
      },
      y: {
        title: {
          display: true,
          text: "Spread ($/gal)",
        },
      },
    },
  };

  return (
    <div className="app">
      <h1>Energy Futures Dashboard</h1>

      <div className="tabs">
        {(["RBOB", "HO"] as ProductType[]).map((tab) => (
          <button
            key={tab}
            className={`tab ${activeTab === tab ? "active" : ""}`}
            onClick={() => handleTabChange(tab)}
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
          Object.keys(spreadData).length > 0 ? (
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
