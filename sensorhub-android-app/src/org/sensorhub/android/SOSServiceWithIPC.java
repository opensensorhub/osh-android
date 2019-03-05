package org.sensorhub.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.service.sos.SOSService;
import org.vast.ows.OWSException;
import org.vast.ows.OWSRequest;
import org.vast.ows.OWSUtils;
import org.vast.xml.DOMHelper;
import org.vast.xml.DOMHelperException;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static android.content.ContentValues.TAG;

class SOSServiceWithIPC extends SOSService {
    @Override
    public void start() throws SensorHubException
    {
        super.start();
        // FUTURE: If we don't want to use HTTPServlet don't call this.
        // Could be set as a preference in the sharedPreference

        Context androidContext = config.androidContext;

        BroadcastReceiver receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                StringBuilder sb = new StringBuilder();

                sb.append('\n');
                sb.append("Action: " + intent.getAction());
                sb.append('\n');
                sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME));
                sb.append('\n');

                String sosPayload = intent.getStringExtra("SOS");
                ipcHandler(sosPayload);

                String log = sb.toString();
                Log.d(TAG, log);
                Toast.makeText(context, log, Toast.LENGTH_LONG).show();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("org.sofwerx.ogc.ACTION_SOS");
        androidContext.registerReceiver(receiver, filter);
    }

    private void ipcHandler(String body) {
        OWSUtils owsUtils = new OWSUtils();

        ByteArrayInputStream is = new ByteArrayInputStream(body.getBytes());

        try {
            DOMHelper dom = new DOMHelper(is, false);
            Element requestElt = dom.getBaseElement();
            OWSRequest req = owsUtils.readXMLQuery(dom, requestElt);
            // Subclass of OWSRequest (InsertSensor Object)

            // process request and get response
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            req.setResponseStream(os);
            servlet.handleRequest(req);

            // send response
            // convert back outputstream 'os'  into something you can broadcast via IPC
        } catch (DOMHelperException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (OWSException e) {
            e.printStackTrace();
        }
    }
}
