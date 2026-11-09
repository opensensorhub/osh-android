package org.sensorhub.impl.sensor.meshtastic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import com.google.protobuf.CodedInputStream;

import net.opengis.sensorml.v20.PhysicalComponent;
import org.meshtastic.proto.MeshProtos;
import org.sensorhub.android.SensorHubService;
import org.sensorhub.android.comm.ble.BleConfig;
import org.sensorhub.android.comm.ble.BleNetwork;
import org.sensorhub.api.comm.ble.GattCallback;
import org.sensorhub.api.comm.ble.IGattCharacteristic;
import org.sensorhub.api.comm.ble.IGattClient;
import org.sensorhub.api.comm.ble.IGattDescriptor;
import org.sensorhub.api.comm.ble.IGattField;
import org.sensorhub.api.comm.ble.IGattService;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.IStreamingDataInterface;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.android.SensorMLBuilder;
import org.sensorhub.impl.sensor.meshtastic.control.TextMessageControl;
import org.sensorhub.impl.sensor.meshtastic.outputs.AbstractMeshtasticOutput;
import org.sensorhub.impl.sensor.meshtastic.outputs.MyNodeInfoOutput;
import org.sensorhub.impl.sensor.meshtastic.outputs.NodeInfoOutput;
import org.sensorhub.impl.sensor.meshtastic.outputs.PositionPacketOutput;
import org.sensorhub.impl.sensor.meshtastic.outputs.TextMessagePacketOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MeshtasticSensor extends AbstractSensorModule<MeshtasticConfig> {
    static final String UID_PREFIX = "urn:osh:sensor:meshtastic:";

    private static final UUID FROMRADIO_CHARACTERISTIC_UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002");
    private static final UUID TORADIO_CHARACTERISTIC_UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7");
    private static final UUID MESHTASTIC_SERVICE_UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd");
    private static final UUID FROMNUM_CHARACTERISTIC_UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453");
    AtomicBoolean readFromRadio = new AtomicBoolean(false);

    private final ArrayList<PhysicalComponent> smlComponents;
    private final SensorMLBuilder smlBuilder;
    private Context context;
    private boolean btConnected = false;
    private HandlerThread eventThread;
    private BleNetwork bleNetwork;
    private IGattClient gattClient;
    private IGattCharacteristic toRadioChar;
    private IGattCharacteristic fromRadioChar;
    private IGattCharacteristic fromNumChar;
    NodeInfoOutput nodeInfoOutput;
    MyNodeInfoOutput myNodeInfoOutput;
    PositionPacketOutput positionPacketOutput;
    TextMessagePacketOutput textMessagePacketOutput;
    TextMessageControl textMessageControl;


    public MeshtasticSensor() {
        this.smlComponents = new ArrayList<PhysicalComponent>();
        this.smlBuilder = new SensorMLBuilder();
    }

    @Override
    public boolean isConnected() {
        return btConnected;
    }

    @Override
    public void doInit() {
        this.xmlID = "MESHTASTIC" + Build.SERIAL;
        this.uniqueID = UID_PREFIX + config.getUidWithExt();

        context = SensorHubService.getContext();


        BleConfig bleConfig = new BleConfig();
        bleConfig.androidContext = context;

        bleNetwork = new BleNetwork();
        try {
            bleNetwork.init(bleConfig);
            bleNetwork.start();
        } catch (SensorHubException e) {
            throw new RuntimeException(e);
        }

        addOutputs();
        addControls();
    }

    private void addOutputs() {
        nodeInfoOutput = new NodeInfoOutput(this);
        myNodeInfoOutput = new MyNodeInfoOutput(this);
        positionPacketOutput = new PositionPacketOutput(this);
        textMessagePacketOutput = new TextMessagePacketOutput(this);

        addOutput(nodeInfoOutput, false);
        addOutput(myNodeInfoOutput, false);
        addOutput(positionPacketOutput, false);
        addOutput(textMessagePacketOutput, false);
    }

    private void addControls() {
        textMessageControl = new TextMessageControl(this);
        addControlInput(textMessageControl);
    }

    @Override
    public void doStart() throws SensorException {
        if (bleNetwork == null) {
            logger.error("BLE network is not initialized");
        }

        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH}, 1);
        }

        bleNetwork.connectGatt(config.device_name, gattCallback);

        eventThread = new HandlerThread("MeshtasticNodeEventThread");
        eventThread.start();
        Handler eventHandler = new Handler(eventThread.getLooper());
    }

    @Override
    public void doStop() {
        if (gattClient != null) {
            gattClient.disconnect();
            gattClient.close();
            gattClient = null;
        }

        if (bleNetwork != null) {
            try {
                bleNetwork.stop();
            } catch (SensorHubException e) {
                logger.error("Error stopping BLE network");
            }
            bleNetwork = null;
        }
    }


    private GattCallback gattCallback = new GattCallback() {
        @Override
        public void onConnected(IGattClient gatt, int status) {
//            super.onConnected(gatt, status);

            gattClient = gatt;
            btConnected = true;

            logger.info("Meshtastic node is connected");



            // set MTU size to 512
            boolean isRequest = gatt.requestMtu(512);

            if (isRequest)
                gattClient.discoverServices();
//            try {
//
//                Method refresh = gatt.getClass().getMethod("refresh");
//                refresh.invoke(gatt);
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException(e);
//            } catch (InvocationTargetException e) {
//                throw new RuntimeException(e);
//            } catch (NoSuchMethodException e) {
//                throw new RuntimeException(e);
//            }

//            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                gattClient.discoverServices();
//            }, 500);
        }

        @Override
        public void onDisconnected(IGattClient gatt, int status) {
//            super.onDisconnected(gatt, status);
            btConnected = false;
            logger.info("Meshtastic node is disconnected");
        }

        @Override
        public void onServicesDiscovered(IGattClient gatt, int status) {
//            super.onServicesDiscovered(gatt, status);

            IGattService meshtasticService = null;
            for (IGattService service : gattClient.getServices()) {

                UUID uuid = service.getType();
                if (uuid.equals(MESHTASTIC_SERVICE_UUID)) {
                    meshtasticService = service;
                }
            }


            for (IGattCharacteristic characteristic : meshtasticService.getCharacteristics()) {
                UUID uuid = characteristic.getType();

                if (uuid.equals(FROMNUM_CHARACTERISTIC_UUID)){
                    fromNumChar = characteristic;
                } else if (uuid.equals(FROMRADIO_CHARACTERISTIC_UUID)){
                    fromRadioChar = characteristic;
                } else if (uuid.equals(TORADIO_CHARACTERISTIC_UUID)){
                    toRadioChar = characteristic;
                }
            }


            MeshProtos.ToRadio handshake = MeshProtos.ToRadio.newBuilder()
                    .setWantConfigId(0)
                    .build();
            try {
                sendMessage(handshake);
                readFromRadioUntilEmpty();
            } catch (IOException e) {
                getLogger().error("Failed to send handshake message", e);
            }


            if (fromNumChar != null) {
                gattClient.setCharacteristicNotification(fromNumChar, true);
            }

            if (fromRadioChar != null) {
                gattClient.setCharacteristicNotification(fromRadioChar, true);
            }


            readFromRadioUntilEmpty();


            startProcessing();
        }

        @Override
        public void onCharacteristicChanged(IGattClient gatt, IGattField characteristic) {

            UUID uuid = characteristic.getType();

            if (uuid.equals(FROMNUM_CHARACTERISTIC_UUID)) {
                readFromRadioUntilEmpty();
                return;
            }
            if (uuid.equals(FROMRADIO_CHARACTERISTIC_UUID)) {
                byte[] data = characteristic.getValue().array();
                onMessage(data);
            }
        }

        @Override
        public void onCharacteristicRead(IGattClient gatt, IGattField characteristic, int status) {
           UUID uuid = characteristic.getType();

           if (status == 0) {
               if (uuid.equals(FROMRADIO_CHARACTERISTIC_UUID)) {
                   byte[] data = characteristic.getValue().array();
                   if (data.length > 0) {
                       onMessage(data);
                       gattClient.readCharacteristic(fromRadioChar); // keep reading until empty
                   } else {
                       readFromRadio.set(true);
                   }
//                   if (data.length == 0) {
//                       readFromRadio.set(true);
//                   } else {
//                       onMessage(data);
//
//                       if (!readFromRadio.get()) {
//                           gattClient.setCharacteristicNotification(fromRadioChar, true);
//                       }
//                   }
               }
           }
        }

        @Override
        public void onCharacteristicWrite(IGattClient gatt, IGattField characteristic, int status) {
        }

        @Override
        public void onDescriptorRead(IGattClient gatt, IGattDescriptor descriptor, int status) {
        }

        @Override
        public void onDescriptorWrite(IGattClient gatt, IGattDescriptor descriptor, int status) {
            if (status == 0) {

            }
        }
    };

    private void readFromRadioUntilEmpty() {
        if (fromRadioChar != null && gattClient != null) {
//            readFromRadio.set(false);
            gattClient.readCharacteristic(fromRadioChar);
        }
    }

    public void sendMessage(MeshProtos.ToRadio message) throws IOException {
        if (toRadioChar == null || gattClient == null) {

        }

        byte[] bytes = message.toByteArray();

        toRadioChar.setValue(ByteBuffer.wrap(bytes));

        boolean ok = gattClient.writeCharacteristic(toRadioChar);

        if (!ok) {
            logger.debug("failed to send message");
        }
    }

    private void onMessage(byte[] bytes) {
        try {
            MeshProtos.FromRadio msg = MeshProtos.FromRadio.parseFrom(bytes);
            for (Map.Entry<String, ? extends IStreamingDataInterface> entry : getOutputs().entrySet()) {
                AbstractMeshtasticOutput output = (AbstractMeshtasticOutput) entry.getValue();
                if (output.canHandle(msg))
                    output.onMessage(msg);
            }
        } catch (Exception e) {
            getLogger().error("Invalid protobuf: " + e.getMessage());
        }
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final AtomicBoolean bleBusy = new AtomicBoolean(false);

    private void startProcessing() {
        executor.scheduleWithFixedDelay(() -> {
            readFromRadioUntilEmpty();
        }, 1, 1, TimeUnit.SECONDS);
    }
}
