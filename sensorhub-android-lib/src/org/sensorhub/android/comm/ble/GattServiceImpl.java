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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.sensorhub.api.comm.ble.IGattCharacteristic;
import org.sensorhub.api.comm.ble.IGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;


/**
 * <p>
 * Implementation of GATT service wrapping Android BluetoothGattService
 * </p>
 *
 * @author Alex Robin
 * @since May 16, 2016
 */
public class GattServiceImpl implements IGattService
{
    BluetoothGattService aService;
    Map<BluetoothGattCharacteristic, GattCharacteristicImpl> characteristics;
    
    
    GattServiceImpl(BluetoothGattService aService)
    {
        this.aService = aService;
        this.characteristics = new LinkedHashMap<BluetoothGattCharacteristic, GattCharacteristicImpl>();
        
        for (BluetoothGattCharacteristic aChar: aService.getCharacteristics())
        {
            GattCharacteristicImpl charac = new GattCharacteristicImpl(this, aChar);
            characteristics.put(aChar, charac);
        }
    }
    
    
    @Override
    public int getHandle()
    {
        return aService.getInstanceId();
    }


    @Override
    public UUID getType()
    {
        return aService.getUuid();
    }


    @Override
    public Collection<GattCharacteristicImpl> getCharacteristics()
    {
        return characteristics.values();
    }


    @Override
    public boolean addCharacteristic(IGattCharacteristic characteristic)
    {
        return false;
    }

}
