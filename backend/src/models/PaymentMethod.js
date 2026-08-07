import mongoose from "mongoose";

const paymentMethodSchema = new mongoose.Schema(
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
    name: {
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

paymentMethodSchema.index({ ownerId: 1, clientId: 1 }, { unique: true });
paymentMethodSchema.index({ ownerId: 1, name: 1 });

export const PaymentMethod = mongoose.model("PaymentMethod", paymentMethodSchema);
