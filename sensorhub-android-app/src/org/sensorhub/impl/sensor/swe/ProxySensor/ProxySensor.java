package org.sensorhub.impl.sensor.swe.ProxySensor;

import android.util.Log;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.impl.sensor.swe.SWEVirtualSensor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.opengis.gml.v32.AbstractFeature;
import net.opengis.sensorml.v20.AbstractPhysicalProcess;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataChoice;
import net.opengis.swe.v20.DataComponent;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.client.sos.SOSClient;
import org.sensorhub.impl.client.sos.SOSClient.SOSRecordListener;
import org.sensorhub.impl.client.sps.SPSClient;
import org.sensorhub.impl.sensor.AbstractSensorModule;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.vast.ows.GetCapabilitiesRequest;
import org.vast.ows.OWSException;
import org.vast.ows.OWSUtils;
import org.vast.ows.sos.GetResultRequest;
import org.vast.ows.sos.SOSOfferingCapabilities;
import org.vast.ows.sos.SOSServiceCapabilities;
import org.vast.ows.sos.SOSUtils;
import org.vast.util.TimeExtent;

public class ProxySensor extends SWEVirtualSensor {
//    protected static final Logger log = LoggerFactory.getLogger(ProxySensor.class);
    private static final String TAG = "OSHProxySensor";
    private static final String SOS_VERSION = "2.0";
    private static final String SPS_VERSION = "2.0";
    private static final double STREAM_END_TIME = 2e9; //

    AbstractFeature currentFoi;
    List<SOSClient> sosClients;
    SPSClient spsClient;
    Map<String, SOSRecordListener> recordListeners;

    public ProxySensor() {
        super();
    }

    @Override
    public void start() throws SensorHubException {
        Log.d(TAG, "Starting Proxy Sensor");

        checkConfig();
        removeAllOutputs();
        removeAllControlInputs();
        OWSUtils owsUtils = new OWSUtils();

        // create SOS clients, to be started by a different event
        if (config.sosEndpointUrl != null) {
            // find matching offering(s) for sensor UID
            SOSServiceCapabilities caps = null;
            try {
                GetCapabilitiesRequest getCap = new GetCapabilitiesRequest();
                getCap.setService(SOSUtils.SOS);
                getCap.setVersion(SOS_VERSION);
                getCap.setGetServer(config.sosEndpointUrl);
                caps = owsUtils.<SOSServiceCapabilities>sendRequest(getCap, false);
            } catch (OWSException e) {
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
                            req.setGetServer(config.sosEndpointUrl);
                            req.setVersion(SOS_VERSION);
                            req.setOffering(offeringID);
                            req.getObservables().add(obsProp);
                            req.setTime(TimeExtent.getPeriodStartingNow(STREAM_END_TIME));
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
//                                log.warn("Cannot get remote sensor description", e);
                                Log.d(TAG, "Cannot get remote sensor description.", e);
                            }

                            // create output
                            final ProxySensorOutput output = new ProxySensorOutput(this, recordDef, sos.getRecommendedEncoding());
                            this.addOutput(output, false);

                            recordListeners.put(sos.toString(), new SOSRecordListener() {
                                @Override
                                public void newRecord(DataBlock data) {
                                    output.publishNewRecord(data);
                                }
                            });

//                            // TODO: Move to event based start
//                            sos.startStream(new SOSRecordListener() {
//                                @Override
//                                public void newRecord(DataBlock data) {
//                                    output.publishNewRecord(data);
//                                }
//                            });

                            outputNum++;
                        }
                    }
                }
            }

            if (sosClients.isEmpty())
                throw new SensorHubException("Requested observation data is not available from SOS " + config.sosEndpointUrl +
                        ". Check Sensor UID and observed properties have valid values.");
        }

    }

    public void startSOSStreams() throws SensorHubException {
        for (SOSClient client: sosClients){
            client.startStream(recordListeners.get(client.toString()));
        }
    }
}
