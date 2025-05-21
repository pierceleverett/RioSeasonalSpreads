export type ContractType = "MN" | "NQ" | "MV";
export type ProductType = "RBOB" | "HO";

export interface DropdownProps {
  label: string;
  options: string[];
  selected: string;
  onSelect: (value: string) => void;
}

export interface TabProps {
  activeTab: ProductType;
  onTabChange: (tab: ProductType) => void;
}
