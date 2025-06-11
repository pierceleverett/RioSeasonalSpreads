import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // Needed for Docker container
    port: 5173, // Explicit port declaration
    strictPort: true, // Don't try other ports if 5173 is busy
  },
  preview: {
    port: 5173, // Preview server port
    host: true,
  },
});
