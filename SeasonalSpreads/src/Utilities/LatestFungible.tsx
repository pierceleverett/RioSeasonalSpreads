import React, { useState, useEffect } from "react";

interface FungibleData {
  data: {
    [cycle: string]: {
      [product: string]: {
        [location: string]: string;
      };
    };
  };
}

interface ApiResponse {
  currentData: FungibleData;
  previousData: FungibleData;
  currentReportDate: string;
  isNewerData: boolean;
  previousReportDate: string;
}

const FungibleDeliveriesTable: React.FC = () => {
  const [apiData, setApiData] = useState<ApiResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCycle, setSelectedCycle] = useState<string>("");
  const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
  const [locations, setLocations] = useState<string[]>([]);

  // Auto-select A2, D2, and 62 products when cycle changes
  useEffect(() => {
    if (!apiData || !selectedCycle) return;

    const cycleData = apiData.currentData.data[selectedCycle];
    if (!cycleData) return;

    const autoSelectedProducts = Object.keys(cycleData).filter((product) =>
      ["A2", "D2", "62"].includes(product)
    );

    setSelectedProducts(autoSelectedProducts);
  }, [selectedCycle, apiData]);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(
        "https://rioseasonalspreads-production.up.railway.app/getRecentFungible"
      );
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      const jsonData: ApiResponse = await response.json();
      setApiData(jsonData);

      // Extract all unique locations from the data
      const allLocations = new Set<string>();
      const currentData = jsonData.currentData.data;

      Object.values(currentData).forEach((cycleData) => {
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
      setLocations(Array.from(allLocations).sort());

      // Auto-select the first cycle if available
      const cycles = Object.keys(currentData);
      if (cycles.length > 0 && !selectedCycle) {
        setSelectedCycle(cycles[0]);
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to fetch fungible data"
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
    const cycleData = apiData.currentData.data[selectedCycle];
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

    const cycleData = apiData.currentData.data[selectedCycle];
    const prevCycleData = apiData.previousData.data[selectedCycle];

    if (!cycleData || typeof cycleData !== "object") return null;

    const productData = cycleData[product];
    if (!productData || typeof productData !== "object") return null;

    const currentDate = productData[location];
    if (!currentDate) return null;

    let diffDays: number | null = null;

    if (prevCycleData && typeof prevCycleData === "object") {
      const prevProductData = prevCycleData[product];
      if (prevProductData && typeof prevProductData === "object") {
        const previousDate = prevProductData[location];
        diffDays = calculateDateDifference(currentDate, previousDate);
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

  const renderTable = () => {
    if (!apiData || !selectedCycle || selectedProducts.length === 0)
      return null;

    return (
      <div
        style={{
          overflowX: "auto",
          marginTop: "20px",
          border: "1px solid #e0e0e0",
          borderRadius: "4px",
        }}
      >
        <table
          style={{
            width: "100%",
            borderCollapse: "collapse",
          }}
        >
          <thead>
            <tr style={{ backgroundColor: "#f5f5f5" }}>
              <th
                style={{
                  padding: "12px",
                  textAlign: "left",
                  borderBottom: "1px solid #e0e0e0",
                  fontWeight: "600",
                }}
              >
                Product
              </th>
              <th
                style={{
                  padding: "12px",
                  textAlign: "left",
                  borderBottom: "1px solid #e0e0e0",
                  fontWeight: "600",
                }}
              >
                Cycle
              </th>
              {locations.map((location) => (
                <th
                  key={location}
                  style={{
                    padding: "12px",
                    textAlign: "left",
                    borderBottom: "1px solid #e0e0e0",
                    fontWeight: "600",
                  }}
                >
                  {location}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {selectedProducts.map((product) => (
              <tr key={product} style={{ borderBottom: "1px solid #e0e0e0" }}>
                <td style={{ padding: "12px" }}>{product}</td>
                <td style={{ padding: "12px" }}>{selectedCycle}</td>
                {locations.map((location) => (
                  <td
                    key={`${product}-${location}`}
                    style={{ padding: "12px" }}
                  >
                    {renderDateWithChange(product, location)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  const renderCycleTabs = () => {
    if (!apiData) return null;

    return (
      <div
        style={{
          display: "flex",
          marginBottom: "20px",
          overflowX: "auto",
          paddingBottom: "8px",
        }}
      >
        {Object.keys(apiData.currentData.data)
          .sort()
          .map((cycle) => (
            <button
              key={cycle}
              onClick={() => setSelectedCycle(cycle)}
              style={{
                padding: "8px 16px",
                marginRight: "8px",
                backgroundColor:
                  selectedCycle === cycle ? "#1890ff" : "#f5f5f5",
                color: selectedCycle === cycle ? "white" : "#333",
                border: "1px solid #d9d9d9",
                borderRadius: "4px",
                cursor: "pointer",
                fontWeight: "600",
                fontSize: "14px",
                transition: "all 0.3s",
                whiteSpace: "nowrap",
                flexShrink: 0,
              }}
            >
              {cycle}
            </button>
          ))}
      </div>
    );
  };

  if (loading && !apiData) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <div
      style={{
        padding: "20px",
        fontFamily: "Segoe UI, sans-serif",
        maxWidth: "1200px",
        margin: "0 auto",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "20px",
        }}
      >
        <button
          onClick={handleRefresh}
          style={{
            backgroundColor: "#1890ff",
            color: "white",
            border: "none",
            padding: "8px 16px",
            borderRadius: "4px",
            cursor: "pointer",
            fontWeight: "600",
            fontSize: "14px",
          }}
        >
          Refresh Data
        </button>
      </div>

      {apiData && (
        <>
          {renderCycleTabs()}

          <div style={{ display: "flex" }}>
            <div
              style={{
                width: "25%",
                paddingRight: "20px",
              }}
            >
              <h2
                style={{
                  fontSize: "18px",
                  fontWeight: "600",
                  marginBottom: "12px",
                }}
              >
                Products
              </h2>
              {getProductsForCycle().map((product) => (
                <div key={product} style={{ marginBottom: "8px" }}>
                  <label style={{ display: "flex", alignItems: "center" }}>
                    <input
                      type="checkbox"
                      checked={selectedProducts.includes(product)}
                      onChange={() => handleProductToggle(product)}
                      style={{
                        marginRight: "8px",
                        width: "16px",
                        height: "16px",
                      }}
                    />
                    {product}
                  </label>
                </div>
              ))}
            </div>

            <div style={{ width: "75%" }}>
              {selectedProducts.length > 0 ? (
                renderTable()
              ) : (
                <div
                  style={{
                    overflowX: "auto",
                    marginTop: "20px",
                    border: "1px solid #e0e0e0",
                    borderRadius: "6px",
                    padding: "12px",
                    minWidth: "100%",
                    boxSizing: "border-box",
                  }}
                >
                  Select products to display the delivery table
                </div>
              )}
            </div>
          </div>

          <div
            style={{
              marginTop: "20px",
              fontSize: "14px",
              color: "#666",
              textAlign: "right",
            }}
          >
            Report Date: {apiData.currentReportDate || "N/A"}
          </div>
        </>
      )}
    </div>
  );
};

export default FungibleDeliveriesTable;
