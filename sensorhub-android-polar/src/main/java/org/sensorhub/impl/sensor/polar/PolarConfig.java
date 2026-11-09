/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 The Initial Developer is Botts Innovative Research Inc. Portions created by the Initial
 Developer are Copyright (C) 2025 the Initial Developer. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.polar;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorConfig;

import android.content.Context;
import android.provider.Settings;


/**
 *
 * @author Kalyn Stricklin
 * @since Jan 13, 2023
 */
public class PolarConfig extends SensorConfig
{

    public PolarConfig()
    {
        this.moduleClass = Polar.class.getCanonicalName();
    }

    /** Polar device ID printed on the device (e.g. "A1B2C3D4") */
    public String deviceId;
    public String uid_extension;
    public boolean enablePpi = true;

    /** Enable ECG waveform streaming (H10 only) */
    public boolean enableEcg = false;

    /** Enable accelerometer streaming (H10 only) */
    public boolean enableAccelerometer = false;

    public static String getUid() {
        Context context = SensorHubService.getContext();
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public String getUidWithExt()
    {
        String baseUid = getUid();
        if (uid_extension != null && !uid_extension.isEmpty())
            return baseUid + ":" + uid_extension;
        return baseUid;
    }
}
