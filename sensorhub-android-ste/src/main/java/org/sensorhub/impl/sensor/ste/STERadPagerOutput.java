package org.sensorhub.impl.sensor.ste;

import static org.sensorhub.impl.sensor.android.AndroidSensorsDriver.LOCAL_REF_FRAME;

import android.annotation.SuppressLint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Vector;

import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.GeoPosHelper;

public class STERadPagerOutput extends AbstractSensorOutput<STERadPager> implements LocationListener {
    private String localFrameURI;
    String name = "STE Rad Pager Data";
    DataComponent dataComponent;
    DataEncoding dataEncoding;
    LocationManager locManager;
    LocationProvider locProvider;
    Location lastLocation;
    double lastLocationTime;
    boolean enabled;

    protected STERadPagerOutput(STERadPager parent, LocationManager locManager, LocationProvider locProvider) {
        super("STE Rad Pager Data", parent);
        this.locManager = locManager;
        this.locProvider = locProvider;
        this.name = locProvider.getName().replaceAll(" ", "_") + "_data";
        this.localFrameURI = parent.getUniqueIdentifier() + "#" + LOCAL_REF_FRAME;
    }

    public void doInit() {
        SWEHelper fac = new SWEHelper();
        GeoPosHelper geoFac = new GeoPosHelper();
        Vector locVector = geoFac.newLocationVectorLLA(null);
        locVector.setLocalFrame(localFrameURI);

        dataComponent = fac.createRecord()
                .name(name)
                .label("STE Rad Pager Data")
                .definition("http://sensorml.com/ont/swe/property/STE_Rad_Pager_Data")
                .addField("time", fac.createTime().asSamplingTimeIsoUTC()
                        .label("Time Stamp")
                        .build())
                .addField("Counts", fac.createQuantity()
                        .label("Counts")
                        .definition("http://sensorml.com/ont/swe/property/Gamma_Counts")
                        .description("# of Gamma Detection Events measure by the rad sensing assembly every half second pre-scaled by 1/2")
                        .build())
                .addField("CPS", fac.createQuantity()
                        .label("Counts per Second")
                        .definition("http://sensorml.com/ont/swe/property/Gamma_CPS")
                        .description("Counts per Second calculated by the Counts Property * 4")
                        .build())
                .addField("Saturation", fac.createBoolean()
                        .label("Saturation")
                        .definition("http://sensorml.com/ont/swe/property/Rad_Saturation")
                        .description("If the Rad Sensor is saturated on the front end, invalidating the Counts value. USER SHOULD RETREAT IF THIS IS TRUE!!!")
                        .build())
                .addField("Threshold", fac.createQuantity()
                        .label("Threshold")
                        .definition("http://sensorml.com/ont/swe/property/Rad_Bkgd_Threshold")
                        .description("Measured background radiation threshold")
                        .build())
                .addField("Alarm Level Value", fac.createQuantity()
                        .label("Alarm Level - Value")
                        .definition("http://sensorml.com/ont/swe/property/Rad_Alarm_Level_Value")
                        .description("Alarm Level indicated")
                        .build())
                .addField("Alarm Level Exposure Rate", fac.createQuantity()
                        .label("Alarm Level - Exposure Rate")
                        .definition("http://sensorml.com/ont/swe/property/Rad_Alarm_Level_Exposure_Rate")
                        .description("Exposure Rate indicated")
                        .uom("uR/h")
                        .build())
                .addField("location", locVector)
                .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    @SuppressLint("MissingPermission")
    public void doStart(Handler handler) {
        locManager.requestLocationUpdates(locProvider.getName(), 1000, 0.0f, this, handler.getLooper());
    }

    @Override
    public DataComponent getRecordDescription() {
        return dataComponent;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 0;
    }

    public void insertSensorData(String[] data) {
        int alarm;
        boolean saturated;

        if (data[1].equals("!")) {
            saturated = true;
            alarm = 9;
        } else if (data[1].equals("")) {
            saturated = false;
            alarm = 0;
        } else {
            saturated = false;
            alarm = Integer.parseInt(data[1]);
        }

        int counts = Integer.parseInt(data[2]);
        int cps = counts * 4;
        int threshold = Integer.parseInt(data[3]);
        int exposureRate;
        switch (alarm) {
            case 0:
                exposureRate = 0;
                break;
            case 1:
                exposureRate = 10;
                break;
            case 2:
                exposureRate = 25;
                break;
            case 3:
                exposureRate = 60;
                break;
            case 4:
                exposureRate = 140;
                break;
            case 5:
                exposureRate = 300;
                break;
            case 6:
                exposureRate = 600;
                break;
            case 7:
                exposureRate = 1400;
                break;
            case 8:
                exposureRate = 3000;
                break;
            case 9:
                exposureRate = 6000;
                break;
            case -1:
                // Figure out how to communicate that this is bad
                exposureRate = -1;
                break;
            default:
                exposureRate = Integer.parseInt(null);
                break;
        }

        buildRecord(counts, cps, threshold, saturated, alarm, exposureRate);
    }

    private void buildRecord(int count, int cps, int threshold, boolean saturated, int alarmLevel, int exposureRate) {
        DataBlock dataBlock = dataComponent.createDataBlock();

        dataBlock.setDoubleValue(0, System.currentTimeMillis() / 1000);
        dataBlock.setDoubleValue(1, count);
        dataBlock.setDoubleValue(2, cps);
        dataBlock.setBooleanValue(3, saturated);
        dataBlock.setDoubleValue(4, threshold);
        dataBlock.setDoubleValue(5, alarmLevel);
        dataBlock.setDoubleValue(6, exposureRate);

        // if last location is less than 2 minutes old, use it
        if ((double) (System.currentTimeMillis() / 1000) - (lastLocationTime) < 120.0) {
            dataBlock.setDoubleValue(7, lastLocation.getLatitude());
            dataBlock.setDoubleValue(8, lastLocation.getLongitude());
            dataBlock.setDoubleValue(9, lastLocation.getAltitude());
        } else {
            log.debug("Location is too old, not using it");
//            dataBlock.setDoubleValue(7, null);
//            dataBlock.setDoubleValue(8, null);
//            dataBlock.setDoubleValue(9, null);
        }

        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
    }

    @Override
    public void onLocationChanged(Location location) {
        /*log.debug("Location received from " + getName() + ": "
                  + location.getLatitude() + ", " +
                  + location.getLongitude() + ", " +
                  + location.getAltitude()); */

        double sampleTime = location.getTime() / 1000.0;
        lastLocation = location;
        lastLocationTime = sampleTime;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }


    @Override
    public void onProviderEnabled(String provider) {
        enabled = true;
    }


    @Override
    public void onProviderDisabled(String provider) {
        enabled = false;
    }

}
