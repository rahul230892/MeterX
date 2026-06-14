import { Router } from "express";
import { Meter } from "../models/Meter.js";
import { Reading } from "../models/Reading.js";
import { readingSchema } from "../validation.js";

export function createReadingRouter() {
  const router = Router();

  router.get("/", async (request, response, next) => {
    try {
      const query = { ownerId: request.user.id };
      if (request.query.meterClientId) {
        query.meterClientId = String(request.query.meterClientId);
      }
      const readings = await Reading.find(query)
        .sort({ readingDate: -1, clientCreatedAt: -1 })
        .lean();
      return response.json({ readings: readings.map(toReadingResponse) });
    } catch (error) {
      return next(error);
    }
  });

  router.put("/:clientId", async (request, response, next) => {
    try {
      const reading = readingSchema.parse({
        ...request.body,
        clientId: request.params.clientId,
      });
      const meterExists = await Meter.exists({
        ownerId: request.user.id,
        clientId: reading.meterClientId,
      });
      if (!meterExists) {
        return response.status(400).json({ error: "Referenced meter does not exist." });
      }
      const saved = await Reading.findOneAndUpdate(
        { ownerId: request.user.id, clientId: reading.clientId },
        toReadingDocument(request.user.id, reading),
        { upsert: true, new: true, runValidators: true },
      ).lean();
      return response.json({ reading: toReadingResponse(saved) });
    } catch (error) {
      return next(error);
    }
  });

  router.delete("/:clientId", async (request, response, next) => {
    try {
      const reading = await Reading.findOneAndDelete({
        ownerId: request.user.id,
        clientId: String(request.params.clientId),
      });
      if (!reading) {
        return response.status(404).json({ error: "Reading not found." });
      }
      return response.status(204).send();
    } catch (error) {
      return next(error);
    }
  });

  return router;
}

export function toReadingDocument(ownerId, reading) {
  return {
    ownerId,
    clientId: reading.clientId,
    meterClientId: reading.meterClientId,
    value: reading.value,
    readingDate: reading.readingDate,
    isBilled: reading.isBilled,
    clientCreatedAt: reading.createdAt,
  };
}

export function toReadingResponse(reading) {
  return {
    clientId: reading.clientId,
    meterClientId: reading.meterClientId,
    value: reading.value,
    readingDate: reading.readingDate,
    isBilled: reading.isBilled,
    createdAt: reading.clientCreatedAt,
    updatedAt: reading.updatedAt,
  };
}
