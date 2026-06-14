import { createApp } from "./app.js";
import { loadConfig } from "./config.js";
import { connectDatabase, disconnectDatabase } from "./database.js";

const config = loadConfig();

let server;

try {
  await connectDatabase(config.MONGODB_URI);
  server = createApp(config).listen(config.PORT, () => {
    console.log(`MeterX backend listening on port ${config.PORT}`);
  });
} catch (error) {
  console.error("Failed to start MeterX backend:", error);
  process.exit(1);
}

async function shutdown(signal) {
  console.log(`${signal} received, shutting down`);
  if (!server) {
    await disconnectDatabase();
    process.exit(0);
  }
  server.close(async () => {
    await disconnectDatabase();
    process.exit(0);
  });
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
