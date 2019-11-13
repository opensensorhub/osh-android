/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2019 Botts Innovative Research, Inc. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.spotreport;

import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.api.sensor.SensorConfig;
import android.content.Context;

/**
 * <p>
 * Configuration class for the Spot Report driver
 * </p>
 *
 * @author Nicolas Garay <nicolasgaray@icloud.com>
 * @since Nov 9, 2019
 */
public class SpotReportConfig extends SensorConfig {

    public String runName;
    public String runDescription;
    public int imgWidth;
    public int imgHeight;
    
    public transient Context androidContext;

    public SpotReportConfig() {

        this.moduleClass = SpotReportDriver.class.getCanonicalName();
    }

    @Override
    public ModuleConfig clone()
    {
        return this; // disable clone for now as it crashes Android app
    }
}
