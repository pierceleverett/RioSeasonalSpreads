import React, { useState, useEffect, useRef } from "react";
import { Line } from "react-chartjs-2";
import { TimeScale } from "chart.js";

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

import type {ChartData} from "chart.js"
import type { ChartOptions } from "chart.js";
import type { ChartDataset } from "chart.js";
import { Chart } from "chart.js";
import "chartjs-adapter-date-fns";

// Register ChartJS components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  TimeScale
);

interface TransitData {
  [cycle: string]: {
    [date: string]: number;
  };
}

interface ColonialTransitChartProps {
  routeName: string;
}

const ColonialTransitChart: React.FC<ColonialTransitChartProps> = () => {
  const [transitData, setTransitData] = useState<TransitData>({});
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRoute, setSelectedRoute] = useState<string>("HTNGBJ");
  const [selectedProduct, setSelectedProduct] = useState<string>("GAS");
  const [startCycle, setStartCycle] = useState<number>(1);
  const [endCycle, setEndCycle] = useState<number>(Infinity);

  const chartRef = useRef<Chart<
    "line",
    { x: string; y: number | null }[],
    unknown
  > | null>(null);


  const routeOptions = [
    { value: "HTNGBJ", label: "HTN to GBJ" },
    { value: "GBJLNJ", label: "GBJ to LNJ" },
  ];

  const productOptions = [
    { value: "GAS", label: "Gas" },
    { value: "DISTILLATES", label: "Distillates" },
  ];

  const handleRefresh = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(
        "https://rioseasonalspreads-production.up.railway.app/updateColonialTransit",
        {
          method: "POST",
        }
      );
      if (!response.ok) {
        throw new Error(`Refresh failed: ${response.status}`);
      }
      // Optionally re-fetch transit data after refresh
      const updatedData = await fetch(
        `https://rioseasonalspreads-production.up.railway.app/getColonialTransit?route=${selectedRoute}-${selectedProduct}`
      );
      if (!updatedData.ok) {
        throw new Error(`Data fetch failed: ${updatedData.status}`);
      }
      const data = await updatedData.json();
      setTransitData(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "An unknown error occurred"
      );
    } finally {
      setLoading(false);
    }
  };


  useEffect(() => {
    const fetchTransitData = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await fetch(
          `https://rioseasonalspreads-production.up.railway.app/getColonialTransit?route=${selectedRoute}-${selectedProduct}`
        );
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        setTransitData(data);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "An unknown error occurred"
        );
        setTransitData({});
      } finally {
        setLoading(false);
      }
    };

    fetchTransitData();
  }, [selectedRoute, selectedProduct]);

  // Destroy chart instance when component unmounts
  useEffect(() => {
    return () => {
      if (chartRef.current) {
        chartRef.current.destroy();
        chartRef.current = null;
      }
    };
  }, []);

  // Generate distinct colors for each cycle
const generateColors = (count: number): string[] => {
  const colors: string[] = [];
  const baseSaturation = 80;
  const baseLightness = 45;

  for (let i = 0; i < count; i++) {
    const hue = (i * 137.508) % 360; // Use golden angle for better distribution
    const saturation = baseSaturation + (i % 2 === 0 ? 0 : 10); // Slight variation
    const lightness = baseLightness + (i % 3) * 5; // Slight variation
    colors.push(`hsl(${hue}, ${saturation}%, ${lightness}%)`);
  }

  return colors;
};


const prepareChartData = (): ChartData<
  "line",
  { x: string; y: number | null }[]
> => {
  const cycles = Object.keys(transitData)
    .map((c) => parseInt(c))
    .filter((c) => !isNaN(c))
    .sort((a, b) => a - b);

  const filteredCycles = cycles
    .filter((c) => c >= startCycle && c <= endCycle)
    .map(String);

  const colors = generateColors(cycles.length);

  // Get all unique dates across all cycles
  const allDates = new Set<string>();
  cycles.forEach((cycle) => {
    const cycleData = transitData[cycle];
    if (cycleData) {
      // Add null check
      Object.keys(cycleData).forEach((date) => allDates.add(date));
    }
  });
  const sortedDates = Array.from(allDates).sort();

  const datasets: ChartDataset<"line", { x: string; y: number | null }[]>[] =
    filteredCycles.map((cycle, index) => {
      const cycleData = transitData[cycle];
      const data = sortedDates.map((date) => ({
        x: date,
        y: cycleData && cycleData[date] ? cycleData[date] : null, // Safe access
      }));

      return {
        label: `Cycle ${cycle}`,
        data: data,
        borderColor: colors[index],
        backgroundColor: colors[index],
        borderWidth: 1.5,
        pointRadius: 3,
        tension: 0.1,
      };
    });

  return {
    labels: sortedDates,
    datasets: datasets,
  };
};

  const chartData = prepareChartData();

  const options: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: "right",
        labels: {
          boxWidth: 12,
          padding: 20,
        },
      },
      title: {
        display: true,
        text: `${selectedRoute === "HTNGBJ" ? "HTN to GBJ" : "GBJ to LNJ"} ${
          selectedProduct === "GAS" ? "Gas" : "Distillates"
        } Transit Times by Cycle`,
        font: {
          size: 16,
        },
      },
      tooltip: {
        callbacks: {
          label: (context) => {
            return `Cycle ${context.dataset.label?.split(" ")[1]}: ${
              context.parsed.y?.toFixed(2) || "N/A"
            } days`;
          },
        },
      },
    },
    scales: {
      x: {
        type: "time",
        time: {
          unit: "day",
          tooltipFormat: "yyyy-MM-dd",
          displayFormats: {
            day: "MMM dd",
          },
        },
        title: {
          display: true,
          text: "Date",
        },
      },
      y: {
        title: {
          display: true,
          text: "Transit Time (days)",
        },
        ticks: {
          callback: (value) => `${value} days`,
        },
      },
    },
    interaction: {
      intersect: false,
      mode: "index",
    },
  };

  if (loading) {
    return <div>Loading transit data...</div>;
  }

  if (error) {
    return <div>Error: {error}</div>;
  }

  if (Object.keys(transitData).length === 0) {
    return <div>No transit data available</div>;
  }

  return (
    <div style={{ width: "100%", margin: "20px 0" }}>
      <div style={{ marginBottom: "20px", display: "flex", gap: "20px" }}>
        <div>
          <label htmlFor="route-select">Route: </label>
          <select
            id="route-select"
            value={selectedRoute}
            onChange={(e) => setSelectedRoute(e.target.value)}
          >
            {routeOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="product-select">Product: </label>
          <select
            id="product-select"
            value={selectedProduct}
            onChange={(e) => setSelectedProduct(e.target.value)}
          >
            {productOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <button
          onClick={handleRefresh}
          style={{ padding: "6px 12px", cursor: "pointer" }}
        >
          Refresh
        </button>
      </div>
      <div>
        <label htmlFor="start-cycle">Start Cycle: </label>
        <input
          type="number"
          id="start-cycle"
          value={startCycle}
          min={1}
          onChange={(e) => setStartCycle(Number(e.target.value))}
        />
      </div>
      <div>
        <label htmlFor="end-cycle">End Cycle: </label>
        <input
          type="number"
          id="end-cycle"
          value={endCycle === Infinity ? "" : endCycle}
          min={1}
          onChange={(e) => setEndCycle(Number(e.target.value) || Infinity)}
        />
      </div>

      <div style={{ height: "600px", position: "relative" }}>
        <Line
          key={`${selectedRoute}-${selectedProduct}-${Date.now()}`} // force remount
          data={chartData}
          options={options}
          ref={chartRef}
        />
      </div>
      <div style={{ marginTop: "10px", fontSize: "12px", color: "#666" }}>
        <p>
          Hover over lines to see cycle details. Click on legend items to toggle
          visibility.
        </p>
      </div>
    </div>
  );
};

export default ColonialTransitChart;
