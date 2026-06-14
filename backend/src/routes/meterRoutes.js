import { Router } from "express";
import { Meter } from "../models/Meter.js";
import { Reading } from "../models/Reading.js";
import { meterSchema } from "../validation.js";

export function createMeterRouter() {
  const router = Router();

  router.get("/", async (request, response, next) => {
    try {
      const meters = await Meter.find({ ownerId: request.user.id })
        .sort({ clientCreatedAt: -1 })
        .lean();
      return response.json({ meters: meters.map(toMeterResponse) });
    } catch (error) {
      return next(error);
    }
  });

  router.put("/:clientId", async (request, response, next) => {
    try {
      const meter = meterSchema.parse({
        ...request.body,
        clientId: request.params.clientId,
      });
      const saved = await Meter.findOneAndUpdate(
        { ownerId: request.user.id, clientId: meter.clientId },
        toMeterDocument(request.user.id, meter),
        { upsert: true, new: true, runValidators: true },
      ).lean();
      return response.json({ meter: toMeterResponse(saved) });
    } catch (error) {
      return next(error);
    }
  });

  router.delete("/:clientId", async (request, response, next) => {
    try {
      const query = {
        ownerId: request.user.id,
        clientId: String(request.params.clientId),
      };
      const meter = await Meter.findOneAndDelete(query);
      if (!meter) {
        return response.status(404).json({ error: "Meter not found." });
      }
      await Reading.deleteMany({
        ownerId: request.user.id,
        meterClientId: query.clientId,
      });
      return response.status(204).send();
    } catch (error) {
      return next(error);
    }
  });

  return router;
}

export function toMeterDocument(ownerId, meter) {
  return {
    ownerId,
    clientId: meter.clientId,
    nickname: meter.nickname,
    type: meter.type,
    meterNumber: meter.meterNumber,
    consumerNumber: meter.consumerNumber || null,
    freeUnits: meter.type === "ELECTRICITY" ? meter.freeUnits : null,
    cycleBaseline: meter.cycleBaseline ?? null,
    clientCreatedAt: meter.createdAt,
  };
}

export function toMeterResponse(meter) {
  return {
    clientId: meter.clientId,
    nickname: meter.nickname,
    type: meter.type,
    meterNumber: meter.meterNumber,
    consumerNumber: meter.consumerNumber,
    freeUnits: meter.freeUnits,
    cycleBaseline: meter.cycleBaseline,
    createdAt: meter.clientCreatedAt,
    updatedAt: meter.updatedAt,
  };
}
