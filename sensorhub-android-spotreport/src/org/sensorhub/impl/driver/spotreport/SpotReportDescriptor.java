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

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;

/**
 * <p>
 * Descriptor of Android sensors driver module for automatic discovery
 * by the ModuleRegistry
 * </p>
 *
 * @author Nicolas Garay <nicolasgaray@icloud.com>
 * @since Nov 9, 2019
 */
public class SpotReportDescriptor implements IModuleProvider
{

    @Override
    public String getModuleName()
    {
        return "Spot Report Driver";
    }


    @Override
    public String getModuleDescription()
    {
        return "Driver for Spot Reports submitted from Android devices";
    }


    @Override
    public String getModuleVersion()
    {
        return "0.5";
    }


    @Override
    public String getProviderName()
    {
        return "Botts Innovative Research, Inc.";
    }


    @Override
    public Class<? extends IModule<?>> getModuleClass()
    {
        return SpotReportDriver.class;
    }


    @Override
    public Class<? extends ModuleConfig> getModuleConfigClass()
    {
        return SpotReportConfig.class;
    }

}
