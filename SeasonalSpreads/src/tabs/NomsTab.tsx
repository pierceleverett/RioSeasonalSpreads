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

interface StubNomData {
  Stub_172029_Nomination: string | null;
  Stub_32_Nomination: string | null;
}

interface StubApiResponse {
  DateInfoReportDate: string;
  FungibleReportDate: string;
  data: {
    [key: string]: StubNomData;
  };
}

interface ApiData {
  [key: string]: NomData;
}

const NomsTab: React.FC = () => {
  const [data, setData] = useState<ApiData | null>(null);
  const [stubResponse, setStubResponse] = useState<StubApiResponse | null>(
    null
  );
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [bulletinDates, setBulletinDates] = useState<{
    origin: string;
    dateInfo: string;
    fungible: string;
  } | null>(null);

  function isNomData(obj: any): obj is NomData {
    return "Origin_Bulletin_Date" in obj && "DateInfo_Bulletin_Date" in obj;
  }

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [mainLineResponse, stubResponse] = await Promise.all([
          fetch(
            "https://rioseasonalspreads-production.up.railway.app/getMainLine"
          ),
          fetch(
            "https://rioseasonalspreads-production.up.railway.app/getStubNoms"
          ),
        ]);

        if (!mainLineResponse.ok)
          throw new Error(`Main line error: ${mainLineResponse.status}`);
        if (!stubResponse.ok)
          throw new Error(`Stub line error: ${stubResponse.status}`);

        const [mainLineResult, stubResult] = await Promise.all([
          mainLineResponse.json(),
          stubResponse.json(),
        ]);

        setData(mainLineResult);
        setStubResponse(stubResult);

        // Get bulletin dates
        const firstCycle = Object.values(mainLineResult)[0];
        if (firstCycle && isNomData(firstCycle)) {
          setBulletinDates({
            origin: firstCycle.Origin_Bulletin_Date,
            dateInfo: firstCycle.DateInfo_Bulletin_Date,
            fungible: stubResult.FungibleReportDate,
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

  const formatDate = (dateStr: string | null | undefined): string => {
    if (!dateStr) return "";
    // Handle ISO format dates (YYYY-MM-DD)
    if (dateStr.includes("-")) {
      const [year, month, day] = dateStr.split("-").map(Number);
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
      return `${day}-${monthNames[month - 1]}-${year.toString().slice(2)}`;
    }
    // Handle MM/DD format
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

  if (loading) return <div style={{ textAlign: "center" }}>Loading...</div>;
  if (error)
    return (
      <div style={{ textAlign: "center", color: "red" }}>Error: {error}</div>
    );
  if (!data || !stubResponse)
    return <div style={{ textAlign: "center" }}>No data available</div>;

  return (
    <div style={{ padding: "20px", maxWidth: 1400, margin: "0 auto" }}>
      <div style={{ display: "flex", gap: "20px", flexWrap: "wrap" }}>
        {/* Main Line Table */}
        <div style={{ flex: 1, minWidth: 600 }}>
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
        </div>

        {/* Stub Line Table */}
        <div style={{ flex: 1, minWidth: 300 }}>
          <h1 style={{ textAlign: "center" }}>Stub Line Noms Due</h1>
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
                <th style={{ border: "1px solid #ddd", padding: "8px" }}>
                  Cycle
                </th>
                <th style={{ border: "1px solid #ddd", padding: "8px" }}>
                  17,19,20,29
                </th>
                <th style={{ border: "1px solid #ddd", padding: "8px" }}>32</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(stubResponse.data).map(([cycle, cycleData]) => (
                <tr key={cycle}>
                  <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                    {cycle}
                  </td>
                  <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                    {formatDate(cycleData.Stub_172029_Nomination)}
                  </td>
                  <td style={{ border: "1px solid #ddd", padding: "4px" }}>
                    {formatDate(cycleData.Stub_32_Nomination)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {bulletinDates && (
        <div style={{ textAlign: "center", margin: "20px 0" }}>
          <div>
            <strong>Origin Bulletin Date:</strong>{" "}
            {formatDate(bulletinDates.origin)}
          </div>
          <div>
            <strong>DateInfo Bulletin Date:</strong>{" "}
            {formatDate(bulletinDates.dateInfo)}
          </div>
          <div>
            <strong>Fungible Report Date:</strong>{" "}
            {formatDate(bulletinDates.fungible)}
          </div>
        </div>
      )}
    </div>
  );
};

export default NomsTab;
