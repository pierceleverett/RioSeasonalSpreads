import React, { useState, useEffect } from "react";

interface ApiResponse {
  "Gas Scheduling End": Record<string, Record<string, string>>;
  "Gas Starts": Record<string, Record<string, string>>;
  "Oil Scheduling End": Record<string, Record<string, string>>;
  "Oil Starts": Record<string, Record<string, string>>;
}

interface BulletinDates {
  "Old Bulletin Date": string;
  "Recent Bulletin Date": string;
}

interface TableData {
  cycle: string;
  gasDate?: string;
  oilDate?: string;
  gasDiff: number | null;
  oilDiff: number | null;
}

const parseDate = (dateStr: string | undefined): Date | null => {
  if (!dateStr) return null;
  const [month, day] = dateStr.split("/").map(Number);
  const year = new Date().getFullYear();
  return new Date(year, month - 1, day);
};

const calculateDateDiff = (
  newDateStr: string | undefined,
  oldDateStr: string | undefined
): number | null => {
  if (!newDateStr || !oldDateStr) return null;

  const newDate = parseDate(newDateStr);
  const oldDate = parseDate(oldDateStr);

  if (!newDate || !oldDate) return null;

  const diffTime = newDate.getTime() - oldDate.getTime();
  const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));

  return diffDays !== 0 ? diffDays : null;
};

const ExplorerStartsComponent: React.FC = () => {
  const [newData, setNewData] = useState<ApiResponse | null>(null);
  const [oldData, setOldData] = useState<ApiResponse | null>(null);
  const [bulletinDates, setBulletinDates] = useState<BulletinDates | null>(
    null
  );
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedOrigin, setSelectedOrigin] = useState<string>("PAS");
  const origins = ["PTN", "PTA", "PAS", "GRV", "GLN", "WDR", "HMD"];

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [newResponse, oldResponse, bulletinResponse] = await Promise.all([
          fetch(
            "https://rioseasonalspreads-production.up.railway.app/getExplorerStarts"
          ),
          fetch(
            "https://rioseasonalspreads-production.up.railway.app/getOldExplorerStarts"
          ),
          fetch(
            "https://rioseasonalspreads-production.up.railway.app/getExplorerBulletinDates"
          ),
        ]);

        if (!newResponse.ok || !oldResponse.ok || !bulletinResponse.ok) {
          throw new Error(
            `HTTP error! status: ${newResponse.status}/${oldResponse.status}/${bulletinResponse.status}`
          );
        }

        const [newJson, oldJson, bulletinJson] = await Promise.all([
          newResponse.json(),
          oldResponse.json(),
          bulletinResponse.json(),
        ]);

        setNewData(newJson);
        setOldData(oldJson);
        setBulletinDates(bulletinJson);
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
    newGasData: Record<string, Record<string, string>>,
    newOilData: Record<string, Record<string, string>>,
    oldGasData: Record<string, Record<string, string>>,
    oldOilData: Record<string, Record<string, string>>
  ): TableData[] => {
    if (!newData || !oldData) return [];

    const allCycles = new Set<string>([
      ...Object.keys(newGasData),
      ...Object.keys(newOilData),
      ...Object.keys(oldGasData),
      ...Object.keys(oldOilData),
    ]);

    return Array.from(allCycles)
      .sort((a, b) => parseInt(a) - parseInt(b))
      .map((cycle) => {
        const newGasDate = newGasData[cycle]?.[selectedOrigin];
        const oldGasDate = oldGasData[cycle]?.[selectedOrigin];
        const gasDiff = calculateDateDiff(newGasDate, oldGasDate) ?? null;

        const newOilDate = newOilData[cycle]?.[selectedOrigin];
        const oldOilDate = oldOilData[cycle]?.[selectedOrigin];
        const oilDiff = calculateDateDiff(newOilDate, oldOilDate) ?? null;

        return {
          cycle,
          gasDate: newGasDate,
          oilDate: newOilDate,
          gasDiff,
          oilDiff,
        };
      })
      .filter((row) => row.gasDate || row.oilDate);
  };

  const renderDateWithDiff = (
    date: string | undefined,
    diff: number | null
  ) => {
    if (!date) return "-";

    if (diff === null) {
      return date;
    }

    const diffText = diff > 0 ? `+${diff}` : diff.toString();
    const color = diff > 0 ? "red" : "green";

    return (
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <span>{date}</span>
        <span
          style={{
            color: color,
            marginLeft: "5px",
            fontWeight: "bold",
            fontSize: "0.9em",
          }}
        >
          ({diffText})
        </span>
      </div>
    );
  };

  if (loading)
    return (
      <div style={{ textAlign: "center", padding: "20px" }}>Loading...</div>
    );
  if (error)
    return (
      <div style={{ textAlign: "center", padding: "20px", color: "red" }}>
        Error: {error}
      </div>
    );
  if (!newData || !oldData)
    return (
      <div style={{ textAlign: "center", padding: "20px" }}>
        No data available
      </div>
    );

  const schedulingEndData = getTableData(
    newData["Gas Scheduling End"],
    newData["Oil Scheduling End"],
    oldData["Gas Scheduling End"],
    oldData["Oil Scheduling End"]
  );

  const startsData = getTableData(
    newData["Gas Starts"],
    newData["Oil Starts"],
    oldData["Gas Starts"],
    oldData["Oil Starts"]
  );

  return (
    <div style={{ padding: "20px", maxWidth: "1200px", margin: "0 auto" }}>
      <div style={{ textAlign: "center", marginBottom: "20px" }}>
        <h1 style={{ marginBottom: "15px" }}>Explorer Starts Data</h1>
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

      <div
        style={{
          display: "flex",
          gap: "30px",
          flexWrap: "wrap",
          justifyContent: "center",
        }}
      >
        <div style={{ flex: 1, minWidth: "400px" }}>
          <h2
            style={{
              marginBottom: "10px",
              fontSize: "18px",
              textAlign: "center",
            }}
          >
            Scheduling End
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
                    {renderDateWithDiff(row.gasDate, row.gasDiff)}
                  </td>
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {renderDateWithDiff(row.oilDate, row.oilDiff)}
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
            Starts
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
                    {renderDateWithDiff(row.gasDate, row.gasDiff)}
                  </td>
                  <td
                    style={{
                      padding: "8px",
                      border: "1px solid #ddd",
                      textAlign: "center",
                    }}
                  >
                    {renderDateWithDiff(row.oilDate, row.oilDiff)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {bulletinDates && (
        <div
          style={{
            marginTop: "30px",
            textAlign: "center",
            padding: "15px",
            backgroundColor: "#f8f9fa",
            borderRadius: "4px",
            border: "1px solid #dee2e6",
          }}
        >
          <h3 style={{ marginBottom: "10px" }}>Bulletin Dates</h3>
          <div
            style={{ display: "flex", justifyContent: "center", gap: "20px" }}
          >
            <div>
              <strong>Old Bulletin:</strong>{" "}
              {bulletinDates["Old Bulletin Date"]}
            </div>
            <div>
              <strong>Recent Bulletin:</strong>{" "}
              {bulletinDates["Recent Bulletin Date"]}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ExplorerStartsComponent;
