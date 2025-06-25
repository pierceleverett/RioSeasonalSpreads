import React, { useState, useEffect, useRef } from "react";
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
import { FaUndo } from "react-icons/fa";
import type { ChartOptions } from "chart.js";

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

interface TransitData {
  [date: string]: number;
}

const ROUTE_OPTIONS = [
  "Port Arthur-Houston",
  "Port Arthur-Greenville",
  "Pasadena-Greenville",
  "Greenville-Glenpool",
  "Port Arthur-Glenpool",
  "Pasadena-Glenpool",
  "Port Arthur-Wood River",
  "Glenpool-Wood River",
  "Wood River-Hammond",
  "Glenpool-Hammond Area",
  "HOUSTON - HMD TRANSIT",
  "STORAGE",
  "DAYS",
];

const ExplorerTransitChart: React.FC = () => {
  const [transitData, setTransitData] = useState<TransitData>({});
  const [selectedRoute, setSelectedRoute] = useState<string>(ROUTE_OPTIONS[10]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chartRef = useRef<ChartJS<"line"> | null>(null);

  useEffect(() => {
    const fetchTransitData = async () => {
      if (!selectedRoute) return;

      setIsLoading(true);
      setError(null);

      try {
        const response = await fetch(
          `https://rioseasonalspreads-production.up.railway.app/getExplorerData?route=${encodeURIComponent(
            selectedRoute
          )}`
        );
        if (!response.ok) {
          throw new Error("Failed to fetch transit data");
        }
        const data = await response.json();
        setTransitData(data);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "An unknown error occurred"
        );
        setTransitData({});
      } finally {
        setIsLoading(false);
      }
    };

    fetchTransitData();
  }, [selectedRoute]);

  const handleResetZoom = () => {
    if (chartRef.current) {
      chartRef.current.resetZoom();
    }
  };

  const handleRouteChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedRoute(e.target.value);
  };

  // Prepare chart data
  const chartData = {
    labels: Object.keys(transitData).sort(),
    datasets: [
      {
        label: `${selectedRoute} Transit Time (days)`,
        data: Object.keys(transitData)
          .sort()
          .map((date) => transitData[date]),
        borderColor: "rgba(75, 192, 192, 1)",
        backgroundColor: "rgba(75, 192, 192, 0.2)",
        borderWidth: 2,
        tension: 0.1,
        fill: true,
        pointRadius: 4,
        pointHoverRadius: 6,
      },
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
        position: "top" as const,
        labels: {
          font: {
            size: 14,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
      title: {
        display: true,
        text: `Explorer Transit Times - ${selectedRoute}`,
        font: {
          size: 18,
          weight: "bold",
          family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
        },
      },
      tooltip: {
        callbacks: {
          label: (context: any) => {
            return `${context.dataset.label}: ${context.parsed.y} days`;
          },
        },
      },
    },
    scales: {
      x: {
        title: {
          display: true,
          text: "Date",
          font: {
            size: 14,
            weight: "bold",
          },
        },
      },
      y: {
        title: {
          display: true,
          text: "Transit Time (days)",
          font: {
            size: 14,
            weight: "bold",
          },
        },
        min: 0,
      },
    },
  };

  return (
    <div style={{ padding: "20px", maxWidth: "1200px", margin: "0 auto" }}>
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          marginBottom: "20px",
        }}
      >
        <label
          htmlFor="route-select"
          style={{
            marginBottom: "8px",
            fontWeight: "bold",
            fontSize: "16px",
          }}
        >
          Select Route:
        </label>
        <select
          id="route-select"
          value={selectedRoute}
          onChange={handleRouteChange}
          style={{
            padding: "10px",
            fontSize: "16px",
            borderRadius: "4px",
            border: "1px solid #ccc",
            width: "100%",
            maxWidth: "400px",
          }}
        >
          {ROUTE_OPTIONS.map((route) => (
            <option key={route} value={route}>
              {route}
            </option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: "40px" }}>
          Loading data...
        </div>
      ) : error ? (
        <div style={{ color: "red", textAlign: "center" }}>{error}</div>
      ) : Object.keys(transitData).length > 0 ? (
        <>
          <button
            onClick={handleResetZoom}
            style={{
              padding: "8px 16px",
              background: "#f0f0f0",
              border: "1px solid #ccc",
              borderRadius: "4px",
              cursor: "pointer",
              marginBottom: "10px",
              display: "flex",
              alignItems: "center",
              gap: "8px",
              marginLeft: "auto",
              marginRight: "auto",
            }}
          >
              <FaUndo />  Reset Zoom
          </button>

          <div style={{ height: "500px" }}>
            <Line ref={chartRef} data={chartData} options={chartOptions} />
          </div>
        </>
      ) : (
        <div style={{ textAlign: "center", padding: "40px" }}>
          No data available for the selected route
        </div>
      )}
    </div>
  );
};

export default ExplorerTransitChart;
