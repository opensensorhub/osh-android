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


import android.provider.MediaStore;

/**
 * <p>
 * Configuration of video encoding parameters
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Aug 10, 2020
 */
public class VideoEncoderConfig
{
    public final static String JPEG_CODEC = "JPEG";
    public final static String H264_CODEC = "H264";
    public final static String H265_CODEC = "H265";
    public final static String VP9_CODEC = "VP9";
    public final static String VP8_CODEC = "VP8";

    public static class VideoResolution
    {
        public int width = 0;
        public int height = 0;
        public int minBitrate = 0; // kbits/s
        public int maxBitrate = 0; // kbits/s
        public int selectedBitrate = 0;
    }

    public String codec = JPEG_CODEC;
    public int frameRate = 30;
    public VideoResolution[] resolutions;


    public VideoEncoderConfig()
    {
        resolutions = new VideoResolution[1];
        resolutions[0] = new VideoResolution();
    }
}
