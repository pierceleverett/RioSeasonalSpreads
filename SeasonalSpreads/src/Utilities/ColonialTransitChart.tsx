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
  TimeScale,
} from "chart.js";
import "chartjs-adapter-date-fns";

import type {ChartType, ChartOptions, ChartDataset, ScatterDataPoint} from "chart.js"



// Extend Chart.js types to include our custom property
declare module 'chart.js' {
  interface ChartDatasetProperties<TType extends ChartType, TData> {
    backgroundData?: {
      min: number;
      max: number;
      color: string;
    } | null;
  }
}

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

interface RealTransitData {
  [cycle: string]: number[];
}

const ColonialTransitChart: React.FC = () => {
  const [transitData, setTransitData] = useState<TransitData>({});
  const [realTransitData, setRealTransitData] = useState<RealTransitData>({});
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedFuel, setSelectedFuel] = useState<string>("A2");
  const [selectedRoute, setSelectedRoute] = useState<string>("HTNGBJ");
  const [startCycle, setStartCycle] = useState<number>(1);
  const [endCycle, setEndCycle] = useState<number>(72);

  const chartRef = useRef<ChartJS<"line"> | null>(null);

  const fuelOptions = [
    { value: "A2", label: "A2" },
    { value: "A3", label: "A3" },
    { value: "A4", label: "A4" },
    { value: "D2", label: "D2" },
    { value: "D3", label: "D3" },
    { value: "D4", label: "D4" },
    { value: "F2", label: "F2" },
    { value: "F3", label: "F3" },
    { value: "F4", label: "F4" },
    { value: "62", label: "62" },
  ];

  const routeOptions = [
    { value: "HTNGBJ", label: "HTN to GBJ" },
    { value: "GBJLNJ", label: "GBJ to LNJ" },
  ];

  const getRouteParam = () => {
    const product = selectedFuel === "62" ? "DISTILLATES" : "GAS";
    return `${selectedRoute}-${product}`;
  };

  const fetchTransitData = async () => {
    try {
      const response = await fetch(
        `https://rioseasonalspreads-production.up.railway.app/getColonialTransit?route=${getRouteParam()}`
      );
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
      return await response.json();
    } catch (err) {
      throw err;
    }
  };

  const fetchRealTransitData = async () => {
    try {
      const response = await fetch(
        `https://rioseasonalspreads-production.up.railway.app/getRealTransit?fuel=${selectedFuel}&route=${selectedRoute}`
      );
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
      return await response.json();
    } catch (err) {
      throw err;
    }
  };
    const updateSpreads = async () => {
      try {
        const response = await fetch(
          "https://rioseasonalspreads-production.up.railway.app/updateColonialTransit",
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
            },
          }
        );

        if (!response.ok) {
          throw new Error("Failed to update transit data");
        }
        console.log("Transit data updated successfully");
      } catch (error) {
        console.error("Error updating transit data:", error);
      }
    };


  const handleRefresh = async () => {
    try {
      setLoading(true);
      setError(null);
      updateSpreads();
      const [transitData, realData] = await Promise.all([
        fetchTransitData(),
        fetchRealTransitData()
      ]);
      setTransitData(transitData);
      setRealTransitData(realData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "An unknown error occurred");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    handleRefresh();
  }, [selectedFuel, selectedRoute]);

  useEffect(() => {
    return () => {
      chartRef.current?.destroy();
    };
  }, []);

  const generateColors = (count: number): string[] => {
    const colors: string[] = [];
    for (let i = 0; i < count; i++) {
      const hue = (i * 137.508) % 360; // Golden angle distribution
      colors.push(`hsl(${hue}, 80%, 45%)`);
    }
    return colors;
  };

  const prepareChartData = () => {
    const cycles = Object.keys(transitData)
      .map(Number)
      .filter(c => !isNaN(c) && c >= startCycle && c <= endCycle)
      .sort((a, b) => a - b);

    const colors = generateColors(cycles.length);

    // Get all unique dates across all cycles
    const allDates = new Set<string>();
    cycles.forEach(cycle => {
      Object.keys(transitData[cycle.toString()] || {}).forEach(date => {
        allDates.add(date);
      });
    });

    const sortedDates = Array.from(allDates).sort();
    const xMin = sortedDates[0];
    const xMax = sortedDates[sortedDates.length - 1];

    const datasets: ChartDataset<"line", ScatterDataPoint[]>[] = cycles.map((cycle, index) => {
      const cycleStr = cycle.toString();
      const cycleData = transitData[cycleStr] || {};
      const realData = realTransitData[cycleStr] || [];
      

const lineData: ScatterDataPoint[] = sortedDates
  .filter((date) => cycleData[date] !== null && cycleData[date] !== undefined)
  .map((date) => ({
    x: new Date(date).getTime(), // Convert Date to timestamp
    y: cycleData[date],
  }));


      const backgroundData = realData.length > 0 ? {
        min: Math.min(...realData),
        max: Math.max(...realData),
        color: colors[index] + "33" // Add alpha for transparency
      } : null;

      return {
        type: "line",
        label: `Cycle ${cycle}`,
        data: lineData,
        borderColor: colors[index],
        backgroundColor: colors[index],
        borderWidth: 2,
        pointRadius: 3,
        tension: 0.1,
        backgroundData
      };
    });

    return {
      chartData: {
        labels: sortedDates,
        datasets
      },
      xAxisRange: { min: xMin, max: xMax }
    };
  };

const backgroundAreaPlugin = {
  id: "backgroundArea",
  beforeDatasetsDraw(chart: ChartJS) {
    const ctx = chart.ctx;
    chart.data.datasets.forEach((dataset: any, i: number) => {
      if (!dataset.backgroundData) return;

      const meta = chart.getDatasetMeta(i);
      if (!meta.data.length) return;

      const firstPoint = meta.data[0];
      const lastPoint = meta.data[meta.data.length - 1];
      const minY = chart.scales.y.getPixelForValue(dataset.backgroundData.min);
      const maxY = chart.scales.y.getPixelForValue(dataset.backgroundData.max);

      ctx.save();

      if (dataset.backgroundData.min === dataset.backgroundData.max) {
        // Draw a thin line for zero-range data
        ctx.beginPath();
        ctx.moveTo(firstPoint.x, minY);
        ctx.lineTo(lastPoint.x, minY);
        ctx.lineWidth = 1; // Thinner than the main line (which is 2)
        ctx.strokeStyle = dataset.borderColor;
        ctx.stroke();
      } else {
        // Draw filled rectangle for normal range
        ctx.beginPath();
        ctx.moveTo(firstPoint.x, minY);
        ctx.lineTo(lastPoint.x, minY);
        ctx.lineTo(lastPoint.x, maxY);
        ctx.lineTo(firstPoint.x, maxY);
        ctx.closePath();

        // Use the border color with transparency
        ctx.fillStyle = dataset.borderColor
          .replace(")", ", 0.2)")
          .replace("rgb", "rgba");
        ctx.fill();
      }

      ctx.restore();
    });
  },
};

  const { chartData, xAxisRange } = prepareChartData();

  const options: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: "right",
        labels: { boxWidth: 12, padding: 20 },
      },
      title: {
        display: true,
        text: `${
          selectedRoute === "HTNGBJ" ? "HTN to GBJ" : "GBJ to LNJ"
        } ${selectedFuel} Transit Times`,
        font: { size: 16 },
      },
      tooltip: {
        mode: "nearest",
        intersect: false,
        filter: function (tooltipItem) {
          // Only show tooltip for items that have background data
          return (
            chartData.datasets[tooltipItem.datasetIndex].backgroundData !== null
          );
        },
        callbacks: {
          label: function (context) {
            const label = context.dataset.label || "";
            const value = context.parsed.y || 0;
            const realData =
              realTransitData[context.dataset.label?.split(" ")[1] || ""];

            let tooltipText = `${label}: ${value.toFixed(2)} days`;
            if (realData?.length) {
              tooltipText += ` (Actual range: ${Math.min(
                ...realData
              )}-${Math.max(...realData)} days)`;
            }
            return tooltipText;
          },
        },
      },
    },
    scales: {
      x: {
        type: "time",
        min: xAxisRange.min,
        max: xAxisRange.max,
        time: {
          unit: "day",
          tooltipFormat: "yyyy-MM-dd",
          displayFormats: { day: "MMM dd" },
        },
        title: { display: true, text: "Date" },
      },
      y: {
        title: { display: true, text: "Transit Time (days)" },
        ticks: { callback: (value: any) => `${value} days` },
        // Add this to ensure the y-axis scales to include background areas
        suggestedMin:
          Math.min(
            ...Object.values(realTransitData).flatMap((data) => data),
            ...Object.values(transitData).flatMap((cycle) =>
              Object.values(cycle)
            )
          ) - 1, // Add some padding
        suggestedMax:
          Math.max(
            ...Object.values(realTransitData).flatMap((data) => data),
            ...Object.values(transitData).flatMap((cycle) =>
              Object.values(cycle)
            )
          ) + 1, // Add some padding
      },
    },
    interaction: { intersect: false, mode: "index" },
  };

  if (loading) return <div>Loading transit data...</div>;
  if (error) return <div>Error: {error}</div>;
  if (Object.keys(transitData).length === 0) return <div>No transit data available</div>;

  return (
    <div style={{ width: "100%", margin: "20px 0", textAlign: "center" }}>
      <div
        style={{
          marginBottom: "20px",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: "10px",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <label htmlFor="fuel-select">Fuel: </label>
          <select
            id="fuel-select"
            value={selectedFuel}
            onChange={(e) => setSelectedFuel(e.target.value)}
            style={{ textAlign: "center" }}
          >
            {fuelOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <label htmlFor="route-select">Route: </label>
          <select
            id="route-select"
            value={selectedRoute}
            onChange={(e) => setSelectedRoute(e.target.value)}
            style={{ textAlign: "center" }}
          >
            {routeOptions.map((option) => (
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

        <div style={{ display: "flex", gap: "20px", justifyContent: "center" }}>
          <div>
            <label htmlFor="start-cycle">Start Cycle: </label>
            <input
              type="number"
              id="start-cycle"
              value={startCycle}
              min={1}
              max={72}
              onChange={(e) =>
                setStartCycle(Math.min(72, Math.max(1, Number(e.target.value))))
              }
              style={{ width: "60px", textAlign: "center" }}
            />
          </div>
          <div>
            <label htmlFor="end-cycle">End Cycle: </label>
            <input
              type="number"
              id="end-cycle"
              value={endCycle}
              min={1}
              max={72}
              onChange={(e) =>
                setEndCycle(Math.min(72, Math.max(1, Number(e.target.value))))
              }
              style={{ width: "60px", textAlign: "center" }}
            />
          </div>
        </div>
      </div>

      <div style={{ height: "600px", position: "relative", margin: "0 auto" }}>
        <Line
          key={`${selectedFuel}-${selectedRoute}-${startCycle}-${endCycle}`}
          data={chartData}
          options={options}
          ref={chartRef}
          plugins={[backgroundAreaPlugin]}
        />
      </div>

      <div style={{ marginTop: "10px", fontSize: "12px", color: "#666" }}>
        <p>
          Hover over lines to see cycle details. Click on legend items to toggle
          visibility.
        </p>
        <p>Shaded areas represent actual transit time ranges.</p>
      </div>
    </div>
  );
};

export default ColonialTransitChart;