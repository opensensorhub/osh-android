package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.os.Build;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BLEBeaconDriver extends AbstractSensorModule<BLEBeaconConfig> implements BeaconConsumer, RangeNotifier {
    private static final Logger log = LoggerFactory.getLogger(BLEBeaconDriver.class.getSimpleName());
    public static final String LOCAL_REF_FRAME = "LOCAL_FRAME";
    private static final String TAG = "BLEBeaconDriver";

    String localFrameURI;
    List<PhysicalComponent> smlComponents;
    private BeaconManager mBeaconManager;
    private BLEBeaconRawOutput rawOutput;
    private BLEBeaconLocationOutput locOutput;
    private List<Beacon> closestBeacons;
    private Map<String, double[]> urlLocations;
    private Triangulation triangulation;

    public BLEBeaconDriver() {
        // TODO: implement something better for this down the road
        urlLocations = new HashMap<>();
        urlLocations.put("http://cardbeacon", new double[]{34.2520745, -86.2012021, 283.0});
        urlLocations.put("http://opensensorhub.org", new double[]{34.251943, -86.201209, 283.0});
    }

    @Override
    public synchronized void init() throws SensorHubException {
        Context androidContext = config.androidContext;

        String deviceID = Settings.Secure.getString(androidContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.xmlID = "ANDROID_SENSORS_" + Build.SERIAL;     // Deprecated in API level 26
        this.uniqueID = "urn:android:ble_beacon:" + deviceID;
        this.localFrameURI = this.uniqueID + "#" + LOCAL_REF_FRAME;

        triangulation = new Triangulation();
        closestBeacons = new ArrayList<>();
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
        Beacon closestBeacon = null;

        for (Beacon beacon : beacons) {
            if (beacon.getServiceUuid() == 0xfeaa && beacon.getBeaconTypeCode() == 0x10) {
                // This is an Eddystone-URL frame
                String url = UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray());
                Log.d(TAG, "Beacon ID: " + beacon.getId1() + "\nBeacon URL: " + url +
                        " approximately " + beacon.getDistance() + " meters away.");
                // TODO: Need to improve this to handle non-EsURL beacons that have info in the other ID slots
//                this.beacons.put(beacon.getId1(), beacon);
                rawOutput.sendBeaconRecord(beacon);
                if (closestBeacon == null || beacon.getDistance() < closestBeacon.getDistance()) {
                    closestBeacon = beacon;
                }
                // insert new beacons
                // TODO: check that we don't duplicate beacons accidentally
                if (closestBeacons.isEmpty() || closestBeacons.get(0).getDistance() > beacon.getDistance()) {
                    closestBeacons.add(0, beacon);
                } else if (closestBeacons.size() == 1 || closestBeacons.get(1).getDistance() < beacon.getDistance()) {
                    closestBeacons.add(1, beacon);
                } else if (closestBeacons.size() == 2 || closestBeacons.get(2).getDistance() < beacon.getDistance()) {
                    closestBeacons.add(2, beacon);
                }
            }
        }
        // cut list to closest 3 beacons if too large, might be a better way to deal with this...
        if (closestBeacons.size() > 3) {
            closestBeacons = closestBeacons.subList(0, 2);
        }
        if (closestBeacon != null) {
            locOutput.sendMeasurement(closestBeacon);
        }
    }

    private List<Beacon> getBestBeacons(Collection<Beacon> beacons) {
        List<Beacon> bestBeacons = new ArrayList<>();
        for (Beacon beacon : beacons) {
            if (bestBeacons.size() == 0) {
                bestBeacons.add(beacon);
            } else if (bestBeacons.size() < 3) {
                for (int i = 0; i < bestBeacons.size(); i++) {
                    if (beacon.getDistance() < bestBeacons.get(i).getDistance()) {
                        bestBeacons.add(i, beacon);
                        break;
                    }
                }
            }
        }
        return bestBeacons;
    }

    private void determineLocation() {
        if (closestBeacons.isEmpty()) {
            return;
        } else if (closestBeacons.size() < 3) {
            closestBeacons.get(0).getDistance();
            // TODO: figure out location here instead of in output...
        } else {
            // TODO: triangulate based on best three
            String b1, b2, b3;
            Triangulation trilaterationAlgo = new Triangulation();

            b1 = UrlBeaconUrlCompressor.uncompress(closestBeacons.get(0).getId1().toByteArray());
            b2 = UrlBeaconUrlCompressor.uncompress(closestBeacons.get(1).getId1().toByteArray());
            b3 = UrlBeaconUrlCompressor.uncompress(closestBeacons.get(2).getId1().toByteArray());

            Triangulation.Vec3d[] locations = new Triangulation.Vec3d[4];
            locations[0] = new Triangulation.Vec3d(urlLocations.get(b1)[0], urlLocations.get(b1)[1], urlLocations.get(b1)[2]);
            locations[0] = new Triangulation.Vec3d(urlLocations.get(b2)[0], urlLocations.get(b2)[1], urlLocations.get(b2)[2]);
            locations[0] = new Triangulation.Vec3d(urlLocations.get(b3)[0], urlLocations.get(b3)[1], urlLocations.get(b3)[2]);
            double[] distances = {
                    closestBeacons.get(0).getDistance(),
                    closestBeacons.get(1).getDistance(),
                    closestBeacons.get(2).getDistance()};
            Triangulation.Vec3d solution = new Triangulation.Vec3d(0.0, 0.0, 0.0);
            Log.d(TAG, "determineLocation: " + trilaterationAlgo.getLocation(solution, 0, locations, distances));
        }
    }

    private double haversineDist(String url1, String url2) {
        double R = 6372.8;  // Approximate Radius of Earth in kilometers
        double[] coords1 = urlLocations.get(url1);
        double[] coords2 = urlLocations.get(url2);

        double dLat = Math.toRadians(coords2[0] - coords1[0]);
        double dLon = Math.toRadians(coords2[1] - coords1[1]);
        double lat1 = Math.toRadians(coords1[0]);
        double lat2 = Math.toRadians(coords2[0]);

        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c;
    }
}
