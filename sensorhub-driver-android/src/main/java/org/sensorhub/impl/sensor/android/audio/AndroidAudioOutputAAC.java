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

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.android.AndroidSensorsDriver;


/**
 * <p>
 * Implementation of data interface capturing audio data and compressing it
 * using AAC codec.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since March 22, 2021
 */
@SuppressWarnings("deprecation")
public class AndroidAudioOutputAAC extends AndroidAudioOutput
{
    private static final String CODEC_NAME = "AAC";
    private static int[] ADTS_FREQ_TABLE = { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350 };

    int adtsFreqIdx;


    public AndroidAudioOutputAAC(AndroidSensorsDriver parentModule) throws SensorException
    {
        super(parentModule, "audio" + "_" + CODEC_NAME);
    }

    @Override
    protected String getCodecName()
    {
        return CODEC_NAME;
    }

    @Override
    protected void initCodec(int inputBufferSize) throws SensorException
    {
        try {
            final String videoCodec = MediaFormat.MIMETYPE_AUDIO_AAC;
            mCodec = MediaCodec.createEncoderByType(videoCodec);
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(videoCodec, sampleRateHz, 1);
            mediaFormat.setInteger("pcm-encoding", pcmEncoding);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputBufferSize);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            log.debug("MediaCodec initialized");
        }
        catch (Exception e)
        {
            throw new SensorException("Cannot initialize codec " + mCodec.getName(), e);
        }
    }


    @Override
    protected void initAudio() throws SensorException
    {
        super.initAudio();

        // set ADTS packet header size
        packetHeaderSize = 7;

        // compute sample rate index to be used in ADTS headers
        adtsFreqIdx = -1;
        for (int i = 0; i < ADTS_FREQ_TABLE.length; i++)
        {
            if (ADTS_FREQ_TABLE[i] == sampleRateHz)
                adtsFreqIdx = i;
        }
        if (adtsFreqIdx < 0)
            throw new SensorException("Unsupported sample rate for AAC: " + sampleRateHz);
    }


    protected void addPacketHeader(byte[] packet, int dataLen)
    {
        int profile = 2; // AAC LC
        int freqIdx = adtsFreqIdx;
        int chanCfg = 1; // mono
        int frameLen = dataLen + packetHeaderSize;

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF1;
        packet[2] = (byte)(((profile - 1) << 6) + (freqIdx << 2) +(chanCfg >> 2));
        packet[3] = (byte)(((chanCfg & 3) << 6) + (frameLen >> 11));
        packet[4] = (byte)((frameLen & 0x7FF) >> 3);
        packet[5] = (byte)(((frameLen & 7) << 5) + 0x1F);
        packet[6] = (byte)0xFC;
    }
}
