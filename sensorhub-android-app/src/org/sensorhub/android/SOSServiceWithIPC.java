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

import static android.content.ContentValues.TAG;

public class SOSServiceWithIPC extends SOSService
{
    public static final String SQAN_TEST = "SA";
    private static final String SQAN_EXTRA = "channel";
    public static final String ACTION_SOS = "org.sofwerx.ogc.ACTION_SOS";
    private static final String EXTRA_PAYLOAD = "SOS";
    private static final String EXTRA_ORIGIN = "src";
    private Context androidContext;

    @Override
    public void start() throws SensorHubException
    {
        super.start();
        androidContext = ((SOSServiceWithIPCConfig) config).androidContext;

        BroadcastReceiver receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String origin = intent.getStringExtra(EXTRA_ORIGIN);
                if (!context.getPackageName().equalsIgnoreCase(origin))
                {
                    String requestPayload = intent.getStringExtra(EXTRA_PAYLOAD);
                    handleIPCRequest(requestPayload);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SOS);

        androidContext.registerReceiver(receiver, filter);
    }

    private void handleIPCRequest(String body)
    {
        OWSUtils owsUtils = new OWSUtils();
        ByteArrayInputStream is = new ByteArrayInputStream(body.getBytes());

        try {
            DOMHelper dom = new DOMHelper(is, false);
            Element requestElt = dom.getBaseElement();
            OWSRequest request = owsUtils.readXMLQuery(dom, requestElt);

            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            request.setResponseStream(responseStream);
            servlet.handleRequest(request);

            /**
             * request are small usually, but responses can be really large. There is a limit to the size of response
             */
            String responsePayload = responseStream.toString();
            Intent responseIntent = new Intent();
            responseIntent.setAction(ACTION_SOS);
            responseIntent.putExtra(EXTRA_ORIGIN, androidContext.getPackageName());
            responseIntent.putExtra(EXTRA_PAYLOAD, responsePayload);

            androidContext.sendBroadcast(responseIntent);
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
        // OGCException e
        /**
         * TODO: Look how server is handling the this exception
         */
    }
}

