import { z } from "zod";

export const credentialsSchema = z.object({
  username: z.string().trim().toLowerCase().min(3).max(64),
  password: z.string().min(8).max(128),
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(8).max(128),
  newPassword: z.string().min(8).max(128),
});

export const meterSchema = z
  .object({
    clientId: z.union([z.string(), z.number()]).transform(String),
    nickname: z.string().trim().min(1).max(120),
    type: z.enum(["ELECTRICITY", "WATER", "GAS"]),
    meterNumber: z.string().trim().min(1).max(120),
    consumerNumber: z.string().trim().max(120).nullable().optional(),
    freeUnits: z.number().positive().nullable().optional(),
    cycleBaseline: z.number().nonnegative().nullable().optional(),
    createdAt: z.number().int().nonnegative(),
  })
  .superRefine((meter, context) => {
    if (meter.type === "ELECTRICITY" && meter.freeUnits == null) {
      context.addIssue({
        code: "custom",
        path: ["freeUnits"],
        message: "Electricity meters require freeUnits.",
      });
    }
  });

export const readingSchema = z.object({
  clientId: z.union([z.string(), z.number()]).transform(String),
  meterClientId: z.union([z.string(), z.number()]).transform(String),
  value: z.number().nonnegative(),
  readingDate: z.number().int(),
  isBilled: z.boolean().default(false),
  createdAt: z.number().int().nonnegative(),
});

export const paymentMethodSchema = z.object({
  clientId: z.union([z.string(), z.number()]).transform(String),
  name: z.string().trim().min(1).max(120),
  createdAt: z.number().int().nonnegative(),
});

export const paymentSchema = z.object({
  clientId: z.union([z.string(), z.number()]).transform(String),
  meterClientId: z.union([z.string(), z.number()]).transform(String),
  readingClientId: z.union([z.string(), z.number()]).transform(String),
  amount: z.number().positive(),
  paymentDate: z.number().int(),
  methodName: z.string().trim().min(1).max(120),
  createdAt: z.number().int().nonnegative(),
});

export const snapshotSchema = z
  .object({
    meters: z.array(meterSchema).max(10000),
    readings: z.array(readingSchema).max(100000),
    paymentMethods: z.array(paymentMethodSchema).max(1000).default([]),
    payments: z.array(paymentSchema).max(100000).default([]),
  })
  .superRefine((snapshot, context) => {
    const meterIds = new Set(snapshot.meters.map((meter) => meter.clientId));
    const billedReadingIds = new Set(
      snapshot.readings
        .filter((reading) => reading.isBilled)
        .map((reading) => reading.clientId),
    );
    const uniqueMeterIds = new Set();
    const uniqueReadingIds = new Set();
    const uniquePaymentMethodIds = new Set();
    const uniquePaymentIds = new Set();
    const paidReadingIds = new Set();

    snapshot.meters.forEach((meter, index) => {
      if (uniqueMeterIds.has(meter.clientId)) {
        context.addIssue({
          code: "custom",
          path: ["meters", index, "clientId"],
          message: "Duplicate meter clientId.",
        });
      }
      uniqueMeterIds.add(meter.clientId);
    });

    snapshot.readings.forEach((reading, index) => {
      if (!meterIds.has(reading.meterClientId)) {
        context.addIssue({
          code: "custom",
          path: ["readings", index, "meterClientId"],
          message: "Reading references an unknown meter.",
        });
      }
      if (uniqueReadingIds.has(reading.clientId)) {
        context.addIssue({
          code: "custom",
          path: ["readings", index, "clientId"],
          message: "Duplicate reading clientId.",
        });
      }
      uniqueReadingIds.add(reading.clientId);
    });

    snapshot.paymentMethods.forEach((method, index) => {
      if (uniquePaymentMethodIds.has(method.clientId)) {
        context.addIssue({
          code: "custom",
          path: ["paymentMethods", index, "clientId"],
          message: "Duplicate payment method clientId.",
        });
      }
      uniquePaymentMethodIds.add(method.clientId);
    });

    snapshot.payments.forEach((payment, index) => {
      if (!meterIds.has(payment.meterClientId)) {
        context.addIssue({
          code: "custom",
          path: ["payments", index, "meterClientId"],
          message: "Payment references an unknown meter.",
        });
      }
      if (!billedReadingIds.has(payment.readingClientId)) {
        context.addIssue({
          code: "custom",
          path: ["payments", index, "readingClientId"],
          message: "Payment references an unknown billed reading.",
        });
      }
      if (uniquePaymentIds.has(payment.clientId)) {
        context.addIssue({
          code: "custom",
          path: ["payments", index, "clientId"],
          message: "Duplicate payment clientId.",
        });
      }
      if (paidReadingIds.has(payment.readingClientId)) {
        context.addIssue({
          code: "custom",
          path: ["payments", index, "readingClientId"],
          message: "Duplicate payment for billed reading.",
        });
      }
      uniquePaymentIds.add(payment.clientId);
      paidReadingIds.add(payment.readingClientId);
    });
  });
