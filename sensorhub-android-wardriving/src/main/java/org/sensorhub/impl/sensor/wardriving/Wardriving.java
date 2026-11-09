/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 The Initial Developer is Botts Innovative Research Inc. Portions created by the Initial
 Developer are Copyright (C) 2025 the Initial Developer. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.wardriving;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.core.content.ContextCompat;

import net.opengis.sensorml.v20.PhysicalComponent;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.android.SensorMLBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wardriving sensor driver that scans for WiFi access points and
 * records their details along with the device's GPS location.
 *
 * @author Kalyn Stricklin
 * @since April 6, 2026
 */
public class Wardriving extends AbstractSensorModule<WardrivingConfig> {
    static final String UID_PREFIX = "urn:osh:sensor:wardriving:";

    private final ArrayList<PhysicalComponent> smlComponents;
    private final SensorMLBuilder smlBuilder;
    static final Logger logger = LoggerFactory.getLogger(Wardriving.class.getSimpleName());

    private Context context;
    WifiOutput wifiOutput;
    BLEOutput bleOutput;
    private HandlerThread eventThread;
    private Handler eventHandler;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothManager bluetoothManager;
    private WifiManager wifiManager;
    private LocationManager locationManager;
    private BroadcastReceiver wifiReceiver;
    private LocationListener locationListener;

    private volatile double currentLat = 0.0;
    private volatile double currentLon = 0.0;
    private volatile double currentAlt = 0.0;
    private volatile boolean scanning = false;
    private Runnable scanRunnable;
    private ScanCallback bleScanCallback;

    public Wardriving() {
        this.smlComponents = new ArrayList<PhysicalComponent>();
        this.smlBuilder = new SensorMLBuilder();
    }

    @Override
    public void doInit() {
        logger.info("Initializing Wardriving Sensor");
        this.xmlID = "WARDRIVING_" + Build.SERIAL;
        this.uniqueID = UID_PREFIX + config.getUidWithExt();

        context = SensorHubService.getContext();

        bleOutput = new BLEOutput(this);
        bleOutput.doInit();
        addOutput(bleOutput, false);

        wifiOutput = new WifiOutput(this);
        wifiOutput.doInit();
        addOutput(wifiOutput, false);
    }

    @Override
    public void doStart() throws SensorException {
        eventThread = new HandlerThread("WardrivingThread");
        eventThread.start();
        eventHandler = new Handler(eventThread.getLooper());

        wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        if (wifiManager == null)
            throw new SensorException("WiFi service not available");

        if (!wifiManager.isWifiEnabled()) {
            logger.warn("WiFi is disabled");
        }

        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                logger.info("WiFi scan broadcast received");
                handleScanResults();
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_EXPORTED);

        // start GPS location updates
        startLocationUpdates();

        scanning = true;
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    logger.info("Triggering WiFi scan");
                    boolean started = wifiManager.startScan();
                    logger.info("WiFi scan started: {}", started);
                    eventHandler.postDelayed(this, config.scanIntervalMs);
                }
            }
        };
        eventHandler.post(scanRunnable);

        // start BLE scanning
        startBleScan();

        logger.info("Wardriving sensor started");
    }

    private void startBleScan() {
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            logger.warn("BluetoothManager not available, skipping BLE scan");
            return;
        }

        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            logger.warn("Bluetooth adapter not available or disabled, skipping BLE scan");
            return;
        }

        try {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                logger.error("BLUETOOTH_SCAN permission not granted");
                return;
            }

            bluetoothLeScanner = adapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                logger.warn("BLE scanner not available");
                return;
            }

            bleScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                    if (!scanning) return;

                    String address = result.getDevice().getAddress();
                    String name = null;
                    try {
                        name = result.getDevice().getName();
                    } catch (SecurityException e) {
                    }
                    int rssi = result.getRssi();

                    bleOutput.setData(address, name, rssi, currentLat, currentLon, currentAlt);
                }

                @Override
                public void onBatchScanResults(List<android.bluetooth.le.ScanResult> results) {
                    for (android.bluetooth.le.ScanResult result : results) {
                        onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    logger.error("BLE scan failed with error code: {}", errorCode);
                }
            };

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0)
                    .build();

            bluetoothLeScanner.startScan(Collections.<ScanFilter>emptyList(), settings, bleScanCallback);
            logger.info("BLE scanning started");

        } catch (SecurityException e) {
            logger.error("Security exception starting BLE scan", e);
        }
    }

    private void startLocationUpdates() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                currentLat = location.getLatitude();
                currentLon = location.getLongitude();
                currentAlt = location.hasAltitude() ? location.getAltitude() : 0.0;
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {}
        };

        try {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000, 0, locationListener, eventThread.getLooper());

                Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) {
                    currentLat = last.getLatitude();
                    currentLon = last.getLongitude();
                    currentAlt = last.hasAltitude() ? last.getAltitude() : 0.0;
                }
            } else {
                logger.error("Location permission not granted");
            }
        } catch (SecurityException e) {
            logger.error("Security exception requesting location updates", e);
        }
    }

    private void handleScanResults() {
        if (!scanning)
            return;

        try {
            List<ScanResult> results = wifiManager.getScanResults();
            if (results == null || results.isEmpty()) {
                logger.debug("No WiFi scan results");
                return;
            }

            logger.info("Scan found {} WiFi access points at [{}, {}]",
                    results.size(), currentLat, currentLon);

            for (ScanResult ap : results) {
                logger.info("AP: BSSID={} SSID=\"{}\" RSSI={}dBm Freq={}MHz Security={}",
                        ap.BSSID,
                        ap.SSID != null ? ap.SSID : "<hidden>",
                        ap.level,
                        ap.frequency,
                        ap.capabilities);

                wifiOutput.setData(
                        ap.BSSID,
                        ap.SSID,
                        ap.level,
                        ap.frequency,
                        ap.capabilities,
                        currentLat,
                        currentLon,
                        currentAlt
                );
            }
        } catch (SecurityException e) {
            logger.error("Security exception reading scan results", e);
        }
    }

    @Override
    public void doStop() {
        scanning = false;

        if (eventHandler != null && scanRunnable != null) {
            eventHandler.removeCallbacks(scanRunnable);
        }

        if (wifiReceiver != null) {
            try {
                context.unregisterReceiver(wifiReceiver);
            } catch (IllegalArgumentException e) {
                logger.warn("WiFi receiver already unregistered");
            }
            wifiReceiver = null;
        }

        if (bluetoothLeScanner != null && bleScanCallback != null) {
            try {
                bluetoothLeScanner.stopScan(bleScanCallback);
            } catch (SecurityException e) {
                logger.warn("Security exception stopping BLE scan", e);
            }
            bleScanCallback = null;
            bluetoothLeScanner = null;
        }

        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }

        if (eventThread != null) {
            eventThread.quitSafely();
            eventThread = null;
        }

        eventHandler = null;
        logger.info("Wardriving sensor stopped");
    }

    @Override
    public boolean isConnected() {
        return wifiManager != null && scanning;
    }
}
