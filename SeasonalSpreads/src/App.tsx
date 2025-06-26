import React, { useState } from "react";
import SpreadsTab from "./tabs/SpreadsTab.tsx";
import GulfCoastDiffsTab from "./tabs/GulfCoastDiffsTab.tsx";
import ChicagoDiffsTab from "./tabs/ChicagoDiffsTab.tsx";
import TransitTimesTab from "./tabs/TransitTimesTab.tsx";
import MagellanTab from "./tabs/MagellanTab.tsx";

type TabOption =
  | "Spreads"
  | "Gulf Coast Diffs"
  | "Chicago Diffs"
  | "Transit Times"
  | "Magellan";

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabOption>("Spreads");

  const renderTabContent = () => {
    switch (activeTab) {
      case "Spreads":
        return <SpreadsTab />;
      case "Gulf Coast Diffs":
        return <GulfCoastDiffsTab />;
      case "Chicago Diffs":
        return <ChicagoDiffsTab />;
      case "Transit Times":
        return <TransitTimesTab />;
      case "Magellan":
        return <MagellanTab />;
      default:
        return null;
    }
  };

  const tabs: TabOption[] = [
    "Spreads",
    "Gulf Coast Diffs",
    "Chicago Diffs",
    "Transit Times",
    "Magellan",
  ];

  return (
    <div style={{ padding: "20px", fontFamily: "Segoe UI" }}>
      <h1 style={{ textAlign: "center", marginBottom: "20px" }}>
        Energy Futures Dashboard
      </h1>
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          gap: "10px",
          flexWrap: "wrap",
          marginBottom: "30px",
        }}
      >
        {tabs.map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            style={{
              padding: "10px 20px",
              borderRadius: "4px",
              border: "none",
              backgroundColor: activeTab === tab ? "#3498db" : "#ecf0f1",
              color: activeTab === tab ? "white" : "#2c3e50",
              fontWeight: "bold",
              cursor: "pointer",
            }}
          >
            {tab}
          </button>
        ))}
      </div>
      {renderTabContent()}
    </div>
  );
};

export default App;
