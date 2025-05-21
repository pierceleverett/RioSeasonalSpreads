import React, { useState } from "react";
import "./styles/main.css";

type ProductType = "RBOB" | "HO";
type MonthCode = "MN" | "NQ" | "MV";

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ProductType>("RBOB");
  const [selectedMonth, setSelectedMonth] = useState<MonthCode | "">("");

  const monthOptions: MonthCode[] = ["MN", "NQ", "MV"];

  const handleTabChange = (tab: ProductType) => {
    setActiveTab(tab);
    setSelectedMonth(""); // Reset month when changing tabs
  };

  return (
    <div className="app">
      <h1>Energy Futures Dashboard</h1>

      <div className="tabs">
        {(["RBOB", "HO"] as ProductType[]).map((tab) => (
          <button
            key={tab}
            className={`tab ${activeTab === tab ? "active" : ""}`}
            onClick={() => handleTabChange(tab)}
          >
            {tab}
          </button>
        ))}
      </div>

      <div className="month-selector">
        <label>Select month: </label>
        <select
          value={selectedMonth}
          onChange={(e) => setSelectedMonth(e.target.value as MonthCode | "")}
        >
          <option value="">-- Select --</option>
          {monthOptions.map((month) => (
            <option key={month} value={month}>
              {month}
            </option>
          ))}
        </select>
      </div>

      <div className="graph-container">
        <h2>{activeTab} Spread Analysis</h2>
        {selectedMonth ? (
          <div className="graph-content">
            <img
              src={`/images/spreads/${activeTab}-${selectedMonth}.png`}
              alt={`${activeTab} ${selectedMonth} Spread Chart`}
              className="spread-chart"
            />
          </div>
        ) : (
          <p className="prompt">Please select a month to view data</p>
        )}
      </div>
    </div>
  );
};

export default App;
