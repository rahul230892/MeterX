import mongoose from "mongoose";
import { Router } from "express";
import { Meter } from "../models/Meter.js";
import { Reading } from "../models/Reading.js";
import { snapshotSchema } from "../validation.js";
import { toMeterDocument, toMeterResponse } from "./meterRoutes.js";
import { toReadingDocument, toReadingResponse } from "./readingRoutes.js";

export function createSyncRouter() {
  const router = Router();

  router.get("/", async (request, response, next) => {
    try {
      const [meters, readings] = await Promise.all([
        Meter.find({ ownerId: request.user.id })
          .sort({ clientCreatedAt: -1 })
          .lean(),
        Reading.find({ ownerId: request.user.id })
          .sort({ readingDate: -1, clientCreatedAt: -1 })
          .lean(),
      ]);
      return response.json({
        version: 1,
        syncedAt: Date.now(),
        meters: meters.map(toMeterResponse),
        readings: readings.map(toReadingResponse),
      });
    } catch (error) {
      return next(error);
    }
  });

  router.put("/", async (request, response, next) => {
    const session = await mongoose.startSession();
    try {
      const snapshot = snapshotSchema.parse(request.body);
      await session.withTransaction(async () => {
        await Reading.deleteMany({ ownerId: request.user.id }).session(session);
        await Meter.deleteMany({ ownerId: request.user.id }).session(session);

        if (snapshot.meters.length) {
          await Meter.insertMany(
            snapshot.meters.map((meter) => toMeterDocument(request.user.id, meter)),
            { session },
          );
        }
        if (snapshot.readings.length) {
          await Reading.insertMany(
            snapshot.readings.map((reading) =>
              toReadingDocument(request.user.id, reading),
            ),
            { session },
          );
        }
      });
      return response.json({
        syncedAt: Date.now(),
        meterCount: snapshot.meters.length,
        readingCount: snapshot.readings.length,
      });
    } catch (error) {
      return next(error);
    } finally {
      await session.endSession();
    }
  });

  return router;
}
