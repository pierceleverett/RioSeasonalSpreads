import React, { useState, useEffect } from "react";

interface DateData {
  [product: string]: {
    [location: string]: string;
  };
}

interface ApiResponse {
  currentData: {
    reportDate: string;
    data: DateData;
  };
  oldData: {
    reportDate: string;
    data: DateData;
  };
}

const DateInfoTable: React.FC = () => {
  const [apiData, setApiData] = useState<ApiResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
  const [locations, setLocations] = useState<string[]>([]);

  useEffect(() => {
    if (!apiData) return;

    const currentData = apiData.currentData.data;
    const autoSelectedProducts = Object.keys(currentData).filter(
      (product) =>
        product.startsWith("A") || product.startsWith("D") || product === "62"
    );

    setSelectedProducts(autoSelectedProducts);
  }, [apiData]);

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
      const currentData = jsonData.currentData.data;

      Object.values(currentData).forEach((productData) => {
        if (productData && typeof productData === "object") {
          Object.keys(productData).forEach((location) => {
            allLocations.add(location);
          });
        }
      });
      setLocations(Array.from(allLocations).sort());
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

  const getProducts = (): string[] => {
    if (!apiData) return [];
    const currentData = apiData.currentData.data;
    if (!currentData || typeof currentData !== "object") return [];
    return Object.keys(currentData).sort();
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
    if (!apiData) return null;

    const currentData = apiData.currentData.data;
    const oldData = apiData.oldData.data;

    if (!currentData || typeof currentData !== "object") return null;

    const productData = currentData[product];
    if (!productData || typeof productData !== "object") return null;

    const currentDate = productData[location];
    if (!currentDate) return null;

    let diffDays: number | null = null;

    if (oldData && typeof oldData === "object") {
      const oldProductData = oldData[product];
      if (oldProductData && typeof oldProductData === "object") {
        const oldDate = oldProductData[location];
        diffDays = calculateDateDifference(currentDate, oldDate);
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
      {/* Header with refresh button */}
      <div
        style={{
          display: "flex",
          justifyContent: "flex-start",
          marginBottom: "15px",
          width: "100%",
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
          }}
        >
          Refresh Data
        </button>
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
            {getProducts().map((product) => (
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
              Select products to display the date info table
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
          Current Report Date: {apiData?.currentData.reportDate || "N/A"}
        </div>
        <div>Previous Report Date: {apiData?.oldData.reportDate || "N/A"}</div>
      </div>
    </div>
  );
};

export default DateInfoTable;
