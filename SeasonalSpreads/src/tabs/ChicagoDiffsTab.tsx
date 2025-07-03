import React, { useState, useEffect } from "react";
import CsvSpreadChart from "../Utilities/CsvSpreadChart";

const ChicagoDiffsTab: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"91Chi" | "ChiCBOB">("91Chi");

      useEffect(() => {
        const updateSpreads = async () => {
          try {
            const response = await fetch(
              "https://rioseasonalspreads-production.up.railway.app/updateGC",
              {
                method: "POST",
                headers: {
                  "Content-Type": "application/json",
                },
              }
            );
    
            if (!response.ok) {
              throw new Error("Failed to update spread data");
            }
            console.log("Spread data updated successfully");
          } catch (error) {
            console.error("Error updating spread data:", error);
          }
        };
    
        // Call updateSpreads when component mounts and when commodity changes
        updateSpreads();
      }, []); // Add dependencies as needed

  return (
    <div>
      <div className="flex-center" style={{ marginBottom: "30px" }}>
        <button
          className={`tab-button ${activeTab === "91Chi" ? "active" : ""}`}
          onClick={() => setActiveTab("91Chi")}
        >
          91 Chi vs USGC 93 + Transport
        </button>
        <button
          className={`tab-button ${activeTab === "ChiCBOB" ? "active" : ""}`}
          onClick={() => setActiveTab("ChiCBOB")}
        >
          BCX RBOB - BCX CBOB
        </button>
      </div>

      {activeTab === "91Chi" && <CsvSpreadChart type="91Chi" />}
      {activeTab === "ChiCBOB" && <CsvSpreadChart type="ChiCBOB" />}
    </div>
  );
};

export default ChicagoDiffsTab;
