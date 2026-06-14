# MeterX Backend

Node.js REST API for syncing MeterX Android data to MongoDB. Each user has an
isolated set of meters and readings.

## Requirements

- Node.js 20 or newer
- MongoDB 6+ or MongoDB Atlas

MongoDB transactions are used by the full snapshot endpoint, so production
MongoDB must run as a replica set. Atlas supports this automatically.

## Setup

```bash
cd backend
cp .env.example .env
npm install
npm run dev
```

Set `MONGODB_URI` to a connection string whose default database is `meterx`:

```text
mongodb+srv://USER:PASSWORD@CLUSTER.mongodb.net/meterx
```

Generate a strong JWT secret:

```bash
openssl rand -hex 32
```

Do not commit `.env` or put the MongoDB connection string in the Android APK.

## Authentication

Register:

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "rahul",
  "password": "a-strong-password"
}
```

Login:

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "rahul",
  "password": "a-strong-password"
}
```

Both return a JWT. Send it on protected requests:

```http
Authorization: Bearer JWT_TOKEN
```

## Sync API

- `GET /api/sync` downloads the user's complete cloud snapshot.
- `PUT /api/sync` atomically replaces the user's cloud snapshot.
- `GET /api/meters` lists meters.
- `PUT /api/meters/:clientId` creates or updates one meter.
- `DELETE /api/meters/:clientId` deletes a meter and its readings.
- `GET /api/readings?meterClientId=...` lists readings.
- `PUT /api/readings/:clientId` creates or updates one reading.
- `DELETE /api/readings/:clientId` deletes one reading.

Android Room IDs are sent as `clientId` strings. This lets existing local data
upload without changing the current Room schema.

### Snapshot example

```json
{
  "meters": [
    {
      "clientId": "1",
      "nickname": "Home",
      "type": "ELECTRICITY",
      "meterNumber": "M-100",
      "consumerNumber": null,
      "freeUnits": 200,
      "cycleBaseline": 1450,
      "createdAt": 1710000000000
    }
  ],
  "readings": [
    {
      "clientId": "1",
      "meterClientId": "1",
      "value": 1482,
      "readingDate": 20500,
      "isBilled": false,
      "createdAt": 1710000000000
    }
  ]
}
```

## Verification

```bash
npm test
npm run check
```

## Render Deployment

The repository root includes `render.yaml`. Create a Render Blueprint from the
GitHub repository and provide only `MONGODB_URI`; Render generates `JWT_SECRET`.

Use a MongoDB Atlas connection string whose database path ends in `/meterx`.
