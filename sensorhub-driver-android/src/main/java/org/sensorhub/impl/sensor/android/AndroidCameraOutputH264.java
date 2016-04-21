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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.opengis.swe.v20.BinaryBlock;
import net.opengis.swe.v20.BinaryComponent;
import net.opengis.swe.v20.BinaryEncoding;
import net.opengis.swe.v20.ByteEncoding;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.DataType;
import net.opengis.swe.v20.Time;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.cdm.common.CDMException;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;
import org.vast.data.SWEFactory;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.view.SurfaceHolder;


/**
 * <p>
 * Implementation of data interface for Android cameras using legacy Camera API
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since June 11, 2015
 */
@SuppressWarnings("deprecation")
public class AndroidCameraOutputH264 extends AbstractSensorOutput<AndroidSensorsDriver> implements IAndroidOutput, Camera.PreviewCallback
{
    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(AndroidCameraOutputH264.class.getSimpleName());
    protected static final String TIME_REF = "http://www.opengis.net/def/trs/BIPM/0/UTC";

    int cameraId;
    Camera camera;
    int imgHeight, imgWidth, frameRate;
    byte[] imgBuf1, imgBuf2;
    byte[] codecInfoData;
    MediaCodec mCodec;
    BufferInfo bufferInfo = new BufferInfo();    
    SurfaceHolder previewSurfaceHolder;

    String name;
    DataComponent dataStruct;
    BinaryEncoding dataEncoding;
    int samplingPeriod;
    long systemTimeOffset = -1L;


    protected AndroidCameraOutputH264(AndroidSensorsDriver parentModule, int cameraId, SurfaceHolder previewSurfaceHolder)
    {
        super(parentModule);
        this.cameraId = cameraId;
        this.name = "camera" + cameraId + "_data";
        this.previewSurfaceHolder = previewSurfaceHolder;
    }


    @Override
    public String getName()
    {
        return name;
    }


    @Override
    public void init() throws SensorException
    {        
        // init camera hardware and H264 codec
        initCam();
        initCodec();

        // create SWE Common data structure            
        SWEFactory fac = new SWEFactory();
        dataStruct = fac.newDataRecord(2);
        dataStruct.setName(getName());

        Time time = fac.newTime();
        time.getUom().setHref(Time.ISO_TIME_UNIT);
        time.setDefinition(SWEConstants.DEF_SAMPLING_TIME);
        time.setReferenceFrame(TIME_REF);
        dataStruct.addComponent("time", time);

        DataArray img = fac.newDataArray(imgHeight);
        img.setDefinition("http://sensorml.com/ont/swe/property/VideoFrame");
        dataStruct.addComponent("videoFrame", img);

        DataArray imgRow = fac.newDataArray(imgWidth);
        img.addComponent("row", imgRow);

        DataRecord imgPixel = fac.newDataRecord(3);
        imgPixel.addComponent("red", fac.newCount(DataType.BYTE));
        imgPixel.addComponent("green", fac.newCount(DataType.BYTE));
        imgPixel.addComponent("blue", fac.newCount(DataType.BYTE));
        imgRow.addComponent("pixel", imgPixel);

        // SWE Common encoding
        dataEncoding = fac.newBinaryEncoding();
        dataEncoding.setByteEncoding(ByteEncoding.RAW);
        dataEncoding.setByteOrder(ByteOrder.BIG_ENDIAN);
        BinaryComponent timeEnc = fac.newBinaryComponent();
        timeEnc.setRef("/" + time.getName());
        timeEnc.setCdmDataType(DataType.DOUBLE);
        dataEncoding.addMemberAsComponent(timeEnc);
        //BinaryBlock compressedBlock = fac.newBinaryBlock();
        //compressedBlock.setRef("/" + img.getName());
        //compressedBlock.setCompression("H264");
        BinaryBlock compressedBlock = fac.newBinaryBlock();
        compressedBlock.setRef("/" + img.getName());
        compressedBlock.setCompression("H264");
        dataEncoding.addMemberAsBlock(compressedBlock);

        // resolve encoding so compressed blocks can be properly generated
        try { SWEHelper.assignBinaryEncoding(dataStruct, dataEncoding); }
        catch (CDMException e) { throw new SensorException("Encoding config error", e); }

        // start streaming video            
        camera.startPreview();        
    }
    
    
    protected void initCam() throws SensorException
    {
        try
        {
            // open camera and get parameters
            camera = Camera.open(cameraId);
            Parameters camParams = camera.getParameters();
    
            // get supported preview sizes
            for (Camera.Size imgSize : camParams.getSupportedPreviewSizes())
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
    
            // setup buffers and callback
            int bufSize = imgWidth * imgHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
            imgBuf1 = new byte[bufSize];
            imgBuf2 = new byte[bufSize];
            camera.addCallbackBuffer(imgBuf1);
            camera.addCallbackBuffer(imgBuf2);
            camera.setPreviewCallbackWithBuffer(this);
            camera.setDisplayOrientation(90);
            
            // connect to UI preview surface
            if (previewSurfaceHolder != null)
                camera.setPreviewDisplay(previewSurfaceHolder);
        }
        catch (Exception e)
        {
            throw new SensorException("Cannot access camera " + cameraId, e);
        }
    }


    protected void initCodec() throws SensorException
    {
        try
        {
            mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, imgWidth, imgHeight);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
            mCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mCodec.start();
            log.debug("MediaCodec initialized");
        }
        catch (Exception e)
        {
            throw new SensorException("Error while initializing codec " + mCodec.getName(), e);
        }
    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        long timeStamp = SystemClock.elapsedRealtimeNanos() / 1000;

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
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, AndroidCameraOutputH264.this, latestRecord));
    }


    @Override
    public void stop()
    {
        if (camera != null)
        {
            camera.release();
            camera = null;
        }
        
        if (mCodec != null)
        {
            mCodec.stop();
            mCodec.release();
        }
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


    protected final double getJulianTimeStamp(long sensorTimeStampUs)
    {
        long sensorTimeMillis = sensorTimeStampUs / 1000;

        if (systemTimeOffset < 0)
            systemTimeOffset = System.currentTimeMillis() - sensorTimeMillis;

        return (systemTimeOffset + sensorTimeMillis) / 1000.;
    }
}
