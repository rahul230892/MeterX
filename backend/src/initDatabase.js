import { loadConfig } from "./config.js";
import { connectDatabase, disconnectDatabase } from "./database.js";
import { Meter } from "./models/Meter.js";
import { Reading } from "./models/Reading.js";
import { User } from "./models/User.js";

const config = loadConfig();

try {
  await connectDatabase(config.MONGODB_URI);
  await Promise.all([User.init(), Meter.init(), Reading.init()]);
  console.log("MeterX database collections and indexes are ready.");
} finally {
  await disconnectDatabase();
}
