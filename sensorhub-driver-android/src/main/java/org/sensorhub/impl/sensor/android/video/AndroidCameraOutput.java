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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import net.opengis.swe.v20.BinaryComponent;
import net.opengis.swe.v20.BinaryEncoding;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.DataStream;
import net.opengis.swe.v20.DataType;
import net.opengis.swe.v20.Quantity;

import org.sensorhub.algo.vecmath.Vect3d;
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
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.GeoPosHelper;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;


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
public abstract class AndroidCameraOutput extends AbstractSensorOutput<AndroidSensorsDriver> implements IAndroidOutput, Camera.PreviewCallback, SensorEventListener
{
    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(AndroidCameraOutput.class.getSimpleName());

    Looper bgLooper;
    int cameraId;
    Camera camera;
    int imgHeight, imgWidth, frameRate = 25;
    int bitrate = 5 * 1000 * 1000;
    byte[] imgBuf1, imgBuf2;
    byte[] codecInfoData;
    MediaCodec mCodec;
    BufferInfo bufferInfo = new BufferInfo();
    SurfaceTexture previewTexture;

    boolean outputVideoRoll;
    SensorManager sensorManager;
    Sensor gravitySensor;
    int cameraOrientation;
    int videoRollAngle;

    String name;
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    int samplingPeriod;
    long systemTimeOffset = -1L;
    int selectedRes = 0;


    protected abstract void initCodec() throws SensorException;

    protected abstract String getCodecName();


    protected AndroidCameraOutput(AndroidSensorsDriver parentModule, int cameraId, SurfaceTexture previewTexture, String name) throws SensorException
    {
        super(parentModule);
        this.cameraId = cameraId;
        this.name = name;
        //this.previewSurfaceHolder = previewSurfaceHolder;
        this.previewTexture = previewTexture;
        this.sensorManager = parentModule.getSensorManager();

        // init camera hardware and H264
        initCam();
        initCodec();
        initOutputStructure();
    }


    protected void initOutputStructure()
    {
        // create SWE Common data structure and encoding
        VideoCamHelper fac = new VideoCamHelper();
        DataStream videoStream = fac.newVideoOutputCODEC(getName(), imgWidth, imgHeight, getCodecName());
        dataStruct = videoStream.getElementType();
        dataEncoding = videoStream.getEncoding();

        // keep old def URI so web clients still work as-is
        dataStruct.setDefinition("http://sensorml.com/ont/swe/property/VideoFrame");

        // add video roll component if enabled and gravity sensor is available
        if (getParentModule().getConfiguration().outputVideoRoll)
        {
            List<Sensor> gravitySensors = sensorManager.getSensorList(Sensor.TYPE_GRAVITY);
            if (!gravitySensors.isEmpty()) {
                gravitySensor = gravitySensors.get(0);

                Quantity roll = fac.createQuantity()
                    .name("videoRoll")
                    .definition(GeoPosHelper.DEF_ROLL)
                    .label("Video Roll Angle")
                    .uomCode("deg")
                    .build();
                ((DataRecord) dataStruct).getFieldList().add(1, roll);

                BinaryComponent rollEnc = fac.newBinaryComponent();
                rollEnc.setRef("/" + roll.getName());
                rollEnc.setCdmDataType(DataType.SHORT);
                ((BinaryEncoding) dataEncoding).addMemberAsComponent(rollEnc);

                outputVideoRoll = true;
            }
        }
    }


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


    protected void initVideoCapture(Camera.CameraInfo info) throws SensorException {
        // default for Most of the CODECs
        // if camera was successfully opened, prepare for video capture
        if (camera != null)
        {
            try
            {
                Parameters camParams = camera.getParameters();

                // set video capture and encodign options
                frameRate = parentSensor.getConfiguration().videoConfig.frameRate;
                imgWidth = parentSensor.getConfiguration().videoConfig.resolutions[selectedRes].width;
                imgHeight = parentSensor.getConfiguration().videoConfig.resolutions[selectedRes].height;
                bitrate = parentSensor.getConfiguration().videoConfig.resolutions[selectedRes].selectedBitrate*1000;

                // set parameters
                camParams.setPreviewSize(imgWidth, imgHeight);
                camParams.setVideoStabilization(camParams.isVideoStabilizationSupported());
                camParams.setPreviewFormat(ImageFormat.NV21);
                camParams.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                //camParams.setPreviewFrameRate(30);
                camera.setParameters(camParams);
                log.info("Fps ranges: {}", Arrays.deepToString(camParams.getSupportedPreviewFpsRange().toArray(new int[0][])));
                log.info("Frame rates: {}", camParams.getSupportedPreviewFrameRates());

                // setup buffers and callback
                int bufSize = imgWidth * imgHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
                imgBuf1 = new byte[bufSize];
                imgBuf2 = new byte[bufSize];
                camera.addCallbackBuffer(imgBuf1);
                camera.addCallbackBuffer(imgBuf2);
                camera.setPreviewCallbackWithBuffer(AndroidCameraOutput.this);
                camera.setDisplayOrientation(info.orientation);
                cameraOrientation = info.orientation;
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
            // if gravity sensor is available, register to receive its data
            if (outputVideoRoll && gravitySensor != null)
                sensorManager.registerListener(this, gravitySensor, 1000000);
        }
        catch (Exception e)
        {
            throw new SensorException("Cannot register to gravity sensor events", e);
        }

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
    
    
    protected void sendCompressedData(long timeStamp, byte[] compressedData)
    {
        // generate new data record
        DataBlock newRecord;
        if (latestRecord == null)
            newRecord = dataStruct.createDataBlock();
        else
            newRecord = latestRecord.renew();

        // set time stamp
        int idx = 0;
        double samplingTime = getJulianTimeStamp(timeStamp);
        newRecord.setDoubleValue(idx++, samplingTime);

        if (outputVideoRoll)
            newRecord.setShortValue(idx++, (short)videoRollAngle);

        // set encoded data
        AbstractDataBlock frameData = ((DataBlockMixed) newRecord).getUnderlyingObject()[idx++];
        frameData.setUnderlyingObject(compressedData);

        // send event
        latestRecord = newRecord;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, AndroidCameraOutput.this, latestRecord));
    }


    /* callback for gravity sensor readings */
    @Override
    public void onSensorChanged(SensorEvent event)
    {
        Vect3d g = new Vect3d(event.values[0], event.values[1], 0.0);
        g.normalize();
        double gDir = Math.atan2(g.y, g.x) / Math.PI * 180.;
        videoRollAngle = cameraOrientation + (int)gDir - 90;
        //log.trace("Gravity direction: {}°", gDir);
        //log.trace("Video roll: {}°", videoRollAngle);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
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

        if (sensorManager != null)
        {
            sensorManager.unregisterListener(this);
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
