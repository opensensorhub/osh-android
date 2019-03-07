package org.sensorhub.android;

import android.content.Context;

import org.sensorhub.impl.service.sos.SOSServiceConfig;

public class SOSServiceWithIPCConfig extends SOSServiceConfig
{
    public transient Context androidContext;

    public SOSServiceWithIPCConfig()
    {
        super();
    }
}
