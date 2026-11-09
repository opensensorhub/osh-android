package org.sensorhub.android;

import android.content.Context;

import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.service.sos.SOSServiceConfig;

public class SOSServiceWithIPCConfig extends SOSServiceConfig
{
    public transient Context androidContext;

    public SOSServiceWithIPCConfig()
    {
        super();
    }

    @Override
    public ModuleConfig clone()
    {
        return this; // disable clone for now as it crashes Android app
    }
}
