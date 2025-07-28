import React, { useState, useEffect } from "react";

interface DateData {
  [cycle: string]: {
    [product: string]: {
      [location: string]: string;
    };
  };
}

interface ApiResponse {
  "Recent Data": {
    reportDate: string;
    [cycle: string]:
      | {
          [product: string]: {
            [location: string]: string;
          };
        }
      | string;
  };
  "Old Data": {
    reportDate: string;
    [cycle: string]:
      | {
          [product: string]: {
            [location: string]: string;
          };
        }
      | string;
  };
}

const DateInfoTable: React.FC = () => {
  const [apiData, setApiData] = useState<ApiResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCycle, setSelectedCycle] = useState<string>("");
  const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
  const [locations, setLocations] = useState<string[]>([]);

  useEffect(() => {
    if (!apiData || !selectedCycle) return;

    const recentData = apiData["Recent Data"];
    if (typeof recentData === "object" && recentData[selectedCycle]) {
      const cycleData = recentData[selectedCycle];
      if (typeof cycleData === "object") {
        const autoSelectedProducts = Object.keys(cycleData).filter(
          (product) =>
            product.startsWith("A") ||
            product.startsWith("D") ||
            product === "62"
        );
        setSelectedProducts(autoSelectedProducts);
      }
    }
  }, [selectedCycle, apiData]);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(
        "https://rioseasonalspreads-production.up.railway.app/getDateInfo"
      );
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      const jsonData: ApiResponse = await response.json();
      setApiData(jsonData);

      const allLocations = new Set<string>();
      const recentData = jsonData["Recent Data"];

      if (typeof recentData === "object") {
        Object.values(recentData).forEach((cycleData) => {
          if (cycleData && typeof cycleData === "object") {
            Object.values(cycleData).forEach((productData) => {
              if (productData && typeof productData === "object") {
                Object.keys(productData).forEach((location) => {
                  allLocations.add(location);
                });
              }
            });
          }
        });
      }
      setLocations(Array.from(allLocations).sort());

      if (typeof recentData === "object") {
        const cycles = Object.keys(recentData).filter(
          (key) => key !== "reportDate"
        );
        if (cycles.length > 0 && !selectedCycle) {
          setSelectedCycle(cycles[0]);
        }
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to fetch date info data"
      );
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleRefresh = () => {
    fetchData();
  };

  const handleProductToggle = (product: string) => {
    setSelectedProducts((prev) =>
      prev.includes(product)
        ? prev.filter((p) => p !== product)
        : [...prev, product]
    );
  };

  const getProductsForCycle = (): string[] => {
    if (!apiData || !selectedCycle) return [];
    const recentData = apiData["Recent Data"];
    if (typeof recentData !== "object") return [];
    const cycleData = recentData[selectedCycle];
    if (!cycleData || typeof cycleData !== "object") return [];
    return Object.keys(cycleData).sort();
  };

  const calculateDateDifference = (
    currentDate: string,
    previousDate: string | undefined
  ): number | null => {
    if (!previousDate) return null;

    const parseDate = (dateStr: string) => {
      const [month, day] = dateStr.split("/").map(Number);
      return new Date(2025, month - 1, day);
    };

    try {
      const current = parseDate(currentDate);
      const previous = parseDate(previousDate);
      const diffTime = current.getTime() - previous.getTime();
      const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));
      return diffDays;
    } catch (e) {
      console.error("Error parsing dates:", e);
      return null;
    }
  };

  const renderDateWithChange = (product: string, location: string) => {
    if (!apiData || !selectedCycle) return null;

    const recentData = apiData["Recent Data"];
    const oldData = apiData["Old Data"];

    if (typeof recentData !== "object") return null;
    const cycleData = recentData[selectedCycle];
    if (!cycleData || typeof cycleData !== "object") return null;

    const productData = cycleData[product];
    if (!productData || typeof productData !== "object") return null;

    const currentDate = productData[location];
    if (!currentDate) return null;

    let diffDays: number | null = null;

    if (typeof oldData === "object") {
      const oldCycleData = oldData[selectedCycle];
      if (oldCycleData && typeof oldCycleData === "object") {
        const oldProductData = oldCycleData[product];
        if (oldProductData && typeof oldProductData === "object") {
          const oldDate = oldProductData[location];
          diffDays = calculateDateDifference(currentDate, oldDate);
        }
      }
    }

    let changeIndicator = null;
    if (diffDays !== null && diffDays !== 0) {
      const color = diffDays > 0 ? "red" : "green";
      const symbol = diffDays > 0 ? "+" : "";
      changeIndicator = (
        <span
          style={{
            color,
            fontSize: "0.8em",
            marginLeft: "4px",
            fontWeight: "bold",
          }}
        >
          {symbol}
          {diffDays}
        </span>
      );
    }

    return (
      <span>
        {currentDate}
        {changeIndicator}
      </span>
    );
  };

  if (loading && !apiData) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <div
      style={{
        padding: "15px",
        fontFamily: "Segoe UI, sans-serif",
        maxWidth: "1400px",
        margin: "0 auto",
        fontSize: "13px",
      }}
    >
      {/* Centered header with refresh button and cycle tabs */}
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          marginBottom: "15px",
          width: "100%",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "15px",
            maxWidth: "100%",
            overflowX: "auto",
            padding: "8px 0",
          }}
        >
          <button
            onClick={handleRefresh}
            style={{
              backgroundColor: "#1890ff",
              color: "white",
              border: "none",
              padding: "6px 12px",
              borderRadius: "3px",
              cursor: "pointer",
              fontWeight: "600",
              fontSize: "12px",
              flexShrink: 0,
            }}
          >
            Refresh Data
          </button>

          {apiData && (
            <div
              style={{
                display: "flex",
                gap: "8px",
                flexShrink: 0,
                paddingRight: "15px",
              }}
            >
              {Object.keys(apiData["Recent Data"])
                .filter((key) => key !== "reportDate")
                .sort()
                .map((cycle) => (
                  <button
                    key={cycle}
                    onClick={() => setSelectedCycle(cycle)}
                    style={{
                      padding: "8px 16px",
                      backgroundColor:
                        selectedCycle === cycle ? "#1890ff" : "#f5f5f5",
                      color: selectedCycle === cycle ? "white" : "#333",
                      border: "1px solid #d9d9d9",
                      borderRadius: "4px",
                      cursor: "pointer",
                      fontWeight: "600",
                      fontSize: "14px",
                      whiteSpace: "nowrap",
                      flexShrink: 0,
                    }}
                  >
                    {cycle}
                  </button>
                ))}
            </div>
          )}
        </div>
      </div>

      {/* Main content area */}
      <div style={{ display: "flex", gap: "15px" }}>
        {/* Product selection panel */}
        <div style={{ width: "120px", flexShrink: 0 }}>
          <h2
            style={{
              fontSize: "14px",
              fontWeight: "600",
              marginBottom: "10px",
            }}
          >
            Products
          </h2>
          <div style={{ maxHeight: "400px", overflowY: "auto" }}>
            {getProductsForCycle().map((product) => (
              <div key={product} style={{ marginBottom: "6px" }}>
                <label style={{ display: "flex", alignItems: "center" }}>
                  <input
                    type="checkbox"
                    checked={selectedProducts.includes(product)}
                    onChange={() => handleProductToggle(product)}
                    style={{
                      marginRight: "6px",
                      width: "14px",
                      height: "14px",
                    }}
                  />
                  <span style={{ fontSize: "12px" }}>{product}</span>
                </label>
              </div>
            ))}
          </div>
        </div>

        {/* Delivery table */}
        <div style={{ flex: 1, minWidth: 0 }}>
          {selectedProducts.length > 0 ? (
            <div
              style={{
                overflowX: "auto",
                border: "1px solid #e0e0e0",
                borderRadius: "3px",
              }}
            >
              <table
                style={{
                  width: "100%",
                  borderCollapse: "collapse",
                  minWidth: "900px",
                  fontSize: "12px",
                }}
              >
                <thead>
                  <tr style={{ backgroundColor: "#f5f5f5" }}>
                    <th
                      style={{
                        padding: "8px",
                        textAlign: "left",
                        borderBottom: "1px solid #e0e0e0",
                        fontWeight: "600",
                        minWidth: "60px",
                      }}
                    >
                      Product
                    </th>
                    <th
                      style={{
                        padding: "8px",
                        textAlign: "left",
                        borderBottom: "1px solid #e0e0e0",
                        fontWeight: "600",
                        minWidth: "50px",
                      }}
                    >
                      Cycle
                    </th>
                    {locations.map((location) => (
                      <th
                        key={location}
                        style={{
                          padding: "8px",
                          textAlign: "left",
                          borderBottom: "1px solid #e0e0e0",
                          fontWeight: "600",
                          minWidth: "80px",
                        }}
                      >
                        {location}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {selectedProducts.map((product) => (
                    <tr
                      key={product}
                      style={{ borderBottom: "1px solid #e0e0e0" }}
                    >
                      <td style={{ padding: "8px" }}>{product}</td>
                      <td style={{ padding: "8px" }}>{selectedCycle}</td>
                      {locations.map((location) => (
                        <td
                          key={`${product}-${location}`}
                          style={{ padding: "8px" }}
                        >
                          {renderDateWithChange(product, location)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div
              style={{
                border: "1px solid #e0e0e0",
                borderRadius: "4px",
                padding: "10px",
                textAlign: "center",
                fontSize: "12px",
              }}
            >
              Select products to display the date table
            </div>
          )}
        </div>
      </div>

      {/* Report date footer */}
      <div
        style={{
          marginTop: "15px",
          fontSize: "12px",
          color: "#666",
          textAlign: "right",
        }}
      >
        <div>
          Current Report Date:{" "}
          {apiData && typeof apiData["Recent Data"] === "object"
            ? apiData["Recent Data"].reportDate
            : "N/A"}
        </div>
        <div>
          Previous Report Date:{" "}
          {apiData && typeof apiData["Old Data"] === "object"
            ? apiData["Old Data"].reportDate
            : "N/A"}
        </div>
      </div>
    </div>
  );
};

export default DateInfoTable;
