import { defineConfig } from "vite";
import solid from "vite-plugin-solid";

export default defineConfig({
  plugins: [solid()],
  // Matches tsconfig.json; index.tsx awaits at the top level.
  build: { target: "es2022" },
  // Sona's API is proxied onto the same origin, which the SameSite refresh cookie needs.
  server: { port: 5173, proxy: { "/api": { target: "http://localhost:8080", ws: true } } },
});
