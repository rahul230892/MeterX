import jwt from "jsonwebtoken";
import { User } from "./models/User.js";

export function createToken(user, config) {
  return jwt.sign(
    { sub: user.id, username: user.username },
    config.JWT_SECRET,
    { expiresIn: config.JWT_EXPIRES_IN },
  );
}

export function authenticate(config) {
  return async function authenticationMiddleware(request, response, next) {
    const authorization = request.get("authorization");
    const token = authorization?.startsWith("Bearer ")
      ? authorization.slice(7)
      : null;

    if (!token) {
      return response.status(401).json({ error: "Authentication required." });
    }

    try {
      const payload = jwt.verify(token, config.JWT_SECRET);
      const user = await User.findById(payload.sub);
      if (!user) {
        return response.status(401).json({ error: "User no longer exists." });
      }
      request.user = user;
      return next();
    } catch {
      return response.status(401).json({ error: "Invalid or expired token." });
    }
  };
}
