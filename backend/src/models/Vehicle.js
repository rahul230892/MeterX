import mongoose from "mongoose";

const vehicleSchema = new mongoose.Schema(
  {
    ownerId: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true, index: true },
    clientId: { type: String, required: true, trim: true },
    name: { type: String, required: true, trim: true, maxlength: 120 },
    registrationNumber: { type: String, required: true, trim: true, maxlength: 40 },
    currentKm: { type: Number, required: true, min: 0 },
    clientCreatedAt: { type: Number, required: true, min: 0 },
  },
  { timestamps: true },
);

vehicleSchema.index({ ownerId: 1, clientId: 1 }, { unique: true });

export const Vehicle = mongoose.model("Vehicle", vehicleSchema);
