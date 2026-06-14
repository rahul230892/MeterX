import assert from "node:assert/strict";
import test from "node:test";
import request from "supertest";
import { createApp } from "../src/app.js";
import { snapshotSchema } from "../src/validation.js";

const config = {
  JWT_SECRET: "test-secret-that-is-at-least-thirty-two-characters",
  JWT_EXPIRES_IN: "1h",
  CORS_ORIGIN: "*",
};

test("health endpoint reports service status", async () => {
  const response = await request(createApp(config)).get("/health");

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, {
    status: "ok",
    service: "meterx-backend",
  });
});

test("protected sync endpoint rejects anonymous access", async () => {
  const response = await request(createApp(config)).get("/api/sync");

  assert.equal(response.status, 401);
  assert.equal(response.body.error, "Authentication required.");
});

test("snapshot validation accepts MeterX Android data", () => {
  const snapshot = snapshotSchema.parse({
    meters: [
      {
        clientId: 1,
        nickname: "Home",
        type: "ELECTRICITY",
        meterNumber: "M-1",
        consumerNumber: null,
        freeUnits: 200,
        cycleBaseline: 1000,
        createdAt: 1710000000000,
      },
    ],
    readings: [
      {
        clientId: 10,
        meterClientId: 1,
        value: 1025,
        readingDate: 20500,
        isBilled: false,
        createdAt: 1710000000000,
      },
    ],
  });

  assert.equal(snapshot.meters[0].clientId, "1");
  assert.equal(snapshot.readings[0].meterClientId, "1");
});

test("snapshot validation rejects orphan readings", () => {
  const result = snapshotSchema.safeParse({
    meters: [],
    readings: [
      {
        clientId: "10",
        meterClientId: "missing",
        value: 10,
        readingDate: 20500,
        isBilled: false,
        createdAt: 1710000000000,
      },
    ],
  });

  assert.equal(result.success, false);
});
