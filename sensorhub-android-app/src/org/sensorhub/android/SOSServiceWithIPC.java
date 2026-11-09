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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SOSServiceWithIPC extends SOSService
{
    private static final Logger log = LoggerFactory.getLogger(SOSServiceWithIPC.class);
    public static final String SQAN_TEST = "SA";
    private static final String SQAN_EXTRA = "channel";
    public static final String ACTION_SOS = "org.sofwerx.ogc.ACTION_SOS";
    private static final String EXTRA_PAYLOAD = "SOS";
    private static final String EXTRA_ORIGIN = "src";
    private Context androidContext;
    private BroadcastReceiver receiver;

    @Override
    public void start() throws SensorHubException
    {
        super.start();
        androidContext = ((SOSServiceWithIPCConfig) config).androidContext;

        receiver = new BroadcastReceiver()
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

        androidContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void stop() throws SensorHubException
    {
        if (androidContext != null && receiver != null) {
            androidContext.unregisterReceiver(receiver);
            receiver = null;
        }
        super.stop();
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
            // TODO: determine why this error appears when trying to call handle request
//            servlet.handleRequest(request);

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
            log.error("Error parsing IPC request DOM", e);
        }
        catch (IOException e)
        {
            log.error("IO error handling IPC request", e);
        }
        catch (OWSException e)
        {
            log.error("OWS error handling IPC request", e);
        }
        // OGCException e
        /**
         * TODO: Look how server is handling the this exception
         */
    }
}

