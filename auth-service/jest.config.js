module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  testMatch: ["**/*.spec.ts", "**/test/**/*.e2e-spec.ts"],
  collectCoverageFrom: ["src/**/*.ts", "!src/**/*.module.ts"]
};