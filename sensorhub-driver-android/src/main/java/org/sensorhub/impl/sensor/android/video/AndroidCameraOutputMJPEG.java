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
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.SystemClock;

import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.android.AndroidSensorsDriver;

import java.io.ByteArrayOutputStream;


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
public class AndroidCameraOutputMJPEG extends AndroidCameraOutput
{
    private static final String CODEC_NAME = "JPEG";

    ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
    YuvImage yuvImg1, yuvImg2;
    Rect imgArea;

    public AndroidCameraOutputMJPEG(AndroidSensorsDriver parentModule, int cameraId, SurfaceTexture previewTexture) throws SensorException {
        super(parentModule, cameraId, previewTexture, "camera" + cameraId + "_M" + CODEC_NAME);
    }

    @Override
    protected String getCodecName() {
        return CODEC_NAME;
    }

    @Override
    protected void initVideoCapture(Camera.CameraInfo info) throws SensorException {
        super.initVideoCapture(info);
        imgArea = new Rect(0, 0, imgWidth, imgHeight);
        yuvImg1 = new YuvImage(imgBuf1, ImageFormat.NV21, imgWidth, imgHeight, null);
        yuvImg2 = new YuvImage(imgBuf2, ImageFormat.NV21, imgWidth, imgHeight, null);
    }

    @Override
    protected void initCodec() throws SensorException {}


    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        long timeStamp = SystemClock.elapsedRealtimeNanos() / 1000;

        // select current buffer
        YuvImage yuvImg = (data == imgBuf1) ? yuvImg1 : yuvImg2;

        // compress as JPEG
        jpegBuf.reset();
        yuvImg.compressToJpeg(imgArea, 90, jpegBuf);

        // release buffer for next frame
        camera.addCallbackBuffer(data);

        sendCompressedData(timeStamp, jpegBuf.toByteArray());
    }
}
