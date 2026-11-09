package org.sensorhub.impl.sensor.meshtastic;

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;

public class MeshtasticDescriptor implements IModuleProvider {
    @Override
    public String getModuleName() {
        return "Meshtastic Driver";
    }

    @Override
    public String getModuleDescription() {
        return "Driver Meshtastic Node connected via BLE";
    }

    @Override
    public String getModuleVersion() {
        return "1.0.0";
    }

    @Override
    public String getProviderName() {
        return "Botts Innovative Research, Inc.";
    }

    @Override
    public Class<? extends IModule<?>> getModuleClass() {
        return MeshtasticSensor.class;
    }

    @Override
    public Class<? extends ModuleConfig> getModuleConfigClass() {
        return MeshtasticConfig.class;
    }
}
