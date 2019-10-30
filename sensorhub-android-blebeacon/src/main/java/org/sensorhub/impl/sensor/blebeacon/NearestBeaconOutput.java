package org.sensorhub.impl.sensor.blebeacon;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Vector;

import org.altbeacon.beacon.Beacon;
import org.sensorhub.algo.vecmath.Vect3d;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.helper.GeoPosHelper;

public class NearestBeaconOutput extends AbstractSensorOutput<BLEBeaconDriver> {
    private static final String URL_DEF = "http://sensorml.com/ont/swe/property/";

    String name;
    DataComponent posDataStruct;
    //    DataComponent beaconDataStruct;
    DataEncoding dataEncoding;


    protected NearestBeaconOutput(BLEBeaconDriver parentModule) {
        super(parentModule);
        this.name = parentModule.getName() + " Nearest Beacon";

        // output structure (time + location)
        GeoPosHelper fac = new GeoPosHelper();
        posDataStruct = fac.newDataRecord();
        posDataStruct.setName(getName());
        posDataStruct.setDefinition("http://sensorml.com/ont/swe/property/NearestBeacon");

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

        posDataStruct.addComponent("time", fac.newTimeStampIsoUTC());
        posDataStruct.addComponent("nearest_beacon", nearBeacon);

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
        return 0;
    }

    protected void sendMeasurement(Beacon beacon, Vect3d location) {
        DataBlock dataBlock = posDataStruct.createDataBlock();
        double sampleTime = System.currentTimeMillis() / 1000;

        dataBlock.setDoubleValue(0, sampleTime);
        dataBlock.setDoubleValue(1, location.x);
        dataBlock.setDoubleValue(2, location.y);
        dataBlock.setDoubleValue(3, location.z);
        dataBlock.setDoubleValue(7, beacon.getDistance());

        // update latest record and send event
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, NearestBeaconOutput.this, dataBlock));
    }
}
