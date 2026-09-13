import mongoose from "mongoose";

const vehicleRecordSchema = new mongoose.Schema(
  {
    ownerId: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true, index: true },
    clientId: { type: String, required: true, trim: true },
    vehicleClientId: { type: String, required: true, trim: true },
    type: { type: String, required: true, enum: ["POLLUTION", "INSURANCE", "SERVICE"] },
    recordDate: { type: Number, required: true },
    nextDueDate: { type: Number, required: true },
    amount: { type: Number, min: 0, default: null },
    kmReading: { type: Number, required: true, min: 0 },
    notes: { type: String, trim: true, maxlength: 500, default: null },
    clientCreatedAt: { type: Number, required: true, min: 0 },
  },
  { timestamps: true },
);

vehicleRecordSchema.index({ ownerId: 1, clientId: 1 }, { unique: true });
vehicleRecordSchema.index({ ownerId: 1, vehicleClientId: 1, nextDueDate: 1 });

export const VehicleRecord = mongoose.model("VehicleRecord", vehicleRecordSchema);
