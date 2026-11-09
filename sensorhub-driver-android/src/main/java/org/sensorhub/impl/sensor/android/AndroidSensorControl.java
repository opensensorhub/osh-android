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
import android.hardware.SensorManager;

import net.opengis.swe.v20.DataComponent;

import org.sensorhub.impl.sensor.AbstractSensorControl;


/**
 * <p>
 * Implementation of control interface for Android sensors
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Sep 6, 2013
 */
public class AndroidSensorControl extends AbstractSensorControl<AndroidSensorsDriver>
{
    SensorManager aSensorManager;
    Sensor aSensor;
    
    
    protected AndroidSensorControl(AndroidSensorsDriver parentModule, SensorManager aSensorManager, Sensor aSensor)
    {
        super(aSensor.getName()+"_control", parentModule);
        this.aSensorManager = aSensorManager;
        this.aSensor = aSensor;
    }


    @Override
    public String getName()
    {
        return aSensor.getName() + "_control";
    }
    
    
    @Override
    public DataComponent getCommandDescription()
    {
        // TODO Auto-generated method stub
        return null;
    }


    //TODO: Check that these are the correct types
    /*@Override
    public boolean execCommand(DataBlock command) throws SensorException
    {
        // TODO Auto-generated method stub
        return null;
    }*/


    /*@Override
    public CommandAck execCommandGroup(List<DataBlock> commands) throws SensorException
    {
        // TODO Auto-generated method stub
        return null;
    }*/

}
