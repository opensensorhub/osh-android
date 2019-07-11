package org.sensorhub.impl.sensor.swe.ProxySensor;

import android.util.Log;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.swe.SWEVirtualSensor;
import org.sensorhub.impl.sensor.swe.SWEVirtualSensorOutput;

import static android.content.ContentValues.TAG;

public class ProxySensorOutput extends SWEVirtualSensorOutput
{
    ProxySensor parentSensor;
    DataComponent recordStructure;
    DataEncoding recordEncoding;

    public ProxySensorOutput(ProxySensor sensor, DataComponent recordStructure, DataEncoding recordEncoding) {
        super(sensor, recordStructure, recordEncoding);
        this.parentSensor = sensor;
        this.recordStructure = recordStructure;
        this.recordEncoding = recordEncoding;
    }

    @Override
    public void registerListener(IEventListener listener)
    {
//        super.registerListener(listener);
        Log.d(TAG, "Registering Proxy Sensor Listener");
        //TODO: How to start the SOS stream at this point?
        try {
        this.parentSensor.startSOSStreams();
    } catch (SensorHubException e) {
        Log.d(TAG, "Error Starting Stream while registering Proxy Sensor", e);
    }
        eventHandler.registerListener(listener);
    }

    @Override
    public void publishNewRecord(DataBlock dataBlock) {
        super.publishNewRecord(dataBlock);
        Log.d(TAG, "publishNewRecord: ");
    }
}
