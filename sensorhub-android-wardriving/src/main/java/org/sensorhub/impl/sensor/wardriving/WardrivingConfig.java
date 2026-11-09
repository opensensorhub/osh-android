/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.wardriving;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorConfig;

import android.content.Context;
import android.provider.Settings;


/**
 *
 * @author Kalyn Stricklin
 * @since April 6, 2026
 */
public class WardrivingConfig extends SensorConfig
{

    public WardrivingConfig()
    {
        this.moduleClass = Wardriving.class.getCanonicalName();
    }
    public String uid_extension;

    public long scanIntervalMs = 10000;

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
