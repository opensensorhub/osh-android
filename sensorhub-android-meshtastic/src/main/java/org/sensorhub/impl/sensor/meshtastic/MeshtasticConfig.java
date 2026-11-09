package org.sensorhub.impl.sensor.meshtastic;

import android.content.Context;
import android.provider.Settings;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorConfig;

public class MeshtasticConfig extends SensorConfig {
    public MeshtasticConfig() {
        this.moduleClass = MeshtasticSensor.class.getCanonicalName();
    }

    public String device_name;
    public String uid_extension;

    public static String getUid() {
        Context context = SensorHubService.getContext();
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public String getUidWithExt()
    {
        String baseUid = getUid();
        if (uid_extension != null && !uid_extension.isEmpty())
            return baseUid + ":" + uid_extension;
        return baseUid;
    }

}
