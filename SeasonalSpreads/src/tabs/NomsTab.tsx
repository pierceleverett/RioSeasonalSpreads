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

interface FungibleData {
  currentData: {
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
  const [fungibleData, setFungibleData] = useState<FungibleData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [bulletinDates, setBulletinDates] = useState<{
    origin: string;
    dateInfo: string;
  } | null>(null);

  // List of holidays (month is 0-indexed)
  const HOLIDAYS = [
    // 2025 Holidays
    new Date(2025, 0, 1), // New Year's Day
    new Date(2025, 0, 20), // Martin Luther King Jr. Day
    new Date(2025, 1, 17), // Presidents' Day
    new Date(2025, 4, 26), // Memorial Day
    new Date(2025, 5, 19), // Juneteenth
    new Date(2025, 6, 4), // Independence Day
    new Date(2025, 8, 1), // Labor Day
    new Date(2025, 10, 11), // Veterans Day
    new Date(2025, 10, 27), // Thanksgiving Day
    new Date(2025, 11, 25), // Christmas Day

    // 2026 Holidays
    new Date(2026, 0, 1), // New Year's Day
    new Date(2026, 0, 19), // Martin Luther King Jr. Day
    new Date(2026, 1, 16), // Presidents' Day
    new Date(2026, 4, 25), // Memorial Day
    new Date(2026, 5, 19), // Juneteenth
    new Date(2026, 6, 3), // Independence Day (observed)
    new Date(2026, 6, 4), // Independence Day (actual)
    new Date(2026, 8, 7), // Labor Day
    new Date(2026, 10, 11), // Veterans Day
    new Date(2026, 10, 26), // Thanksgiving Day
    new Date(2026, 11, 25), // Christmas Day

    // 2027 Holidays
    new Date(2027, 0, 1), // New Year's Day
    new Date(2027, 0, 18), // Martin Luther King Jr. Day
    new Date(2027, 1, 15), // Presidents' Day
    new Date(2027, 4, 31), // Memorial Day
    new Date(2027, 5, 19), // Juneteenth
    new Date(2027, 6, 5), // Independence Day (observed)
    new Date(2027, 6, 4), // Independence Day (actual)
    new Date(2027, 8, 6), // Labor Day
    new Date(2027, 10, 11), // Veterans Day
    new Date(2027, 10, 25), // Thanksgiving Day
    new Date(2027, 11, 24), // Christmas Day (observed)
    new Date(2027, 11, 25), // Christmas Day (actual)

    // 2028 Holidays
    new Date(2028, 0, 1), // New Year's Day
    new Date(2028, 0, 17), // Martin Luther King Jr. Day
    new Date(2028, 1, 21), // Presidents' Day
    new Date(2028, 4, 29), // Memorial Day
    new Date(2028, 5, 19), // Juneteenth
    new Date(2028, 6, 4), // Independence Day
    new Date(2028, 8, 4), // Labor Day
    new Date(2028, 10, 10), // Veterans Day (observed)
    new Date(2028, 10, 11), // Veterans Day (actual)
    new Date(2028, 10, 23), // Thanksgiving Day
    new Date(2028, 11, 25), // Christmas Day

    // 2029 Holidays
    new Date(2029, 0, 1), // New Year's Day
    new Date(2029, 0, 15), // Martin Luther King Jr. Day
    new Date(2029, 1, 19), // Presidents' Day
    new Date(2029, 4, 28), // Memorial Day
    new Date(2029, 5, 19), // Juneteenth
    new Date(2029, 6, 4), // Independence Day
    new Date(2029, 8, 3), // Labor Day
    new Date(2029, 10, 11), // Veterans Day (observed)
    new Date(2029, 10, 12), // Veterans Day (actual)
    new Date(2029, 10, 22), // Thanksgiving Day
    new Date(2029, 11, 25), // Christmas Day

    // 2030 Holidays
    new Date(2030, 0, 1), // New Year's Day
    new Date(2030, 0, 21), // Martin Luther King Jr. Day
    new Date(2030, 1, 18), // Presidents' Day
    new Date(2030, 4, 27), // Memorial Day
    new Date(2030, 5, 19), // Juneteenth
    new Date(2030, 6, 4), // Independence Day
    new Date(2030, 8, 2), // Labor Day
    new Date(2030, 10, 11), // Veterans Day
    new Date(2030, 10, 28), // Thanksgiving Day
    new Date(2030, 11, 25), // Christmas Day
  ];

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);

        // Fetch main line data
        const mainLineResponse = await fetch(
          "https://rioseasonalspreads-production.up.railway.app/getMainLine"
        );
        if (!mainLineResponse.ok) {
          throw new Error(`HTTP error! status: ${mainLineResponse.status}`);
        }
        const mainLineResult: ApiData = await mainLineResponse.json();
        setData(mainLineResult);

        // Get bulletin dates from the first available cycle
        const firstCycle = Object.values(mainLineResult)[0];
        if (firstCycle) {
          setBulletinDates({
            origin: firstCycle.Origin_Bulletin_Date,
            dateInfo: firstCycle.DateInfo_Bulletin_Date,
          });
        }

        // Fetch fungible data
        const fungibleResponse = await fetch(
          "https://rioseasonalspreads-production.up.railway.app/getRecentFungible"
        );
        if (!fungibleResponse.ok) {
          throw new Error(`HTTP error! status: ${fungibleResponse.status}`);
        }
        const fungibleResult: FungibleData = await fungibleResponse.json();
        setFungibleData(fungibleResult);
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

  // Check if a date is a holiday
  const isHoliday = (date: Date): boolean => {
    return HOLIDAYS.some(
      (holiday) =>
        holiday.getDate() === date.getDate() &&
        holiday.getMonth() === date.getMonth() &&
        holiday.getFullYear() === date.getFullYear()
    );
  };

  // Check if a date is a weekend
  const isWeekend = (date: Date): boolean => {
    return date.getDay() === 0 || date.getDay() === 6;
  };

  // Subtract business days from a date (excluding weekends and holidays)
  const subtractBusinessDays = (dateStr: string, days: number): string => {
    if (!dateStr) return "";

    const [month, day] = dateStr.split("/").map(Number);
    let date = new Date(new Date().getFullYear(), month - 1, day);
    let daysSubtracted = 0;

    while (daysSubtracted < days) {
      date = new Date(date.getTime() - 24 * 60 * 60 * 1000); // Subtract one day

      if (!isWeekend(date) && !isHoliday(date)) {
        daysSubtracted++;
      }
    }

    return formatDate(`${date.getMonth() + 1}/${date.getDate()}`);
  };

  // Format date from "mm/dd" to "d-Mmm" format (e.g., "07/07" to "7-Jul")
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

  // Get fungible date for a specific location and product
  const getFungibleDate = (
    cycle: string,
    product: string,
    location: string
  ): string => {
    if (!fungibleData) return "";

    const cycleData = fungibleData.currentData.data[cycle];
    if (!cycleData) return "";

    const productData = cycleData[product];
    if (!productData) return "";

    return productData[location] || "";
  };

  // Render fungible table for a specific location
  const renderFungibleTable = (location: string, locationName: string) => {
    if (!fungibleData) return null;

    const cycles = Object.keys(fungibleData.currentData.data).sort();

    return (
      <div style={{ marginTop: "40px" }}>
        <h2 style={{ textAlign: "center" }}>{locationName} Fungible Dates</h2>
        <table
          style={{
            borderCollapse: "collapse",
            width: "50%",
            margin: "0 auto 20px",
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
                62 (-2bd)
              </th>
              <th style={{ border: "1px solid #ddd", padding: "8px" }}>
                54 (-2bd)
              </th>
            </tr>
          </thead>
          <tbody>
            {cycles.map((cycle) => {
              const date62 = getFungibleDate(cycle, "62", location);
              const date54 = getFungibleDate(cycle, "54", location);

              return (
                <tr key={cycle}>
                  <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                    {cycle}
                  </td>
                  <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                    {date62 ? subtractBusinessDays(date62, 2) : ""}
                  </td>
                  <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                    {date54 ? subtractBusinessDays(date54, 2) : ""}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  };

  if (loading) return <div style={{ textAlign: "center" }}>Loading...</div>;
  if (error)
    return (
      <div style={{ textAlign: "center", color: "red" }}>Error: {error}</div>
    );
  if (!data)
    return <div style={{ textAlign: "center" }}>No data available</div>;

  return (
    <div
      style={{
        padding: "20px",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
      }}
    >
      <h1 style={{ textAlign: "center" }}>Main Line Noms Due</h1>
      <table
        style={{
          borderCollapse: "collapse",
          width: "80%",
          marginBottom: "20px",
          border: "1px solid #ddd",
          textAlign: "center",
        }}
      >
        <thead>
          <tr style={{ textAlign: "center" }}>
            <th
              rowSpan={2}
              style={{
                border: "1px solid #ddd",
                padding: "8px",
                textAlign: "center",
              }}
            >
              Cycle
            </th>
            <th
              colSpan={3}
              style={{
                border: "1px solid #ddd",
                padding: "8px",
                textAlign: "center",
              }}
            >
              Line 2
            </th>
            <th
              colSpan={3}
              style={{
                border: "1px solid #ddd",
                padding: "8px",
                textAlign: "center",
              }}
            >
              Line 1
            </th>
          </tr>
          <tr style={{ textAlign: "center" }}>
            {/* Line 2 headers */}
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                textAlign: "center",
              }}
            >
              62
            </th>
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                textAlign: "center",
              }}
            >
              HTN lift
            </th>
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                backgroundColor: "#fffacd",
                textAlign: "center",
              }}
            >
              51/54/62
            </th>
            {/* Line 1 headers */}
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                textAlign: "center",
              }}
            >
              A
            </th>
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                textAlign: "center",
              }}
            >
              HTN lift
            </th>
            <th
              style={{
                border: "1px solid #ddd",
                padding: "4px",
                backgroundColor: "#fffacd",
                textAlign: "center",
              }}
            >
              Gas
            </th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(data).map(([cycle, cycleData]) => (
            <tr key={cycle} style={{ textAlign: "center" }}>
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  textAlign: "center",
                }}
              >
                {cycle}
              </td>
              {/* Line 2 data */}
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  textAlign: "center",
                }}
              >
                {formatDate(cycleData["62_Scheduling_Date"])}
              </td>
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  textAlign: "center",
                }}
              >
                {formatDate(cycleData["62_Origin_Date"])}
              </td>
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  textAlign: "center",
                  backgroundColor: "#fffacd",
                }}
              >
                {formatDate(cycleData.Distillate_Nomination)}
              </td>
              {/* Line 1 data */}
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  textAlign: "center",
                }}
              >
                {formatDate(cycleData.A_Scheduling_Date)}
              </td>
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  textAlign: "center",
                }}
              >
                {formatDate(cycleData.A_Origin_Date)}
              </td>
              <td
                style={{
                  border: "1px solid #ddd",
                  padding: "4px",
                  textAlign: "center",
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
        <div
          style={{
            marginTop: "20px",
            textAlign: "center",
          }}
        >
          <div>
            <strong>Origin Bulletin Date:</strong> {bulletinDates.origin}
          </div>
          <div>
            <strong>DateInfo Bulletin Date:</strong> {bulletinDates.dateInfo}
          </div>
        </div>
      )}

      {/* Render fungible tables */}
      {fungibleData && (
        <>
          {renderFungibleTable("Atlanta", "ATJ")}
          {renderFungibleTable("Belton", "BLJ")}
          {renderFungibleTable("Dorsey", "DYY")}
        </>
      )}
    </div>
  );
};

export default NomsTab;
