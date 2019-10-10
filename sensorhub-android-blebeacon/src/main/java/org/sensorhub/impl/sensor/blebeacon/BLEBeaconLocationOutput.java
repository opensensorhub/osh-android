package org.sensorhub.impl.sensor.blebeacon;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.Time;
import net.opengis.swe.v20.Vector;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.helper.GeoPosHelper;

public class BLEBeaconLocationOutput extends AbstractSensorOutput<BLEBeaconDriver> {
    String name;
    DataComponent posDataStruct;
    DataEncoding posEncoding;

    protected BLEBeaconLocationOutput(BLEBeaconDriver parentModule) {
        super(parentModule);
        this.name = parentModule.getName() + "_location_data";

        // output structure (time + location)
        GeoPosHelper fac = new GeoPosHelper();
       posDataStruct = fac.newDataRecord();
       posDataStruct.setName(getName());
       posDataStruct.setDefinition("http://sensorml.com/ont/swe/property/BLEBeaconLocation");

       // add fields
        Time time = fac.newTimeStampIsoUTC();
        Vector vec = fac.newLocationVectorLLA(null);
        vec.setLocalFrame(parentSensor.localFrameURI);

        posDataStruct.addComponent("time", time);
        posDataStruct.addComponent("location", vec);

        // output encoding
        posEncoding = fac.newTextEncoding(",", "\n");
    }

    protected void start(){}

    @Override
    public String getName(){
        return this.name;
    }

    @Override
    public DataComponent getRecordDescription() {
        return posDataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return posEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 0;
    }

    protected void sendMeasurement(Beacon beacon) {
        DataBlock dataBlock = posDataStruct.createDataBlock();
        double sampleTime = System.currentTimeMillis();

        if (UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray()).equals("http://cardbeacon")) {
            dataBlock.setDoubleValue(0, sampleTime);
            dataBlock.setDoubleValue(1, 0.0);
            dataBlock.setDoubleValue(2, 0.0);
            dataBlock.setDoubleValue(3, 283.0);
        } else {
            dataBlock.setDoubleValue(0, sampleTime);
            dataBlock.setDoubleValue(1, 0.0);
            dataBlock.setDoubleValue(2, 0.0);
            dataBlock.setDoubleValue(3, 283.0);
        }

        // update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, BLEBeaconLocationOutput.this, dataBlock));
    }
}
