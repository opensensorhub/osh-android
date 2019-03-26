package org.sensorhub.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

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

public class SOSServiceWithIPC extends SOSService
{
    public static final String ACTION_SOS = "org.sofwerx.ogc.ACTION_SOS";
    private static final String EXTRA_PAYLOAD = "SOS";
    private static final String EXTRA_ORIGIN = "src";

    @Override
    public void start() throws SensorHubException
    {
        super.start();
        // FUTURE: If we don't want to use HTTPServlet don't call this.
        // Could be set as a preference in the sharedPreference

        Context androidContext = ((SOSServiceWithIPCConfig) config).androidContext;

        BroadcastReceiver receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String sosPayload = intent.getStringExtra(EXTRA_PAYLOAD);
                ipcHandler(sosPayload);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SOS);
        androidContext.registerReceiver(receiver, filter);
    }

    private void ipcHandler(String body)
    {
        OWSUtils owsUtils = new OWSUtils();

        ByteArrayInputStream is = new ByteArrayInputStream(body.getBytes());

        try {
            DOMHelper dom = new DOMHelper(is, false);
            Element requestElt = dom.getBaseElement();
            OWSRequest request = owsUtils.readXMLQuery(dom, requestElt);
            // Subclass of OWSRequest (InsertSensor Object)

            // process request and get response
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            request.setResponseStream(responseStream);
            servlet.handleRequest(request);

            // send response
            // convert back outputstream 'os'  into something you can broadcast via IPC
            String responsePayload = responseStream.toString();
        }
        catch (DOMHelperException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (OWSException e)
        {
            e.printStackTrace();
        }
    }
}

