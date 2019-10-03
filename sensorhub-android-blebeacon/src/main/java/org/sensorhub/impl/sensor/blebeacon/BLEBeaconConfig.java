package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;

import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.api.sensor.SensorConfig;

public class BLEBeaconConfig extends SensorConfig {
    public boolean activateBLEBeacon = false;

    public String deviceName;
    public String runName;
    public String runDescription;

    public transient Context androidContext;

    public BLEBeaconConfig(){
        this.moduleClass = BLEBeaconDriver.class.getCanonicalName();
    }

    // clone disabled as it causes crashes for now
    @Override
    public ModuleConfig clone(){
        return this;
    }
}
