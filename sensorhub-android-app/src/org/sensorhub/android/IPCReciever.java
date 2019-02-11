package org.sensorhub.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.vast.ows.OWSRequest;
import org.vast.ows.sos.InsertSensorRequest;

public class IPCReciever extends BroadcastReceiver {
    private static final String TAG = "MyBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        String origin = extras.getString("src");
        String payload = extras.getString("SOS");

        String log;
        log = origin;
        Log.d(TAG, '\n'+log);
        log = payload;
        Log.d(TAG, '\n'+log);
    }
}
