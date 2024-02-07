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


/**
 * <p>
 * Configuration of audio encoding parameters
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Mar 22, 2021
 */
public class AudioEncoderConfig
{
    public final static String AAC_CODEC = "AAC";
    public final static String AMRNB_CODEC = "AMR-NB";
    public final static String AMRWB_CODEC = "AMR-WB";
    public final static String FLAC_CODEC = "FLAC";
    public final static String OPUS_CODEC = "OPUS";
    public final static String VORBIS_CODEC = "VORBIS";
    public final static String PCM_CODEC = "PCM";


    public String codec = AAC_CODEC;
    public int sampleRate = 8000; // Hz
    public int bitRate = 64; // kbits/sec
}
