package org.sensorhub.impl.sensor.swe.ProxySensor;

import android.util.Log;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.sensorhub.impl.sensor.swe.SWEVirtualSensor;
import org.sensorhub.impl.sensor.swe.SWEVirtualSensorOutput;

import static android.content.ContentValues.TAG;

public class ProxySensorOutput extends SWEVirtualSensorOutput
{
    public ProxySensorOutput(SWEVirtualSensor sensor, DataComponent recordStructure, DataEncoding recordEncoding) {
        super(sensor, recordStructure, recordEncoding);
    }

    @Override
    public void publishNewRecord(DataBlock dataBlock) {
        Log.d(TAG, "publishNewRecord");
    }
}
