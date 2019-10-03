package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.HandlerThread;
import android.provider.Settings;

import net.opengis.sensorml.v20.PhysicalComponent;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BLEBeaconDriver extends AbstractSensorModule<BLEBeaconConfig> {
    private static final Logger log = LoggerFactory.getLogger(BLEBeaconDriver.class.getSimpleName());
    public static final String LOCAL_REF_FRAME = "LOCAL_FRAME";

    String localFrameURI;
    HandlerThread eventThread;
    SensorManager sensorManager;
    LocationManager locationManager;
//    SensorMLBuilder sensorMLBuilder;
    List<PhysicalComponent> smlComponents;
    BLEBeaconRawOutput rawOutput;

    public BLEBeaconDriver(){}

    @Override
    public synchronized void init() throws SensorHubException{
        Context androidContext  =  config.androidContext;

        String deviceID = Settings.Secure.getString(androidContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.xmlID = "ANDROID_SENSORS_" + Build.SERIAL;     // Deprecated in API level 26
        this.uniqueID = "urn:android:device:" + deviceID;
        this.localFrameURI = this.uniqueID + "#" + LOCAL_REF_FRAME;

        rawOutput = new BLEBeaconRawOutput(this);
        addOutput(rawOutput, false);
    }

    @Override
    public void start() throws SensorHubException {
        // start the BLEBeacon Manager scanning

    }

    @Override
    public void stop() throws SensorHubException {

    }

    protected void useSensor(ISensorDataInterface output, Sensor sensor){
        addOutput(output, false);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Logger getLogger(){return log;}
}
