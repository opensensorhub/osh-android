/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.dji;

import java.io.ByteArrayOutputStream;
import java.util.EnumSet;

import android.graphics.SurfaceTexture;
import dji.sdk.camera.Camera;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataStream;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.videocam.VideoCamHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;
import org.vast.swe.SWEHelper;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;


/**
 * <p>
 * Implementation of data interface for FLIR One camera using FLIR SDK
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Mar 5, 2016
 */
public class DjiCameraOutput extends AbstractSensorOutput<DjiDriver>
{
    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(DjiCameraOutput.class.getSimpleName());
    protected static final String TIME_REF = "http://www.opengis.net/def/trs/BIPM/0/UTC";
    
    long samplingTime;
    int imgHeight, imgWidth, frameRate;
    ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
    SurfaceTexture previewTexture;
    Camera djiCamera;

    String name;
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    
    
    protected DjiCameraOutput(DjiDriver parentModule, SurfaceTexture previewTexture)
    {
        super(parentModule);
        this.name = "flirone_camera_data";
        this.previewTexture = previewTexture;
        
        imgWidth = 480;
        imgHeight = 640;
        frameRate = 9;
    }
    
    
    @Override
    public String getName()
    {
        return name;
    }
    
    
    protected void init() throws SensorException
    {
        // create SWE Common data structure            
        VideoCamHelper fac = new VideoCamHelper();
        DataStream videoStream = fac.newVideoOutputMJPEG(getName(), imgWidth, imgHeight);
        dataStruct = videoStream.getElementType();
        dataEncoding = videoStream.getEncoding();
        dataStruct.getComponent(1).setDefinition(SWEHelper.getPropertyUri("ThermalImage"));
    }
    
    
    public void start(Context context, Camera djiCamera)
    {
        this.djiCamera = djiCamera;
    }
    
    
    /*@Override
    public void onFrameProcessed(RenderedImage img)
    {
        log.trace("Frame processed");
        
        // compress as JPEG
        jpegBuf.reset();
        Bitmap bitmap = img.getBitmap();
        bitmap.compress(CompressFormat.JPEG, 90, jpegBuf);
        
        // generate new data record
        DataBlock newRecord;
        if (latestRecord == null)
            newRecord = dataStruct.createDataBlock();
        else
            newRecord = latestRecord.renew();
        
        // set time stamp
        newRecord.setDoubleValue(0, samplingTime/1000.0);
        
        // set encoded data
        AbstractDataBlock frameData = ((DataBlockMixed)newRecord).getUnderlyingObject()[1];
        frameData.setUnderlyingObject(jpegBuf.toByteArray());
        
        // send event
        latestRecord = newRecord;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, DjiCameraOutput.this, latestRecord));
    }*/
    
    
    @Override
    public void stop()
    {
        if (djiCamera != null)
        {
            //djiCamera.stopRecordVideo();
            djiCamera = null;
        }
    }


    @Override
    public double getAverageSamplingPeriod()
    {
        return 1./ (double)frameRate;
    }


    @Override
    public DataComponent getRecordDescription()
    {
        return dataStruct;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return dataEncoding;
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
}
