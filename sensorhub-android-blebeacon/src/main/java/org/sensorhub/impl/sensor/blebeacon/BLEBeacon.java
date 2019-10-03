package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BLEBeacon implements BeaconConsumer, RangeNotifier {
    private Context parentContext;
    private String TAG = "BLEBeacon-DeafultTAG";
    private BeaconManager mBeaconManager;
    private Map<Identifier, Beacon> beacons;

    public BLEBeacon(Context context){
        this.parentContext = context;
        this.beacons = new HashMap<Identifier, Beacon>();
    }

    public void BeaconManagerSetup(){
        mBeaconManager = BeaconManager.getInstanceForApplication(parentContext);
        // Detect the URL frame:
        mBeaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
        mBeaconManager.bind(this);
    }

    public void unbind(){
        try {
            mBeaconManager.stopRangingBeaconsInRegion(new Region("all-beacons-region", null, null, null));
        } catch (RemoteException e){
            Log.d(TAG, "unbind: " + e);
        }

        mBeaconManager.unbind(this);
    }

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
        return this.parentContext;
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
        for (Beacon beacon: beacons) {
            if (beacon.getServiceUuid() == 0xfeaa && beacon.getBeaconTypeCode() == 0x10) {
                // This is an Eddystone-URL frame
                String url = UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray());
                Log.d(TAG, "Beacon ID: "+ beacon.getId1() + "Beacon URL: " + url +
                        " approximately " + beacon.getDistance() + " meters away.");
                // TODO: Need to improve this to handle non-EsURL beacons that have info in the other ID slots
                this.beacons.put(beacon.getId1(), beacon);
            }
        }
    }

    public void printBeaconList(){
        Log.d(TAG, "printBeaconList: " + this.beacons);
    }


}
