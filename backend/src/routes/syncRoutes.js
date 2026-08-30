import mongoose from "mongoose";
import { Router } from "express";
import { Meter } from "../models/Meter.js";
import { PaymentMethod } from "../models/PaymentMethod.js";
import { PaymentRecord } from "../models/PaymentRecord.js";
import { Reading } from "../models/Reading.js";
import { snapshotSchema } from "../validation.js";
import { toMeterDocument, toMeterResponse } from "./meterRoutes.js";
import { toReadingDocument, toReadingResponse } from "./readingRoutes.js";

export function createSyncRouter() {
  const router = Router();

  router.get("/", async (request, response, next) => {
    try {
      const [meters, readings, paymentMethods, payments] = await Promise.all([
        Meter.find({ ownerId: request.user.id })
          .sort({ clientCreatedAt: -1 })
          .lean(),
        Reading.find({ ownerId: request.user.id })
          .sort({ readingDate: -1, clientCreatedAt: -1 })
          .lean(),
        PaymentMethod.find({ ownerId: request.user.id })
          .sort({ name: 1 })
          .lean(),
        PaymentRecord.find({ ownerId: request.user.id })
          .sort({ paymentDate: -1, clientCreatedAt: -1 })
          .lean(),
      ]);
      return response.json({
        version: 3,
        syncedAt: Date.now(),
        meters: meters.map(toMeterResponse),
        readings: readings.map(toReadingResponse),
        paymentMethods: paymentMethods.map(toPaymentMethodResponse),
        payments: payments.map(toPaymentRecordResponse),
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
        await PaymentRecord.deleteMany({ ownerId: request.user.id }).session(session);
        await PaymentMethod.deleteMany({ ownerId: request.user.id }).session(session);
        await Reading.deleteMany({ ownerId: request.user.id }).session(session);
        await Meter.deleteMany({ ownerId: request.user.id }).session(session);

        if (snapshot.paymentMethods.length) {
          await PaymentMethod.insertMany(
            snapshot.paymentMethods.map((method) =>
              toPaymentMethodDocument(request.user.id, method),
            ),
            { session },
          );
        }
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
        if (snapshot.payments.length) {
          await PaymentRecord.insertMany(
            snapshot.payments.map((payment) =>
              toPaymentRecordDocument(request.user.id, payment),
            ),
            { session },
          );
        }
      });
      return response.json({
        syncedAt: Date.now(),
        meterCount: snapshot.meters.length,
        readingCount: snapshot.readings.length,
        paymentMethodCount: snapshot.paymentMethods.length,
        paymentCount: snapshot.payments.length,
      });
    } catch (error) {
      return next(error);
    } finally {
      await session.endSession();
    }
  });

  return router;
}

function toPaymentMethodDocument(ownerId, method) {
  return {
    ownerId,
    clientId: method.clientId,
    name: method.name,
    clientCreatedAt: method.createdAt,
  };
}

function toPaymentMethodResponse(method) {
  return {
    clientId: method.clientId,
    name: method.name,
    createdAt: method.clientCreatedAt,
    updatedAt: method.updatedAt,
  };
}

function toPaymentRecordDocument(ownerId, payment) {
  return {
    ownerId,
    clientId: payment.clientId,
    meterClientId: payment.meterClientId,
    readingClientId: payment.readingClientId,
    amount: payment.amount,
    paymentDate: payment.paymentDate,
    methodName: payment.methodName,
    clientCreatedAt: payment.createdAt,
  };
}

function toPaymentRecordResponse(payment) {
  return {
    clientId: payment.clientId,
    meterClientId: payment.meterClientId,
    readingClientId: payment.readingClientId,
    amount: payment.amount,
    paymentDate: payment.paymentDate,
    methodName: payment.methodName,
    createdAt: payment.clientCreatedAt,
    updatedAt: payment.updatedAt,
  };
}
