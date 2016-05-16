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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.sensorhub.api.comm.ble.IGattCharacteristic;
import org.sensorhub.api.comm.ble.IGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;


/**
 * <p>
 * Implementation of GATT characteristic wrapping Android BluetoothGattCharacteristic
 * </p>
 *
 * @author Alex Robin
 * @since May 16, 2016
 */
public class GattCharacteristicImpl implements IGattCharacteristic
{
    GattServiceImpl service;
    BluetoothGattCharacteristic aChar;
    Map<UUID, GattDescriptorImpl> descriptors;
    
    
    GattCharacteristicImpl(GattServiceImpl service, BluetoothGattCharacteristic aChar)
    {
        this.service = service;
        this.aChar = aChar;
        this.descriptors = new LinkedHashMap<UUID, GattDescriptorImpl>();
        
        for (BluetoothGattDescriptor aDesc: aChar.getDescriptors())
        {
            GattDescriptorImpl desc = new GattDescriptorImpl(this, aDesc);
            descriptors.put(aDesc.getUuid(), desc);
        }
    }
    
    
    @Override
    public IGattService getService()
    {
        return service;
    }


    @Override
    public int getHandle()
    {
        return aChar.getInstanceId();
    }


    @Override
    public UUID getType()
    {
        return aChar.getUuid();
    }


    @Override
    public int getProperties()
    {
        return aChar.getProperties();
    }


    @Override
    public int getPermissions()
    {
        return aChar.getPermissions();
    }


    @Override
    public Map<UUID, GattDescriptorImpl> getDescriptors()
    {
        return descriptors;
    }


    @Override
    public ByteBuffer getValue()
    {
        ByteBuffer data = ByteBuffer.wrap(aChar.getValue());
        data.order(ByteOrder.LITTLE_ENDIAN);
        return data;
    }


    @Override
    public boolean setValue(ByteBuffer value)
    {
        return aChar.setValue(value.array());
    }

}
