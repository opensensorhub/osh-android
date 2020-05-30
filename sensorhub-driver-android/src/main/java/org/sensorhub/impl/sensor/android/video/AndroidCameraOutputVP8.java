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

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import net.opengis.swe.v20.DataStream;

import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.android.AndroidSensorsDriver;
import org.sensorhub.impl.sensor.videocam.VideoCamHelper;


/**
 * <p>
 * Implementation of data interface for Android cameras using legacy Camera API.
 * This will encode the video frames as a raw VP9 stream (i.e. NAL units).
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since June 11, 2015
 */
@SuppressWarnings("deprecation")
public class AndroidCameraOutputVP8 extends AndroidCameraOutput
{
    public AndroidCameraOutputVP8(AndroidSensorsDriver parentModule, int cameraId, SurfaceTexture previewTexture) throws SensorException {
        super(parentModule,cameraId,previewTexture,"camera" + cameraId + "_VP8");
    }


    @Override
    protected void initOutputStructure() {
        // create SWE Common data structure and encoding
        VideoCamHelper fac = new VideoCamHelper();
        // use the one of H264
        DataStream videoStream = fac.newVideoOutputH264(getName(), imgWidth, imgHeight);
        dataStruct = videoStream.getElementType();
        dataEncoding = videoStream.getEncoding();
    }

    @Override
    protected void initCodec() throws SensorException {
        try {
            final String videoCodec = MediaFormat.MIMETYPE_VIDEO_VP8;
            mCodec = MediaCodec.createEncoderByType(videoCodec);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(videoCodec, imgWidth, imgHeight);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate );
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            log.debug("MediaCodec initialized");
        }
        catch (Exception e)
        {
            throw new SensorException("Cannot initialize codec " + mCodec.getName(), e);
        }
    }
}
