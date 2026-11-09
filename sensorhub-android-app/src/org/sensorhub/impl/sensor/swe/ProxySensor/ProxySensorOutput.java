package org.sensorhub.impl.sensor.swe.ProxySensor;

import static android.content.ContentValues.TAG;

import android.util.Log;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.sensorhub.impl.client.sos.SOSClient;
import org.sensorhub.impl.sensor.swe.SWEVirtualSensorOutput;

public class ProxySensorOutput extends SWEVirtualSensorOutput
{
    ProxySensor parentSensor;
    DataComponent recordStructure;
    DataEncoding recordEncoding;

    public ProxySensorOutput(ProxySensor sensor, DataComponent recordStructure, DataEncoding recordEncoding, SOSClient sosClient) {
        // TODO: we need SOS clients, this is not a fix
        super(sensor, recordStructure, recordEncoding, sosClient);
        this.parentSensor = sensor;
        this.recordStructure = recordStructure;
        this.recordEncoding = recordEncoding;
    }

    @Override
    public void publishNewRecord(DataBlock dataBlock) {
        super.publishNewRecord(dataBlock);
        Log.d(TAG, "publishNewRecord: ");
    }

    @Override
    public long getLatestRecordTime() {
        return System.currentTimeMillis();
    }

    /*@Override
    public void registerListener(IEventListener listener)
    {
        Log.d(TAG, "Registering Proxy Sensor Listener for: " + this.name);
        try {
            this.parentSensor.startSOSStream(this.name);
        } catch (SensorHubException e) {
            Log.d(TAG, "Error Starting Stream while registering Proxy Sensor", e);
        }
        eventHandler.registerListener(listener);
    }*/

    /*@Override
    public void unregisterListener(IEventListener listener) {
        try {
            this.parentSensor.stopSOSStream(this.name);
            Log.d(TAG, "unregisterListener: Stopping stream: " + this.name);
        } catch (SensorHubException e) {
            e.printStackTrace();
        }
    }*/
}
