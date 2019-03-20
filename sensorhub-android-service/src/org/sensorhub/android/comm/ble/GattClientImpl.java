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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.sensorhub.api.comm.ble.GattCallback;
import org.sensorhub.api.comm.ble.IGattCharacteristic;
import org.sensorhub.api.comm.ble.IGattClient;
import org.sensorhub.api.comm.ble.IGattDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Toast;


/**
 * <p>
 * Android GATT client implementation
 * </p>
 *
 * @author Alex Robin
 * @since May 16, 2016
 */
public class GattClientImpl implements IGattClient
{
    static final Logger log = LoggerFactory.getLogger(GattClientImpl.class.getSimpleName());
    protected static final UUID NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    protected static final long CMD_EXEC_TIMEOUT = 1000L; 
    
    GattCallback callback;
    Map<BluetoothGattService, GattServiceImpl> services;
    ExecutorService bleCmdExec;
    
    Context aContext;
    BluetoothDevice aDevice;
    BluetoothGatt aGatt;
        
    
    public GattClientImpl(Context aContext, BluetoothDevice aDevice, GattCallback callback)
    {
        this.aContext = aContext;
        this.aDevice = aDevice;
        
        this.callback = callback;
        this.services = new LinkedHashMap<BluetoothGattService, GattServiceImpl>();
        this.bleCmdExec = Executors.newSingleThreadExecutor();
    }
    
    
    @Override
    public void connect()
    {
        // check device has BLE support
        if (!aContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(aContext, "BLE is not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // connect to GATT server on remote device and listen for events
        aGatt = aDevice.connectGatt(aContext, false, new BluetoothGattCallback()
        {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
            {
                if (newState == BluetoothGatt.STATE_CONNECTED)
                    callback.onConnected(GattClientImpl.this, status);
                else if (newState == BluetoothGatt.STATE_DISCONNECTED)
                    callback.onDisconnected(GattClientImpl.this, status);
            }
            
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status)
            {
                for (BluetoothGattService aService: aGatt.getServices())
                    services.put(aService, new GattServiceImpl(aService));
                callback.onServicesDiscovered(GattClientImpl.this, status);
            }
            
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic aChar)
            {
                callback.onCharacteristicChanged(GattClientImpl.this, getCharObject(aChar));
            }            

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic aChar, int status)
            {
                IGattCharacteristic characteristic = getCharObject(aChar);
                notifyCommandExecuted(characteristic);
                callback.onCharacteristicRead(GattClientImpl.this, characteristic, status);
            }
            
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic aChar, int status)
            {
                IGattCharacteristic characteristic = getCharObject(aChar);
                notifyCommandExecuted(characteristic);
                callback.onCharacteristicWrite(GattClientImpl.this, characteristic, status);
            }
            
            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor aDesc, int status)
            {
                IGattDescriptor descriptor = getDescObject(aDesc);
                notifyCommandExecuted(descriptor);
                callback.onDescriptorRead(GattClientImpl.this, descriptor, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor aDesc, int status)
            {
                IGattDescriptor descriptor = getDescObject(aDesc);
                notifyCommandExecuted(descriptor);
                callback.onDescriptorWrite(GattClientImpl.this, descriptor, status);
            }           
        });
    }
    
    
    @Override
    public void disconnect()
    {
        aGatt.disconnect();
    }
    
    
    @Override
    public boolean discoverServices()
    {
        return aGatt.discoverServices();
    }


    @Override
    public Collection<GattServiceImpl> getServices()
    {
        return services.values();
    }


    @Override
    public boolean readCharacteristic(final IGattCharacteristic characteristic)
    {
        bleCmdExec.submit(new Runnable() {
            public void run()
            {
                aGatt.readCharacteristic(((GattCharacteristicImpl)characteristic).aChar);
                waitForCommandExecuted(characteristic);
            }
        });
        
        return true;
    }


    @Override
    public boolean writeCharacteristic(final IGattCharacteristic characteristic)
    {
        bleCmdExec.submit(new Runnable() {
            public void run()
            {
                aGatt.writeCharacteristic(((GattCharacteristicImpl)characteristic).aChar);
                waitForCommandExecuted(characteristic);
            }
        });
        
        return true;
    }


    @Override
    public boolean setCharacteristicNotification(final IGattCharacteristic characteristic, final boolean enable)
    {
        bleCmdExec.submit(new Runnable() {
            public void run()
            {
                BluetoothGattCharacteristic aChar = ((GattCharacteristicImpl)characteristic).aChar;
                if (!aGatt.setCharacteristicNotification(aChar, enable))
                    return;
                BluetoothGattDescriptor aDesc = aChar.getDescriptor(NOTIFICATION_DESCRIPTOR_UUID);
                aDesc.setValue(enable ? new byte[] { 0x03, 0x00 } : new byte[] { 0x00, 0x00 });
                aGatt.writeDescriptor(aDesc);
                waitForCommandExecuted(getDescObject(aDesc));
            }
        });
        
        return true;
    }


    @Override
    public boolean readDescriptor(final IGattDescriptor descriptor)
    {
        bleCmdExec.submit(new Runnable() {
            public void run()
            {
                aGatt.readDescriptor(((GattDescriptorImpl)descriptor).aDesc);
                waitForCommandExecuted(descriptor);
            }
        });
        
        return true;
    }


    @Override
    public boolean writeDescriptor(final IGattDescriptor descriptor)
    {
        bleCmdExec.submit(new Runnable() {
            public void run()
            {
                aGatt.writeDescriptor(((GattDescriptorImpl)descriptor).aDesc);
                waitForCommandExecuted(descriptor);
            }
        });
        
        return true;
    }
    
    
    protected IGattCharacteristic getCharObject(BluetoothGattCharacteristic aChar)
    {
        GattServiceImpl service = services.get(aChar.getService());
        return service.characteristics.get(aChar);
    }
    
    
    protected IGattDescriptor getDescObject(BluetoothGattDescriptor aDesc)
    {
        GattServiceImpl service = services.get(aDesc.getCharacteristic().getService());
        GattCharacteristicImpl charac = service.characteristics.get(aDesc.getCharacteristic());
        return charac.descriptors.get(aDesc.getUuid());
    }

    
    protected void waitForCommandExecuted(Object bleObject)
    {
        synchronized (bleObject)
        {
            try
            {
                bleObject.wait(CMD_EXEC_TIMEOUT);
            }
            catch (InterruptedException e)
            {
            }
        }
    }
    
    
    protected void notifyCommandExecuted(Object bleObject)
    {
        synchronized (bleObject)
        {
            bleObject.notifyAll();
        }
    }


    @Override
    public void close()
    {
        if (aGatt != null)
        {
            aGatt.disconnect();
            aGatt.close();
        }
        
        services = null;
    }

}
