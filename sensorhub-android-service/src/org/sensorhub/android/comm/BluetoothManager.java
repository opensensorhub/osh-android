/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.android.comm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


public class BluetoothManager
{

    
    public List<BluetoothDevice> discoverDevices(Context context, final String macAddress)
    {
        final ArrayList<BluetoothDevice> foundDevices = new ArrayList<BluetoothDevice>();
        
        // find bluetooth SPP devices
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(new BroadcastReceiver()
        {
            public void onReceive(Context context, Intent intent)
            {
                String action = intent.getAction();
                
                if (BluetoothDevice.ACTION_FOUND.equals(action))
                {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    System.out.println("  Device: " + device.getAddress() + ", " + device);
                    
                    if (device.getAddress().matches(macAddress))
                        foundDevices.add(device);
                }
                else
                {
                    if (BluetoothDevice.ACTION_UUID.equals(action))
                    {
//                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                        Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
//                        for (int i = 0; i < uuidExtra.length; i++)
//                        {
//                            out.append("\n  Device: " + device.getName() + ", " + device + ", Service: " + uuidExtra[i].toString());
//                        }
                    }
                    else
                    {
                        if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
                        {
                            System.out.println("Discovery Started...");
                        }
                        else
                        {
                            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
                            {
                                System.out.println("\nDiscovery Finished");
//                                Iterator<bluetoothdevice> itr = btDeviceList.iterator();
//                                while (itr.hasNext())
//                                {
//                                    // Get Services for paired devices
//                                    BluetoothDevice device = itr.next();
//                                    out.append("\nGetting Services for " + device.getName() + ", " + device);
//                                    if (!device.fetchUuidsWithSdp())
//                                    {
//                                        out.append("\nSDP Failed for " + device.getName());
//                                    }
//
//                                }
                            }
                        }
                    }
                }
            }
        }, filter, Context.RECEIVER_EXPORTED); // Don't forget to unregister during onDestroy

        // Getting the Bluetooth adapter
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        System.out.println("Adapter: " + btAdapter);
        btAdapter.startDiscovery();
        
        
        return foundDevices;
    }
    
    
    /**
     * Returns the first paired device whose address or name matches the given identifier.
     * Tries MAC address match first, then falls back to name matching (startsWith, case-insensitive).
     * @param deviceId MAC address or device name to match
     * @return first matching device
     * @throws IOException if a paired device with a matching address or name cannot be found
     */
    public BluetoothDevice findDevice(String deviceId) throws IOException
    {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

        if(btAdapter == null || !btAdapter.isEnabled()) throw new IOException("Bluetooth is not available or not enabled");

        // match by MAC address
        for (BluetoothDevice dev: btAdapter.getBondedDevices())
        {
            if (dev.getAddress() != null && dev.getAddress().equalsIgnoreCase(deviceId)) {
                return dev;
            }
        }

        // match by device name (case-insensitive startsWith)
        String lowerDeviceId = deviceId.toLowerCase();
        for (BluetoothDevice dev: btAdapter.getBondedDevices())
        {
            if (dev.getName() != null && dev.getName().toLowerCase().startsWith(lowerDeviceId)) {
                return dev;
            }
        }

        throw new IOException("Cannot find device " + deviceId);
    }
    
    
    public BluetoothSocket connectToSerialDevice(String macAddress) throws IOException
    {
        BluetoothDevice dev = findDevice(macAddress);
        UUID spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        BluetoothSocket socket = dev.createRfcommSocketToServiceRecord(spp);
        return socket;
    }
}
