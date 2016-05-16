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
import java.util.UUID;
import org.sensorhub.api.comm.ble.IGattDescriptor;
import org.sensorhub.api.comm.ble.IGattField;
import android.bluetooth.BluetoothGattDescriptor;


/**
 * <p>
 * Implementation of GATT descriptor wrapping Android BluetoothGattDescriptor
 * </p>
 *
 * @author Alex Robin
 * @since May 16, 2016
 */
public class GattDescriptorImpl implements IGattDescriptor
{
    GattCharacteristicImpl characteristic;
    BluetoothGattDescriptor aDesc;
    
    
    GattDescriptorImpl(GattCharacteristicImpl characteristic, BluetoothGattDescriptor aDesc)
    {
        this.characteristic = characteristic;
        this.aDesc = aDesc;
    }
    
    
    @Override
    public IGattField getCharacteristic()
    {
        return characteristic;
    }


    @Override
    public UUID getType()
    {
        return aDesc.getUuid();
    }


    @Override
    public int getPermissions()
    {
        return aDesc.getPermissions();
    }


    @Override
    public ByteBuffer getValue()
    {
        ByteBuffer data = ByteBuffer.wrap(aDesc.getValue());
        data.order(ByteOrder.LITTLE_ENDIAN);
        return data;
    }


    @Override
    public boolean setValue(ByteBuffer value)
    {
        return aDesc.setValue(value.array());
    }

}
