package org.sensorhub.impl.sensor.swe.ProxySensor;

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.module.JarModuleProvider;

public class ProxySensorDescriptor extends JarModuleProvider implements IModuleProvider
{
    @Override
    public String getModuleName()
    {
        return "Proxy Sensor";
    }

    @Override
    public String getModuleDescription()
    {
        return "Altered SWEVirtualSensor with changes to event listeners implementation sending data.";
    }

    @Override
    public Class<? extends IModule<?>> getModuleClass()
    {
        return ProxySensor.class;
    }

    @Override
    public Class<? extends ModuleConfig> getModuleConfigClass()
    {
        return ProxySensorConfig.class;
    }
}
