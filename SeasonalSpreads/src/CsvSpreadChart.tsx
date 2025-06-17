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
      ...["2020", "2021", "2022", "2023", "2024", "2025"].map(
        (year, index) => ({
          label: year,
          data: allDates.map((date) => dataMap.get(year)?.get(date) ?? null),
          borderColor: `rgba(${50 + index * 30}, ${100 + index * 20}, 200, 1)`,
          backgroundColor: `rgba(${50 + index * 30}, ${
            100 + index * 20
          }, 200, 0.3)`,
          borderWidth: year === "2025" ? 3 : 1,
          borderDash: year === "2025" ? [] : [5, 5],
          tension: 0.1,
          pointRadius: 0,
        })
      ),
      ...(dataMap.has("5YEARAVG")
        ? [
            {
              label: "5-Year Average",
              data: allDates.map(
                (date) => dataMap.get("5YEARAVG")?.get(date) ?? null
              ),
              borderColor: "rgba(255, 0, 0, 1)",
              backgroundColor: "rgba(255, 0, 0, 0.3)",
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
      legend: { position: "top" },
      title: { display: true, text: `${type} Spread Chart` },
      zoom: {
        zoom: {
          wheel: { enabled: true },
          pinch: { enabled: true },
          mode: "xy",
        },
        pan: { enabled: true, mode: "xy" },
      },
    },
    elements: {
      line: { tension: 0.1, spanGaps: true },
      point: { radius: 0 },
    },
    scales: {
      x: { title: { display: true, text: "Date (MM/DD)" } },
      y: { title: { display: true, text: "Spread ($/gal)" } },
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
