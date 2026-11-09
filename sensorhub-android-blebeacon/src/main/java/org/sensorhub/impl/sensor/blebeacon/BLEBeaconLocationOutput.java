package org.sensorhub.impl.sensor.blebeacon;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Vector;

import org.altbeacon.beacon.Beacon;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.helper.GeoPosHelper;

public class BLEBeaconLocationOutput extends AbstractSensorOutput<BLEBeaconDriver> {

    private static final String ACTION_SUBMIT_TRACK_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_TRACK";

    private static final String URL_DEF = "http://sensorml.com/ont/swe/property/";

    String name;
    DataComponent posDataStruct;
//    DataComponent beaconDataStruct;
    DataEncoding dataEncoding;


    protected BLEBeaconLocationOutput(BLEBeaconDriver parentModule) {
        super(parentModule.getName() + " Location", parentModule);
        this.name = parentModule.getName() + " Location";

        // output structure (time + location)
        GeoPosHelper fac = new GeoPosHelper();
        posDataStruct = fac.newDataRecord();
        posDataStruct.setName(getName());
        posDataStruct.setDefinition("http://sensorml.com/ont/swe/property/BLEBeaconLocation");

        // detected beacons structure


        // add fields
        Vector vec = fac.newLocationVectorLLA(null);
        vec.setLocalFrame(parentSensor.localFrameURI);

        Vector nearBeacon = fac.newVector(URL_DEF + "nearest_beacon",
                null,
                new String[]{"lat", "lon", "alt", "dist"},
                new String[]{"Geodetic Latitude", "Longitude", "Altitude", "Distance"},
                new String[] {"deg", "deg", "m", "m"},
                new String[] {"Lat", "Long", "h", "dist"});

        Vector beaconVec = fac.newVector(URL_DEF + "beacons_data",
                null,
                new String[]{"lat", "lon", "alt", "dist"},
                new String[]{"Geodetic Latitude", "Longitude", "Altitude", "Distance"},
                new String[] {"deg", "deg", "m", "m"},
                new String[] {"Lat", "Long", "h", "dist"});

//        Vector vec1 = fac.newLocationVectorLLA(URL_DEF + "beacon_location");
//        Vector vec2 = fac.newLocationVectorLLA(URL_DEF + "b2_location");
//        Vector vec3 = fac.newLocationVectorLLA(URL_DEF + "b3_location");
//
//        vec1.setLocalFrame(parentSensor.localFrameURI);
//        vec2.setLocalFrame(parentSensor.localFrameURI);
//        vec3.setLocalFrame(parentSensor.localFrameURI);

        posDataStruct.addComponent("time", fac.newTimeStampIsoUTC());
        posDataStruct.addComponent("est_location", vec);
        posDataStruct.addComponent("nearest_beacon", nearBeacon);

        // TODO: maybe there's a better way to present this data... TESTING new approach
        posDataStruct.addComponent("beacon_1", beaconVec);
//        posDataStruct.addComponent("beacon_1_range", fac.newQuantity(URL_DEF + "b1_distance", null, null, "m"));
        posDataStruct.addComponent("beacon_2", beaconVec);
//        posDataStruct.addComponent("beacon_2_range", fac.newQuantity(URL_DEF + "b2_distance", null, null, "m"));
        posDataStruct.addComponent("beacon_3", beaconVec);
//        posDataStruct.addComponent("beacon_3_range", fac.newQuantity(URL_DEF + "b3_distance", null, null, "m"));

        // output encoding
        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    protected void start() {
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public DataComponent getRecordDescription() {
        return posDataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 1.0;
    }

    protected void sendMeasurement(double[] estLocation, Beacon[] beacons) {

        DataBlock dataBlock = posDataStruct.createDataBlock();
        double sampleTime = System.currentTimeMillis() / 1000;
        // TODO: Handle null or {0,0,0} better
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
//        dataBlock.setDoubleValue(7, beacons[0].getDistance());
        dataBlock.setDoubleValue(7, parentSensor.getBeaconDistance(beacons[0]));

        dataBlock.setDoubleValue(8, b2Loc[0]);
        dataBlock.setDoubleValue(9, b2Loc[1]);
        dataBlock.setDoubleValue(10, b2Loc[2]);
//        dataBlock.setDoubleValue(11, beacons[1].getDistance());
        dataBlock.setDoubleValue(11, parentSensor.getBeaconDistance(beacons[1]));

        dataBlock.setDoubleValue(12, b3Loc[0]);
        dataBlock.setDoubleValue(13, b3Loc[1]);
        dataBlock.setDoubleValue(14, b3Loc[2]);
//        dataBlock.setDoubleValue(15, beacons[2].getDistance());
        dataBlock.setDoubleValue(15, parentSensor.getBeaconDistance(beacons[2]));

        // update latest record and send event
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, BLEBeaconLocationOutput.this, dataBlock));
    }
}
