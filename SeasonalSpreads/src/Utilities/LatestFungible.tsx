import React, { useState, useEffect } from "react";

interface FungibleData {
  report_date: string;
  data: {
    [cycle: string]: {
      [product: string]: {
        [location: string]: string;
      };
    };
  };
}

const FungibleDeliveriesTable: React.FC = () => {
  const [data, setData] = useState<FungibleData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCycle, setSelectedCycle] = useState<string>("");
  const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
  const [locations, setLocations] = useState<string[]>([]);

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
      const jsonData: FungibleData = await response.json();
      setData(jsonData);

      // Extract all unique locations from the data
      const allLocations = new Set<string>();
      Object.values(jsonData.data).forEach((cycleData) => {
        Object.values(cycleData).forEach((productData) => {
          Object.keys(productData).forEach((location) => {
            allLocations.add(location);
          });
        });
      });
      setLocations(Array.from(allLocations).sort());

      // Auto-select the first cycle if available
      const cycles = Object.keys(jsonData.data);
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
    setSelectedProducts([]);
  };

  const handleProductToggle = (product: string) => {
    setSelectedProducts((prev) =>
      prev.includes(product)
        ? prev.filter((p) => p !== product)
        : [...prev, product]
    );
  };

  const getProductsForCycle = (): string[] => {
    if (!data || !selectedCycle) return [];
    const cycleData = data.data[selectedCycle];
    return cycleData ? Object.keys(cycleData).sort() : [];
  };

  const renderTable = () => {
    if (!data || !selectedCycle || selectedProducts.length === 0) return null;

    const cycleData = data.data[selectedCycle];

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
                    {cycleData[product]?.[location] || ""}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  if (loading && !data) return <div>Loading...</div>;
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

      {data && (
        <>
          <div style={{ marginBottom: "20px" }}>
            <label
              style={{
                display: "block",
                marginBottom: "8px",
                fontWeight: "600",
              }}
            >
              Select Cycle:
              <select
                value={selectedCycle}
                onChange={(e) => {
                  setSelectedCycle(e.target.value);
                  setSelectedProducts([]);
                }}
                style={{
                  marginLeft: "8px",
                  padding: "8px 12px",
                  borderRadius: "4px",
                  border: "1px solid #d9d9d9",
                }}
              >
                {Object.keys(data.data)
                  .sort()
                  .map((cycle) => (
                    <option key={cycle} value={cycle}>
                      {cycle}
                    </option>
                  ))}
              </select>
            </label>
          </div>

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

            <div style={{ width: "95%" }}>
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
            Report Date: {data.report_date}
          </div>
        </>
      )}
    </div>
  );
};

export default FungibleDeliveriesTable;
