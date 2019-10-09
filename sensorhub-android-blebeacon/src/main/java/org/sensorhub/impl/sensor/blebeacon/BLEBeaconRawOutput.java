package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
import android.util.Log;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

import java.util.Collection;

public class BLEBeaconRawOutput extends AbstractSensorOutput<BLEBeaconDriver>{
    private static final String BLE_BEACON_DEF = "http://sensorml.com/ont/swe/property/BLEBeacon";
    private static final String URL_DEF = "http://sensorml.com/ont/swe/property/";
    private static final String TAG = "BLEBeaconOutputRaw";

    String name = "BLE Beacon Raw Data";
    boolean enabled;
    DataComponent bleData;
    DataEncoding dataEncoding;
    double samplingPeriod;
    long systemTimeOffset = -1L;

    BeaconManager mBeaconManager;
//    private Map<Identifier, Beacon> beacons;


    protected BLEBeaconRawOutput(BLEBeaconDriver parent) {
        super(parent);
//        beacons = Collections.<Identifier, Beacon>emptyMap();

        // create output structure
        SWEHelper fac = new SWEHelper();
        bleData = fac.newDataRecord();
        bleData.setName(getName());
        bleData.setDefinition(BLE_BEACON_DEF);
        bleData.setDescription("Bluetooth Low Energy Beacon readings for commonly available data");

        // add fields
        bleData.addComponent("time", fac.newTimeStampIsoUTC());
        bleData.addComponent("id", fac.newCategory("http://sensorml.com/ont/swe/property/SensorID", null, null, null));
        bleData.addComponent("name", fac.newText(URL_DEF + "name", null, null));
        bleData.addComponent("ID1", fac.newText(URL_DEF + "Identifier_1", null, "First of the three ids used by most BLE beacons, in ES-URL, the only id"));
        bleData.addComponent("ID2", fac.newText(URL_DEF + "Identifier_2", null, null));
        bleData.addComponent("ID3", fac.newText(URL_DEF + "Identifier_3", null, null));
        bleData.addComponent("txPower", fac.newQuantity(URL_DEF + "txPower", null, null, "dBm"));
        bleData.addComponent("RSSI", fac.newQuantity(URL_DEF + "rssi", null, null, "dBm"));     // Is this value measured, and is it affected by the settings in the beacon
        bleData.addComponent("distance", fac.newQuantity(URL_DEF + "distance", null, null, "m"));

        dataEncoding = fac.newTextEncoding(",", "\n");

        // Other fields that can be added
        // Extra Data Fields
        // Manufacturer
        // Service UUID
        // MultiFrameBeacon
        // Parser Identifier // Note: this is part of ABL, not the beacon itself
    }

    public void start() {
        // BLE Beacon Initialization
//        mBeaconManager = BeaconManager.getInstanceForApplication(getParentModule().getConfiguration().androidContext);
//        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
//        mBeaconManager.bind(this);
    }

    public void stop() {
//        try {
//            mBeaconManager.stopRangingBeaconsInRegion(new Region("url-beacons-region", null, null, null));
//        } catch (RemoteException e) {
//            Log.d(TAG, "unbind: " + e);
//        }
//
//        mBeaconManager.unbind(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DataComponent getRecordDescription() {
        return bleData;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 0;   // TODO: implement a calculation for this
    }

    /*// BLE Beacon Consumer/Range Notifier Require Implementations
    @Override
    public void onBeaconServiceConnect() {
        Region region = new Region("all-beacons-region", null, null, null);
        try {
            mBeaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        mBeaconManager.setRangeNotifier(this);
    }

    @Override
    public Context getApplicationContext() {
        return getParentModule().getConfiguration().androidContext;
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
                Log.d(TAG, "Beacon ID: " + beacon.getId1() + "Beacon URL: " + url +
                        " approximately " + beacon.getDistance() + " meters away.");
                // TODO: Need to improve this to handle non-EsURL beacons that have info in the other ID slots
//                this.beacons.put(beacon.getId1(), beacon);
                sendBeaconRecord(beacon);
            }
        }
    }*/

    public void sendBeaconRecord(Beacon beacon) {
        Log.d(TAG, "sendBeaconRecord");
        double time = System.currentTimeMillis() / 1000.;
        DataBlock dataBlock = bleData.createDataBlock();

        dataBlock.setDoubleValue(0, time);
        dataBlock.setStringValue(1, "test-ble-beacon-id");
        dataBlock.setStringValue(2, beacon.getBluetoothName());
        dataBlock.setStringValue(3, beacon.getId1().toString());
       /* if (beacon.getId2() != null) {
            dataBlock.setStringValue(4, beacon.getId2().toString());
        } else {
            dataBlock.setStringValue(4, "NONE");
        }
        if (beacon.getId3() != null) {
            dataBlock.setStringValue(5, beacon.getId3().toString());
        } else {
            dataBlock.setStringValue(5, "NONE");
        }*/
        dataBlock.setStringValue(4, "NONE");
        dataBlock.setStringValue(5, "NONE");
        dataBlock.setDoubleValue(6, beacon.getTxPower());
        dataBlock.setDoubleValue(7, beacon.getRssi());
        dataBlock.setDoubleValue(8, beacon.getDistance());

        // Push the data
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, BLEBeaconRawOutput.this, dataBlock));
    }
}
