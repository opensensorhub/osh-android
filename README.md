### OpenSensorHub for Android

This repo contains modules specific to Android, including:

- A demo Android app
- A SensorHub service usable by other Android apps
- Drivers for sensors accessible through the Android 
  [Sensor API](http://developer.android.com/guide/topics/sensors/sensors_overview.html),
  [Camera API](http://developer.android.com/guide/topics/media/camera.html) and
  [Location API](http://developer.android.com/guide/topics/location/index.html)
  
The demo app allows a phone or tablet running Android 5.0 (Lollipop) or later to send sensor data to a remote OSH node using the SOS-T standard.

Supported sensors include the ones that are on-board the phone:
- GPS
- Raw IMU (Accelerometers, Gyroscopes, Magnetometers)
- Fused Orientation (Quaternions or Euler angles)
- Video Camera (MJPEG or H264 codec)

But also other sensors connected via USB, Bluetooth or Bluetooth Smart:
- FLIR One Thermal Camera (USB)
- Trupulse 360 Range Finder (Bluetooth)
- Angel Sensor Wrist Band (Bluetooth Smart)


### Build

The App can be built using Gradle or IntelliJ Idea (version 2017.2.3 or later). It doesn't work in Android Studio until full support for "Gradle Composite Builds" is added.

You'll need to install an up-to-date Android SDK and set its path in the [local.properties](local.properties) file. Also make sure you pulled source code from the following repos: `osh-core`, `osh-comm`, `osh-processing`, `osh-positioning`, `osh-sensors` and `osh-video` in the same location as this repo.

To build using command line gradle, just run `./gradlew build` and you'll find the resulting APK in `sensorhub-android-app/build/outputs/apk`

You can also install it on a USB connected device by running `./gradlew installDebug`.


### How to use the App

The App is designed to be connected to an OSH server (for instance, you can use the [osh-base](https://github.com/opensensorhub/osh-distros/tree/master/osh-base) distribution) and upload inertial, GPS and video data to it.

For the connection to be established, you'll have to configure the App with the correct endpoint of the server (see the [App Documentation](http://docs.opensensorhub.org/user/android-app/))

You'll also have to enable transactional operations on the server side so the phone can register itself and send its data. For this, use the following steps:

- Open the web admin interface at <http://your_domain:port/sensorhub/admin>
- Go to the "Services" tab on the left
- Select "SOS Service"
- Check "Enable Transactional"
- Click "Apply Changes" at the top right
- Optionally click the "Save" button to keep this configuration after restart
