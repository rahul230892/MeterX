import path from "node:path";
import { fileURLToPath } from "node:url";
import cors from "cors";
import express from "express";
import rateLimit from "express-rate-limit";
import helmet from "helmet";
import { ZodError } from "zod";
import { authenticate } from "./auth.js";
import { createAuthRouter } from "./routes/authRoutes.js";
import { createMeterRouter } from "./routes/meterRoutes.js";
import { createReadingRouter } from "./routes/readingRoutes.js";
import { createSyncRouter } from "./routes/syncRoutes.js";

const publicDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../public",
);

export function createApp(config) {
  const app = express();

  app.disable("x-powered-by");
  app.use(helmet());
  app.use(cors({
    origin: config.CORS_ORIGIN === "*" ? true : config.CORS_ORIGIN.split(","),
  }));
  app.use(express.json({ limit: "5mb" }));
  app.use(rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 300,
    standardHeaders: "draft-8",
    legacyHeaders: false,
  }));

  app.get("/health", (_request, response) => {
    response.json({ status: "ok", service: "meterx-backend" });
  });
  app.use("/downloads", express.static(path.join(publicDirectory, "downloads")));
  app.get("/api/app/latest", (_request, response) => {
    response.json({
      latestVersionCode: config.APP_LATEST_VERSION_CODE,
      latestVersionName: config.APP_LATEST_VERSION_NAME,
      minSupportedVersionCode: config.APP_MIN_SUPPORTED_VERSION_CODE,
      apkUrl: config.APP_APK_URL,
      releaseNotes: config.APP_RELEASE_NOTES,
      forceUpdate: true,
    });
  });
  app.use("/api/auth", createAuthRouter(config));

  const requireAuthentication = authenticate(config);
  app.use("/api/meters", requireAuthentication, createMeterRouter());
  app.use("/api/readings", requireAuthentication, createReadingRouter());
  app.use("/api/sync", requireAuthentication, createSyncRouter());

  app.use((_request, response) => {
    response.status(404).json({ error: "Route not found." });
  });

  app.use((error, _request, response, _next) => {
    if (error instanceof ZodError) {
      return response.status(400).json({
        error: "Validation failed.",
        details: error.issues,
      });
    }
    if (error?.code === 11000) {
      return response.status(409).json({ error: "Record already exists." });
    }
    console.error(error);
    return response.status(500).json({ error: "Internal server error." });
  });

  return app;
}
