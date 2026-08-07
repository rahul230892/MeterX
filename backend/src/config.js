import "dotenv/config";
import { z } from "zod";

const schema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().int().min(1).max(65535).default(3000),
  MONGODB_URI: z.string().min(1),
  JWT_SECRET: z.string().min(32),
  JWT_EXPIRES_IN: z.string().default("30d"),
  CORS_ORIGIN: z.string().default("*"),
  APP_LATEST_VERSION_CODE: z.coerce.number().int().positive().default(5),
  APP_LATEST_VERSION_NAME: z.string().default("1.4"),
  APP_MIN_SUPPORTED_VERSION_CODE: z.coerce.number().int().positive().default(5),
  APP_APK_URL: z.string().url().default(
    "https://meterx-backend.onrender.com/downloads/meterx-latest.apk",
  ),
  APP_RELEASE_NOTES: z.string().default(
    "Update MeterX to get the latest fixes and features.",
  ),
});

export function loadConfig(environment = process.env) {
  return schema.parse(environment);
}
