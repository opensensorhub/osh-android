package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import net.opengis.sensorml.v20.PhysicalComponent;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class BLEBeaconDriver extends AbstractSensorModule<BLEBeaconConfig> implements BeaconConsumer, RangeNotifier {
    private static final Logger log = LoggerFactory.getLogger(BLEBeaconDriver.class.getSimpleName());
    public static final String LOCAL_REF_FRAME = "LOCAL_FRAME";
    private static final String TAG = "BLEBeaconDriver";

    String localFrameURI;
    List<PhysicalComponent> smlComponents;
    private BeaconManager mBeaconManager;
    private BLEBeaconRawOutput rawOutput;
    private BLEBeaconLocationOutput locOutput;

    public BLEBeaconDriver() {
    }

    @Override
    public synchronized void init() throws SensorHubException {
        Context androidContext = config.androidContext;

        String deviceID = Settings.Secure.getString(androidContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.xmlID = "ANDROID_SENSORS_" + Build.SERIAL;     // Deprecated in API level 26
        this.uniqueID = "urn:android:ble_beacon:" + deviceID;
        this.localFrameURI = this.uniqueID + "#" + LOCAL_REF_FRAME;

        rawOutput = new BLEBeaconRawOutput(this);
        locOutput = new BLEBeaconLocationOutput(this);
        addOutput(rawOutput, false);
        addOutput(locOutput, false);
    }

    @Override
    public void start() throws SensorHubException {
        // start the BLEBeacon Manager scanning
        // BLE Beacon Initialization
        mBeaconManager = BeaconManager.getInstanceForApplication(getConfiguration().androidContext);
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
        mBeaconManager.bind(this);
        rawOutput.start();
        locOutput.start();
    }

    @Override
    public void stop() throws SensorHubException {
        try {
            mBeaconManager.stopRangingBeaconsInRegion(new Region("url-beacons-region", null, null, null));
        } catch (RemoteException e) {
            Log.d(TAG, "unbind: " + e);
        }

        mBeaconManager.unbind(this);
    }

    protected void useSensor(ISensorDataInterface output, Sensor sensor) {
        addOutput(output, false);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    // BLE Beacon Consumer/Range Notifier Require Implementations
    @Override
    public void onBeaconServiceConnect() {
        Region region = new Region("url-beacons-region", null, null, null);
        try {
            mBeaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        mBeaconManager.setRangeNotifier(this);
    }

    @Override
    public Context getApplicationContext() {
        return getConfiguration().androidContext;
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {

    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        return false;
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        for (Beacon beacon : beacons) {
            if (beacon.getServiceUuid() == 0xfeaa && beacon.getBeaconTypeCode() == 0x10) {
                // This is an Eddystone-URL frame
                String url = UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray());
                Log.d(TAG, "Beacon ID: " + beacon.getId1() + "\nBeacon URL: " + url +
                        " approximately " + beacon.getDistance() + " meters away.");
                // TODO: Need to improve this to handle non-EsURL beacons that have info in the other ID slots
//                this.beacons.put(beacon.getId1(), beacon);
                rawOutput.sendBeaconRecord(beacon);
                locOutput.sendMeasurement(beacon);
            }
        }
    }
}
