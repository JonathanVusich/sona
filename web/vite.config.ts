import { defineConfig } from "vite";
import solid from "vite-plugin-solid";

export default defineConfig({
  plugins: [solid()],
  // Sona's API is proxied onto the same origin, which the SameSite refresh cookie needs.
  server: { port: 5173, proxy: { "/api": "http://localhost:8080" } },
});
