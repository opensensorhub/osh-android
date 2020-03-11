/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.dji;

import android.graphics.SurfaceTexture;
import org.sensorhub.api.sensor.SensorConfig;
import android.content.Context;


/**
 * <p>
 * Configuration class for DJI drone
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Mar 5, 2016
 */
public class DjiConfig extends SensorConfig
{     
    public boolean enableVisOutput = false;
    public boolean enableStatusOutput = false;
    
    public String runName;
    public String runDescription; 
    
    public transient Context androidContext;
    public transient SurfaceTexture camPreviewTexture;
       
    
    
    public DjiConfig()
    {
        this.moduleClass = DjiDriver.class.getCanonicalName();
    }
}
