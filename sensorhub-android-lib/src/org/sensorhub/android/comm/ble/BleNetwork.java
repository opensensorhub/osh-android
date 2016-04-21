/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.android.comm.ble;

import java.util.Collection;
import org.sensorhub.api.comm.IDeviceScanner;
import org.sensorhub.api.comm.INetworkInfo;
import org.sensorhub.api.comm.ble.GattCallback;
import org.sensorhub.api.comm.ble.IBleNetwork;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.module.AbstractModule;


public class BleNetwork extends AbstractModule<BleConfig> implements IBleNetwork<BleConfig>
{

    @Override
    public String getInterfaceName()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public org.sensorhub.api.comm.ICommNetwork.NetworkType getNetworkType()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IDeviceScanner getDeviceScanner()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<? extends INetworkInfo> getAvailableNetworks()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void start() throws SensorHubException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void stop() throws SensorHubException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void cleanup() throws SensorHubException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean startPairing(String address)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void connectGatt(String address, GattCallback callback)
    {
        // TODO Auto-generated method stub
        
    }

}
