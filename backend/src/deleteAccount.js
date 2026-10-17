import { Meter } from "./models/Meter.js";
import { PaymentMethod } from "./models/PaymentMethod.js";
import { PaymentRecord } from "./models/PaymentRecord.js";
import { Reading } from "./models/Reading.js";
import { User } from "./models/User.js";
import { Vehicle } from "./models/Vehicle.js";
import { VehicleRecord } from "./models/VehicleRecord.js";

export async function deleteAccountAndData(userId) {
  await Promise.all([
    PaymentRecord.deleteMany({ ownerId: userId }),
    PaymentMethod.deleteMany({ ownerId: userId }),
    Reading.deleteMany({ ownerId: userId }),
    Meter.deleteMany({ ownerId: userId }),
    VehicleRecord.deleteMany({ ownerId: userId }),
    Vehicle.deleteMany({ ownerId: userId }),
  ]);
  await User.deleteOne({ _id: userId });
}
