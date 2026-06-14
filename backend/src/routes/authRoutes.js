import bcrypt from "bcryptjs";
import { Router } from "express";
import { createToken } from "../auth.js";
import { User } from "../models/User.js";
import { credentialsSchema } from "../validation.js";

export function createAuthRouter(config) {
  const router = Router();

  router.post("/register", async (request, response, next) => {
    try {
      const credentials = credentialsSchema.parse(request.body);
      const existing = await User.exists({ username: credentials.username });
      if (existing) {
        return response.status(409).json({ error: "Username is already registered." });
      }

      const user = await User.create({
        username: credentials.username,
        passwordHash: await bcrypt.hash(credentials.password, 12),
      });
      return response.status(201).json({
        token: createToken(user, config),
        user: { id: user.id, username: user.username },
      });
    } catch (error) {
      return next(error);
    }
  });

  router.post("/login", async (request, response, next) => {
    try {
      const credentials = credentialsSchema.parse(request.body);
      const user = await User.findOne({ username: credentials.username })
        .select("+passwordHash");
      if (!user || !(await bcrypt.compare(credentials.password, user.passwordHash))) {
        return response.status(401).json({ error: "Invalid username or password." });
      }
      return response.json({
        token: createToken(user, config),
        user: { id: user.id, username: user.username },
      });
    } catch (error) {
      return next(error);
    }
  });

  return router;
}
