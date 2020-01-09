package org.sensorhub.impl.sensor.blebeacon;

import android.content.Intent;
import android.util.Log;

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

    private static final String ACTION_SUBMIT_TRACK_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_TRACK";

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
        return 1.0;
    }

    protected void sendMeasurement(Beacon beacon) {

        DataBlock dataBlock = posDataStruct.createDataBlock();
        double sampleTime = System.currentTimeMillis() / 1000;
        double[] locationDecDeg = parentSensor.getBeaconLocation(beacon);

        Log.d("NearestBeaconOutput", "Publishing Intent");

        Intent submitReportIntent = new Intent(ACTION_SUBMIT_TRACK_REPORT);
        submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        submitReportIntent.putExtra("lat", locationDecDeg[0]);
        submitReportIntent.putExtra("lon", locationDecDeg[1]);
        double confidence = 0;

        if (parentSensor.getBeaconDistance(beacon) > 0) {

            confidence = (1 / parentSensor.getBeaconDistance(beacon));
        }

        submitReportIntent.putExtra("confidence", confidence);
        submitReportIntent.putExtra("resourceType", "device");
        submitReportIntent.putExtra("resourceId", parentSensor.getConfiguration().id);
        submitReportIntent.putExtra("resourceLabel", parentSensor.getConfiguration().name);
        submitReportIntent.putExtra("trackingMethod", "bt_beacon");
        parentSensor.getConfiguration().androidContext.sendBroadcast(submitReportIntent);

        Log.d("NearestBeaconOutput", "Published Intent");


        dataBlock.setDoubleValue(0, sampleTime);
        dataBlock.setDoubleValue(1, locationDecDeg[0]);
        dataBlock.setDoubleValue(2, locationDecDeg[1]);
        dataBlock.setDoubleValue(3, locationDecDeg[2]);
//        dataBlock.setDoubleValue(4, beacon.getDistance());
        dataBlock.setDoubleValue(4, parentSensor.getBeaconDistance(beacon));

        // update latest record and send event
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, NearestBeaconOutput.this, dataBlock));
    }
}
