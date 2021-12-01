/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.android.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import net.opengis.swe.v20.BinaryBlock;
import net.opengis.swe.v20.BinaryComponent;
import net.opengis.swe.v20.BinaryEncoding;
import net.opengis.swe.v20.ByteEncoding;
import net.opengis.swe.v20.ByteOrder;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataType;

import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.android.AndroidSensorsDriver;
import org.sensorhub.impl.sensor.android.IAndroidOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.cdm.common.CDMException;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;

import java.nio.ByteBuffer;


/**
 * <p>
 * Implementation of data interface capturing audio data using Android
 * AudioRecord API. See https://www.codota.com/code/java/classes/android.media.AudioRecord
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Mar 22, 2021
 */
@SuppressWarnings("deprecation")
public abstract class AndroidAudioOutput extends AbstractSensorOutput<AndroidSensorsDriver> implements IAndroidOutput {
    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(AndroidAudioOutput.class.getSimpleName());

    DataComponent dataStruct;
    DataEncoding dataEncoding;
    int sampleRateHz = 11025;
    int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
    int numSamplesPerRecord;
    int bytesPerSample;
    int bytesPerRecord;
    int bitrate = 64 * 1000; // bits/s

    Looper bgLooper;
    AudioRecord audioRecord;
    long systemTimeOffset = -1L;
    long lastRecordTime = 0;
    byte[] codecInfoData;
    MediaCodec mCodec;
    BufferInfo bufferInfo = new BufferInfo();
    int packetHeaderSize = 0;

    protected abstract String getCodecName();

    protected abstract void initCodec(int inputBufferSize) throws SensorException;

    protected abstract void addPacketHeader(byte[] packet, int packetLen);


    protected AndroidAudioOutput(AndroidSensorsDriver parentModule, String name) throws SensorException {
        super(name, parentModule);

        // init audio recorder and codec
        initAudio();
        initCodec(bytesPerRecord);
        initOutputStructure();
    }


    protected void initOutputStructure() {
        // create SWE Common data structure and encoding
        SWEHelper swe = new SWEHelper();
        String numSamplesId = "NUM_SAMPLES";
        dataStruct = swe.createRecord()
                .name(getName())
                .definition(SWEHelper.getPropertyUri("AudioFrame"))
                .addField("time", swe.createTime().asPhenomenonTimeIsoUTC().build())
                .addField("sampleRate", swe.createQuantity()
                        .label("Sample Rate")
                        .description("Number of audio samples per second")
                        .definition(SWEHelper.getQudtUri("DataRate"))
                        .uomCode("Hz"))
                .addField("numSamples", swe.createCount()
                        .id(numSamplesId)
                        .label("Num Samples")
                        .description("Number of audio samples packaged in this record"))
                .addField("samples", swe.createArray()
                        .withVariableSize(numSamplesId)
                        .withElement("sample", swe.createCount()
                                .label("Audio Sample")
                                .definition(SWEConstants.DEF_DN)
                                .dataType(DataType.SHORT))
                        .build())
                .build();

        //////////////////////////
        // binary encoding spec //
        //////////////////////////
        BinaryEncoding dataEnc = swe.newBinaryEncoding();
        dataEnc.setByteEncoding(ByteEncoding.RAW);
        dataEnc.setByteOrder(ByteOrder.BIG_ENDIAN);
        BinaryComponent comp;

        // time stamp
        comp = swe.newBinaryComponent();
        comp.setRef("/" + dataStruct.getComponent(0).getName());
        comp.setCdmDataType(DataType.DOUBLE);
        dataEnc.addMemberAsComponent(comp);

        // sample rate
        comp = swe.newBinaryComponent();
        comp.setRef("/" + dataStruct.getComponent(1).getName());
        comp.setCdmDataType(DataType.INT);
        dataEnc.addMemberAsComponent(comp);

        // num samples
        comp = swe.newBinaryComponent();
        comp.setRef("/" + dataStruct.getComponent(2).getName());
        comp.setCdmDataType(DataType.INT);
        dataEnc.addMemberAsComponent(comp);

        // audio codec
        BinaryBlock compressedBlock = swe.newBinaryBlock();
        compressedBlock.setRef("/" + dataStruct.getComponent(3).getName());
        compressedBlock.setCompression(getCodecName());
        dataEnc.addMemberAsBlock(compressedBlock);

        try {
            SWEHelper.assignBinaryEncoding(dataStruct, dataEnc);
        } catch (CDMException e) {
            throw new RuntimeException("Invalid binary encoding configuration", e);
        }
        ;

        this.dataEncoding = dataEnc;
    }


    @SuppressLint("MissingPermission")
    protected void initAudio() throws SensorException {
        AudioEncoderConfig audioConfig = parentSensor.getConfiguration().audioConfig;
        sampleRateHz = audioConfig.sampleRate;
        bitrate = audioConfig.bitRate * 1000;

        if (numSamplesPerRecord <= 0)
            numSamplesPerRecord = (int) (sampleRateHz / 10);
        bytesPerSample = (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) ? 1 : 2;
        bytesPerRecord = numSamplesPerRecord * bytesPerSample;

        // create audio recorder object
        // we use an internal buffer twice as big as the read buffer
        // so we have time to compress a chunk while we're recording the next one
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                pcmEncoding,
                bytesPerRecord * 2);
    }


    @Override
    public void start(Handler eventHandler) throws SensorException
    {
        try
        {
            // start codec
            if (mCodec != null)
                mCodec.start();

            // reset time
            lastRecordTime = 0;
            systemTimeOffset = -1;

            // read audio samples in background thread
            Thread bgThread = new Thread() {
                public void run()
                {
                    try
                    {
                        byte[] buffer = new byte[numSamplesPerRecord*2]; // 16 bits samples
                        audioRecord.startRecording();

                        while (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
                            readAndEncodeAudioData();
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            };
            bgThread.start();
        }
        catch (Exception e)
        {
            throw new SensorException("Cannot start codec " + mCodec.getName(), e);
        }
    }


    private void readAndEncodeAudioData()
    {
        // get next available buffer from codec
        int inputBufferIndex = mCodec.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0)
        {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();

            int readBytes = audioRecord.read(inputBuffer, bytesPerRecord);
            log.debug(lastRecordTime + ": " + readBytes + " audio bytes read");
            mCodec.queueInputBuffer(inputBufferIndex, 0, readBytes, lastRecordTime, 0);
            lastRecordTime += (numSamplesPerRecord * 1000000) / sampleRateHz; // in µs

            encode();
        }
    }


    private void encode()
    {
        int outputBufferIndex;

        do {
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputBufferIndex >= 0) {
                ByteBuffer outBuffer = mCodec.getOutputBuffer(outputBufferIndex);
                int outDataSize = outBuffer.remaining();
                log.debug(bufferInfo.presentationTimeUs + ": " + outDataSize + " compressed audio bytes");
                int outDataOffset = packetHeaderSize;

                // create buffer to hold packet header + data
                byte[] outData = new byte[outDataSize + outDataOffset];

                // copy encoded data
                outBuffer.get(outData, outDataOffset, outDataSize);
                addPacketHeader(outData, outDataSize);

                // release output buffer
                mCodec.releaseOutputBuffer(outputBufferIndex, false);

                // send it to output (but skip codec config bytes)
                if (bufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                    sendCompressedData(bufferInfo.presentationTimeUs, outData);
            }
        }
        while (outputBufferIndex >= 0);
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
        newRecord.setIntValue(idx++, sampleRateHz);
        newRecord.setIntValue(idx++, numSamplesPerRecord);

        // set encoded data
        AbstractDataBlock audioData = ((DataBlockMixed) newRecord).getUnderlyingObject()[idx++];
        audioData.setUnderlyingObject(compressedData);

        // send event
        latestRecord = newRecord;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, latestRecord));
    }


    @Override
    public void stop()
    {
        if (audioRecord != null)
        {
            audioRecord.stop();
            audioRecord = null;
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
    public double getAverageSamplingPeriod()
    {
        return numSamplesPerRecord/(double)sampleRateHz;
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
