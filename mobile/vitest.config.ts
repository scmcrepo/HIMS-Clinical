import { defineConfig } from "vitest/config";

// Only __tests__ runs here, and only against src/core — the layer with no
// React Native imports. Screen tests need a simulator and live in EAS CI.
export default defineConfig({
  test: {
    include: ["__tests__/**/*.test.ts"],
    environment: "node",
  },
});
