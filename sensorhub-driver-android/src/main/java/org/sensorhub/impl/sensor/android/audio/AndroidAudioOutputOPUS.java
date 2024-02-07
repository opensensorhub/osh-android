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

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.android.AndroidSensorsDriver;


/**
 * <p>
 * Implementation of data interface capturing audio data and compressing it
 * using the Opus codec.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since March 29, 2021
 */
@SuppressWarnings("deprecation")
public class AndroidAudioOutputOPUS extends AndroidAudioOutput
{
    private static final String CODEC_NAME = "OPUS";

    int adtsFreqIdx;


    public AndroidAudioOutputOPUS(AndroidSensorsDriver parentModule) throws SensorException
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
            final String audioCodec = MediaFormat.MIMETYPE_AUDIO_OPUS;
            mCodec = MediaCodec.createEncoderByType(audioCodec);
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(audioCodec, sampleRateHz, 1);
            mediaFormat.setInteger("pcm-encoding", pcmEncoding);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputBufferSize);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
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

        // no header for opus
        packetHeaderSize = 0;
    }


    protected void addPacketHeader(byte[] packet, int dataLen)
    {
    }
}
