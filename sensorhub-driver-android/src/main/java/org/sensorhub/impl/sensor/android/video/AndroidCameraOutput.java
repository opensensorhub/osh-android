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

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;

import java.nio.ByteBuffer;


/**
 * <p>
 * Implementation of data interface for Android cameras using legacy Camera API.
 * This will encode the video frames as a raw H264 stream (i.e. NAL units).
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since June 11, 2015
 */
@SuppressWarnings("deprecation")
public abstract class AndroidCameraOutput extends AbstractSensorOutput<AndroidSensorsDriver> implements IAndroidOutput, Camera.PreviewCallback
{
    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(AndroidCameraOutput.class.getSimpleName());
    protected static final String TIME_REF = "http://www.opengis.net/def/trs/BIPM/0/UTC";

    Looper bgLooper;
    int cameraId;
    Camera camera;
    int imgHeight, imgWidth, frameRate = 25;
    byte[] imgBuf1, imgBuf2;
    byte[] codecInfoData;
    MediaCodec mCodec;
    BufferInfo bufferInfo = new BufferInfo();
    SurfaceTexture previewTexture;

    int bitrate = 5 * 1000 * 1000;

    String name;
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    int samplingPeriod;
    long systemTimeOffset = -1L;


    protected AndroidCameraOutput(AndroidSensorsDriver parentModule, int cameraId, SurfaceTexture previewTexture, String name) throws SensorException
    {
        super(parentModule);
        this.cameraId = cameraId;
        this.name = name;
        //this.previewSurfaceHolder = previewSurfaceHolder;
        this.previewTexture = previewTexture;

        // init camera hardware and H264 codec
        initCam();
        initCodec();
        initOutputStructure();
    }


    protected abstract void initOutputStructure();

    protected void initCam() throws SensorException
    {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

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
        
        initVideoCapture(info);
    }


    private static final float BPP = 0.25f;
    private int calcBitRate(int mWidth,int mHeight, int frameRate) {
        final int bitrate = (int)(BPP * frameRate * mWidth * mHeight);
        return bitrate;
    }

    protected abstract void initCodec() throws SensorException;

    protected void initVideoCapture(Camera.CameraInfo info) throws SensorException {
        // default for Most of the CODECs
        // if camera was successfully opened, prepare for video capture
        if (camera != null)
        {
            try
            {
                Parameters camParams = camera.getParameters();

                // get supported preview sizes
                for (Camera.Size imgSize : camParams.getSupportedPreviewSizes())
                {
                    if (imgSize.width >= 1100 && imgSize.width <= 1300)
                    {
                        imgWidth = imgSize.width;
                        imgHeight = imgSize.height;
                        break;
                    }
                }
                // set parameters
                camParams.setPreviewSize(imgWidth, imgHeight);
                camParams.setVideoStabilization(camParams.isVideoStabilizationSupported());
                camParams.setPreviewFormat(ImageFormat.NV21);
                camParams.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                camera.setParameters(camParams);

                // setup buffers and callback
                int bufSize = imgWidth * imgHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
                imgBuf1 = new byte[bufSize];
                imgBuf2 = new byte[bufSize];
                camera.addCallbackBuffer(imgBuf1);
                camera.addCallbackBuffer(imgBuf2);
                camera.setPreviewCallbackWithBuffer(AndroidCameraOutput.this);
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
    public void start(Handler eventHandler) throws SensorException
    {        
        try
        {
            // start codec
            if (mCodec != null)
                mCodec.start();
        }
        catch (Exception e)
        {
            throw new SensorException("Cannot start codec " + mCodec.getName(), e);
        }
        
        try
        {
            // start streaming video        
            if (previewTexture != null)
                camera.setPreviewTexture(previewTexture);
            camera.startPreview();
        }
        catch (Exception e)
        {
            throw new SensorException("Cannot start capture on camera " + cameraId, e);
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        long timeStamp = SystemClock.elapsedRealtimeNanos() / 1000;

        // convert to NV12
        int uvPos = imgWidth * imgHeight;
        for (int i = uvPos; i < data.length; i+=2)
        {
            byte b = data[i];
            data[i] = data[i+1];
            data[i+1] = b;
        }
        
        // compress using selected codec
        encode(timeStamp, data);
        
        // release buffer for next frame
        camera.addCallbackBuffer(data);
    }


    private void encode(long timeStamp, byte[] data)
    {
        // copy frame data to buffer
        int inputBufferIndex = mCodec.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0)
        {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(data);
            mCodec.queueInputBuffer(inputBufferIndex, 0, data.length, timeStamp, 0);
        }
        else
        {
            // skip frame if no buffer is available
            return;
        }
        
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
        if (outputBufferIndex >= 0)
        {
            ByteBuffer outBuffer = mCodec.getOutputBuffer(outputBufferIndex);                                       
            int outDataSize = bufferInfo.size - bufferInfo.offset;
            int outDataOffset = 0;
            byte[] outData;
            
            // insert SPS/PPS before each key frame
            if (codecInfoData != null && bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME)
            {
                outDataOffset = codecInfoData.length;
                outData = new byte[outDataSize + outDataOffset];
                System.arraycopy(codecInfoData, 0, outData, 0, codecInfoData.length);
            }
            else
                outData = new byte[outDataSize];
            
            // copy encoded data
            outBuffer.position(bufferInfo.offset);
            outBuffer.get(outData, outDataOffset, bufferInfo.size);
            
            // release output buffer
            mCodec.releaseOutputBuffer(outputBufferIndex, false);
            
            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                codecInfoData = outData;
            else
                sendCompressedData(bufferInfo.presentationTimeUs, outData);
        }
    }
    
    
    private void sendCompressedData(long timeStamp, byte[] compressedData)
    {
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
        AbstractDataBlock frameData = ((DataBlockMixed) newRecord).getUnderlyingObject()[1];
        frameData.setUnderlyingObject(compressedData);

        // send event
        latestRecord = newRecord;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, AndroidCameraOutput.this, latestRecord));
    }


    @Override
    public void stop()
    {
        if (camera != null)
        {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        
        if (mCodec != null)
        {
            mCodec.stop();
            mCodec.release();
        }
        
        if (bgLooper != null)
        {
            bgLooper.quit();
            bgLooper = null;            
        }
    }


    @Override
    public String getName()
    {
        return name;
    }


    @Override
    public double getAverageSamplingPeriod()
    {
        return 1. / (double) frameRate;
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


    protected double getJulianTimeStamp(long sensorTimeStampUs)
    {
        long sensorTimeMillis = sensorTimeStampUs / 1000;

        if (systemTimeOffset < 0)
            systemTimeOffset = System.currentTimeMillis() - sensorTimeMillis;

        return (systemTimeOffset + sensorTimeMillis) / 1000.;
    }
}
