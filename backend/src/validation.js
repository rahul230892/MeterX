import { z } from "zod";

export const credentialsSchema = z.object({
  username: z.string().trim().toLowerCase().min(3).max(64),
  password: z.string().min(8).max(128),
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

export const snapshotSchema = z
  .object({
    meters: z.array(meterSchema).max(10000),
    readings: z.array(readingSchema).max(100000),
  })
  .superRefine((snapshot, context) => {
    const meterIds = new Set(snapshot.meters.map((meter) => meter.clientId));
    const uniqueMeterIds = new Set();
    const uniqueReadingIds = new Set();

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
  });
