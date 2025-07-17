// Noms.tsx
import React, { useEffect, useState } from "react";

interface NomData {
  Distillate_Nomination: string;
  A_Scheduling_Date: string;
  Origin_Bulletin_Date: string;
  DateInfo_Bulletin_Date: string;
  A_Origin_Date: string;
  "62_Origin_Date": string;
  Gas_Nomination: string;
  Earliest_Distillate_Scheduling_Date: string;
  "62_Scheduling_Date": string;
}

interface ApiData {
  [key: string]: NomData;
}

const NomsTab: React.FC = () => {
  const [data, setData] = useState<ApiData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [bulletinDates, setBulletinDates] = useState<{
    origin: string;
    dateInfo: string;
  } | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch(
          "https://rioseasonalspreads-production.up.railway.app/getMainLine"
        );
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const result: ApiData = await response.json();
        setData(result);

        // Get bulletin dates from the first available cycle
        const firstCycle = Object.values(result)[0];
        if (firstCycle) {
          setBulletinDates({
            origin: firstCycle.Origin_Bulletin_Date,
            dateInfo: firstCycle.DateInfo_Bulletin_Date,
          });
        }
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

  // Format date from "mm/dd" to "d-Mmm" format (e.g., "07/07" to "7-Jul")
  const formatDate = (dateStr: string) => {
    if (!dateStr) return "";

    const [month, day] = dateStr.split("/").map(Number);
    const monthNames = [
      "Jan",
      "Feb",
      "Mar",
      "Apr",
      "May",
      "Jun",
      "Jul",
      "Aug",
      "Sep",
      "Oct",
      "Nov",
      "Dec",
    ];
    return `${day}-${monthNames[month - 1]}`;
  };

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!data) return <div>No data available</div>;

  return (
    <div style={{ padding: "20px" }}>
      <h1>Main Line Noms Due</h1>
      <table
        style={{
          borderCollapse: "collapse",
          width: "100%",
          marginBottom: "20px",
          border: "1px solid #ddd",
        }}
      >
        <thead>
          <tr>
            <th
              rowSpan={2}
              style={{ border: "1px solid #ddd", padding: "8px" }}
            >
              Cycle
            </th>
            <th
              colSpan={3}
              style={{ border: "1px solid #ddd", padding: "8px" }}
            >
              Line 2
            </th>
            <th
              colSpan={2}
              style={{ border: "1px solid #ddd", padding: "8px" }}
            >
              Line 1
            </th>
          </tr>
          <tr>
            {/* Line 2 headers */}
            <th style={{ border: "1px solid #ddd", padding: "8px" }}>62</th>
            <th style={{ border: "1px solid #ddd", padding: "8px" }}>
              HTN lift
            </th>
            <th style={{ border: "1px solid #ddd", padding: "8px" }}>
              51/54/62
            </th>
            {/* Line 1 headers */}
            <th style={{ border: "1px solid #ddd", padding: "8px" }}>A</th>
            <th style={{ border: "1px solid #ddd", padding: "8px" }}>
              HTN lift
            </th>
            <th style={{ border: "1px solid #ddd", padding: "8px" }}>Gas</th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(data).map(([cycle, cycleData]) => (
            <tr key={cycle}>
              <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                {cycle}
              </td>
              {/* Line 2 data */}
              <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                {formatDate(cycleData["62_Scheduling_Date"])}
              </td>
              <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                {formatDate(cycleData["62_Origin_Date"])}
              </td>
              <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                {formatDate(cycleData.Distillate_Nomination)}
              </td>
              {/* Line 1 data */}
              <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                {formatDate(cycleData.A_Scheduling_Date)}
              </td>
              <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                {formatDate(cycleData.A_Origin_Date)}
              </td>
              <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                {formatDate(cycleData.Gas_Nomination)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {bulletinDates && (
        <div style={{ marginTop: "20px" }}>
          <div>
            <strong>Origin Bulletin Date:</strong> {bulletinDates.origin}
          </div>
          <div>
            <strong>DateInfo Bulletin Date:</strong> {bulletinDates.dateInfo}
          </div>
        </div>
      )}
    </div>
  );
};

export default NomsTab;
