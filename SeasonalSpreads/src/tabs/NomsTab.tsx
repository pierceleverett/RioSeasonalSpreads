// Noms.tsx
import React, { useEffect, useState } from "react";

interface NomData {
  Distillate_Nomination: string | null;
  A_Scheduling_Date: string;
  Origin_Bulletin_Date: string;
  DateInfo_Bulletin_Date: string;
  A_Origin_Date: string;
  "62_Origin_Date": string;
  Gas_Nomination: string | null;
  Earliest_Distillate_Scheduling_Date: string;
  "62_Scheduling_Date": string;
}

interface ApiData {
  [key: string]: NomData;
}

interface FungibleResponse {
  currentData: {
    reportDate: string;
    data: {
      [cycle: string]: {
        [product: string]: {
          [location: string]: string;
        };
      };
    };
  };
  previousData: {
    reportDate: string;
    data: {
      [cycle: string]: {
        [product: string]: {
          [location: string]: string;
        };
      };
    };
  };
}

const NomsTab: React.FC = () => {
  const [data, setData] = useState<ApiData | null>(null);
  const [fungibleData, setFungibleData] = useState<FungibleResponse | null>(
    null
  );
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [bulletinDates, setBulletinDates] = useState<{
    origin: string;
    dateInfo: string;
  } | null>(null);

  // List of holidays (2023-2030)
  const HOLIDAYS = [
    // 2023 Holidays
    new Date(2023, 0, 2), // New Year's Day
    new Date(2023, 0, 16), // Martin Luther King Jr. Day
    new Date(2023, 1, 20), // Presidents' Day
    new Date(2023, 4, 29), // Memorial Day
    new Date(2023, 5, 19), // Juneteenth
    new Date(2023, 6, 4), // Independence Day
    new Date(2023, 8, 4), // Labor Day
    new Date(2023, 10, 10), // Veterans Day
    new Date(2023, 10, 23), // Thanksgiving Day
    new Date(2023, 11, 25), // Christmas Day
    // ... (other years would continue here)
  ];

  function isNomData(obj: any): obj is NomData {
    return "Origin_Bulletin_Date" in obj && "DateInfo_Bulletin_Date" in obj;
  }

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);

        // Fetch both APIs in parallel
        const [mainLineResponse, fungibleResponse] = await Promise.all([
          fetch(
            "https://rioseasonalspreads-production.up.railway.app/getMainLine"
          ),
          fetch(
            "https://rioseasonalspreads-production.up.railway.app/getRecentFungible"
          ),
        ]);

        if (!mainLineResponse.ok)
          throw new Error(`Main line error: ${mainLineResponse.status}`);
        if (!fungibleResponse.ok)
          throw new Error(`Fungible error: ${fungibleResponse.status}`);

        const [mainLineResult, fungibleResult] = await Promise.all([
          mainLineResponse.json(),
          fungibleResponse.json(),
        ]);

        setData(mainLineResult);
        setFungibleData(fungibleResult);

        // Get bulletin dates
        // Update the bulletin dates section in the fetchData function
        const firstCycle = Object.values(mainLineResult)[0];
        if (firstCycle && isNomData(firstCycle)) {
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

  // Date utilities
  const isHoliday = (date: Date): boolean =>
    HOLIDAYS.some(
      (h) =>
        h.getDate() === date.getDate() &&
        h.getMonth() === date.getMonth() &&
        h.getFullYear() === date.getFullYear()
    );

  const isWeekend = (date: Date): boolean =>
    date.getDay() === 0 || date.getDay() === 6;

  const subtractBusinessDays = (dateStr: string, days: number): string => {
    if (!dateStr) return "";
    const [month, day] = dateStr.split("/").map(Number);
    let date = new Date(new Date().getFullYear(), month - 1, day);
    let daysSubtracted = 0;

    while (daysSubtracted < days) {
      date = new Date(date.getTime() - 86400000); // Subtract one day
      if (!isWeekend(date) && !isHoliday(date)) daysSubtracted++;
    }

    return formatDate(`${date.getMonth() + 1}/${date.getDate()}`);
  };

  const formatDate = (dateStr: string | null | undefined): string => {
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

  const getFungibleDate = (
    cycle: string,
    product: string,
    location: string
  ): string => {
    return fungibleData?.currentData?.data[cycle]?.[product]?.[location] || "";
  };

  const renderFungibleTable = (location: string, locationName: string) => {
    if (!fungibleData) return null;
    const cycles = Object.keys(fungibleData.currentData.data).sort();

    return (
      <div style={{ flex: 1, minWidth: 300 }}>
        <h3 style={{ textAlign: "center" }}>{locationName}</h3>
        <table
          style={{
            borderCollapse: "collapse",
            width: "100%",
            border: "1px solid #ddd",
            textAlign: "center",
          }}
        >
          <thead>
            <tr>
              <th style={{ border: "1px solid #ddd", padding: "8px" }}>
                Cycle
              </th>
              <th style={{ border: "1px solid #ddd", padding: "8px" }}>
                62
              </th>
              <th style={{ border: "1px solid #ddd", padding: "8px" }}>
                54
              </th>
            </tr>
          </thead>
          <tbody>
            {cycles.map((cycle) => (
              <tr key={cycle}>
                <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                  {cycle}
                </td>
                <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                  {subtractBusinessDays(
                    getFungibleDate(cycle, "62", location),
                    2
                  )}
                </td>
                <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                  {subtractBusinessDays(
                    getFungibleDate(cycle, "54", location),
                    2
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  if (fungibleData) {
  console.log(fungibleData.currentData.reportDate);
  }

  if (loading) return <div style={{ textAlign: "center" }}>Loading...</div>;
  if (error)
    return (
      <div style={{ textAlign: "center", color: "red" }}>Error: {error}</div>
    );
  if (!data)
    return <div style={{ textAlign: "center" }}>No data available</div>;

  return (
    <div style={{ padding: "20px", maxWidth: 1200, margin: "0 auto" }}>
      {/* Main Line Table */}
      <h1 style={{ textAlign: "center" }}>Main Line Noms Due</h1>
      <table
        style={{
          borderCollapse: "collapse",
          width: "100%",
          margin: "20px auto",
          border: "1px solid #ddd",
          textAlign: "center",
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
              colSpan={3}
              style={{ border: "1px solid #ddd", padding: "8px" }}
            >
              Line 1
            </th>
          </tr>
          <tr>
            {/* Line 2 headers */}
            <th style={{ border: "1px solid #ddd", padding: "4px" }}>62</th>
            <th style={{ border: "1px solid #ddd", padding: "4px" }}>
              HTN lift
            </th>
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                backgroundColor: "#fffacd",
              }}
            >
              51/54/62
            </th>
            {/* Line1 headers */}
            <th style={{ border: "1px solid #ddd", padding: "4px" }}>A</th>
            <th style={{ border: "1px solid #ddd", padding: "4px" }}>
              HTN lift
            </th>
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                backgroundColor: "#fffacd",
              }}
            >
              Gas
            </th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(data).map(([cycle, cycleData]) => (
            <tr key={cycle}>
              <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                {cycle}
              </td>
              {/* Line2 data */}
              <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                {formatDate(cycleData["62_Scheduling_Date"])}
              </td>
              <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                {formatDate(cycleData["62_Origin_Date"])}
              </td>
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  backgroundColor: "#fffacd",
                }}
              >
                {formatDate(cycleData.Distillate_Nomination)}
              </td>
              {/* Line1 data */}
              <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                {formatDate(cycleData.A_Scheduling_Date)}
              </td>
              <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                {formatDate(cycleData.A_Origin_Date)}
              </td>
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  backgroundColor: "#fffacd",
                }}
              >
                {formatDate(cycleData.Gas_Nomination)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {bulletinDates && (
        <div style={{ textAlign: "center", margin: "20px 0" }}>
          <div>
            <strong>Origin Bulletin Date:</strong> {bulletinDates.origin}
          </div>
          <div>
            <strong>DateInfo Bulletin Date:</strong> {bulletinDates.dateInfo}
          </div>
        </div>
      )}

      {/* Fungible Tables */}
      {fungibleData && (
        <div style={{ marginTop: 40 }}>
          <h2 style={{ textAlign: "center" }}>Stub Lines</h2>
          <div style={{ textAlign: "center", marginBottom: 20 }}>
            <strong>Report Date:</strong>{" "}
            {fungibleData.currentData.reportDate}
          </div>

          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: 20,
              flexWrap: "wrap",
            }}
          >
            {renderFungibleTable("Atlanta", "Line 20 - ATJ")}
            {renderFungibleTable("Belton", "Line 29 - BLJ")}
            {renderFungibleTable("Dorsey", "Line 32 - DYJ")}
          </div>
        </div>
      )}
    </div>
  );
};

export default NomsTab;
