import React, { useState, useEffect } from "react";

interface ApiResponse {
  "Gas Scheduling End": Record<string, Record<string, string>>;
  "Gas Starts": Record<string, Record<string, string>>;
  "Oil Scheduling End": Record<string, Record<string, string>>;
  "Oil Starts": Record<string, Record<string, string>>;
}

interface TableData {
  cycle: string;
  gasDate?: string;
  oilDate?: string;
}

const ExplorerStartsComponent: React.FC = () => {
  const [data, setData] = useState<ApiResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedOrigin, setSelectedOrigin] = useState<string>("PAS");
  const origins = ["PTN", "PTA", "PAS", "GRV", "GLN", "WDR", "HMD"];

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch(
          "https://rioseasonalspreads-production.up.railway.app/getExplorerStarts"
        );
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const jsonData = await response.json();
        setData(jsonData);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "An unknown error occurred"
        );
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  const getTableData = (
    gasData: Record<string, Record<string, string>>,
    oilData: Record<string, Record<string, string>>
  ): TableData[] => {
    if (!data) return [];

    // Get all unique cycles from both gas and oil data
    const allCycles = new Set<string>([
      ...Object.keys(gasData),
      ...Object.keys(oilData),
    ]);

    return Array.from(allCycles)
      .sort((a, b) => parseInt(a) - parseInt(b))
      .map((cycle) => ({
        cycle,
        gasDate: gasData[cycle]?.[selectedOrigin],
        oilDate: oilData[cycle]?.[selectedOrigin],
      }))
      .filter((row) => row.gasDate || row.oilDate); // Only include cycles with data
  };

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!data) return <div>No data available</div>;

  const schedulingEndData = getTableData(
    data["Gas Scheduling End"],
    data["Oil Scheduling End"]
  );
  const startsData = getTableData(data["Gas Starts"], data["Oil Starts"]);

  return (
    <div style={{ padding: "20px", maxWidth: "1200px", margin: "0 auto" }}>
      <div style={{ textAlign: "center", marginBottom: "20px" }}>
        <h1 style={{ marginBottom: "15px" }}>Explorer Scheduling Data</h1>
        <div>
          <label htmlFor="origin-select" style={{ marginRight: "10px" }}>
            Select Origin:{" "}
          </label>
          <select
            id="origin-select"
            value={selectedOrigin}
            onChange={(e) => setSelectedOrigin(e.target.value)}
            style={{
              padding: "5px 10px",
              fontSize: "14px",
              borderRadius: "4px",
              border: "1px solid #ccc",
              textAlign: "center",
            }}
          >
            {origins.map((origin) => (
              <option key={origin} value={origin}>
                {origin}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div style={{ display: "flex", gap: "30px", flexWrap: "wrap" }}>
        <div style={{ flex: 1, minWidth: "400px" }}>
          <h2
            style={{
              marginBottom: "10px",
              fontSize: "18px",
              textAlign: "center",
            }}
          >
            Scheduling Due Date
          </h2>
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "14px",
              boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
              margin: "0 auto",
            }}
          >
            <thead>
              <tr style={{ backgroundColor: "#f2f2f2" }}>
                <th
                  style={{
                    padding: "8px",
                    border: "1px solid #ddd",
                    textAlign: "center",
                  }}
                >
                  Cycle
                </th>
                <th
                  style={{
                    padding: "8px",
                    border: "1px solid #ddd",
                    textAlign: "center",
                  }}
                >
                  Gas
                </th>
                <th
                  style={{
                    padding: "8px",
                    border: "1px solid #ddd",
                    textAlign: "center",
                  }}
                >
                  Oil
                </th>
              </tr>
            </thead>
            <tbody>
              {schedulingEndData.map((row) => (
                <tr
                  key={`scheduling-end-${row.cycle}`}
                  style={{ borderBottom: "1px solid #eee" }}
                >
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {row.cycle}
                  </td>
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {row.gasDate || "-"}
                  </td>
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {row.oilDate || "-"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div style={{ flex: 1, minWidth: "400px" }}>
          <h2
            style={{
              marginBottom: "10px",
              fontSize: "18px",
              textAlign: "center",
            }}
          >
            Start Date
          </h2>
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "14px",
              boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
              margin: "0 auto",
            }}
          >
            <thead>
              <tr style={{ backgroundColor: "#f2f2f2" }}>
                <th
                  style={{
                    padding: "8px",
                    border: "1px solid #ddd",
                    textAlign: "center",
                  }}
                >
                  Cycle
                </th>
                <th
                  style={{
                    padding: "8px",
                    border: "1px solid #ddd",
                    textAlign: "center",
                  }}
                >
                  Gas
                </th>
                <th
                  style={{
                    padding: "8px",
                    border: "1px solid #ddd",
                    textAlign: "center",
                  }}
                >
                  Oil
                </th>
              </tr>
            </thead>
            <tbody>
              {startsData.map((row) => (
                <tr
                  key={`starts-${row.cycle}`}
                  style={{ borderBottom: "1px solid #eee" }}
                >
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {row.cycle}
                  </td>
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {row.gasDate || "-"}
                  </td>
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {row.oilDate || "-"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default ExplorerStartsComponent;
