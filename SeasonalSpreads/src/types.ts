export type MonthCode =
  | "F"
  | "G"
  | "H"
  | "J"
  | "K"
  | "M"
  | "N"
  | "Q"
  | "U"
  | "V"
  | "X"
  | "Z";
export type ProductType = "RBOB" | "HO";
export type SpreadCode = `${MonthCode}${MonthCode}`; // e.g. "FX", "MN"
