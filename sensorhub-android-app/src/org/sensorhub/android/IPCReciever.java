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
        StringBuilder sb = new StringBuilder();

        sb.append('\n');
        sb.append("Action: " + intent.getAction());
        sb.append('\n');
        sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME));
        sb.append('\n');

        String log = sb.toString();
        Log.d(TAG, log);
        Toast.makeText(context, log, Toast.LENGTH_LONG).show();
    }
}
