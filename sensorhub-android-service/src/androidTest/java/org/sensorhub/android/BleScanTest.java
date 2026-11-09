//package org.sensorhub.android;
//
//
//import androidx.test.ext.junit.runners.AndroidJUnit4;
//
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.sensorhub.android.comm.ble.BleConfig;
//import org.sensorhub.android.comm.ble.BleNetwork;
//import org.sensorhub.api.comm.IDeviceInfo;
//import org.sensorhub.api.comm.IDeviceScanCallback;
//import org.sensorhub.api.comm.IDeviceScanner;
//import org.sensorhub.api.common.SensorHubException;
//
//@RunWith(AndroidJUnit4.class)
//public class BleScanTest {
//
//    @Test
//    public void scanForDevices() throws SensorHubException {
//        BleConfig bleConfig = new BleConfig();
//
//        BleNetwork bleNetwork = new BleNetwork();
//        bleNetwork.init(bleConfig);
//        bleNetwork.start();
//
//        IDeviceScanner scanner = bleNetwork.getDeviceScanner();
//        scanner.startScan(new IDeviceScanCallback() {
//            @Override
//            public void onDeviceFound(IDeviceInfo info) {
//                System.out.println("Device detected");
//                System.out.println("Name: " + info.getName());
//                System.out.println("Address: " + info.getAddress());
//                System.out.println("RSSI: " + info.getSignalLevel());
//                System.out.println();
//            }
//
//            @Override
//            public void onScanError(Throwable e) {
//                e.printStackTrace();
//            }
//        });
//
//        try {
//            Thread.sleep(10000);
//        } catch (java.lang.Exception e) {
//            throw new RuntimeException(e);
//        }
//
//        scanner.stopScan();
//    }
//
//}
