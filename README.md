# OpenSensorHub for Android

An Android application that collects sensor data from phones and tablets and streams it to a remote [OpenSensorHub](https://opensensorhub.org/) (OSH) server using OGC standards (SOS-T and Connected Systems API).

## Supported Sensors

**On-Device:**
- GPS location
- IMU (accelerometers, gyroscopes, magnetometers)
- Fused orientation (quaternions or Euler angles)
- Video camera (H.264, H.265, VP8, VP9, MJPEG)
- Audio microphone (AAC, OPUS)

**External (USB / Bluetooth / BLE):**
- FLIR One thermal camera (USB)
- Trupulse 360 range finder (Bluetooth)
- Angel Sensor wrist band (BLE)
- Kestrel weather meter (BLE)
- Polar H9/H10 heart rate monitor (Bluetooth)
- Meshtastic mesh radio (Bluetooth)
- STE radiation pager
- BLE beacons
- Garmin Wearable Sensor

## Requirements
- **Android device** running Android 14 (API 34) or later
- **JDK 17**
- **Android SDK** with Build Tools 30.0.2 and API level 33 installed
- **Android Studio** (recommended) or Gradle 7.4+ for command-line builds
- **Git** (with submodule support)

## Setup

### 1. Clone the Repository

```bash
git clone --recursive https://github.com/botts-innovative-research/osh-android.git
cd osh-android
```

If you already cloned without `--recursive`, initialize the submodules separately:

```bash
git submodule update --init --recursive
```

This pulls in two required submodule dependencies:
- **osh-core** -- the core OpenSensorHub framework
- **osh-addons** -- additional sensor drivers and processing modules

### 2. Configure the Android SDK Path
Create or verify the `local.properties` file in the project root:

```properties
sdk.dir=/path/to/your/Android/sdk
```

On macOS this is typically `~/Library/Android/sdk`. On Linux it is often `~/Android/Sdk`.

### 3. Build the App

#### Option A: Android Studio (Recommended)
1. Open Android Studio
2. **File > Open** and select the `osh-android` project directory
3. Wait for Gradle sync to complete
4. **Build > Build Bundle(s) / APK(s) > Build APK(s)**
5. The APK is output to `sensorhub-android-app/build/outputs/apk/debug/`

#### Option B: Command Line
```bash
./gradlew build -x test
```

The APK is output to `sensorhub-android-app/build/outputs/apk/debug/`.


### 4. Install on a Device

```bash
./gradlew :sensorhub-android-app:assembleDebug 

adb install -r sensorhub-android-app/build/outputs/apk/debug/sensorhub-android-app-debug.apk
```

## Project Structure

```
osh-android/
  sensorhub-android-app/       # Main demo application (APK)
  sensorhub-android-service/   # Background SensorHub service library
  sensorhub-android-lib/       # Dependency aggregation library
  sensorhub-driver-android/    # Core Android sensor & camera driver
  sensorhub-android-polar/     # Polar heart rate monitor driver
  sensorhub-android-meshtastic/# Meshtastic mesh radio driver
  sensorhub-android-ste/       # STE radiation pager driver
  sensorhub-android-flirone/   # FLIR One thermal camera driver
  sensorhub-android-blebeacon/ # BLE beacon detector driver
  submodules/
    osh-core/                   # Core OSH framework
    osh-addons/                 # Additional drivers & processing
```

## Troubleshooting
- **Gradle sync fails**: Verify that submodules are initialized (`git submodule update --init --recursive`) and that `local.properties` points to a valid Android SDK.
- **Build errors with JDK**: Ensure JDK 17 is installed and selected in Android Studio (File > Settings > Build > Gradle > Gradle JDK).
- **App crashes on launch**: Confirm the device runs Android 14 (API 34) or later.
- **No data reaching server**: Check that the server IP/port are correct, and the device has network connectivity.
- **Bluetooth sensors not connecting**: Pair the external sensor in Android system Bluetooth settings before enabling it in the app.
