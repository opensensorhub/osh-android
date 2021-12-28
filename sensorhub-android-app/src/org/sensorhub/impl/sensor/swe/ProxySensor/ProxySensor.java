package org.sensorhub.impl.sensor.swe.ProxySensor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import net.opengis.gml.v32.AbstractFeature;
import net.opengis.sensorml.v20.AbstractPhysicalProcess;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.client.sos.SOSClient;
import org.sensorhub.impl.client.sos.SOSClient.SOSRecordListener;
import org.sensorhub.impl.client.sps.SPSClient;
import org.sensorhub.impl.sensor.swe.SWEVirtualSensor;
import org.vast.ows.GetCapabilitiesRequest;
import org.vast.ows.OWSException;
import org.vast.ows.OWSUtils;
import org.vast.ows.sos.GetResultRequest;
import org.vast.ows.sos.SOSOfferingCapabilities;
import org.vast.ows.sos.SOSServiceCapabilities;
import org.vast.ows.sos.SOSUtils;
import org.vast.util.TimeExtent;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ProxySensor extends SWEVirtualSensor {
    //    protected static final Logger log = LoggerFactory.getLogger(ProxySensor.class);
    private static final String TAG = "OSHProxySensor";
    private static final String SOS_VERSION = "2.0";
    private static final String SPS_VERSION = "2.0";
    private static final double STREAM_END_TIME = 2e9; //

    AbstractFeature currentFoi;
    List<SOSClient> sosClients;
    SPSClient spsClient;

    public static final String ACTION_PROXY = "org.sofwerx.ogc.ACTION_PROXY";
    private static final String EXTRA_PAYLOAD = "PROXY";
    private static final String EXTRA_ORIGIN = "src";
    private Context androidContext;

    public ProxySensor() {
        super();
    }

    @Override
    public void start() throws SensorHubException {
        androidContext = ((ProxySensorConfig) config).androidContext;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String origin = intent.getStringExtra(EXTRA_ORIGIN);
                if (!context.getPackageName().equalsIgnoreCase(origin)) {
                    String requestPayload = intent.getStringExtra(EXTRA_PAYLOAD);   // TODO: Can be observable property string
                    try {
                        stopSOSStreams();
                    } catch (SensorHubException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PROXY);

        androidContext.registerReceiver(receiver, filter);

        Log.d(TAG, "Starting Proxy Sensor");

        checkConfig();
        removeAllOutputs();
        removeAllControlInputs();
        OWSUtils owsUtils = new OWSUtils();

        // create SOS clients, to be started by a different event
        if (config.sosEndpoint != null) {
            // find matching offering(s) for sensor UID
            SOSServiceCapabilities caps = null;
            try {
                GetCapabilitiesRequest getCap = new GetCapabilitiesRequest();
                getCap.setService(SOSUtils.SOS);
                getCap.setVersion(SOS_VERSION);
                // TODO: Verify that this is giving us a complete url
                getCap.setGetServer(config.sosEndpoint.resourcePath);
                caps = owsUtils.<SOSServiceCapabilities>sendRequest(getCap, false);
            } catch (OWSException | IOException e) {
                throw new SensorHubException("Cannot retrieve SOS capabilities", e);
            }

            // scan all offerings and connect to selected ones
            int outputNum = 1;
            sosClients = new ArrayList<SOSClient>(config.observedProperties.size());
            for (SOSOfferingCapabilities offering : caps.getLayers()) {
                if (offering.getMainProcedure().equals(config.sensorUID)) {
                    String offeringID = offering.getIdentifier();

                    for (String obsProp : config.observedProperties) {
                        if (offering.getObservableProperties().contains(obsProp)) {
                            // create data request
                            GetResultRequest req = new GetResultRequest();
                            // TODO: same as above, verify URL
                            req.setGetServer(config.sosEndpoint.resourcePath);
                            req.setVersion(SOS_VERSION);
                            req.setOffering(offeringID);
                            req.getObservables().add(obsProp);
                            req.setTime(TimeExtent.beginNow(Instant.ofEpochMilli((long)STREAM_END_TIME) ));
                            req.setXmlWrapper(false);

                            // create client and retrieve result template
                            SOSClient sos = new SOSClient(req, config.sosUseWebsockets);
                            sosClients.add(sos);
                            sos.retrieveStreamDescription();
                            DataComponent recordDef = sos.getRecordDescription();
                            if (recordDef.getName() == null)
                                recordDef.setName("output" + outputNum);

                            // retrieve sensor description from remote SOS if available (first time only)
                            try {
                                if (outputNum == 1 && config.sensorML == null)
                                    this.sensorDescription = (AbstractPhysicalProcess) sos.getSensorDescription(config.sensorUID);
                            } catch (SensorHubException e) {
                                Log.d(TAG, "Cannot get remote sensor description.", e);
                            }

                            // create output
                            final ProxySensorOutput output = new ProxySensorOutput(this, recordDef, sos.getRecommendedEncoding(), sos);
                            this.addOutput(output, false);

                            // HACK TO PREVENT GETRESULT TIME ERROR
//                            sos.startStream((data) -> output.publishNewRecord(data));
//                            try {
//                                Thread.sleep(1000);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }
//                            sos.stopStream();

                            outputNum++;
                        }
                    }
                }
            }

            if (sosClients.isEmpty())
                throw new SensorHubException("Requested observation data is not available from SOS " + config.sosEndpoint.resourcePath +
                        ". Check Sensor UID and observed properties have valid values.");
        }

    }

    public void startSOSStreams() throws SensorHubException {
        for (int i = 0; i < sosClients.size(); i++) {
            String name = sosClients.get(i).getRecordDescription().getName();
            SOSRecordListener rl = new SOSRecordListener() {
                @Override
                public void newRecord(DataBlock data) {
                    ProxySensorOutput output = (ProxySensorOutput) getObservationOutputs().get(name);
                    output.publishNewRecord(data);
                }
            };
            sosClients.get(i).startStream(rl);
        }
    }

    public void startSOSStream(String outputName) throws SensorHubException {
        for (SOSClient client : sosClients) {
            if (client.getRecordDescription().getName().equals(outputName)) {
                SOSRecordListener recordListener = new SOSRecordListener() {
                    @Override
                    public void newRecord(DataBlock data) {
                        ProxySensorOutput output = (ProxySensorOutput) getObservationOutputs().get(outputName);
                        output.publishNewRecord(data);
                    }
                };
                client.startStream(recordListener);
                break;
            }
        }
    }

    public void stopSOSStreams() throws SensorHubException {
        for (int i = 0; i < sosClients.size(); i++) {
            sosClients.get(i).stopStream();
        }
    }

    public void stopSOSStream(String outputName) throws SensorHubException {
        for (SOSClient client : sosClients) {
            if (client.getRecordDescription().getName().equals(outputName)) {
                client.stopStream();
                break;
            }
        }
    }
}
