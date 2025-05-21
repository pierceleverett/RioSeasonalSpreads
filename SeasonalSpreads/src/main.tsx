import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App"; // Ensure this matches your file name exactly

// Find the root element
const rootElement = document.getElementById("root");
if (!rootElement) throw new Error("Failed to find the root element");

// Create a root and render the app
const root = createRoot(rootElement);
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
