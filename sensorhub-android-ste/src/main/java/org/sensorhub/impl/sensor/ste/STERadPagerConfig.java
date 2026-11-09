package org.sensorhub.impl.sensor.ste;

import android.content.Context;
import android.provider.Settings;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorConfig;

public class STERadPagerConfig extends SensorConfig {
    public STERadPagerConfig() {
        this.moduleClass = STERadPager.class.getCanonicalName();
    }

    public static String getUid() {
        Context context = SensorHubService.getContext();
        return "urn:android:ste:radpager:" + Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}
