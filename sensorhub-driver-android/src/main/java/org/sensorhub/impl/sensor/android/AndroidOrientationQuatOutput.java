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
import net.opengis.swe.v20.Vector;
import org.sensorhub.algo.vecmath.Quat4d;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.vast.swe.helper.GeoPosHelper;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


/**
 * <p>
 * Implementation of data interface for Android rotation vector sensors
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Jan 18, 2015
 */
public class AndroidOrientationQuatOutput extends AndroidSensorOutput implements SensorEventListener
{
    Quat4d att = new Quat4d();


    public static void getQuaternionFromVector(Quat4d q, float[] rv)
    {
        q.x = rv[0];
        q.y = rv[1];
        q.z = rv[2];

        // quaternion scalar value
        // check because rotation vector may have only 3 components
        if (rv.length > 3) {
            q.s = rv[3];
        } else {
            q.s = 1 - q.x*q.x - q.y*q.y - q.z*q.z;
            q.s = (q.s > 0) ? (float) Math.sqrt(q.s) : 0;
        }
    }
    
    
    protected AndroidOrientationQuatOutput(AndroidSensorsDriver parentModule, SensorManager aSensorManager, Sensor aSensor)
    {
        super(parentModule, aSensorManager, aSensor);
        this.name = "quat_orientation_data";
                
        // create output structure
        GeoPosHelper fac = new GeoPosHelper();
        dataStruct = fac.newDataRecord(2);
        dataStruct.setName(getName());
        dataStruct.setDefinition("http://sensorml.com/ont/swe/property/OrientationQuaternion");
        dataStruct.addComponent("time", fac.newTimeStampIsoUTC());

        // attitude quaternion
        Vector quat = fac.newQuatOrientationENU(null);
        quat.setLocalFrame(parentSensor.localFrameURI);
        dataStruct.addComponent("orient", quat);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int arg1)
    {     
    }

    
    @Override
    public void onSensorChanged(SensorEvent e)
    {
        double sampleTime = getJulianTimeStamp(e.timestamp);
        if (latestRecord != null && sampleTime - latestRecord.getDoubleValue(0) < samplingPeriod*0.99)
            return;
        
        // convert to quaternion + normalize
        getQuaternionFromVector(att, e.values);
        att.normalize();
        
        // this is the right formula to compute heading of back camera look direction
        //Vect3d look = new Vect3d(0,0,-1);
        //att.mulQVQtilde(look, look);
        //double heading = 90 - 180/Math.PI*Math.atan2(look.y, look.x);
        //System.out.println("heading=" + heading);
        
        // build and populate datablock
        DataBlock dataBlock = dataStruct.createDataBlock();
        dataBlock.setDoubleValue(0, sampleTime);
        dataBlock.setFloatValue(1, (float)att.x);
        dataBlock.setFloatValue(2, (float)att.y);
        dataBlock.setFloatValue(3, (float)att.z);
        dataBlock.setFloatValue(4, (float)att.s); 
        
        // TODO since this sensor is high rate,we could package several records in a single event
        // update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, this, dataBlock)); 
    }    
}
