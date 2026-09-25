const express = require("express");
const jwt = require("jsonwebtoken");

const app = express();
app.use(express.json());

// Shared secret. Spring Boot validates tokens signed with this exact value.
// In production this would come from a secrets manager.
const SECRET = process.env.JWT_SECRET || "leapvelocity-shared-secret-key-32-bytes-minimum";
const ISSUER = process.env.JWT_ISSUER || "leapvelocity-auth";

// Stub users, not a real user store. This is enough to demonstrate:
// valid token -> protected data, missing/wrong token -> rejected.
const USERS = {
  john: { password: "password", roles: ["TRADER"] },
  admin: { password: "admin123", roles: ["ADMIN"] },
};

app.post("/login", (request, response) => {
  const { username, password } = request.body || {};
  const user = USERS[username];

  if (!user || user.password !== password) {
    return response.status(401).json({ error: "invalid username or password" });
  }

  const token = jwt.sign(
    { sub: username, roles: user.roles },
    SECRET,
    {
      algorithm: "HS256",
      expiresIn: process.env.JWT_EXPIRES_IN || "1h",
      issuer: ISSUER,
    }
  );

  return response.json({ token });
});

app.get("/health", (request, response) => response.json({ status: "up" }));

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => {
  console.log(`leapvelocity-auth-stub listening on http://localhost:${PORT}`);
  console.log(
    `Try: curl -X POST http://localhost:${PORT}/login -H "Content-Type: application/json" -d '{"username":"john","password":"password"}'`
  );
});
