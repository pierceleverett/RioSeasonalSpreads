// CsvSpreadChart.tsx
import React, { useEffect, useState, useRef } from "react";
import { Line } from "react-chartjs-2";
import { FaUndo } from "react-icons/fa";
import { Chart as ChartJS } from "chart.js";
import type { ChartOptions } from "chart.js";

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

  const chartData = {
    labels: allDates,
    datasets: Array.from(dataMap.entries()).map(([year, dateMap]) => ({
      label: year,
      data: allDates.map((date) => dateMap.get(date) ?? null),
      borderColor: year === "5YEARAVG" ? "red" : "blue",
      backgroundColor:
        year === "5YEARAVG" ? "rgba(255,0,0,0.3)" : "rgba(0,0,255,0.3)",
      borderWidth: year === "5YEARAVG" ? 3 : 1,
      tension: 0.1,
      pointRadius: 0,
    })),
  };

  const chartOptions: ChartOptions<"line"> = {
    responsive: true,
    plugins: {
      legend: { position: "top" },
      title: { display: true, text: `${type} Spread Chart` },
    },
  };

  return (
    <div style={{ width: "100%", maxWidth: "1200px", margin: "0 auto" }}>
      {isLoading ? (
        <p>Loading...</p>
      ) : error ? (
        <p style={{ color: "red" }}>{error}</p>
      ) : (
        <>
          <button
            onClick={() => chartRef.current?.resetZoom()}
            style={{ marginBottom: "10px" }}
          >
            <FaUndo /> Reset Zoom
          </button>
          <Line ref={chartRef} data={chartData} options={chartOptions} />
        </>
      )}
    </div>
  );
};

export default CsvSpreadChart;
