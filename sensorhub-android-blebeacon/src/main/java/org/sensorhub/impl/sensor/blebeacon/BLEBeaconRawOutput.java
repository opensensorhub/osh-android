package org.sensorhub.impl.sensor.blebeacon;

import android.hardware.SensorEventListener;
import android.os.Handler;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

public class BLEBeaconRawOutput extends AbstractSensorOutput<BLEBeaconDriver> implements  {
    private static final String BLE_BEACON_DEF = "http://sensorml.com/ont/swe/property/BLEBeacon";
    private static final String URL_DEF = "http://sensorml.com/ont/swe/property/";

    String name = "BLE Beacon Raw Data";
    boolean enabled;
    DataComponent bleData;
    DataEncoding dataEncoding;
    double samplingPeriod;
    long systemTimeOffset = -1L;
    BLEBeacon bleBeacon;


    protected BLEBeaconRawOutput(BLEBeaconDriver parent) {
        super(parent);

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

        // BLEBeacon Initialization
        bleBeacon = new BLEBeacon(parent.getConfiguration().androidContext);
    }

    private void sendMeasurement(){
        // Get the data from the beacon manager
        // Build the DataBlock
        DataBlock dataBlock = bleData.createDataBlock();
//        dataBlock.setDoubleValue(0, time);

        // Push Data
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, BLEBeaconRawOutput.this, latestRecord));
    }

    public void start(Handler handler){
        bleBeacon.BeaconManagerSetup();
    }

    public void stop(){
        bleBeacon.unbind();
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
}
