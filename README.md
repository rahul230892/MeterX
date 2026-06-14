# MeterX

MeterX is a native Android app for privately tracking electricity, water, and
gas meters. It is built with Kotlin, Jetpack Compose, Material 3, and Room.

## Features

- Add electricity, water, and gas meters.
- Require a nickname and meter number; consumer number is optional.
- Configure a free-unit allowance for electricity meters.
- Show electricity usage from the current cycle baseline.
- Warn near the allowance at 80% and show red at or above 100%.
- Add dated meter readings and mark a reading as billed.
- Edit the value, date, or billed status of an existing reading.
- Start a new free-unit cycle from the latest billed reading.
- Confirm before deleting a meter or reading.
- Store data in the local Room database and mirror every change to
  `files/meterx_backup.json`.
- Include the database and JSON mirror in Android backup/device transfer.

## Free-unit cycle

The first electricity reading establishes the initial cycle baseline. Usage is
the latest reading minus that baseline. When a reading is marked as billed,
the detail screen offers **Reset free units**, which moves the baseline to that
reading without deleting history.

## Build

Open the project in Android Studio or run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Updating without losing data

Install a newer APK over the existing app instead of uninstalling it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Data is retained because updates keep the same application ID
(`com.meterx.app`), signing key, database name, and compatible Room schema.
Do not uninstall the app or clear its storage before installing the update.
