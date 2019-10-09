package org.sensorhub.impl.sensor.blebeacon;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Time;
import net.opengis.swe.v20.Vector;

import org.altbeacon.beacon.Beacon;
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
        posDataStruct = fac.newDataRecord(2);
        posDataStruct.setName(getName());
        Time time = fac.newTimeStampIsoUTC();
        posDataStruct.addComponent("time", time);
        Vector vec = fac.newLocationVectorLLA(null);
        ((Vector) vec).setLocalFrame(parentSensor.localFrameURI);
        posDataStruct.addComponent("location", vec);

        // output encoding
        posEncoding = fac.newTextEncoding(",", "\n");
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

    protected void sendMeasurement(){

        double sampleTime = System.currentTimeMillis();

        // build and populate datablock
        DataBlock dataBlock = posDataStruct.createDataBlock();
        dataBlock.setDoubleValue(0, sampleTime);
//        dataBlock.setDoubleValue(1, location.getLatitude());
//        dataBlock.setDoubleValue(2, location.getLongitude());
//        dataBlock.setDoubleValue(3, location.getAltitude());

        // update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, this, dataBlock));
    }
}
