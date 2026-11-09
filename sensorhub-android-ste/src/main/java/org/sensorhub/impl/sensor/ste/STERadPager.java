package org.sensorhub.impl.sensor.ste;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.Toast;

import net.opengis.sensorml.v20.PhysicalComponent;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.data.IStreamingDataInterface;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.android.SensorMLBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class STERadPager extends AbstractSensorModule<STERadPagerConfig> {

    private static final UUID DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB");
    private static final UUID MODEL_NUMBER_CHARACTERISTIC = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB");
    private static final UUID UART_SERVICE = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455");
    private static final UUID RX_CHARACTERISTIC = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616");
    private static final UUID TX_CHARACTERISTIC = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3");
    private static final String DEVICE_NAME = "RADIATION PAGER";
    private final ArrayList<PhysicalComponent> smlComponents;
    private final SensorMLBuilder smlBuilder;

    private BluetoothGatt btGatt;
    private BluetoothAdapter btAdapter;
    private Context context;
    private BluetoothGattService deviceInformationService;
    private BluetoothGattService uartService;
    private BluetoothGattCharacteristic modelNumberCharacteristic;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private Timer txNotificationTimer;
    STERadPagerOutput output;
//    STERadPagerLocationOutput locationOutput;
    private boolean btConnected = false;
    private LocationManager locationManager;
    private HandlerThread eventThread;

    public STERadPager() {
        this.smlComponents = new ArrayList<PhysicalComponent>();
        this.smlBuilder = new SensorMLBuilder();
    }

    @Override
    public boolean isConnected() {
        return btConnected;
    }

    @Override
    public void doInit() {
        this.xmlID = "STE_RADPAGER_" + Build.SERIAL;
        this.uniqueID = STERadPagerConfig.getUid();

        context = SensorHubService.getContext();

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Activity.BLUETOOTH_SERVICE);
        btAdapter = bluetoothManager.getAdapter();

        if (btAdapter == null || !btAdapter.isEnabled()) {
            Toast.makeText(context, "Bluetooth is not enabled", Toast.LENGTH_LONG).show();
        }
//        output = new STERadPagerOutput(this);


        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
            this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            List<String> locProviders = locationManager.getAllProviders();
            for (String provName : locProviders) {
                logger.debug("Detected location provider " + provName);
                LocationProvider locProvider = locationManager.getProvider(provName);

                // keep only GPS for now
                if (locProvider.requiresSatellite()) {
//                    locationOutput = new STERadPagerLocationOutput(this, locationManager, locProvider);
                    output = new STERadPagerOutput(this, locationManager, locProvider);
                    useLocationProvider(output, locProvider);
                }
            }
        }
        output.doInit();
        addOutput(output, false);
    }

    @Override
    public void doStart() throws SensorException {
        try {
            Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
            BluetoothDevice device = null;
            for (BluetoothDevice d : devices) {
                if (DEVICE_NAME.equals(d.getName())) {
                    device = d;
                }
            }
            if (null == device) {
                throw new SensorException("Could not find Bluetooth device, unable to start.");
            }
    
            btGatt = device.connectGatt(context, true, gattCallback);
        } catch (SecurityException e) {
            throw new SensorException("Bluetooth permission is required to start the radiation pager.", e);
        }

        eventThread = new HandlerThread("STERadPagerEventThread");
        eventThread.start();
        Handler eventHandler = new Handler(eventThread.getLooper());

//        locationOutput.doStart(eventHandler);
        output.doStart(eventHandler);
    }

    @Override
    public void doStop() {
        if (txNotificationTimer != null) txNotificationTimer.cancel();
        btConnected = false;
        if (btGatt != null) {
            try {
                btGatt.disconnect();
            } catch (SecurityException e) {
                logger.warn("Bluetooth permission revoked while disconnecting radiation pager", e);
            }
            try {
                btGatt.close();
            } catch (SecurityException e) {
                logger.warn("Bluetooth permission revoked while closing radiation pager", e);
            }
            btGatt = null;
        }
        if (eventThread != null) eventThread.quitSafely();
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                btConnected = true;
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    handleBluetoothPermissionLoss(e);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                btConnected = false;
                if (txNotificationTimer != null) txNotificationTimer.cancel();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(RX_CHARACTERISTIC)) {
                parseMessage(characteristic.getValue());
            } else if (characteristic.getUuid().equals(TX_CHARACTERISTIC)) {

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            deviceInformationService = btGatt.getService((DEVICE_INFORMATION_SERVICE));
            uartService = btGatt.getService(UART_SERVICE);
            modelNumberCharacteristic = deviceInformationService.getCharacteristic(MODEL_NUMBER_CHARACTERISTIC);
            rxCharacteristic = uartService.getCharacteristic(RX_CHARACTERISTIC);
            txCharacteristic = uartService.getCharacteristic(TX_CHARACTERISTIC);


            try {
                gatt.setCharacteristicNotification(modelNumberCharacteristic, true);
    
                gatt.setCharacteristicNotification(rxCharacteristic, true);
                BluetoothGattDescriptor rxDescriptor = rxCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                rxDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(rxDescriptor);
    
                gatt.setCharacteristicNotification(txCharacteristic, true);
            } catch (SecurityException e) {
                handleBluetoothPermissionLoss(e);
                return;
            }

            txNotificationTimer = new Timer();
            TimerTask txNotificationTask = new TimerTask() {
                @Override
                public void run() {
                    txCharacteristic.setValue("?");
                    try {
                        gatt.writeCharacteristic(txCharacteristic);
                    } catch (SecurityException e) {
                        cancel();
                        handleBluetoothPermissionLoss(e);
                    }
                }
            };
            txNotificationTimer.schedule(txNotificationTask, 0, 1000);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logger.info("Failed to write to characteristic");
            }
        }

        private void parseMessage(byte[] characteristicValue) {
            String message = new String(characteristicValue, StandardCharsets.UTF_8);
            message = message.replaceAll("\r\n", "");
            String[] splitMsg = message.split(",");

            if (splitMsg[0].equals("GT")) {
                logger.info("Alarm: {}, Counts: {}, Threshold: {}", splitMsg[1], splitMsg[2], splitMsg[3]);
                output.insertSensorData(splitMsg);
            } else {
                logger.info("Unknown message: {}", message);
            }
        }
    };

    private void handleBluetoothPermissionLoss(SecurityException error) {
        btConnected = false;
        if (txNotificationTimer != null) txNotificationTimer.cancel();
        reportError("Bluetooth permission was revoked. Grant permission and restart the radiation pager.", error);
    }

    protected void useLocationProvider(IStreamingDataInterface output, LocationProvider locProvider) {
        addOutput(output, false);
        smlComponents.add(smlBuilder.getComponentDescription(locationManager, locProvider));
        logger.info("Getting data from " + locProvider.getName() + " location provider");
    }
}
