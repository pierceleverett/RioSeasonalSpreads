import React, { useEffect, useState } from "react";

interface FuelDates {
  A: string;
  D: string;
  F: string;
  "62": string;
}

interface OriginData {
  data: {
    [cycle: string]: FuelDates;
  };
  reportDate: string;
}

interface ApiResponse {
  currentData: OriginData;
  previousData: OriginData;
  currentReportDate: string;
  previousReportDate: string;
  isNewerData: boolean;
}

const FuelDateTable: React.FC = () => {
  const [data, setData] = useState<ApiResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch(
          "https://rioseasonalspreads-production.up.railway.app/getOriginStart"
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

  const calculateDayDifference = (
    date1: string,
    date2: string
  ): number | null => {
    if (!date1 || !date2) return null;

    try {
      const [month1, day1] = date1.split("/").map(Number);
      const [month2, day2] = date2.split("/").map(Number);

      // Simple calculation assuming same year
      return (month2 - month1) * 30 + (day2 - day1);
    } catch (e) {
      console.error("Error calculating date difference:", e);
      return null;
    }
  };

  const renderDateWithDifference = (
    cycle: string,
    fuelType: keyof FuelDates
  ) => {
    if (!data) return null;

    const currentDate = data.currentData.data[cycle]?.[fuelType];
    const previousDate = data.previousData.data[cycle]?.[fuelType];

    if (!currentDate) return null;

    let differenceIndicator = null;
    if (previousDate) {
      const diff = calculateDayDifference(previousDate, currentDate);
      if (diff !== null && diff !== 0) {
        const color = diff > 0 ? "red" : "green";
        const symbol = diff > 0 ? "+" : "";
        differenceIndicator = (
          <span
            style={{
              color,
              fontSize: "0.8em",
              marginLeft: "4px",
              fontWeight: "bold",
            }}
          >
            {symbol}
            {diff}
          </span>
        );
      }
    }

    return (
      <span>
        {currentDate}
        {differenceIndicator}
      </span>
    );
  };

  if (loading) return <div>Loading data...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!data) return <div>No data available</div>;

  // Extract cycles from current data
  const cycles = Object.keys(data.currentData.data).sort();

  // Fuel types (A, D, F, 62)
  const fuelTypes = ["A", "D", "F", "62"] as const;

  return (
    <div style={{ padding: "20px", fontFamily: "Arial, sans-serif" }}>
      <table
        style={{
          width: "100%",
          borderCollapse: "collapse",
          marginBottom: "20px",
        }}
      >
        <thead>
          <tr style={{ backgroundColor: "#f5f5f5" }}>
            <th style={{ padding: "10px", border: "1px solid #ddd" }}>
              Fuel Grade
            </th>
            {cycles.map((cycle) => (
              <th
                key={cycle}
                style={{ padding: "10px", border: "1px solid #ddd" }}
              >
                {cycle}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {fuelTypes.map((fuelType) => (
            <tr key={fuelType}>
              <td style={{ padding: "10px", border: "1px solid #ddd" }}>
                {fuelType}
              </td>
              {cycles.map((cycle) => (
                <td
                  key={`${cycle}-${fuelType}`}
                  style={{ padding: "10px", border: "1px solid #ddd" }}
                >
                  {renderDateWithDifference(cycle, fuelType)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <div style={{ marginTop: "20px", fontSize: "14px" }}>
        <div>
          <strong>Current Report Date:</strong> {data.currentReportDate}
        </div>
        <div>
          <strong>Previous Report Date:</strong> {data.previousReportDate}
        </div>
        <div
          style={{
            color: data.isNewerData ? "green" : "red",
            marginTop: "5px",
          }}
        >
          {data.isNewerData
            ? "✓ Newer data available"
            : "⚠ Data may be outdated"}
        </div>
      </div>
    </div>
  );
};

export default FuelDateTable;
