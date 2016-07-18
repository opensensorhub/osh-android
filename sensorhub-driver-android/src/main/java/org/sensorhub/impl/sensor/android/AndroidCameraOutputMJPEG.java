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

import java.io.ByteArrayOutputStream;
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
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceHolder;


/**
 * <p>
 * Implementation of data interface for Android cameras using legacy Camera API.
 * This will encode the video frames as JPEG to produce a MJPEG stream.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since June 11, 2015
 */
@SuppressWarnings("deprecation")
public class AndroidCameraOutputMJPEG extends AbstractSensorOutput<AndroidSensorsDriver> implements IAndroidOutput, Camera.PreviewCallback
{
    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(AndroidCameraOutputMJPEG.class.getSimpleName());
    protected static final String TIME_REF = "http://www.opengis.net/def/trs/BIPM/0/UTC";
    
    Looper bgLooper;
    int cameraId;
    Camera camera;
    int imgHeight, imgWidth, frameRate;
    byte[] imgBuf1, imgBuf2;
    YuvImage yuvImg1, yuvImg2;
    Rect imgArea;
    ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
    SurfaceHolder previewSurfaceHolder;
    
    String name;
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    int samplingPeriod;
    long systemTimeOffset = -1L;
    
    
    protected AndroidCameraOutputMJPEG(AndroidSensorsDriver parentModule, int cameraId, SurfaceHolder previewSurfaceHolder)
    {
        super(parentModule);
        this.cameraId = cameraId;
        this.name = "camera" + cameraId + "_MJPEG";
        this.previewSurfaceHolder = previewSurfaceHolder;
    }
    
    
    @Override
    public String getName()
    {
        return name;
    }
    
    
    @Override
    public void start() throws SensorException
    {
        // handle camera in its own thread        
        Thread bgThread = new Thread() {
            public void run()
            {
                // we need an Android looper to process camera messages
                Looper.prepare();
                bgLooper = Looper.myLooper();
                
                try
                {
                    // open camera and get parameters
                    camera = Camera.open(cameraId);
                    Parameters camParams = camera.getParameters();
                    
                    // get supported preview sizes
                    for (Camera.Size imgSize: camParams.getSupportedPreviewSizes())
                    {
                        if (imgSize.width >= 600 && imgSize.width <= 800)
                        {
                            imgWidth = imgSize.width;
                            imgHeight = imgSize.height;
                            break;
                        }
                    }
                    frameRate = 1;
                    
                    // set parameters
                    camParams.setPreviewSize(imgWidth, imgHeight);
                    camParams.setPreviewFormat(ImageFormat.NV21);
                    camera.setParameters(camParams);
                }
                catch (Exception e)
                {
                    parentSensor.reportError("Cannot access camera " + cameraId, e);
                    return;
                }
                
                try
                {
                    // setup buffers and callback
                    imgArea = new Rect(0, 0, imgWidth, imgHeight);
                    int bufSize = imgWidth*imgHeight*ImageFormat.getBitsPerPixel(ImageFormat.NV21)/8;
                    imgBuf1 = new byte[bufSize];
                    yuvImg1 = new YuvImage(imgBuf1, ImageFormat.NV21, imgWidth, imgHeight, null);
                    imgBuf2 = new byte[bufSize];
                    yuvImg2 = new YuvImage(imgBuf2, ImageFormat.NV21, imgWidth, imgHeight, null);
                    camera.addCallbackBuffer(imgBuf1);
                    camera.addCallbackBuffer(imgBuf2);
                    camera.setPreviewCallbackWithBuffer(AndroidCameraOutputMJPEG.this);
                    camera.setDisplayOrientation(90);
                                
                    // create SWE Common data structure            
                    VideoCamHelper fac = new VideoCamHelper();
                    DataStream videoStream = fac.newVideoOutputMJPEG(getName(), imgWidth, imgHeight);
                    dataStruct = videoStream.getElementType();
                    dataEncoding = videoStream.getEncoding();
                    
                    // start streaming video
                    if (previewSurfaceHolder != null)
                        camera.setPreviewDisplay(previewSurfaceHolder);
                    camera.startPreview();
                }
                catch (Exception e)
                {
                    parentSensor.reportError("Cannot start camera capture", e);
                }
                
                // start processing messages
                Looper.loop();
            }
        };
        bgThread.start();
    }
    
    
    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        long timeStamp = SystemClock.elapsedRealtimeNanos();
        System.out.println("New frame @" + timeStamp);
        
        // select current buffer
        YuvImage yuvImg = (data == imgBuf1) ? yuvImg1 : yuvImg2;
        
        // compress as JPEG
        jpegBuf.reset();
        yuvImg.compressToJpeg(imgArea, 90, jpegBuf);
        
        // release buffer for next frame
        camera.addCallbackBuffer(data);
        
        // generate new data record
        DataBlock newRecord;
        if (latestRecord == null)
            newRecord = dataStruct.createDataBlock();
        else
            newRecord = latestRecord.renew();
        
        // set time stamp
        double samplingTime = getJulianTimeStamp(timeStamp);
        newRecord.setDoubleValue(0, samplingTime);
        
        // set encoded data
        AbstractDataBlock frameData = ((DataBlockMixed)newRecord).getUnderlyingObject()[1];
        frameData.setUnderlyingObject(jpegBuf.toByteArray());
        
        // send event
        latestRecord = newRecord;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, AndroidCameraOutputMJPEG.this, latestRecord));          
    }
    
    
    @Override
    public void stop()
    {
        if (camera != null)
        {
            camera.release();
            camera = null;
        }
        
        if (bgLooper != null)
        {
            bgLooper.quit();
            bgLooper = null;            
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
    
    
    protected final double getJulianTimeStamp(long sensorTimeStampNanos)
    {
        long sensorTimeMillis = sensorTimeStampNanos / 1000000;
        
        if (systemTimeOffset < 0)
            systemTimeOffset = System.currentTimeMillis() - sensorTimeMillis;
            
        return (systemTimeOffset + sensorTimeMillis) / 1000.;
    }
}
