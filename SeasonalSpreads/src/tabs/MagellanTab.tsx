import React, { useState, useEffect, useRef } from "react";
import { FaUpload, FaSpinner, FaChartLine, FaUndo } from "react-icons/fa";
import { Line } from "react-chartjs-2";
import type { ChartOptions } from "chart.js";
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
import type { ChartData } from "chart.js";

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


const FUEL_LABELS: Record<string, string> = {
  A: "A PREMIUM",
  E: "E DENATURED",
  Q: "Q COMMERCIAL JET FUEL",
  V: "V SUB-OCTANE",
  X: "X #2 ULSD",
  Y: "Y #1 ULSD",
};

const DATA_OPTIONS = [
  { value: "System Inventory", label: "System Inventory" },
  { value: "MPL Racks Only", label: "MPL Racks Only" },
  { value: "Offlines and MPL Racks 7-Day Average", label: "Offlines & MPL Racks 7-Day Avg" },
  { value: "Receipts 7-Day Average", label: "Receipts 7-Day Avg" },
];

const MagellanTab: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"graph" | "upload">("graph");
  const [selectedFuel, setSelectedFuel] = useState("A");
  const [selectedDataOption, setSelectedDataOption] = useState(DATA_OPTIONS[0].value);
  const [chartData, setChartData] = useState<Map<string, Map<string, number>>>(new Map());
  const [files, setFiles] = useState<File[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [isLoadingChart, setIsLoadingChart] = useState(true);
  const [uploadMessage, setUploadMessage] = useState("");
  const [lastDate, setLastDate] = useState<string>("");
  const chartRef = useRef<ChartJS<"line"> | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetchChartData();
  }, [selectedFuel, selectedDataOption]);

  useEffect(() => {
    fetchLastDate();
  }, []);

const fetchChartData = async () => {
  setIsLoadingChart(true);
  try {
    const res = await fetch(
      `https://rioseasonalspreads-production.up.railway.app/getMagellanData?fuel=${selectedFuel}&data=${encodeURIComponent(
        selectedDataOption
      )}`
    );
    const json = await res.json();
    const parsed = new Map<string, Map<string, number>>();
    Object.entries(json).forEach(([date, yearValues]) => {
      Object.entries(yearValues as Record<string, number>).forEach(
        ([year, value]) => {
          if (!parsed.has(year)) parsed.set(year, new Map());
          parsed.get(year)!.set(date, value);
        }
      );
    });
    setChartData(parsed);
  } catch (err) {
    console.error("Error fetching chart data:", err);
  } finally {
    setIsLoadingChart(false);
  }
};


  const fetchLastDate = async () => {
    try {
      const res = await fetch("https://rioseasonalspreads-production.up.railway.app/getLatestDate");
      const json = await res.json();
      const date = new Date(json.lastUpdated);
      date.setDate(date.getDate() + 1);
      setLastDate(date.toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" }));
    } catch (err) {
      setLastDate("Error fetching date");
    }
  };

  const handleUpload = async () => {
    if (files.length === 0) {
      setUploadMessage("Please select PDF files first");
      return;
    }
    setIsUploading(true);
    setUploadMessage("");
    for (const file of files) {
      try {
        const formData = new FormData();
        formData.append("file", file);
        const response = await fetch(
          "https://rioseasonalspreads-production.up.railway.app/upload-inventory",
          { method: "POST", body: formData }
        );
        if (!response.ok) throw new Error(`Failed to upload ${file.name}`);
      } catch (err) {
        setUploadMessage(err instanceof Error ? err.message : "Upload failed");
        setIsUploading(false);
        return;
      }
    }
    setUploadMessage("All files processed successfully");
    await fetchLastDate();
    setIsUploading(false);
  };

  const getAllDates = () => {
    const allDates = new Set<string>();
    chartData.forEach((map) => map.forEach((_v, date) => allDates.add(date)));
    return Array.from(allDates).sort((a, b) => {
      const [am, ad] = a.split("/").map(Number);
      const [bm, bd] = b.split("/").map(Number);
      return new Date(2000, am - 1, ad).getTime() - new Date(2000, bm - 1, bd).getTime();
    });
  };

  const getLast30Dates = () => {
    const all = getAllDates();
    const valid = all.filter((d) => chartData.get("2025")?.has(d));
    return (valid.length >= 30 ? valid.slice(-30) : all.slice(-30)).reverse();
  };

  const currentYear: number = new Date().getFullYear();
  const currYear = String(currentYear);
  const year6 = String(currentYear - 5);
  const year7 = String(currentYear - 4);
  const year8 = String(currentYear - 3);
  const year9 = String(currentYear - 2);
  const year10 = String(currentYear - 1);

const getYearColor = (year: string, opacity = 1) => {
  const predefined: Record<string, string> = {
    [year6]: `rgba(128, 0, 128, ${opacity})`,
    [year7]: `rgba(128, 128, 128, ${opacity})`,
    [year8]: `rgba(0, 128, 0, ${opacity})`,
    [year9]: `rgba(0, 0, 255, ${opacity})`,
    [year10]: `rgba(255, 165, 0, ${opacity})`,
    [currYear]: `rgba(255, 140, 0, ${opacity})`,
    "5YEARAVG": `rgba(255, 0, 0, ${opacity})`,
    "10YEARAVG": `rgba(0, 0, 0, ${opacity})`,
  };

  if (predefined[year]) return predefined[year];

  const palette = [
    `rgba(0, 123, 255, ${opacity})`,
    `rgba(40, 167, 69, ${opacity})`,
    `rgba(255, 193, 7, ${opacity})`,
    `rgba(220, 53, 69, ${opacity})`,
    `rgba(23, 162, 184, ${opacity})`,
    `rgba(108, 117, 125, ${opacity})`,
  ];

  const index = parseInt(year) % palette.length;
  return palette[index];
};


  const allDates = getAllDates();
  const last30Dates = getLast30Dates();

  const chart: ChartData<"line"> = {
    labels: allDates,
    datasets: [
      {
        label: "Range Min",
        data: allDates.map((d) =>
          Math.min(
            ...Array.from(chartData.values()).map((m) => m.get(d) ?? Infinity)
          )
        ),
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        pointRadius: 0,
        fill: "+1",
      },
      {
        label: "Range Max",
        data: allDates.map((d) =>
          Math.max(
            ...Array.from(chartData.values()).map((m) => m.get(d) ?? -Infinity)
          )
        ),
        backgroundColor: "rgba(200, 200, 200, 0.2)",
        borderColor: "rgba(200, 200, 200, 0)",
        pointRadius: 0,
        fill: false,
      },
      ...Array.from(chartData.entries()).map(([year, map]) => ({
        label: year,
        data: allDates.map((d) => map.get(d) ?? null),
        borderColor: year === currYear ? "orange" : getYearColor(year),
        backgroundColor: getYearColor(year, 0.5),
        borderWidth: year === currYear || year.includes("AVG") ? 3 : 1,
        borderDash: year === currYear || year.includes("AVG") ? [] : [5, 5],
        tension: 0.1,
        pointRadius: 0,
      })),
    ],
  };


const options: ChartOptions<"line"> = {
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
      text: `${FUEL_LABELS[selectedFuel]} - ${selectedDataOption}`,
      font: {
        size: 18,
        weight: "bold",
        family: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
      },
    },
    zoom: {
      pan: {
        enabled: true,
        mode: "xy",
      },
      zoom: {
        wheel: {
          enabled: true,
        },
        pinch: {
          enabled: true,
        },
        mode: "xy",
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
        text: "Inventory (Barrels)",
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
  <div style={{ padding: "20px", fontFamily: "Segoe UI" }}>
    <h2 style={{ textAlign: "center" }}>Magellan Inventory</h2>
    <p style={{ textAlign: "center", color: "#555" }}>
      <strong>Last Uploaded Report:</strong> {lastDate}
    </p>

    <div
      style={{
        display: "flex",
        justifyContent: "center",
        gap: "10px",
        marginBottom: "20px",
      }}
    >
      <button
        onClick={() => setActiveTab("graph")}
        style={{ fontWeight: activeTab === "graph" ? "bold" : "normal" }}
      >
        <FaChartLine /> Graph
      </button>
      <button
        onClick={() => setActiveTab("upload")}
        style={{ fontWeight: activeTab === "upload" ? "bold" : "normal" }}
      >
        <FaUpload /> Upload
      </button>
    </div>

    {activeTab === "graph" ? (
      <>
        <div style={{ textAlign: "center", marginBottom: "10px" }}>
          <strong>Fuel Type:</strong>
        </div>
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            gap: "10px",
            flexWrap: "wrap",
            marginBottom: "20px",
          }}
        >
          {Object.entries(FUEL_LABELS).map(([code, label]) => (
            <button
              key={code}
              onClick={() => setSelectedFuel(code)}
              style={{
                padding: "8px 16px",
                borderRadius: "4px",
                border: "1px solid #ccc",
                backgroundColor: selectedFuel === code ? "#3498db" : "#f0f0f0",
                color: selectedFuel === code ? "#fff" : "#333",
                fontWeight: "bold",
              }}
            >
              {label}
            </button>
          ))}
        </div>

        <div
          style={{
            display: "flex",
            justifyContent: "center",
            gap: "10px",
            alignItems: "center",
            marginBottom: "20px",
          }}
        >
          <label htmlFor="data-select" style={{ fontWeight: "bold" }}>
            Data View:
          </label>
          <select
            id="data-select"
            value={selectedDataOption}
            onChange={(e) => setSelectedDataOption(e.target.value)}
            style={{ padding: "8px", borderRadius: "4px", minWidth: "250px" }}
          >
            {DATA_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        <div className="graph-container">
          {isLoadingChart ? (
            <p style={{ textAlign: "center", marginTop: "20px" }}>
              <FaSpinner className="spin" /> Loading chart...
            </p>
          ) : (
            <>
              <button
                className="reset-zoom-button"
                onClick={() => chartRef.current?.resetZoom()}
              >
                <FaUndo /> Reset Zoom
              </button>
              <Line ref={chartRef} data={chart} options={options} />
            </>
          )}
        </div>

        {chartData.size > 0 && (
          <div
            className="table-container"
            style={{
              marginTop: "40px",
              backgroundColor: "#fff",
              borderRadius: "8px",
              boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
              padding: "20px",
              overflowX: "auto",
            }}
          >
            <h3 style={{ textAlign: "center", marginBottom: "20px" }}>
              Last 30 Days Data - {currYear}
            </h3>
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                fontSize: "14px",
                fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
              }}
            >
              <thead>
                <tr style={{ backgroundColor: "#f8f9fa" }}>
                  <th
                    style={{
                      padding: "10px",
                      textAlign: "left",
                      borderBottom: "1px solid #ddd",
                    }}
                  >
                    Date
                  </th>
                  {Array.from(chartData.keys())
                    .filter((year) =>
                      [
                        [year6],
                        [year7],
                        [year8],
                        [year9],
                        [year10],
                        [currYear],
                        "5YEARAVG",
                        "10YEARAVG",
                      ].includes(year)
                    )
                    .map((year) => (
                      <th
                        key={year}
                        style={{
                          padding: "10px",
                          textAlign: "right",
                          borderBottom: "1px solid #ddd",
                        }}
                      >
                        {year}
                      </th>
                    ))}
                </tr>
              </thead>
              <tbody>
                {last30Dates.map((date, index) => (
                  <tr
                    key={index}
                    style={{
                      backgroundColor: index % 2 === 0 ? "#fff" : "#f8f9fa",
                    }}
                  >
                    <td style={{ padding: "10px", fontWeight: "bold" }}>
                      {date}
                    </td>
                    {Array.from(chartData.keys())
                      .filter((year) =>
                        [
                          [year6],
                          [year7],
                          [year8],
                          [year9],
                          [year10],
                          [currYear],
                          "5YEARAVG",
                          "10YEARAVG",
                        ].includes(year)
                      )
                      .map((year) => {
                        const value = chartData.get(year)?.get(date);
                        return (
                          <td
                            key={year}
                            style={{
                              padding: "10px",
                              textAlign: "right",
                              fontFamily: "Courier New, monospace",
                              fontWeight: year === "2025" ? "bold" : "normal",
                              color: year === currYear ? "#2c3e50" : "inherit",
                            }}
                          >
                            {value?.toFixed(2) ?? "N/A"}
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
    ) : (
      <>
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            const droppedFiles = Array.from(e.dataTransfer.files).filter(
              (file) => file.type === "application/pdf"
            );
            setFiles(droppedFiles);
            setUploadMessage("");
          }}
          onClick={() => fileInputRef.current?.click()}
          style={{
            border: "2px dashed #bdc3c7",
            borderRadius: "8px",
            padding: "40px",
            textAlign: "center",
            cursor: "pointer",
            backgroundColor: files.length > 0 ? "#e8f5e9" : "#f8f9fa",
            marginBottom: "20px",
          }}
        >
          <input
            type="file"
            ref={fileInputRef}
            onChange={(e) => {
              if (e.target.files) {
                const selected = Array.from(e.target.files).filter(
                  (file) => file.type === "application/pdf"
                );
                setFiles(selected);
                setUploadMessage("");
              }
            }}
            accept=".pdf"
            multiple
            style={{ display: "none" }}
          />
          {files.length > 0 ? (
            <>
              <p>
                <strong>{files.length} file(s) selected:</strong>
              </p>
              <ul style={{ listStyle: "none", padding: 0 }}>
                {files.map((f, i) => (
                  <li key={i}>{f.name}</li>
                ))}
              </ul>
              <p>Click to select different PDFs</p>
            </>
          ) : (
            <>
              <FaUpload size={48} style={{ marginBottom: "10px" }} />
              <p>
                <strong>Drag & drop PDF files, or click to select</strong>
              </p>
              <p>Only PDF files are accepted</p>
            </>
          )}
        </div>

        {uploadMessage && (
          <p
            style={{
              color: uploadMessage.includes("success") ? "#2e7d32" : "#c62828",
              textAlign: "center",
            }}
          >
            {uploadMessage}
          </p>
        )}

        <div
          style={{
            display: "flex",
            justifyContent: "center",
            marginTop: "20px",
          }}
        >
          <button
            onClick={handleUpload}
            disabled={files.length === 0 || isUploading}
            style={{
              padding: "12px 24px",
              backgroundColor: "#2e7d32",
              color: "white",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer",
              fontWeight: "bold",
              opacity: files.length === 0 || isUploading ? 0.6 : 1,
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            {isUploading ? (
              <>
                <FaSpinner className="spin" /> Processing...
              </>
            ) : (
              <>
                <FaUpload /> Upload Files
              </>
            )}
          </button>
        </div>
      </>
    )}
  </div>
);
};

export default MagellanTab;
