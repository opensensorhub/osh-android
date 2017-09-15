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

import android.os.Handler;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Time;
import net.opengis.swe.v20.Vector;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.helper.GeoPosHelper;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;


/**
 * <p>
 * Implementation of data interface for Android location providers
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Jan 18, 2015
 */
public class AndroidLocationOutput extends AbstractSensorOutput<AndroidSensorsDriver> implements IAndroidOutput, LocationListener
{
    LocationManager locManager;
    LocationProvider locProvider;
    String name;
    boolean enabled;
    DataComponent posDataStruct;
    DataEncoding posEncoding;
    
    
    protected AndroidLocationOutput(AndroidSensorsDriver parentModule, LocationManager locManager, LocationProvider locProvider)
    {
        super(parentModule);
        this.locManager = locManager;
        this.locProvider = locProvider;        
        this.name = locProvider.getName().replaceAll(" ", "_") + "_data";
        
        // output structure (time + location)
        GeoPosHelper fac = new GeoPosHelper();
        posDataStruct = fac.newDataRecord(2);
        posDataStruct.setName(getName());
        Time time = fac.newTimeStampIsoUTC();
        posDataStruct.addComponent("time", time);
        Vector vec = fac.newLocationVectorLLA(null);  
        ((Vector)vec).setLocalFrame(parentSensor.localFrameURI);
        posDataStruct.addComponent("location", vec);
        
        // output encoding
        posEncoding = fac.newTextEncoding(",", "\n");
    }
    
    
    @Override
    public String getName()
    {
        return name;
    }
    
    
    @Override
    public void start(Handler eventHandler)
    {
        // request location data
        locManager.requestLocationUpdates(locProvider.getName(), 100, 0.0f, this, eventHandler.getLooper());
    }
    
    
    @Override
    public void stop()
    {
        locManager.removeUpdates(this);
    }


    @Override
    public boolean isEnabled()
    {
        return enabled;
    }


    @Override
    public double getAverageSamplingPeriod()
    {
        return 1.0;
    }


    @Override
    public DataComponent getRecordDescription()
    {
        return posDataStruct;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return posEncoding;
    }

    
    @Override
    public DataBlock getLatestRecord()
    {
        return latestRecord;
    }
    
    
    @Override
    public long getLatestRecordTime()
    {
        return latestRecordTime;
    }


    @Override
    public void onLocationChanged(Location location)
    {
        /*log.debug("Location received from " + getName() + ": "
                  + location.getLatitude() + ", " +
                  + location.getLongitude() + ", " +
                  + location.getAltitude()); */
        
        double sampleTime = location.getTime() / 1000.0;
                
        // build and populate datablock
        DataBlock dataBlock = posDataStruct.createDataBlock();
        dataBlock.setDoubleValue(0, sampleTime);
        dataBlock.setDoubleValue(1, location.getLatitude());
        dataBlock.setDoubleValue(2, location.getLongitude());
        dataBlock.setDoubleValue(3, location.getAltitude());        
                
        // update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, this, dataBlock));
    }


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        
    }


    @Override
    public void onProviderEnabled(String provider)
    {
        enabled = true;        
    }


    @Override
    public void onProviderDisabled(String provider)
    {
        enabled = false;        
    }
    
}
