import React, { useState, useEffect, useRef } from "react";
import { Line } from "react-chartjs-2";
import { FaUndo } from "react-icons/fa";
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
import type { ChartOptions } from "chart.js";

// Register necessary components for Chart.js
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

interface MagellanChartProps {
  fuelType: string;
}

const MagellanChart: React.FC<MagellanChartProps> = ({ fuelType }) => {
  const [chartData, setChartData] = useState<Map<string, Map<string, number>>>(
    new Map()
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chartRef = useRef<ChartJS<"line"> | null>(null);

  useEffect(() => {
    fetchMagellanData();
    // eslint-disable-next-line
  }, [fuelType]);

  const fetchMagellanData = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch(
        `http://rioseasonalspreads-production.up.railway.app/getMagellanData?fuel=${fuelType}`
      );
      if (!response.ok) {
        throw new Error("Failed to fetch Magellan inventory data");
      }
      const data = await response.json();

      const yearMap: Map<string, Map<string, number>> = new Map();




Object.entries(data).forEach(([date, entries]) => {
  Object.entries(entries as Record<string, number>).forEach(([year, value]) => {
    const normalizedYear = year.includes(".") ? String(parseInt(year)) : year;
    if (!yearMap.has(normalizedYear)) yearMap.set(normalizedYear, new Map());
    yearMap.get(normalizedYear)!.set(date, value);
  });
});




      setChartData(yearMap);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "An unknown error occurred"
      );
      setChartData(new Map());
    } finally {
      setIsLoading(false);
    }
  };

  const getAllDates = (): string[] => {
    const allDatesSet = new Set<string>();
    chartData.forEach((dateMap) => {
      dateMap.forEach((_, date) => allDatesSet.add(date));
    });
    return Array.from(allDatesSet).sort((a, b) => {
      const [aMonth, aDay] = a.split("/").map(Number);
      const [bMonth, bDay] = b.split("/").map(Number);
      return (
        new Date(2000, aMonth - 1, aDay).getTime() -
        new Date(2000, bMonth - 1, bDay).getTime()
      );
    });
  };

  const calculateMinMaxRange = () => {
    const allDates = getAllDates();
    const validYears = Array.from(chartData.keys()).filter(
      (year) =>
        !["5YEARAVG", "10YEARAVG", "MAX", "MIN", "10-YEAR-RANGE"].includes(year)
    );

    const min: Array<number | null> = [];
    const max: Array<number | null> = [];

    allDates.forEach((date) => {
      const values = validYears
        .map((year) => chartData.get(year)?.get(date))
        .filter((v): v is number => typeof v === "number");
      if (values.length) {
        min.push(Math.min(...values));
        max.push(Math.max(...values));
      } else {
        min.push(null);
        max.push(null);
      }
    });

    return { min, max };
  };

  const getYearColor = (year: string, opacity = 1): string => {
    const colors: Record<string, string> = {
      "2020": `rgba(128, 0, 128, ${opacity})`, // Purple
      "2021": `rgba(128, 128, 128, ${opacity})`, // Gray
      "2022": `rgba(0, 128, 0, ${opacity})`, // Green
      "2023": `rgba(0, 0, 255, ${opacity})`, // Blue
      "2024": `rgba(255, 165, 0, ${opacity})`, // Orange
      "2025": `rgba(255, 140, 0, ${opacity})`, // Darker Orange
      "5YEARAVG": `rgba(255, 0, 0, ${opacity})`, // Red
      "10YEARAVG": `rgba(0, 0, 0, ${opacity})`, // Red
      MAX: `rgba(255, 0, 0, ${opacity})`, // Red
      MIN: `rgba(255, 0, 0, ${opacity})`, // Red
    };
    return colors[year] || `rgba(0, 0, 0, ${opacity})`;
  };

  const allDates = getAllDates();
  const rangeData = calculateMinMaxRange();

  const handleResetZoom = () => {
    if (chartRef.current) {
      chartRef.current.resetZoom();
    }
  };

  const data = {
    labels: allDates,
    datasets: [
      // Range fill
      {
        label: "Value Range",
        data: rangeData.max,
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        borderWidth: 0,
        pointRadius: 0,
        fill: "+1",
      },
      {
        label: "",
        data: rangeData.min,
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        borderWidth: 0,
        pointRadius: 0,
      },
      // Years 2020-2024
      ...Array.from(chartData.entries())
        .filter(([year]) => parseInt(year) >= 2020 && parseInt(year) <= 2024)
        .map(([year, dateMap]) => ({
          label: year,
          data: allDates.map((date) => dateMap.get(date) ?? null),
          borderColor: getYearColor(year),
          backgroundColor: getYearColor(year, 0.5),
          borderWidth: 1,
          borderDash: [5, 5],
          tension: 0.1,
          pointRadius: 0,
        })),
      // 2025
      ...(chartData.has("2025")
        ? [
            {
              label: "2025",
              data: allDates.map(
                (date) => chartData.get("2025")?.get(date) ?? null
              ),
              borderColor: getYearColor("2025"),
              backgroundColor: getYearColor("2025", 0.5),
              borderWidth: 3,
              tension: 0.1,
              pointRadius: 0,
            },
          ]
        : []),
      // 5-Year Avg
      ...(chartData.has("5YEARAVG")
        ? [
            {
              label: "5-Year Average",
              data: allDates.map(
                (date) => chartData.get("5YEARAVG")?.get(date) ?? null
              ),
              borderColor: getYearColor("5YEARAVG"),
              backgroundColor: getYearColor("5YEARAVG", 0.5),
              borderWidth: 3,
              tension: 0.1,
              pointRadius: 0,
            },
          ]
        : []),
      // 10-Year Avg
      ...(chartData.has("10YEARAVG")
        ? [
            {
              label: "10-Year Average",
              data: allDates.map(
                (date) => chartData.get("10YEARAVG")?.get(date) ?? null
              ),
              borderColor: getYearColor("10YEARAVG"),
              backgroundColor: getYearColor("10YEARAVG", 0.5),
              borderWidth: 3,
              tension: 0.1,
              pointRadius: 0,
            },
          ]
        : []),
    ],
  };

  const options: ChartOptions<"line"> = {
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
        position: "top",
        labels: {
          font: {
            size: 14,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
      title: {
        display: true,
        text: `${fuelType} Inventory Analysis`,
        font: {
          size: 18,
          weight: "bold",
          family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
        },
      },
      tooltip: {
        callbacks: {
          label: (context: any) => {
            const label = context.dataset.label || "";
            const value = context.parsed.y;
            return `${label}: ${value?.toFixed(2) ?? "N/A"}`;
          },
        },
      },
    },
    scales: {
      x: {
        title: {
          display: true,
          text: "Date (MM/DD)",
          font: {
            size: 14,
            weight: "bold",
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
        ticks: {
          font: {
            size: 12,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
      y: {
        title: {
          display: true,
          text: "Inventory (Barrels)",
          font: {
            size: 14,
            weight: "bold",
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
        ticks: {
          font: {
            size: 12,
            family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
          },
        },
      },
    },
    elements: {
      line: {
        tension: 0.1,
        spanGaps: true,
      },
      point: {
        radius: 0,
      },
    },
  };

  return (
    <div className="graph-wrapper" style={{ marginTop: "20px" }}>
      <div className="graph-container" style={{ height: "500px" }}>
        {isLoading ? (
          <p style={{ textAlign: "center" }}>Loading inventory data...</p>
        ) : error ? (
          <p style={{ textAlign: "center", color: "red" }}>{error}</p>
        ) : chartData.size > 0 ? (
          <>
            <button
              onClick={handleResetZoom}
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
              }}
            >
              <FaUndo />
              Reset Zoom
            </button>
            <Line ref={chartRef} data={data} options={options} />
          </>
        ) : (
          <p style={{ textAlign: "center" }}>No inventory data available</p>
        )}
      </div>
    </div>
  );
};

export default MagellanChart;
