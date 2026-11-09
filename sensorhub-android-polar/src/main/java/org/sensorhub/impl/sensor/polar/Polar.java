/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 The Initial Developer is Botts Innovative Research Inc. Portions created by the Initial
 Developer are Copyright (C) 2025 the Initial Developer. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.polar;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.model.PolarAccelerometerData;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarEcgData;
import com.polar.sdk.api.model.PolarHrData;
import com.polar.sdk.api.model.PolarPpiData;

import net.opengis.sensorml.v20.PhysicalComponent;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.android.SensorMLBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * OpenSensorHub driver for Polar H9/H10 heart rate monitors.
 * <p>
 * Uses the Polar BLE SDK for all communication, supporting:
 * <ul>
 *   <li>Heart Rate (all models) via SDK callback</li>
 *   <li>Battery Level (all models) via SDK callback</li>
 *   <li>PPI / RR Intervals (all models) via SDK streaming</li>
 *   <li>ECG waveform at 130 Hz (H10 only) via SDK streaming</li>
 *   <li>3-axis accelerometer (H10 only) via SDK streaming</li>
 * </ul>
 *
 * @author Kalyn Stricklin
 * @since Jan 13, 2023
 */
public class Polar extends AbstractSensorModule<PolarConfig> {
    static final String UID_PREFIX = "urn:osh:sensor:polar:";

    private final ArrayList<PhysicalComponent> smlComponents;
    private final SensorMLBuilder smlBuilder;
    static final Logger logger = LoggerFactory.getLogger(Polar.class.getSimpleName());

    private Context context;
    private BluetoothAdapter btAdapter;

    HeartRateOutput heartRateOutput;
    BatteryOutput batteryOutput;
    PPIOutput ppiOutput;
    ECGOutput ecgOutput;
    AccelerometerOutput accelerometerOutput;

    PolarBleApi api;
    private boolean deviceConnected = false;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public Polar() {
        this.smlComponents = new ArrayList<PhysicalComponent>();
        this.smlBuilder = new SensorMLBuilder();
    }

    @Override
    public void doInit() {
        logger.info("Initializing Polar sensor driver");
        this.xmlID = "POLAR_" + Build.SERIAL;
        this.uniqueID = UID_PREFIX + config.getUidWithExt();

        context = SensorHubService.getContext();

        initPolarApi();

        heartRateOutput = new HeartRateOutput(this);
        heartRateOutput.doInit();
        addOutput(heartRateOutput, false);

        batteryOutput = new BatteryOutput(this);
        batteryOutput.doInit();
        addOutput(batteryOutput, false);

        if (config.enablePpi) {
            ppiOutput = new PPIOutput(this);
            ppiOutput.doInit();
            addOutput(ppiOutput, false);
        }

        // H10 only
        if (config.enableEcg) {
            ecgOutput = new ECGOutput(this);
            ecgOutput.doInit();
            addOutput(ecgOutput, false);
        }

        // H10 only
        if (config.enableAccelerometer) {
            accelerometerOutput = new AccelerometerOutput(this);
            accelerometerOutput.doInit();
            addOutput(accelerometerOutput, false);
        }
    }

    @Override
    public void doStart() throws SensorException {
        if (config.deviceId == null || config.deviceId.isEmpty()) {
            throw new SensorException("Polar device ID is required (printed on the device)");
        }

        try {
            api.connectToDevice(config.deviceId);
            logger.info("Connecting to Polar device: {}", config.deviceId);
        } catch (Exception e) {
            throw new SensorException("Failed to connect to Polar device: " + config.deviceId, e);
        }
    }

    @Override
    public void doStop() {
        disposables.clear();

        if (api != null && config.deviceId != null) {
            try {
                api.disconnectFromDevice(config.deviceId);
            } catch (Exception e) {
                logger.error("Error disconnecting from Polar device", e);
            }
        }

        deviceConnected = false;
    }

    @Override
    public boolean isConnected() {
        return deviceConnected;
    }

    private void startStreaming(Set<? extends PolarBleApi.PolarDeviceDataType> availableTypes) {
        String deviceId = config.deviceId;

        if (ppiOutput != null && availableTypes.contains(PolarBleApi.PolarDeviceDataType.PPI)) {
            Disposable ppiDisposable = api.startPpiStreaming(deviceId)
                    .subscribe(
                            ppiData -> {
                                for (PolarPpiData.PolarPpiSample sample : ppiData.getSamples()) {
                                    ppiOutput.setData(
                                            sample.getPpi(),
                                            sample.getHr(),
                                            sample.getSkinContactSupported(),
                                            sample.getSkinContactStatus()
                                    );
                                }
                            },
                            error -> logger.error("PPI streaming error", error)
                    );
            disposables.add(ppiDisposable);
            logger.info("PPI streaming started");
        }

        // ECG streaming H10 only
        if (ecgOutput != null && availableTypes.contains(PolarBleApi.PolarDeviceDataType.ECG)) {
            Disposable ecgDisposable = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
                    .flatMapPublisher(settings -> api.startEcgStreaming(deviceId, settings))
                    .subscribe(
                            ecgData -> {
                                for (PolarEcgData.PolarEcgDataSample sample : ecgData.getSamples()) {
                                    ecgOutput.setData(sample.getVoltage());
                                }
                            },
                            error -> logger.error("ECG streaming error", error)
                    );
            disposables.add(ecgDisposable);
            logger.info("ECG streaming started");
        }

        // Accelerometer streaming H10 only
        if (accelerometerOutput != null && availableTypes.contains(PolarBleApi.PolarDeviceDataType.ACC)) {
            Disposable accDisposable = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
                    .flatMapPublisher(settings -> api.startAccStreaming(deviceId, settings))
                    .subscribe(
                            accData -> {
                                for (PolarAccelerometerData.PolarAccelerometerDataSample sample : accData.getSamples()) {
                                    accelerometerOutput.setData(
                                            sample.getX(),
                                            sample.getY(),
                                            sample.getZ()
                                    );
                                }
                            },
                            error -> logger.error("Accelerometer streaming error", error)
                    );
            disposables.add(accDisposable);
            logger.info("Accelerometer streaming started");
        }
    }

    /**
     * Initializes the Polar BLE SDK with all supported features and sets up callbacks
     * for HR, battery, device connection
     */
    private void initPolarApi() {
        Set<PolarBleApi.PolarBleSdkFeature> features = new HashSet<>(Arrays.asList(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
        ));

        api = PolarBleApiDefaultImpl.defaultImplementation(context.getApplicationContext(), features);
        api.setApiCallback(new PolarBleApiCallback() {

            @Override
            public void batteryLevelReceived(@NonNull String identifier, int level) {
                super.batteryLevelReceived(identifier, level);
                logger.debug("Battery level: {}%", level);
                if (batteryOutput != null) {
                    batteryOutput.setData(level);
                }
            }

            @Override
            public void blePowerStateChanged(boolean powered) {
                super.blePowerStateChanged(powered);
                if (powered) {
                    BluetoothManager bluetoothManager =
                            (BluetoothManager) context.getSystemService(Activity.BLUETOOTH_SERVICE);
                    btAdapter = bluetoothManager.getAdapter();
                    if (btAdapter == null || !btAdapter.isEnabled()) {
                        logger.warn("Bluetooth adapter is null or not enabled");
                    }
                } else {
                    logger.warn("Bluetooth powered off");
                }
            }

            @Override
            public void bleSdkFeatureReady(@NonNull String identifier,
                                           @NonNull PolarBleApi.PolarBleSdkFeature feature) {
                super.bleSdkFeatureReady(identifier, feature);
                logger.info("SDK feature ready: {}", feature);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                super.deviceConnected(polarDeviceInfo);
                deviceConnected = true;
                logger.info("Connected to Polar device: {}", polarDeviceInfo.getDeviceId());
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                super.deviceConnecting(polarDeviceInfo);
                logger.info("Connecting to Polar device: {}", polarDeviceInfo.getDeviceId());
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                super.deviceDisconnected(polarDeviceInfo);
                deviceConnected = false;
                logger.info("Disconnected from Polar device: {}", polarDeviceInfo.getDeviceId());
            }

            @Override
            public void disInformationReceived(@NonNull String identifier,
                                               @NonNull UUID uuid, @NonNull String value) {
                super.disInformationReceived(identifier, uuid, value);
                logger.debug("Device info - {}: {}", uuid, value);
            }

            @Override
            public void hrFeatureReady(@NonNull String identifier) {
                super.hrFeatureReady(identifier);
                logger.info("Heart Rate feature ready");
            }

            @Override
            public void hrNotificationReceived(@NonNull String identifier,
                                               @NonNull PolarHrData.PolarHrSample data) {
                super.hrNotificationReceived(identifier, data);

                int hr = data.getHr();
                heartRateOutput.setData(hr);

                List<Integer> rrsMs = data.getRrsMs();
                if (rrsMs != null && !rrsMs.isEmpty()) {
                    logger.trace("HR: {} bpm, RR intervals: {}", hr, rrsMs);
                }
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String identifier) {
                super.polarFtpFeatureReady(identifier);
            }

            @Override
            public void sdkModeFeatureAvailable(@NonNull String identifier) {
                super.sdkModeFeatureAvailable(identifier);
            }

            @Override
            public void streamingFeaturesReady(@NonNull String identifier,
                                               @NonNull Set<? extends PolarBleApi.PolarDeviceDataType> features) {
                super.streamingFeaturesReady(identifier, features);
                logger.info("Streaming features ready: {}", features);
                startStreaming(features);
            }
        });
    }
}
