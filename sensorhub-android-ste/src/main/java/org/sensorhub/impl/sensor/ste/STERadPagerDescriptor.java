package org.sensorhub.impl.sensor.ste;

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.module.JarModuleProvider;

public class STERadPagerDescriptor implements IModuleProvider {
    @Override
    public String getModuleName() {
        return "STE Rad Pager Driver";
    }

    @Override
    public String getModuleDescription() {
        return "Driver STE Rad Pager conected via BLE";
    }

    @Override
    public String getModuleVersion() {
        return "0.1";
    }

    @Override
    public String getProviderName() {
        return "Botts Innovative Research, Inc.";
    }

    @Override
    public Class<? extends IModule<?>> getModuleClass() {
        return STERadPager.class;
    }

    @Override
    public Class<? extends ModuleConfig> getModuleConfigClass() {
        return STERadPagerConfig.class;
    }
}
