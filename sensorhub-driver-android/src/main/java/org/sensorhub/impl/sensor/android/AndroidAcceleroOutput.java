/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.android;

import net.opengis.swe.v20.DataBlock;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.vast.swe.helper.GeoPosHelper;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


/**
 * <p>
 * Implementation of data interface for Android accelerometers
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Jan 18, 2015
 */
public class AndroidAcceleroOutput extends AndroidSensorOutput implements SensorEventListener
{
    private static final String ACCEL_UOM = "m/s2";
    
    
    protected AndroidAcceleroOutput(AndroidSensorsDriver parentModule, SensorManager aSensorManager, Sensor aSensor)
    {
        super(parentModule, aSensorManager, aSensor);
        
        // create output structure
        GeoPosHelper fac = new GeoPosHelper();
        dataStruct = fac.newDataRecord(2);
        dataStruct.setName(getName());
        dataStruct.addComponent("time", fac.newTimeStampIsoUTC());
        dataStruct.addComponent("accel", fac.newAccelerationVector(null, parentSensor.localFrameURI, ACCEL_UOM));
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int arg1)
    {     
    }


    @Override
    public void onSensorChanged(SensorEvent e)
    {
        double sampleTime = getJulianTimeStamp(e.timestamp);
        
        // build and populate datablock
        DataBlock dataBlock = dataStruct.createDataBlock();
        dataBlock.setDoubleValue(0, sampleTime);
        dataBlock.setFloatValue(1, e.values[0]);
        dataBlock.setFloatValue(2, e.values[1]);
        dataBlock.setFloatValue(3, e.values[2]);        
                
        // TODO since this sensor is high rate,we could package several records in a single event
        // update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, this, dataBlock));
    }    
}
