import assert from "node:assert/strict";
import test from "node:test";
import request from "supertest";
import { createApp } from "../src/app.js";
import { snapshotSchema } from "../src/validation.js";

const config = {
  JWT_SECRET: "test-secret-that-is-at-least-thirty-two-characters",
  JWT_EXPIRES_IN: "1h",
  CORS_ORIGIN: "*",
  APP_LATEST_VERSION_CODE: 5,
  APP_LATEST_VERSION_NAME: "1.4",
  APP_MIN_SUPPORTED_VERSION_CODE: 5,
  APP_APK_URL: "https://example.com/meterx.apk",
  APP_RELEASE_NOTES: "Test update.",
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

test("app update endpoint reports latest version", async () => {
  const response = await request(createApp(config)).get("/api/app/latest");

  assert.equal(response.status, 200);
  assert.equal(response.body.latestVersionCode, 5);
  assert.equal(response.body.latestVersionName, "1.4");
  assert.equal(response.body.minSupportedVersionCode, 5);
  assert.equal(response.body.apkUrl, "https://example.com/meterx.apk");
  assert.equal(response.body.forceUpdate, true);
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
        isBilled: true,
        createdAt: 1710000000000,
      },
    ],
    paymentMethods: [
      {
        clientId: 20,
        name: "Amazon Pay",
        createdAt: 1710000000000,
      },
    ],
    payments: [
      {
        clientId: 30,
        meterClientId: 1,
        readingClientId: 10,
        amount: 1250,
        paymentDate: 20502,
        methodName: "Amazon Pay",
        createdAt: 1710000000000,
      },
    ],
  });

  assert.equal(snapshot.meters[0].clientId, "1");
  assert.equal(snapshot.readings[0].meterClientId, "1");
  assert.equal(snapshot.paymentMethods[0].clientId, "20");
  assert.equal(snapshot.payments[0].readingClientId, "10");
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

test("snapshot validation rejects payments for unbilled readings", () => {
  const result = snapshotSchema.safeParse({
    meters: [
      {
        clientId: "1",
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
        clientId: "10",
        meterClientId: "1",
        value: 1025,
        readingDate: 20500,
        isBilled: false,
        createdAt: 1710000000000,
      },
    ],
    payments: [
      {
        clientId: "30",
        meterClientId: "1",
        readingClientId: "10",
        amount: 1250,
        paymentDate: 20502,
        methodName: "Amazon Pay",
        createdAt: 1710000000000,
      },
    ],
  });

  assert.equal(result.success, false);
});
