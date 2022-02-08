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

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import net.opengis.swe.v20.AllowedValues;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.Quantity;
import net.opengis.swe.v20.Vector;

import org.sensorhub.algo.vecmath.Quat4d;
import org.sensorhub.algo.vecmath.Vect3d;
import org.sensorhub.api.data.DataEvent;
import org.vast.swe.helper.GeoPosHelper;


/**
 * <p>
 * Implementation of data interface for Android rotation vector sensors
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Jan 18, 2015
 */
public class AndroidOrientationEulerOutput extends AndroidSensorOutput implements SensorEventListener
{
    // for euler computation
    Quat4d att = new Quat4d();
    Vect3d look = new Vect3d();
    Vect3d euler = new Vect3d();
    
    
    protected AndroidOrientationEulerOutput(AndroidSensorsDriver parentModule, SensorManager aSensorManager, Sensor aSensor)
    {
        super(parentModule, aSensorManager, aSensor);
        this.name = "euler_orientation_data";
        
        // create output structure
        GeoPosHelper fac = new GeoPosHelper();
        dataStruct = fac.newDataRecord(2);
        dataStruct.setName(getName());
        dataStruct.setDefinition("http://sensorml.com/ont/swe/property/OrientationEuler");
        
        // time stamp
        dataStruct.addComponent("time", fac.newTimeStampIsoUTC());

        // euler angles vector
        Vector vec = fac.newEulerOrientationENU(null, "deg");
        vec.setLocalFrame(parentSensor.localFrameURI);
        dataStruct.addComponent("orient", vec);
        
        // add constraints
        AllowedValues constraint;
        Quantity c = (Quantity)vec.getComponent(0);
        c.setDefinition(GeoPosHelper.DEF_HEADING_MAGNETIC);
        constraint = fac.newAllowedValues();
        constraint.addInterval(new double[] {-180.0, 180.0});
        c.setConstraint(constraint);

        c = (Quantity)vec.getComponent(1);
        constraint = fac.newAllowedValues();
        constraint.addInterval(new double[] {-90.0, 90.0});
        c.setConstraint(constraint);

        c = (Quantity)vec.getComponent(2);
        constraint = fac.newAllowedValues();
        constraint.addInterval(new double[] {-180.0, 180.0});
        c.setConstraint(constraint);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int arg1)
    {     
    }

    
    @Override
    public void onSensorChanged(SensorEvent e)
    {
        double sampleTime = getJulianTimeStamp(e.timestamp);

        // convert to quaternion + normalize
        AndroidOrientationQuatOutput.getQuaternionFromVector(att, e.values);
        att.normalize();
        
        // Y direction in phone ref frame
        look.x = 0;
        look.y = 1;
        look.z = 0;
        
        // rotate to ENU
        att.rotate(look, look);
                
        double heading = 90. - Math.toDegrees(Math.atan2(look.y, look.x)); 
        if (heading > 180.)
            heading -= 360.;
        double pitch = 0.0;//Math.toDegrees(Math.atan2(look.y, look.x)) - 90.;
        double roll = 0.0;
        
        /*double sqw = q.w*q.w;
        double sqx = q.x*q.x;
        double sqy = q.y*q.y;
        double sqz = q.z*q.z;
        System.out.println(q0);
        euler.z = Math.atan2(2.0 * (q.x*q.y + q.z*q.W), sqx - sqy - sqz + sqw);     // heading
        euler.y = Math.atan2(2.0 * (q.y*q.z + q.x*q.w), -sqx - sqy + sqz + sqw);    // pitch
        euler.x = Math.asin(-2.0 * (q.x*q.z - q.y*q.w));                            // roll
        euler.scale(180./Math.PI);
        
        double oldx = euler.x; // convert to ENU
        euler.x = euler.y;
        euler.y = oldx;
        euler.z = -euler.z;*/
        
        // build and populate datablock
        DataBlock dataBlock = dataStruct.createDataBlock();
        dataBlock.setDoubleValue(0, sampleTime);
        dataBlock.setFloatValue(1, (float)heading);
        dataBlock.setFloatValue(2, (float)pitch);
        dataBlock.setFloatValue(3, (float)roll);
        
        // TODO since this sensor is high rate, we could package several records in a single event
        // update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
    }    
}
