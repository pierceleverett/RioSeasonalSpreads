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
      <div className="overflow-x-auto mt-4">
        <table className="min-w-full border">
          <thead>
            <tr>
              <th className="border px-4 py-2">Product</th>
              <th className="border px-4 py-2">Cycle</th>
              {locations.map((location) => (
                <th key={location} className="border px-4 py-2">
                  {location}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {selectedProducts.map((product) => (
              <tr key={product}>
                <td className="border px-4 py-2">{product}</td>
                <td className="border px-4 py-2">{selectedCycle}</td>
                {locations.map((location) => (
                  <td
                    key={`${product}-${location}`}
                    className="border px-4 py-2"
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
    <div className="container mx-auto p-4">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-2xl font-bold">Fungible Deliveries</h1>
        <button
          onClick={handleRefresh}
          className="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"
        >
          Refresh Data
        </button>
      </div>

      {data && (
        <>
          <div className="mb-4">
            <label className="block mb-2">
              Select Cycle:
              <select
                value={selectedCycle}
                onChange={(e) => {
                  setSelectedCycle(e.target.value);
                  setSelectedProducts([]);
                }}
                className="ml-2 p-2 border rounded"
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

          <div className="flex">
            <div className="w-1/4 pr-4">
              <h2 className="font-bold mb-2">Products</h2>
              {getProductsForCycle().map((product) => (
                <div key={product} className="mb-2">
                  <label className="flex items-center">
                    <input
                      type="checkbox"
                      checked={selectedProducts.includes(product)}
                      onChange={() => handleProductToggle(product)}
                      className="mr-2"
                    />
                    {product}
                  </label>
                </div>
              ))}
            </div>

            <div className="w-3/4">
              {selectedProducts.length > 0 ? (
                renderTable()
              ) : (
                <div className="text-gray-500">
                  Select products to display the delivery table
                </div>
              )}
            </div>
          </div>

          <div className="mt-4 text-sm text-gray-500">
            Report Date: {data.report_date}
          </div>
        </>
      )}
    </div>
  );
};

export default FungibleDeliveriesTable;
