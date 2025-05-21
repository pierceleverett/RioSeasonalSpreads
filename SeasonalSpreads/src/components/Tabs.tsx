import React from "react";
import type { TabProps } from "../types";

const Tabs: React.FC<TabProps> = ({ activeTab, onTabChange }) => {
  return (
    <div className="tabs">
      {(["RBOB", "HO"] as const).map((tab) => (
        <button
          key={tab}
          className={`tab ${activeTab === tab ? "active" : ""}`}
          onClick={() => onTabChange(tab)}
        >
          {tab}
        </button>
      ))}
    </div>
  );
};

export default Tabs;
