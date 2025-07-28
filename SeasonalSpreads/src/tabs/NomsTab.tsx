import React, { useEffect, useState } from "react";
import { useUser } from "@clerk/clerk-react"; 
import { useAuth } from "@clerk/clerk-react";

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
  Min_Stub_32_Nomination: string | null; // For "32 Earliest"
  My_Stub_32_Nomination: string | null; // For "32 Prediction"
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

interface Holiday {
  date: string;
  name: string;
}

interface FungibleData {
  currentData: {
    data: {
      [cycle: string]: {
        [fuelType: string]: {
          [location: string]: string;
        };
      };
    };
    reportDate: string;
  };
  // ... other fields from the response if needed
}

const NomsTab: React.FC = () => {
  const { getToken } = useAuth();
  const { user, isLoaded } = useUser();
  const [data, setData] = useState<ApiData | null>(null);
  const [stubResponse, setStubResponse] = useState<StubApiResponse | null>(
    null
  );
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedYear, setSelectedYear] = useState<number>(
    new Date().getFullYear()
  );
  const [bulletinDates, setBulletinDates] = useState<{
    origin: string;
    dateInfo: string;
    fungible: string;
  } | null>(null);
  const [activeTab, setActiveTab] = useState<"nominations" | "holidays">(
    "nominations"
  );
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [newHoliday, setNewHoliday] = useState<Holiday>({ date: "", name: "" });
  const [isSaving, setIsSaving] = useState(false);
  const [fungibleData, setFungibleData] = useState<FungibleData | null>(null);

  function isNomData(obj: any): obj is NomData {
    return "Origin_Bulletin_Date" in obj && "DateInfo_Bulletin_Date" in obj;
  }

  // Update the filteredHolidays calculation to properly handle date parsing
  const filteredHolidays = holidays.filter((holiday) => {
    try {
      // Parse the date string (handling both YYYY-MM-DD and MM/DD/YYYY formats)
      const dateParts = holiday.date.includes("-")
        ? holiday.date.split("-")
        : holiday.date.split("/");

      let year;
      if (holiday.date.includes("-")) {
        // ISO format (YYYY-MM-DD)
        year = parseInt(dateParts[0]);
      } else {
        // MM/DD/YYYY format
        year = parseInt(dateParts[2]);
      }

      return year === selectedYear;
    } catch (e) {
      console.error("Error parsing holiday date:", holiday.date);
      return false;
    }
  });

  const fetchFungibleData = async () => {
    try {
      const token = await getToken();
      const response = await fetch(
        "https://rioseasonalspreads-production.up.railway.app/getRecentFungible",
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );
      if (!response.ok)
        throw new Error(`HTTP error! status: ${response.status}`);
      return await response.json();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to fetch fungible data"
      );
      return null;
    }
  };

  useEffect(() => {
    const loadData = async () => {
      if (user?.id) {
        const data = await fetchFungibleData();
        if (data) setFungibleData(data);
      }
    };
    loadData();
  }, [user?.id]);

  // Fetch nomination data
  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const token = await getToken(); // Get the session token

        const [mainLineResponse, stubResponse] = await Promise.all([
          fetch(
            `https://rioseasonalspreads-production.up.railway.app/getMainLine?userid=${user?.id}`,
            {
              headers: {
                Authorization: `Bearer ${token}`, // Pass token in Authorization header
              },
            }
          ),
          fetch(
            `https://rioseasonalspreads-production.up.railway.app/getStubNoms?userid=${user?.id}`,
            {
              headers: {
                Authorization: `Bearer ${token}`, // Pass token in Authorization header
              },
            }
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

    if (user?.id) fetchData();
  }, [user?.id]);

  // Load holidays from user metadata
  useEffect(() => {
    if (isLoaded && user) {
      const userHolidays = user.unsafeMetadata.holidays;
      if (Array.isArray(userHolidays)) {
        setHolidays(userHolidays);
      }
    }
  }, [user, isLoaded]);

  const subtractBusinessDays = (dateStr: string, days: number): string => {
    if (!dateStr) return "";

    const [month, day] = dateStr.split("/").map(Number);
    const currentYear = new Date().getFullYear();
    const date = new Date(currentYear, month - 1, day);

    let count = 0;
    while (count < days) {
      date.setDate(date.getDate() - 1);

      if (date.getDay() === 0 || date.getDay() === 6) continue;

      const dateString = `${date.getFullYear()}-${String(
        date.getMonth() + 1
      ).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
      const isHoliday = holidays.some((holiday) => holiday.date === dateString);
      if (isHoliday) continue;

      count++;
    }

    return `${date.getMonth() + 1}/${date.getDate()}`;
  };

  const formatDate = (dateStr: string | null | undefined): string => {
    if (!dateStr) return "";
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
  

  const handleAddHoliday = () => {
    if (newHoliday.date && newHoliday.name) {
      setHolidays([...holidays, newHoliday]);
      setNewHoliday({ date: "", name: "" });
    }
  };

  const handleDeleteHoliday = (index: number) => {
    setHolidays(holidays.filter((_, i) => i !== index));
  };

  const saveHolidays = async () => {
    if (!user) return;

    try {
      setIsSaving(true);
      await user.update({
        unsafeMetadata: {
          holidays: holidays,
        },
      });
      setActiveTab("nominations");
    } catch (err) {
      setError("Failed to save holidays. Please try again.");
    } finally {
      setIsSaving(false);
    }
  };

  if (loading) return <div style={{ textAlign: "center" }}>Loading...</div>;
  if (error)
    return (
      <div style={{ textAlign: "center", color: "red" }}>Error: {error}</div>
    );


    return (
      <div style={{ padding: "20px", maxWidth: 1400, margin: "0 auto" }}>
        <div style={{ display: "flex", gap: "20px", marginBottom: "20px" }}>
          <button
            onClick={() => setActiveTab("nominations")}
            style={{
              padding: "8px 16px",
              background: activeTab === "nominations" ? "#4CAF50" : "#e0e0e0",
              color: activeTab === "nominations" ? "white" : "black",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer",
            }}
          >
            Nominations
          </button>
          <button
            onClick={() => setActiveTab("holidays")}
            style={{
              padding: "8px 16px",
              background: activeTab === "holidays" ? "#4CAF50" : "#e0e0e0",
              color: activeTab === "holidays" ? "white" : "black",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer",
            }}
          >
            Edit Holidays
          </button>
        </div>

        {activeTab === "nominations" ? (
          <>
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
                      <th style={{ border: "1px solid #ddd", padding: "4px" }}>
                        62 - Sched
                      </th>
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
                      <th style={{ border: "1px solid #ddd", padding: "4px" }}>
                        A - Sched
                      </th>
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
                    {data &&
                      Object.entries(data).map(([cycle, cycleData]) => (
                        <tr key={cycle}>
                          <td
                            style={{ border: "1px solid #ddd", padding: "4px" }}
                          >
                            {cycle}
                          </td>
                          <td
                            style={{ border: "1px solid #ddd", padding: "4px" }}
                          >
                            {formatDate(cycleData["62_Scheduling_Date"])}
                          </td>
                          <td
                            style={{ border: "1px solid #ddd", padding: "4px" }}
                          >
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
                          <td
                            style={{ border: "1px solid #ddd", padding: "4px" }}
                          >
                            {formatDate(cycleData.A_Scheduling_Date)}
                          </td>
                          <td
                            style={{ border: "1px solid #ddd", padding: "4px" }}
                          >
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
                    width: "75%",
                    margin: "10px auto",
                    border: "1px solid #ddd",
                    textAlign: "center",
                  }}
                >
                  <thead>
                    <tr>
                      <th
                        style={{
                          border: "1px solid #ddd",
                          padding: "4px",
                          width: "25%",
                        }}
                      >
                        Cycle
                      </th>
                      <th
                        style={{
                          border: "1px solid #ddd",
                          padding: "4px",
                          width: "35%",
                        }}
                      >
                        Upstream of GBJ
                      </th>
                      <th
                        colSpan={2}
                        style={{
                          border: "1px solid #ddd",
                          padding: "4px",
                        }}
                      >
                        Downstream of GBJ
                      </th>
                    </tr>
                    <tr>
                      <th
                        style={{
                          border: "1px solid #ddd",
                          padding: "4px",
                          width: "25%",
                        }}
                      ></th>
                      <th
                        style={{
                          border: "1px solid #ddd",
                          padding: "4px",
                          width: "35%",
                        }}
                      ></th>
                      <th
                        style={{
                          border: "1px solid #ddd",
                          padding: "4px",
                          width: "17.5%",
                        }}
                      >
                        Earliest
                      </th>
                      <th
                        style={{
                          border: "1px solid #ddd",
                          padding: "4px",
                          width: "17.5%",
                        }}
                      >
                        Prediction
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {stubResponse &&
                      Object.entries(stubResponse.data).map(
                        ([cycle, cycleData]) => (
                          <tr key={cycle}>
                            <td
                              style={{
                                border: "1px solid #ddd",
                                padding: "4px",
                                width: "25%",
                              }}
                            >
                              {cycle}
                            </td>
                            <td
                              style={{
                                border: "1px solid #ddd",
                                padding: "4px",
                                width: "35%",
                              }}
                            >
                              {formatDate(cycleData.Stub_172029_Nomination)}
                            </td>
                            <td
                              style={{
                                border: "1px solid #ddd",
                                padding: "4px",
                                width: "17.5%",
                              }}
                            >
                              {formatDate(cycleData.Min_Stub_32_Nomination)}
                            </td>
                            <td
                              style={{
                                border: "1px solid #ddd",
                                padding: "4px",
                                width: "17.5%",
                              }}
                            >
                              {formatDate(cycleData.My_Stub_32_Nomination)}
                            </td>
                          </tr>
                        )
                      )}
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

            {/* New Stub Line Scheduling Tables */}
            {fungibleData && (
              <div style={{ marginTop: "40px" }}>
                <h1 style={{ textAlign: "center", marginBottom: "30px" }}>
                  Stub Line Scheduling
                </h1>

                <div
                  style={{
                    display: "flex",
                    gap: "20px",
                    flexWrap: "wrap",
                    justifyContent: "center",
                  }}
                >
                  {/* Table 1: Line 20 - ATJ (Atlanta) */}
                  <div style={{ flex: 1, minWidth: 300 }}>
                    <h2 style={{ textAlign: "center" }}>Line 17/20 - ATJ</h2>
                    <table
                      style={{
                        borderCollapse: "collapse",
                        width: "100%",
                        margin: "0 auto",
                        border: "1px solid #ddd",
                        textAlign: "center",
                      }}
                    >
                      <thead>
                        <tr>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            Cycle
                          </th>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            62
                          </th>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            54
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {Object.entries(fungibleData.currentData.data).map(
                          ([cycle, cycleData]) => {
                            const atlanta62 = cycleData["62"]?.["Atlanta"];
                            const atlanta54 = cycleData["54"]?.["Atlanta"];

                            return (
                              <tr key={cycle}>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {cycle}
                                </td>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {atlanta62
                                    ? subtractBusinessDays(atlanta62, 3)
                                    : "-"}
                                </td>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {atlanta54
                                    ? subtractBusinessDays(atlanta54, 3)
                                    : "-"}
                                </td>
                              </tr>
                            );
                          }
                        )}
                      </tbody>
                    </table>
                  </div>

                  {/* Table 2: Belton */}
                  <div style={{ flex: 1, minWidth: 300 }}>
                    <h2 style={{ textAlign: "center" }}>Line 29 - BLJ</h2>
                    <table
                      style={{
                        borderCollapse: "collapse",
                        width: "100%",
                        margin: "0 auto",
                        border: "1px solid #ddd",
                        textAlign: "center",
                      }}
                    >
                      <thead>
                        <tr>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            Cycle
                          </th>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            62
                          </th>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            A
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {Object.entries(fungibleData.currentData.data).map(
                          ([cycle, cycleData]) => {
                            const belton62 = cycleData["62"]?.["Belton"];
                            const beltonA = cycleData["A2"]?.["Belton"];

                            return (
                              <tr key={cycle}>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {cycle}
                                </td>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {belton62
                                    ? subtractBusinessDays(belton62, 3)
                                    : "-"}
                                </td>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {beltonA
                                    ? subtractBusinessDays(beltonA, 3)
                                    : "-"}
                                </td>
                              </tr>
                            );
                          }
                        )}
                      </tbody>
                    </table>
                  </div>

                  {/* Table 3: Dorsey */}
                  <div style={{ flex: 1, minWidth: 300 }}>
                    <h2 style={{ textAlign: "center" }}>Line 32 - DYJ</h2>
                    <table
                      style={{
                        borderCollapse: "collapse",
                        width: "100%",
                        margin: "0 auto",
                        border: "1px solid #ddd",
                        textAlign: "center",
                      }}
                    >
                      <thead>
                        <tr>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            Cycle
                          </th>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            62
                          </th>
                          <th
                            style={{ border: "1px solid #ddd", padding: "8px" }}
                          >
                            F
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {Object.entries(fungibleData.currentData.data).map(
                          ([cycle, cycleData]) => {
                            const dorsey62 = cycleData["62"]?.["Dorsey"];
                            const dorseyF = cycleData["F1"]?.["Dorsey"];

                            return (
                              <tr key={cycle}>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {cycle}
                                </td>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {dorsey62
                                    ? subtractBusinessDays(dorsey62, 3)
                                    : "-"}
                                </td>
                                <td
                                  style={{
                                    border: "1px solid #ddd",
                                    padding: "8px",
                                  }}
                                >
                                  {dorseyF
                                    ? subtractBusinessDays(dorseyF, 3)
                                    : "-"}
                                </td>
                              </tr>
                            );
                          }
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )}
          </>
        ) : (
          <div style={{ maxWidth: 800, margin: "0 auto" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                marginBottom: "20px",
              }}
            >
              <h1 style={{ textAlign: "center", marginBottom: "0" }}>
                Edit Holidays
              </h1>
              <div
                style={{ display: "flex", alignItems: "center", gap: "10px" }}
              >
                <label htmlFor="year-select">Filter by Year:</label>
                <select
                  id="year-select"
                  value={selectedYear}
                  onChange={(e) => setSelectedYear(Number(e.target.value))}
                  style={{ padding: "8px", borderRadius: "4px" }}
                >
                  <option value={new Date().getFullYear()}>Current Year</option>
                  <option value={new Date().getFullYear() + 1}>
                    Next Year
                  </option>
                </select>
              </div>
            </div>

            <div style={{ marginBottom: "30px" }}>
              <h3 style={{ marginBottom: "10px" }}>Current Holidays</h3>
              <table
                style={{
                  width: "100%",
                  borderCollapse: "collapse",
                  marginBottom: "20px",
                }}
              >
                <thead>
                  <tr>
                    <th
                      style={{
                        border: "1px solid #ddd",
                        padding: "6px",
                        textAlign: "left",
                      }}
                    >
                      Date
                    </th>
                    <th
                      style={{
                        border: "1px solid #ddd",
                        padding: "8px",
                        textAlign: "left",
                      }}
                    >
                      Name
                    </th>
                    <th style={{ border: "1px solid #ddd", padding: "8px" }}>
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filteredHolidays.map((holiday, index) => (
                    <tr key={index}>
                      <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                        <input
                          type="date"
                          value={holiday.date}
                          onChange={(e) => {
                            const updated = [...holidays];
                            updated[index].date = e.target.value;
                            setHolidays(updated);
                          }}
                          style={{ width: "90%", padding: "6px" }}
                        />
                      </td>
                      <td style={{ border: "1px solid #ddd", padding: "8px" }}>
                        <input
                          type="text"
                          value={holiday.name}
                          onChange={(e) => {
                            const updated = [...holidays];
                            updated[index].name = e.target.value;
                            setHolidays(updated);
                          }}
                          style={{ width: "90%", padding: "6px" }}
                        />
                      </td>
                      <td
                        style={{
                          border: "1px solid #ddd",
                          padding: "8px",
                          textAlign: "center",
                        }}
                      >
                        <button
                          onClick={() => handleDeleteHoliday(index)}
                          style={{
                            padding: "4px 8px",
                            background: "#ff4444",
                            color: "white",
                            border: "none",
                            borderRadius: "4px",
                            cursor: "pointer",
                          }}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {filteredHolidays.length === 0 && (
                <div
                  style={{
                    textAlign: "center",
                    padding: "20px",
                    color: "#666",
                  }}
                >
                  No holidays for {selectedYear}
                </div>
              )}
            </div>

            <div style={{ marginBottom: "30px" }}>
              <h3 style={{ marginBottom: "10px" }}>Add New Holiday</h3>
              <div
                style={{ display: "flex", gap: "10px", alignItems: "center" }}
              >
                <input
                  type="date"
                  value={newHoliday.date}
                  onChange={(e) =>
                    setNewHoliday({ ...newHoliday, date: e.target.value })
                  }
                  style={{ padding: "8px", flex: 1 }}
                />
                <input
                  type="text"
                  value={newHoliday.name}
                  onChange={(e) =>
                    setNewHoliday({ ...newHoliday, name: e.target.value })
                  }
                  placeholder="Holiday name"
                  style={{ padding: "8px", flex: 2 }}
                />
                <button
                  onClick={handleAddHoliday}
                  style={{
                    padding: "8px 16px",
                    background: "#4CAF50",
                    color: "white",
                    border: "none",
                    borderRadius: "4px",
                    cursor: "pointer",
                  }}
                >
                  Add Holiday
                </button>
              </div>
            </div>

            <div
              style={{ display: "flex", justifyContent: "center", gap: "20px" }}
            >
              <button
                onClick={saveHolidays}
                disabled={isSaving}
                style={{
                  padding: "10px 20px",
                  background: isSaving ? "#cccccc" : "#4CAF50",
                  color: "white",
                  border: "none",
                  borderRadius: "4px",
                  cursor: isSaving ? "not-allowed" : "pointer",
                }}
              >
                {isSaving ? "Saving..." : "Save All Changes"}
              </button>
              <button
                onClick={() => setActiveTab("nominations")}
                style={{
                  padding: "10px 20px",
                  background: "#e0e0e0",
                  border: "none",
                  borderRadius: "4px",
                  cursor: "pointer",
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    );
};

export default NomsTab;