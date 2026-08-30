import { z } from "zod";

export const credentialsSchema = z.object({
  username: z.string().trim().toLowerCase().min(3).max(64),
  password: z.string().min(8).max(128),
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(8).max(128),
  newPassword: z.string().min(8).max(128),
});

const customFieldSchema = z.object({
  id: z.string().trim().min(1).max(80),
  name: z.string().trim().min(1).max(60),
});

const customValueSchema = z.object({
  fieldId: z.string().trim().min(1).max(80),
  value: z.string().trim().min(1).max(240),
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
    customFields: z.array(customFieldSchema).max(50).default([]),
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
    const fieldIds = new Set();
    const fieldNames = new Set();
    meter.customFields.forEach((field, index) => {
      const normalizedName = field.name.toLowerCase();
      if (fieldIds.has(field.id)) {
        context.addIssue({
          code: "custom",
          path: ["customFields", index, "id"],
          message: "Duplicate custom column ID.",
        });
      }
      if (fieldNames.has(normalizedName)) {
        context.addIssue({
          code: "custom",
          path: ["customFields", index, "name"],
          message: "Duplicate custom column name.",
        });
      }
      fieldIds.add(field.id);
      fieldNames.add(normalizedName);
    });
  });

export const readingSchema = z.object({
  clientId: z.union([z.string(), z.number()]).transform(String),
  meterClientId: z.union([z.string(), z.number()]).transform(String),
  value: z.number().nonnegative(),
  readingDate: z.number().int(),
  isBilled: z.boolean().default(false),
  customValues: z.array(customValueSchema).max(50).default([]),
  createdAt: z.number().int().nonnegative(),
}).superRefine((reading, context) => {
  const fieldIds = new Set();
  reading.customValues.forEach((entry, index) => {
    if (fieldIds.has(entry.fieldId)) {
      context.addIssue({
        code: "custom",
        path: ["customValues", index, "fieldId"],
        message: "Duplicate custom column value.",
      });
    }
    fieldIds.add(entry.fieldId);
  });
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
    const customFieldIdsByMeter = new Map(
      snapshot.meters.map((meter) => [
        meter.clientId,
        new Set(meter.customFields.map((field) => field.id)),
      ]),
    );
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
      const customFieldIds = customFieldIdsByMeter.get(reading.meterClientId);
      reading.customValues.forEach((entry, valueIndex) => {
        if (!customFieldIds?.has(entry.fieldId)) {
          context.addIssue({
            code: "custom",
            path: ["readings", index, "customValues", valueIndex, "fieldId"],
            message: "Custom value references a column not defined for this meter.",
          });
        }
      });
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
