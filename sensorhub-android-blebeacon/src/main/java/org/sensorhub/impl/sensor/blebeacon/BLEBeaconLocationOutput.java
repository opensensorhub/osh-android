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
    private static final String URL_DEF = "http://sensorml.com/ont/swe/property/";

    String name;
    DataComponent posDataStruct;
    DataEncoding posEncoding;

    protected BLEBeaconLocationOutput(BLEBeaconDriver parentModule) {
        super(parentModule);
        this.name = parentModule.getName() + " Location";

        // output structure (time + location)
        GeoPosHelper fac = new GeoPosHelper();
       posDataStruct = fac.newDataRecord();
       posDataStruct.setName(getName());
       posDataStruct.setDefinition("http://sensorml.com/ont/swe/property/BLEBeaconLocation");

       // add fields
        Vector vec = fac.newLocationVectorLLA(null);
        vec.setLocalFrame(parentSensor.localFrameURI);

        posDataStruct.addComponent("time", fac.newTimeStampIsoUTC());
        posDataStruct.addComponent("Estimated Location", vec);
        posDataStruct.addComponent("Beacon 1 Location", vec);
        posDataStruct.addComponent("Beacon 1 Range", fac.newQuantity(URL_DEF + "distance", null, null, "m"));
        posDataStruct.addComponent("Beacon 2 Location", vec);
        posDataStruct.addComponent("Beacon 2 Range", fac.newQuantity(URL_DEF + "distance", null, null, "m"));
        posDataStruct.addComponent("Beacon 3 Location", vec);
        posDataStruct.addComponent("Beacon 3 Range", fac.newQuantity(URL_DEF + "distance", null, null, "m"));

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

    protected void sendMeasurement(double[] estLocation, Beacon[] beacons) {
        DataBlock dataBlock = posDataStruct.createDataBlock();
        double sampleTime = System.currentTimeMillis()/1000;
        double[] b1Loc = parentSensor.getBeaconLocation(beacons[0]);
        double[] b2Loc = parentSensor.getBeaconLocation(beacons[1]);
        double[] b3Loc = parentSensor.getBeaconLocation(beacons[2]);

        dataBlock.setDoubleValue(0, sampleTime);
        dataBlock.setDoubleValue(1, estLocation[0]);
        dataBlock.setDoubleValue(2, estLocation[1]);
        dataBlock.setDoubleValue(3, estLocation[2]);

        dataBlock.setDoubleValue(4, b1Loc[0]);
        dataBlock.setDoubleValue(5, b1Loc[1]);
        dataBlock.setDoubleValue(6, b1Loc[2]);
        dataBlock.setDoubleValue(7, beacons[0].getDistance());

        dataBlock.setDoubleValue(8, b2Loc[0]);
        dataBlock.setDoubleValue(9, b2Loc[1]);
        dataBlock.setDoubleValue(10, b2Loc[2]);
        dataBlock.setDoubleValue(11, beacons[1].getDistance());

        dataBlock.setDoubleValue(12, b3Loc[0]);
        dataBlock.setDoubleValue(13, b3Loc[1]);
        dataBlock.setDoubleValue(14, b3Loc[2]);
        dataBlock.setDoubleValue(15, beacons[2].getDistance());
        
        // update latest record and send event
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, BLEBeaconLocationOutput.this, dataBlock));
    }
}
