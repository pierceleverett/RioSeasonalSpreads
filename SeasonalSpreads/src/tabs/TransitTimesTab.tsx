import React, { useState } from "react";
import ExplorerTransitChart from "../Utilities/ExplorerTransitChart";
import ColonialTransitChart from "../Utilities/ColonialTransitChart";

const TransitTimesTab: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"explorer" | "colonial">(
    "explorer"
  );

  return (
    <div style={{ padding: "20px", fontFamily: "Segoe UI" }}>
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          marginBottom: "20px",
          borderBottom: "1px solid #ddd",
        }}
      >
        <button
          style={{
            padding: "10px 20px",
            margin: "0 5px",
            backgroundColor: activeTab === "explorer" ? "#1890ff" : "#f0f0f0",
            color: activeTab === "explorer" ? "white" : "#333",
            border: "none",
            borderRadius: "4px 4px 0 0",
            cursor: "pointer",
            fontSize: "16px",
            fontWeight: "bold",
            transition: "all 0.3s ease",
          }}
          onClick={() => setActiveTab("explorer")}
        >
          Explorer Transit
        </button>
        <button
          style={{
            padding: "10px 20px",
            margin: "0 5px",
            backgroundColor: activeTab === "colonial" ? "#1890ff" : "#f0f0f0",
            color: activeTab === "colonial" ? "white" : "#333",
            border: "none",
            borderRadius: "4px 4px 0 0",
            cursor: "pointer",
            fontSize: "16px",
            fontWeight: "bold",
            transition: "all 0.3s ease",
          }}
          onClick={() => setActiveTab("colonial")}
        >
          Colonial Pipeline
        </button>
      </div>

      {activeTab === "explorer" && (
        <div>
          <h2 style={{ textAlign: "center", marginBottom: "20px" }}>
            Explorer Transit Times
          </h2>
          <ExplorerTransitChart />
        </div>
      )}

      {activeTab === "colonial" && (
        <div>
          <h2 style={{ textAlign: "center", marginBottom: "20px" }}>
            Colonial Pipeline Transit Times
          </h2>
          <ColonialTransitChart routeName="Colonial Pipeline" />
        </div>
      )}
    </div>
  );
};

export default TransitTimesTab;
