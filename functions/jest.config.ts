import type { Config } from "jest";
const config: Config = {
  preset: "ts-jest",
  testEnvironment: "node",
  testMatch: ["<rootDir>/src/**/*.test.ts"],
  moduleFileExtensions: ["ts", "js", "json"],
};
export default config;
