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
    private Map<String, double[]> urlLocationsCart;
    private Map<String, Beacon> beaconMap;
    private Triangulation triangulation;

    public BLEBeaconDriver() {
        // TODO: implement something better for this down the road
        beaconMap = new HashMap<>();
        urlLocations = new HashMap<>();
        urlLocationsCart = new HashMap<>();
        urlLocations.put("http://rpi4-1", new double[]{34.2520736, -86.2012028, 283.0});
        urlLocations.put("http://opensensorhub.org", new double[]{34.2520221, -86.2012018, 283.0});
        urlLocations.put("http://cardbeacon", new double[]{34.2520761, -86.2012561, 283.0});

        urlLocationsCart.put("http://rpi4-1", new double[]{349670.211399502, -5266209.53754881, 3569752.24484278});
        urlLocationsCart.put("http://opensensorhub.org", new double[]{349670.516346565, -5266212.73984997, 3569752.24484278});
        urlLocationsCart.put("http://cardbeacon", new double[]{349665.302111348, -86.2012561, 3569752.24484278});
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
                beaconMap.put(url, beacon);
            }
        }
        // cut list to closest 3 beacons if too large, might be a better way to deal with this...
        if (closestBeacons.size() > 3) {
            closestBeacons = closestBeacons.subList(0, 2);
        }
        if (closestBeacon != null) {
            locOutput.sendMeasurement(closestBeacon);
        }
        determineLocation();
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
        /*if (closestBeacons.isEmpty()) {
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
            locations[1] = new Triangulation.Vec3d(urlLocations.get(b2)[0], urlLocations.get(b2)[1], urlLocations.get(b2)[2]);
            locations[2] = new Triangulation.Vec3d(urlLocations.get(b3)[0], urlLocations.get(b3)[1], urlLocations.get(b3)[2]);
            locations[3] = new Triangulation.Vec3d(urlLocations.get(b1)[0], urlLocations.get(b1)[1], urlLocations.get(b1)[2]);
            double[] distances = {
                    closestBeacons.get(0).getDistance(),
                    closestBeacons.get(1).getDistance(),
                    closestBeacons.get(2).getDistance(),
                    closestBeacons.get(0).getDistance(),
            };
            Triangulation.Vec3d solution = new Triangulation.Vec3d(0.0, 0.0, 0.0);
//            Log.d(TAG, "determineLocation: " + trilaterationAlgo.getLocation(solution, 0, locations, distances));
        }*/
        if(beaconMap.size() == 3){
            double[] distances = new double[3];
            double[][] locations = new double[3][3];
            int i = 0;
            for(Beacon beacon: beaconMap.values()){
                locations[i] = urlLocations.get(UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray()));
                distances[i] = beacon.getDistance();
                i++;
            }

            Triangulation trilaterationAlgo = new Triangulation();
            Triangulation.Vec3d[] v3Loc = new Triangulation.Vec3d[4];
            v3Loc[0] = new Triangulation.Vec3d(locations[0][0], locations[0][1], locations[0][2]);
            v3Loc[1] = new Triangulation.Vec3d(locations[1][0], locations[1][1], locations[1][2]);
            v3Loc[2] = new Triangulation.Vec3d(locations[2][0], locations[2][1], locations[2][2]);
            v3Loc[3] = new Triangulation.Vec3d(locations[0][0], locations[0][1], locations[0][2]);
            double[] triDist = {distances[0], distances[1], distances[2], distances[0]};

            Triangulation.Vec3d solution = new Triangulation.Vec3d(0.0, 0.0, 0.0);
            Log.d(TAG, "determineLocation: " + trilaterationAlgo.getLocation(solution, 0, v3Loc, triDist));

            double[] estLocation = AdaptedCellTrilateration.trackPhone(locations, distances);
            Log.d(TAG, "determineLocation: " + estLocation[0] + "," + estLocation[1]);
        }
    }
}
