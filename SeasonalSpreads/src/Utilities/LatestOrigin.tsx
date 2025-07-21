import React, { useEffect, useState } from "react";

interface FuelDates {
  A: string;
  D: string;
  F: string;
  "62": string;
}

interface ApiResponse {
  [key: string]: FuelDates | { Date: string };
  "Bulletin Date": {
    Date: string;
  };
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

  if (loading) return <div>Loading data...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!data) return <div>No data available</div>;

  // Extract cycles (excluding Bulletin Date)
  const cycles = Object.keys(data).filter((key) => key !== "Bulletin Date");

  // Fuel types (A, D, F, 62)
  const fuelTypes = ["A", "D", "F", "62"] as const;

  return (
    <div className="fuel-date-table-container">
      <table className="fuel-date-table">
        <thead>
          <tr>
            <th>Fuel Grade</th>
            {cycles.map((cycle) => (
              <th key={cycle}>{cycle}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {fuelTypes.map((fuelType) => (
            <tr key={fuelType}>
              <td>{fuelType}</td>
              {cycles.map((cycle) => (
                <td key={`${cycle}-${fuelType}`}>
                  {(data[cycle] as FuelDates)[fuelType]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <div className="bulletin-date">
        Bulletin Date: {data["Bulletin Date"].Date}
      </div>
    </div>
  );
};

export default FuelDateTable;
