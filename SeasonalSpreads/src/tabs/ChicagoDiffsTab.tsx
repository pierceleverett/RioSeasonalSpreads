import React, { useState } from "react";
import CsvSpreadChart from "../Utilities/CsvSpreadChart";

const ChicagoDiffsTab: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"91Chi" | "ChiCBOB">("91Chi");

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
