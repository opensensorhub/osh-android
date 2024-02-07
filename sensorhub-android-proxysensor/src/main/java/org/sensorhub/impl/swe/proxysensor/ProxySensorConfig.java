package org.sensorhub.impl.swe.proxysensor;

import android.content.Context;

import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.sensor.swe.SWEVirtualSensorConfig;

public class ProxySensorConfig extends SWEVirtualSensorConfig
{
    public transient Context androidContext;

    public ProxySensorConfig()
    {
        super();
        this.moduleClass = ProxySensor.class.getCanonicalName();
    }

    @Override
    public ModuleConfig clone()
    {
        return this; // disable clone for now as it crashes Android app
    }
}
