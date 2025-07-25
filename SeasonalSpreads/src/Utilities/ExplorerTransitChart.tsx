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

interface YearlyTransitData {
  [year: string]: {
    [date: string]: string;
  };
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

// Colors for different years (excluding the latest year which will be orange)
const YEAR_COLORS = [
  "rgba(54, 162, 235, 0.8)", // blue
  "rgba(75, 192, 192, 0.8)", // green
  "rgba(153, 102, 255, 0.8)", // purple
  "rgba(255, 159, 64, 0.8)", // orange
  "rgba(199, 199, 199, 0.8)", // gray
];

const ExplorerTransitChart: React.FC = () => {
  const [transitData, setTransitData] = useState<YearlyTransitData>({});
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
  const prepareChartData = () => {
    if (Object.keys(transitData).length === 0)
      return { labels: [], datasets: [] };

    // Get sorted years (newest first)
    const years = Object.keys(transitData).sort(
      (a, b) => parseInt(b) - parseInt(a)
    );
    const latestYear = years[0];

    // Get all unique month-day combinations across all years
    const allDates = new Set<string>();
    years.forEach((year) => {
      Object.keys(transitData[year]).forEach((fullDate) => {
        const [, month, day] = fullDate.split("-");
        allDates.add(`${month}-${day}`);
      });
    });

    const sortedLabels = Array.from(allDates).sort((a, b) => {
      const [aMonth, aDay] = a.split("-").map(Number);
      const [bMonth, bDay] = b.split("-").map(Number);
      return aMonth - bMonth || aDay - bDay;
    });

    // Create datasets for each year
    const datasets = years.map((year, index) => {
      const isLatestYear = year === latestYear;
      const colorIndex = index >= 1 ? (index - 1) % YEAR_COLORS.length : 0;

      return {
        label: year,
        data: sortedLabels.map((label) => {
          // Find the corresponding full date in this year's data
          const fullDateKey = Object.keys(transitData[year]).find(
            (fullDate) => {
              const [, month, day] = fullDate.split("-");
              return `${month}-${day}` === label;
            }
          );
          return fullDateKey
            ? parseFloat(transitData[year][fullDateKey])
            : null;
        }),
        borderColor: isLatestYear
          ? "rgba(255, 99, 71, 1)"
          : YEAR_COLORS[colorIndex],
        backgroundColor: "rgba(0, 0, 0, 0)",
        borderWidth: 1, // Thinner lines for all years
        borderDash: isLatestYear ? [] : [3, 3], // Smaller dashes for older years
        tension: 0.1,
        pointRadius: 2, // Smaller points for all years
        pointHoverRadius: 4, // Slightly larger on hover
        pointBackgroundColor: isLatestYear
          ? "rgba(255, 99, 71, 1)"
          : YEAR_COLORS[colorIndex],
        fill: false,
        spanGaps: true, // Connect lines across gaps in data
      };
    });

    return {
      labels: sortedLabels,
      datasets,
    };
  };

  const chartData = prepareChartData();

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
          usePointStyle: true,
          padding: 20,
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
            return `${context.dataset.label}: ${
              context.parsed.y !== null
                ? context.parsed.y.toFixed(1) + " days"
                : "no data"
            }`;
          },
        },
        displayColors: true,
        usePointStyle: true,
        bodyFont: {
          size: 12,
        },
      },
    },
    scales: {
      x: {
        title: {
          display: true,
          text: "Date (Month-Day)",
          font: {
            size: 14,
            weight: "bold",
          },
        },
        grid: {
          display: false,
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
        grid: {
          color: "rgba(0, 0, 0, 0.1)",
        },
      },
    },
    elements: {
      line: {
        tension: 0.2, // Slightly smoother curves
      },
      point: {
        radius: 2, // Consistent small point size
        hoverRadius: 4, // Slightly larger on hover
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
