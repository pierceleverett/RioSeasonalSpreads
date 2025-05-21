import React from "react";
import type { DropdownProps } from "../types";

const Dropdown: React.FC<DropdownProps> = ({
  label,
  options,
  selected,
  onSelect,
}) => {
  return (
    <div className="dropdown">
      <label>{label}:</label>
      <select value={selected} onChange={(e) => onSelect(e.target.value)}>
        <option value="">Select {label}</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  );
};

export default Dropdown;
