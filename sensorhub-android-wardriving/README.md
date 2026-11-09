# Wardriving WiFi and BLE Scan Driver

OpenSensorHub driver that performs wardriving by scanning for nearby WiFi access points and Bluetooth Low Energy (BLE) devices, and device's GPS location at the time of scan.

## Outputs

### WiFi Scan
Each observation captures a single access point:
- **BSSID** - MAC address of the access point
- **SSID** - Network name (empty for hidden networks)
- **RSSI** - Signal strength in dBm
- **Frequency** - Channel frequency in MHz
- **Capabilities** - Security/encryption schemes (e.g. WPA2, WPA3)
- **Location** - GPS lat/lon/alt of the device at scan time

### BLE Scan
Each observation captures a single BLE device:
- **Device Address** - MAC address of the BLE device
- **Device Name** - Advertised name (if available)
- **RSSI** - Signal strength in dBm
- **Location** - GPS lat/lon/alt of the device at scan time

## Setup
1. Enable the wardriving sensor in the osh-android app sensors tab
2. Ensure WiFi and Bluetooth are enabled on the device
3. Grant location and nearby device permissions if prompted
4. The driver begins periodic WiFi scans and continuous BLE scanning automatically