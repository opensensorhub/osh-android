package org.sensorhub.impl.sensor.blebeacon;

import android.content.Context;

import com.google.gson.annotations.SerializedName;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.api.sensor.SensorConfig;

public class BLEBeaconConfig extends SensorConfig {
    public boolean activateBLEBeacon = false;

    public String deviceName;
    public String runName;
    public String runDescription;

    public transient Context androidContext;

    @DisplayInfo(label = "Clamp to Nearest", desc = "Shows location as the the Nearest Beacon")
    @SerializedName(value = "clampToNearest", alternate = {"enabled"})
    public boolean clampToNearest;

    public BLEBeaconConfig(){
        this.moduleClass = BLEBeaconDriver.class.getCanonicalName();
        this.clampToNearest = true;
    }

    // clone disabled as it causes crashes for now
    @Override
    public ModuleConfig clone(){
        return this;
    }


}
