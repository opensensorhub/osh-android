/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.android.video;

import java.io.ByteArrayOutputStream;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataStream;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.android.AndroidSensorsDriver;
import org.sensorhub.impl.sensor.android.IAndroidOutput;
import org.sensorhub.impl.sensor.videocam.VideoCamHelper;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Looper;
import android.os.SystemClock;


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
public class AndroidCameraOutputMJPEG extends AndroidCameraOutput {

    Looper bgLooper;
    int cameraId;
    Camera camera;
    int imgHeight, imgWidth, frameRate;
    byte[] imgBuf1, imgBuf2;
    YuvImage yuvImg1, yuvImg2;
    Rect imgArea;
    ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
    SurfaceTexture previewTexture;

    String name;
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    int samplingPeriod;
    long systemTimeOffset = -1L;


    public AndroidCameraOutputMJPEG(AndroidSensorsDriver parentModule, int cameraId, SurfaceTexture previewTexture) throws SensorException
    {
        super(parentModule, cameraId, previewTexture, "camera" + cameraId + "_MJPEG");
    }
    
    @Override
    protected void initCam() throws SensorException
    {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);

        // handle camera in its own thread
        // this is to avoid running in the same thread as other sensors
        Thread bgThread = new Thread() {
            public void run()
            {
                try
                {
                    // we need an Android looper to process camera messages
                    Looper.prepare();
                    bgLooper = Looper.myLooper();
                    
                    // open camera and get parameters
                    camera = Camera.open(cameraId);
                    
                    // start processing messages
                    Looper.loop();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                
                synchronized (this)
                {
                    notify();
                }
            }
        };      
        bgThread.start();
        
        // wait until camera is opened
        synchronized (bgThread)
        {
            try
            {
                bgThread.wait(1000);
            }
            catch (InterruptedException e)
            {
            }
        }
        
        // if camera was successfully opened, prepare for video capture
        if (camera != null)
        {
            try
            {
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
                camParams.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                camera.setParameters(camParams);

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
                camera.setDisplayOrientation(info.orientation);
            }
            catch (Exception e)
            {
                throw new SensorException("Cannot initialize camera " + cameraId, e);
            }
        }
        else
        {
            throw new SensorException("Cannot open camera " + cameraId);
        }
    }

    @Override
    protected void initCodec() throws SensorException {

    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        long timeStamp = SystemClock.elapsedRealtimeNanos();
        
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
}
