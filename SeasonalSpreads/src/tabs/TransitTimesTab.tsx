import React, { useState } from "react";
import ExplorerTransitChart from "../Utilities/ExplorerTransitChart";
import ColonialTransitChart from "../Utilities/ColonialTransitChart";

const TransitTimesTab: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"explorer" | "colonial">(
    "explorer"
  );

  return (
    <div
      style={{
        padding: "20px",
        fontFamily: "Segoe UI",
        maxWidth: "1200px",
        margin: "0 auto",
      }}
    >
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
            minWidth: "180px",
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
            minWidth: "180px",
          }}
          onClick={() => setActiveTab("colonial")}
        >
          Colonial Pipeline
        </button>
      </div>

      <div
        style={{
          backgroundColor: "#fff",
          borderRadius: "8px",
          boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
          padding: "20px",
        }}
      >
        {activeTab === "explorer" && (
          <div>
            <h2
              style={{
                textAlign: "center",
                marginBottom: "20px",
                color: "#333",
              }}
            >
              Explorer Transit Times
            </h2>
            <ExplorerTransitChart />
          </div>
        )}

        {activeTab === "colonial" && (
          <div>
            <h2
              style={{
                textAlign: "center",
                marginBottom: "20px",
                color: "#333",
              }}
            >
              Colonial Pipeline Transit Times
            </h2>
            <ColonialTransitChart />
          </div>
        )}
      </div>
    </div>
  );
};

export default TransitTimesTab;
