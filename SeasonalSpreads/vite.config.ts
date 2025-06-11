import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    // Add your Railway URL and any other allowed hosts
    allowedHosts: [
      "riodashboard.up.railway.app",
      "riodashboard.com", // If using custom domain
    ],
  },
  preview: {
    port: 5173,
    host: true,
    // Also add allowedHosts for preview mode
    allowedHosts: ["riodashboard.up.railway.app", "riodashboard.com"],
  },
});
