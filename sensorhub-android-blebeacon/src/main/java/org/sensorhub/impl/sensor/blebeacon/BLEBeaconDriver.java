package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;
import org.sensorhub.algo.geoloc.GeoTransforms;
import org.sensorhub.algo.vecmath.Mat3d;
import org.sensorhub.algo.vecmath.Vect3d;
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
    //    List<PhysicalComponent> smlComponents;
    private BeaconManager mBeaconManager;
    private BLEBeaconRawOutput rawOutput;
    private BLEBeaconLocationOutput locOutput;
    private List<Beacon> closestBeacons;
    private Map<String, Vect3d> url2Locations;
    private Map<String, Beacon> beaconMap;
    private Triangulation triangulation;

    public BLEBeaconDriver() {
        // TODO: implement something better for this down the road
        beaconMap = new HashMap<>();
        url2Locations = new HashMap<>();

        url2Locations.put("http://rpi4-1", new Vect3d(-86.2012028 * Math.PI / 180, 34.2520736 * Math.PI / 180, 283.0));
        url2Locations.put("http://opensensorhub.org", new Vect3d(-86.2012018 * Math.PI / 180, 34.2520221 * Math.PI / 180, 283.0));
        url2Locations.put("http://cardbeacon", new Vect3d(-86.2012561 * Math.PI / 180, 34.2520761 * Math.PI / 180, 283.0));
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

        Beacon[] bArr = beaconMap.values().toArray(new Beacon[0]);
        if (bArr.length >= 3)
            locOutput.sendMeasurement(determineLocation(), bArr);   // Put better checks in place after testing
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

    private double[] determineLocation() {
        /*if (closestBeacons.isEmpty()) {
            return;
        } else if (closestBeacons.size() < 3) {
            closestBeacons.get(0).getDistance();*/

        if (beaconMap.size() == 3) {
            double[] distances = new double[3];
            Vect3d[] locations = new Vect3d[3];
            double[][] locationArr = new double[3][3];
            GeoTransforms geoTransforms = new GeoTransforms();
            int i = 0;
            for (Beacon beacon : beaconMap.values()) {
                locations[i] = url2Locations.get(UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray()));
                Vect3d tempVec = new Vect3d();
                geoTransforms.LLAtoECEF(locations[i], tempVec);

                locations[i] = tempVec;
                locationArr[i] = new double[]{locations[i].y, locations[i].x, locations[i].z};
                distances[i] = beacon.getDistance();
                i++;
            }

            double[] estLocation = AdaptedCellTrilateration.trackPhone(locationArr, distances);
            Vect3d estECEF = new Vect3d(estLocation[0], estLocation[1], locations[0].z);
            Vect3d estLLA = new Vect3d();
            geoTransforms.ECEFtoLLA(estECEF, estLLA);
            Log.d(TAG, "determineLocation: " + estLocation[0] + "," + estLocation[1]);
            return new double[]{estLLA.y * 180 / Math.PI, estLLA.x * 180 / Math.PI, estLLA.z};
        }
        return new double[]{0, 0, 0}; // Todo: add better handling of this case
    }

    private Vect3d MatrixVectorProduct(Mat3d mat, Vect3d vec) {
        Vect3d resultVector = new Vect3d();

        resultVector.x = mat.m00 * vec.x + mat.m01 * vec.x + mat.m02 * vec.x;
        resultVector.y = mat.m10 * vec.y + mat.m11 * vec.y + mat.m12 * vec.y;
        resultVector.z = mat.m20 * vec.z + mat.m21 * vec.z + mat.m22 * vec.z;

        return resultVector;
    }

    public double[] getBeaconLocation(Beacon beacon) {
        Vect3d locationVec = url2Locations.get(UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray()));
        return new double[]{locationVec.y * 180 / Math.PI, locationVec.x * 180 / Math.PI, locationVec.z};
    }
}
