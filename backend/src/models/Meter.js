import mongoose from "mongoose";

const meterSchema = new mongoose.Schema(
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
    nickname: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120,
    },
    type: {
      type: String,
      required: true,
      enum: ["ELECTRICITY", "WATER", "GAS"],
    },
    meterNumber: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120,
    },
    consumerNumber: {
      type: String,
      trim: true,
      maxlength: 120,
      default: null,
    },
    freeUnits: {
      type: Number,
      min: 0,
      default: null,
    },
    cycleBaseline: {
      type: Number,
      min: 0,
      default: null,
    },
    clientCreatedAt: {
      type: Number,
      required: true,
      min: 0,
    },
  },
  { timestamps: true },
);

meterSchema.index({ ownerId: 1, clientId: 1 }, { unique: true });

export const Meter = mongoose.model("Meter", meterSchema);
