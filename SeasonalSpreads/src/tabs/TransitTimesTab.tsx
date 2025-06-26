import React from "react";
import ExplorerTransitChart from "../Utilities/ExplorerTransitChart";

const TransitTimesTab: React.FC = () => {
  return (
    <div style={{ padding: "20px", fontFamily: "Segoe UI" }}>
      <h2 style={{ textAlign: "center", marginBottom: "20px" }}>
        Explorer Transit Times
      </h2>
      <ExplorerTransitChart />

      {/* Future transit time components can be added here */}
    </div>
  );
};

export default TransitTimesTab;
