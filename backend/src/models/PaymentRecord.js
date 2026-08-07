import mongoose from "mongoose";

const paymentRecordSchema = new mongoose.Schema(
  {
    ownerId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    clientId: {
      type: String,
      required: true,
      trim: true,
    },
    meterClientId: {
      type: String,
      required: true,
      trim: true,
    },
    readingClientId: {
      type: String,
      required: true,
      trim: true,
    },
    amount: {
      type: Number,
      required: true,
      min: 0,
    },
    paymentDate: {
      type: Number,
      required: true,
    },
    methodName: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120,
    },
    clientCreatedAt: {
      type: Number,
      required: true,
      min: 0,
    },
  },
  { timestamps: true },
);

paymentRecordSchema.index({ ownerId: 1, clientId: 1 }, { unique: true });
paymentRecordSchema.index(
  { ownerId: 1, readingClientId: 1 },
  { name: "owner_reading_payment", unique: true },
);

export const PaymentRecord = mongoose.model("PaymentRecord", paymentRecordSchema);
