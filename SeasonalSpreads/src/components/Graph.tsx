import React from "react";
import type { ProductType, ContractType } from "../types";

interface GraphProps {
  product: ProductType;
  contracts: Record<ContractType, string>;
}

const Graph: React.FC<GraphProps> = ({ product, contracts }) => {
  return (
    <div className="graph-container">
      <h2>{product} Graph</h2>
      <div className="graph-placeholder">
        <img
          src="https://via.placeholder.com/600x300?text=Graph+Will+Appear+Here"
          alt="Graph placeholder"
        />
        <p>
          Selected Contracts:{" "}
          {Object.values(contracts).filter(Boolean).join(", ") || "None"}
        </p>
      </div>
    </div>
  );
};

export default Graph;
