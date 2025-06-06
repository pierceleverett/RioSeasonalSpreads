import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import "./index.css";
import { ClerkProvider } from "@clerk/clerk-react";

// Import your Publishable Key
const PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY;

if (!PUBLISHABLE_KEY) {
  throw new Error("Missing Publishable Key");
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ClerkProvider
      publishableKey={PUBLISHABLE_KEY}
      appearance={{
        elements: {
          formButtonPrimary: {
            backgroundColor: "#3498db",
            "&:hover": {
              backgroundColor: "#2980b9",
            },
            fontSize: "16px",
            fontWeight: "600",
            padding: "12px 24px",
            borderRadius: "4px",
            boxShadow: "0 2px 10px rgba(52, 152, 219, 0.3)",
          },
        },
      }}
    >
      <App />
    </ClerkProvider>
  </React.StrictMode>
);
