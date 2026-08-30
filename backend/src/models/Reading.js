import mongoose from "mongoose";

const customValueSchema = new mongoose.Schema(
  {
    fieldId: {
      type: String,
      required: true,
      trim: true,
      maxlength: 80,
    },
    value: {
      type: String,
      required: true,
      trim: true,
      maxlength: 240,
    },
  },
  { _id: false },
);

const readingSchema = new mongoose.Schema(
  {
    ownerId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    meterClientId: {
      type: String,
      required: true,
      trim: true,
    },
    clientId: {
      type: String,
      required: true,
      trim: true,
    },
    value: {
      type: Number,
      required: true,
      min: 0,
    },
    readingDate: {
      type: Number,
      required: true,
    },
    isBilled: {
      type: Boolean,
      default: false,
    },
    customValues: {
      type: [customValueSchema],
      default: [],
    },
    clientCreatedAt: {
      type: Number,
      required: true,
      min: 0,
    },
  },
  { timestamps: true },
);

readingSchema.index({ ownerId: 1, clientId: 1 }, { unique: true });
readingSchema.index(
  { ownerId: 1, meterClientId: 1, readingDate: -1 },
  { name: "owner_meter_date" },
);

export const Reading = mongoose.model("Reading", readingSchema);
