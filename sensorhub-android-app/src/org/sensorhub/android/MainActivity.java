/*************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import org.sensorhub.android.comm.BluetoothCommProvider;
import org.sensorhub.android.comm.BluetoothCommProviderConfig;
import org.sensorhub.android.comm.ble.BleConfig;
import org.sensorhub.android.comm.ble.BleNetwork;
import org.sensorhub.api.common.Event;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.module.IModuleConfigRepository;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.impl.client.sost.SOSTClient;
import org.sensorhub.impl.client.sost.SOSTClient.StreamInfo;
import org.sensorhub.impl.client.sost.SOSTClientConfig;
import org.sensorhub.impl.driver.flir.FlirOneCameraConfig;
import org.sensorhub.impl.module.InMemoryConfigDb;
import org.sensorhub.impl.persistence.GenericStreamStorage;
import org.sensorhub.impl.persistence.MaxAgeAutoPurgeConfig;
import org.sensorhub.impl.persistence.StreamStorageConfig;
import org.sensorhub.impl.persistence.h2.MVMultiStorageImpl;
import org.sensorhub.impl.persistence.h2.MVStorageConfig;
import org.sensorhub.impl.sensor.android.AndroidSensorsConfig;
import org.sensorhub.impl.sensor.angel.AngelSensorConfig;
import org.sensorhub.impl.sensor.blebeacon.BLEBeacon;
import org.sensorhub.impl.sensor.blebeacon.BLEBeaconConfig;
import org.sensorhub.impl.sensor.blebeacon.BLEBeaconDriver;
import org.sensorhub.impl.sensor.swe.ProxySensor.ProxySensorConfig;
import org.sensorhub.impl.sensor.trupulse.TruPulseConfig;
import org.sensorhub.impl.service.HttpServerConfig;
import org.sensorhub.impl.service.sos.SOSServiceConfig;
import org.sensorhub.impl.service.sos.SensorDataProviderConfig;
import org.sensorhub.test.sensor.trupulse.SimulatedDataStream;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, IEventListener {
    public static final String ACTION_BROADCAST_RECEIVER = "org.sensorhub.android.BROADCAST_RECEIVER";

    String deviceID;
    String deviceName;
    String runName;

    enum Sensors {
        Android,
        TruPulse,
        TruPulseSim,
        Angel,
        FlirOne,
        DJIDrone,
        ProxySensor
    }

    TextView textArea;
    SensorHubService boundService;
    IModuleConfigRepository sensorhubConfig;
    Handler displayHandler;
    Runnable displayCallback;
    StringBuffer displayText = new StringBuffer();
    boolean oshStarted = false;
    ArrayList<SOSTClient> sostClients = new ArrayList<>();
    ArrayList<ProxySensorConfig> proxySensorConfigs = new ArrayList<>();
    URL sosUrl = null;
    URL sostUrl = null;
    boolean showVideo;

    private ServiceConnection sConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            boundService = ((SensorHubService.LocalBinder) service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            boundService = null;
        }
    };


    protected void updateConfig(SharedPreferences prefs, String runName) {
        // get device name
        deviceID = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        deviceName = prefs.getString("device_name", null);
        if (deviceName == null || deviceName.length() < 2)
            deviceName = deviceID;
        this.runName = runName;

        sensorhubConfig = new InMemoryConfigDb();

        // get SOS URL from config
        String sosUriConfig = prefs.getString("sos_uri", "http://127.0.0.1:8585/sensorhub/sos");
        String sosUser = prefs.getString("sos_username", "");
        String sosPwd = prefs.getString("sos_password", "");
        if (sosUriConfig != null && sosUriConfig.trim().length() > 0) {
            try {
                sosUrl = new URL(sosUriConfig);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        // HTTP Server Config
        HttpServerConfig serverConfig = new HttpServerConfig();
        serverConfig.proxyBaseUrl = "";
        serverConfig.httpPort = 8585;
        serverConfig.autoStart = true;
        sensorhubConfig.add(serverConfig);

        // SOS Config
        SOSServiceConfig sosConfig = new SOSServiceWithIPCConfig();
        sosConfig.moduleClass = SOSServiceWithIPC.class.getCanonicalName();
        ((SOSServiceWithIPCConfig) sosConfig).androidContext = this.getApplicationContext();
        sosConfig.id = "SOS_SERVICE";
        sosConfig.name = "SOS Service";
        sosConfig.autoStart = true;
        sosConfig.enableTransactional = true;

        File dbFile = new File(getApplicationContext().getFilesDir() + "/db/");
        dbFile.mkdirs();
        MVStorageConfig basicStorageConfig = new MVStorageConfig();
        basicStorageConfig.moduleClass = "org.sensorhub.impl.persistence.h2.MVObsStorageImpl";
        basicStorageConfig.storagePath = dbFile.getAbsolutePath() + "/${STORAGE_ID}.dat";
        basicStorageConfig.autoStart = true;
        sosConfig.newStorageConfig = basicStorageConfig;

        // Push Sensors Config
        AndroidSensorsConfig androidSensorsConfig = (AndroidSensorsConfig) createSensorConfig(Sensors.Android);
        sensorhubConfig.add(androidSensorsConfig);
        if (isPushingSensor(Sensors.Android)) {
            addSosTConfig(androidSensorsConfig, sosUser, sosPwd);
        }

        StreamStorageConfig androidStreamStorageConfig = createStreamStorageConfig(androidSensorsConfig);
        addStorageConfig(androidSensorsConfig, androidStreamStorageConfig);

        SensorDataProviderConfig androidDataProviderConfig = createDataProviderConfig(androidSensorsConfig);
        addSosServerConfig(sosConfig, androidDataProviderConfig);

        // TruPulse sensor
        boolean enabled = prefs.getBoolean("trupulse_enable", false);
        if (enabled) {
            String truPulseDevice = prefs.getString("trupulse_datasource", "SIMULATED");
            TruPulseConfig truPulseConfig = truPulseDevice == "SIMULATED"
                    ? (TruPulseConfig) createSensorConfig(Sensors.TruPulseSim)
                    : (TruPulseConfig) createSensorConfig(Sensors.TruPulse);
            sensorhubConfig.add(truPulseConfig);
            if (isPushingSensor(Sensors.TruPulse)) {
                addSosTConfig(truPulseConfig, sosUser, sosPwd);
            }
        }

        // AngelSensor
        enabled = prefs.getBoolean("angel_enabled", false);
        if (enabled) {
            AngelSensorConfig angelConfig = (AngelSensorConfig) createSensorConfig(Sensors.Angel);
            //angelConfig.btAddress = "00:07:80:79:04:AF"; // mike
            //angelConfig.btAddress = "00:07:80:03:0E:0A"; // alex
            angelConfig.btAddress = prefs.getString("angel_address", null);
            sensorhubConfig.add(angelConfig);
            addSosTConfig(angelConfig, sosUser, sosPwd);
        }

        // FLIR One sensor
        enabled = prefs.getBoolean("flirone_enabled", false);
        if (enabled) {
            showVideo = true;

            FlirOneCameraConfig flironeConfig = (FlirOneCameraConfig) createSensorConfig(Sensors.FlirOne);
            sensorhubConfig.add(flironeConfig);
            addSosTConfig(flironeConfig, sosUser, sosPwd);
        }

        /*
        // DJI Drone
        enabled = prefs.getBoolean("dji_enabled", false);
        if (enabled)
        {
            DjiConfig djiConfig = new DjiConfig();
            djiConfig.id = "DJI_DRONE";
            djiConfig.name = "DJI Aircraft [" + deviceName + "]";
            djiConfig.autoStart = true;
            djiConfig.androidContext = this.getApplicationContext();
            djiConfig.camPreviewTexture = boundService.getVideoTexture();
            showVideo = true;
            sensorhubConfig.add(djiConfig);
            addSosTConfig(djiConfig, sosUser, sosPwd);

            SensorDataProviderConfig djiDataProviderConfig = new SensorDataProviderConfig();
            djiDataConsumerConfig.sensorID = djiConfig.id;
            djiDataConsumerConfig.offeringID = djiConfig.id+"-sos";
            djiDataConsumerConfig.enabled = true;
            sosConfig.dataConsumers.add(djiDataConsumerConfig);
        }
        */

        /*
        // Get SOS-T URL from config
        String sostUriConfig = prefs.getString("sost_uri", "");
        String sostUser = prefs.getString("sost_username", "");
        String sostPwd = prefs.getString("sost_password", "");
        if (sostUriConfig != null && sostUriConfig.trim().length() > 0)
        {
            try {
                sostUrl = new URL(sostUriConfig);
            }
            catch (MalformedURLException e)
            {
                e.printStackTrace();
            }
        }

        if (!sostUriConfig.contentEquals(""))
        {
            // SOS-T Client Config
            SOSTClientConfig sostConfig = new SOSTClientConfig();
            sostConfig.sensorID = androidSensorsConfig.id;
            sostConfig.moduleClass = SOSTClient.class.getCanonicalName();
            sostConfig.id = "SOST_CLIENT";
            sostConfig.name = "SOS-T Client";
            sostConfig.autoStart = true;

            // SOS-T Client HTTP Config
            sostConfig.sos = new HTTPConfig();
            sostConfig.sos.enableTLS = sostUrl.getProtocol().toUpperCase().contains("HTTPS") ? true : false;
            sostConfig.sos.remoteHost = sostUrl.getHost();
            sostConfig.sos.remotePort = sostUrl.getPort();
            sostConfig.sos.resourcePath = sostUrl.getPath();
            sostConfig.sos.user = sostUser;
            sostConfig.sos.password = sostPwd;

            sostConfig.connection = new SOSTClientConfig.SOSConnectionConfig();
            sostConfig.connection.usePersistentConnection = true;
            sostConfig.connection.maxQueueSize = 10;
            sostConfig.connection.maxConnectErrors = 10;
            sostConfig.connection.checkReachability = true;
            sostConfig.connection.connectTimeout = 3000;
            sostConfig.connection.reconnectPeriod = 10000;
            sostConfig.connection.reconnectAttempts = 10;

            sensorhubConfig.add(sostConfig);
        }
        */

        // SOS Data Providers
        for (ProxySensorConfig proxySensorConfig : proxySensorConfigs) {
            sensorhubConfig.add(proxySensorConfig);

            SensorDataProviderConfig dataProviderConfig = new SensorDataProviderConfig();
            dataProviderConfig.name = proxySensorConfig.id;
            dataProviderConfig.sensorID = proxySensorConfig.id;
            dataProviderConfig.offeringID = proxySensorConfig.id + "-sos";
            dataProviderConfig.enabled = true;

            sosConfig.dataProviders.add(dataProviderConfig);
        }

        sensorhubConfig.add(sosConfig);
    }

    private boolean isPushingSensor(Sensors sensor) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        if (Sensors.Android.equals(sensor)) {
            if (prefs.getBoolean("accelerometer_enable", false)
                    && prefs.getStringSet("accelerometer_options", Collections.emptySet()).contains("PUSH_REMOTE"))
                return true;
            if (prefs.getBoolean("gyroscope_enable", false)
                    && prefs.getStringSet("gyroscope_options", Collections.emptySet()).contains("PUSH_REMOTE"))
                return true;
            if (prefs.getBoolean("magnetometer_enable", false)
                    && prefs.getStringSet("magnetometer_options", Collections.emptySet()).contains("PUSH_REMOTE"))
                return true;
            if (prefs.getBoolean("orientation_enable", false)
                    && prefs.getStringSet("orientation_options", Collections.emptySet()).contains("PUSH_REMOTE"))
                return true;
            if (prefs.getBoolean("location_enable", false)
                    && prefs.getStringSet("location_options", Collections.emptySet()).contains("PUSH_REMOTE"))
                return true;
            return prefs.getBoolean("video_enable", false)
                    && prefs.getStringSet("video_options", Collections.emptySet()).contains("PUSH_REMOTE");
        } else if (Sensors.TruPulse.equals(sensor) || Sensors.TruPulseSim.equals(sensor)) {
            return prefs.getBoolean("trupulse_enable", false)
                    && prefs.getStringSet("trupulse_options", Collections.emptySet()).contains("PUSH_REMOTE");
        }

        return false;
    }

    private SensorDataProviderConfig createDataProviderConfig(AndroidSensorsConfig sensorConfig) {
        SensorDataProviderConfig dataProviderConfig = new SensorDataProviderConfig();
        dataProviderConfig.sensorID = sensorConfig.id;
        dataProviderConfig.offeringID = sensorConfig.id + ":offering";
        dataProviderConfig.storageID = sensorConfig.id + "#storage";
        dataProviderConfig.enabled = true;
        dataProviderConfig.liveDataTimeout = 600.0;
        dataProviderConfig.maxFois = 10;

        return dataProviderConfig;
    }

    private StreamStorageConfig createStreamStorageConfig(AndroidSensorsConfig sensorConfig) {
        // H2 Storage Config
        File dbFile = new File(getApplicationContext().getFilesDir() + "/db/", deviceID + "_h2.dat");
        dbFile.getParentFile().mkdirs();
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        MVStorageConfig storageConfig = new MVStorageConfig();
        storageConfig.moduleClass = MVMultiStorageImpl.class.getCanonicalName();
        storageConfig.storagePath = dbFile.getPath();
        storageConfig.autoStart = true;
        storageConfig.memoryCacheSize = 102400;
        storageConfig.autoCommitBufferSize = 1024;

        // TODO: Base this on size instead of time. This might error when earliest record is purged and then requested. Test if the capabilities updates...
        // Auto Purge Config
        MaxAgeAutoPurgeConfig autoPurgeConfig = new MaxAgeAutoPurgeConfig();
        autoPurgeConfig.enabled = true;
        autoPurgeConfig.purgePeriod = 24.0 * 60.0 * 60.0;
        autoPurgeConfig.maxRecordAge = 24.0 * 60.0 * 60.0;

        // Stream Storage Config
        StreamStorageConfig streamStorageConfig = new StreamStorageConfig();
        streamStorageConfig.moduleClass = GenericStreamStorage.class.getCanonicalName();
        streamStorageConfig.id = sensorConfig.id + "#storage";
        streamStorageConfig.name = sensorConfig.name + " Storage";
        streamStorageConfig.dataSourceID = sensorConfig.id;
        streamStorageConfig.autoStart = true;
        streamStorageConfig.processEvents = true;
        streamStorageConfig.minCommitPeriod = 10000;
        streamStorageConfig.autoPurgeConfig = autoPurgeConfig;
        streamStorageConfig.storageConfig = storageConfig;
        return streamStorageConfig;
    }

    private SensorConfig createSensorConfig(Sensors sensor) {
        SensorConfig sensorConfig;

        if (Sensors.Android.equals(sensor)) {
            sensorConfig = new AndroidSensorsConfig();
            sensorConfig.id = "urn:android:device:" + deviceID;
            sensorConfig.name = "Android Sensors [" + deviceName + "]";
            sensorConfig.autoStart = true;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

            ((AndroidSensorsConfig) sensorConfig).activateAccelerometer = prefs.getBoolean("accelerometer_enable", false);
            ((AndroidSensorsConfig) sensorConfig).activateGyrometer = prefs.getBoolean("gyroscope_enable", false);
            ((AndroidSensorsConfig) sensorConfig).activateMagnetometer = prefs.getBoolean("magnetometer_enable", false);
            if (prefs.getBoolean("orientation_enable", false)) {
                ((AndroidSensorsConfig) sensorConfig).activateOrientationQuat = prefs.getStringSet("orientation_angles", Collections.emptySet()).contains("QUATERNION");
                ((AndroidSensorsConfig) sensorConfig).activateOrientationEuler = prefs.getStringSet("orientation_angles", Collections.emptySet()).contains("EULER");
            }
            if (prefs.getBoolean("location_enable", false)) {
                ((AndroidSensorsConfig) sensorConfig).activateGpsLocation = prefs.getStringSet("location_type", Collections.emptySet()).contains("GPS");
                ((AndroidSensorsConfig) sensorConfig).activateNetworkLocation = prefs.getStringSet("location_type", Collections.emptySet()).contains("NETWORK");
            }
            if (prefs.getBoolean("video_enable", false)) {
                showVideo = true;

                ((AndroidSensorsConfig) sensorConfig).activateBackCamera = true;
                ((AndroidSensorsConfig) sensorConfig).videoCodec = prefs.getString("video_codec", AndroidSensorsConfig.JPEG_CODEC);
            }

            ((AndroidSensorsConfig) sensorConfig).androidContext = this.getApplicationContext();
            ((AndroidSensorsConfig) sensorConfig).camPreviewTexture = boundService.getVideoTexture();
            ((AndroidSensorsConfig) sensorConfig).runName = runName;
        } else if (Sensors.TruPulse.equals(sensor)) {
            sensorConfig = new TruPulseConfig();
            sensorConfig.id = "TRUPULSE_SENSOR";
            sensorConfig.name = "TruPulse Range Finder [" + deviceName + "]";
            sensorConfig.autoStart = true;

            BluetoothCommProviderConfig btConf = new BluetoothCommProviderConfig();
            btConf.protocol.deviceName = "TP360RB.*";
            btConf.moduleClass = BluetoothCommProvider.class.getCanonicalName();
            ((TruPulseConfig) sensorConfig).commSettings = btConf;
            ((TruPulseConfig) sensorConfig).serialNumber = deviceID;
        } else if (Sensors.TruPulseSim.equals(sensor)) {
            sensorConfig = new TruPulseConfig();
            sensorConfig.id = "TRUPULSE_SENSOR_SIMULATED";
            sensorConfig.name = "Simulated TruPulse Range Finder [" + deviceName + "]";
            sensorConfig.autoStart = true;

            BluetoothCommProviderConfig btConf = new BluetoothCommProviderConfig();
            btConf.protocol.deviceName = "TP360RB.*";
            btConf.moduleClass = SimulatedDataStream.class.getCanonicalName();
            ((TruPulseConfig) sensorConfig).commSettings = btConf;
            ((TruPulseConfig) sensorConfig).serialNumber = deviceID;
        } else if (Sensors.Angel.equals(sensor)) {
            sensorConfig = new AngelSensorConfig();
            sensorConfig.id = "ANGEL_SENSOR";
            sensorConfig.name = "Angel Sensor [" + deviceName + "]";
            sensorConfig.autoStart = true;

            BleConfig bleConf = new BleConfig();
            bleConf.id = "BLE";
            bleConf.moduleClass = BleNetwork.class.getCanonicalName();
            bleConf.androidContext = this.getApplicationContext();
            bleConf.autoStart = true;
            sensorhubConfig.add(bleConf);

            ((AngelSensorConfig) sensorConfig).networkID = bleConf.id;
        } else if (Sensors.FlirOne.equals(sensor)) {
            sensorConfig = new FlirOneCameraConfig();
            sensorConfig.id = "FLIRONE_SENSOR";
            sensorConfig.name = "FLIR One Camera [" + deviceName + "]";
            sensorConfig.autoStart = true;

            ((FlirOneCameraConfig) sensorConfig).androidContext = this.getApplicationContext();
            ((FlirOneCameraConfig) sensorConfig).camPreviewTexture = boundService.getVideoTexture();
        } else if (Sensors.ProxySensor.equals(sensor)) {
            sensorConfig = new ProxySensorConfig();
        } else {
            sensorConfig = new SensorConfig();
        }

        return sensorConfig;
    }

    protected void addStorageConfig(SensorConfig sensorConf, StreamStorageConfig storageConf) {
        if (sensorConf instanceof AndroidSensorsConfig) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            SensorManager sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

            String sensorName;
            for (Sensor sensor : deviceSensors) {
                if (sensor.isWakeUpSensor()) {
                    continue;
                }

                Log.d(TAG, "addStorageConfig: sensor: " + sensor.getName());

                switch (sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        if (!prefs.getBoolean("accelerometer_enable", false)
                                || !prefs.getStringSet("accelerometer_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                            Log.d(TAG, "addStorageConfig: excluding accelerometer");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            storageConf.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addStorageConfig: NOT excluding accelerometer");
                        }
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                        if (!prefs.getBoolean("gyroscope_enable", false)
                                || !prefs.getStringSet("gyroscope_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                            Log.d(TAG, "addStorageConfig: excluding gyroscope");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            storageConf.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addStorageConfig: NOT excluding gyroscope");
                        }
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        if (!prefs.getBoolean("magnetometer_enable", false)
                                || !prefs.getStringSet("magnetometer_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                            Log.d(TAG, "addStorageConfig: excluding magnetometer");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            storageConf.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addStorageConfig: NOT excluding magnetometer");
                        }
                        break;
                    case Sensor.TYPE_ROTATION_VECTOR:
                        if (!prefs.getBoolean("orientation_enable", false)
                                || !prefs.getStringSet("orientation_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                            Log.d(TAG, "addStorageConfig: excluding orientation");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            storageConf.excludedOutputs.add(sensorName);
                            sensorName = "quat_orientation_data";
                            storageConf.excludedOutputs.add(sensorName);
                            sensorName = "euler_orientation_data";
                            storageConf.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addStorageConfig: NOT excluding orientation");
                        }
                        break;
                }
            }
            if (!prefs.getBoolean("location_enable", false)
                    || !prefs.getStringSet("location_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                Log.d(TAG, "addStorageConfig: excluding location");

                sensorName = "gps_data";
                storageConf.excludedOutputs.add(sensorName);
                sensorName = "network_data";
                storageConf.excludedOutputs.add(sensorName);
            } else {
                Log.d(TAG, "addStorageConfig: NOT excluding location");
            }
            if (!prefs.getBoolean("video_enable", false)
                    || !prefs.getStringSet("video_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                Log.d(TAG, "addStorageConfig: excluding video");

                sensorName = "camera0_MJPEG";
                storageConf.excludedOutputs.add(sensorName);
                sensorName = "camera0_H264";
                storageConf.excludedOutputs.add(sensorName);
            } else {
                Log.d(TAG, "addStorageConfig: NOT excluding video");
            }
        }

        sensorhubConfig.add(storageConf);
    }

    protected void addSosServerConfig(SOSServiceConfig sosConf, SensorDataProviderConfig dataProviderConf) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        SensorManager sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        String sensorName;
        for (Sensor sensor : deviceSensors) {
            if (sensor.isWakeUpSensor()) {
                continue;
            }

            Log.d(TAG, "addSosServerConfig: sensor: " + sensor.getName());

            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    if (!prefs.getBoolean("accelerometer_enable", false)
                            || !prefs.getStringSet("accelerometer_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                        Log.d(TAG, "addSosServerConfig: excluding accelerometer");

                        sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                        dataProviderConf.excludedOutputs.add(sensorName);
                    } else {
                        Log.d(TAG, "addSosServerConfig: NOT excluding accelerometer");
                    }
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    if (!prefs.getBoolean("gyroscope_enable", false)
                            || !prefs.getStringSet("gyroscope_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                        Log.d(TAG, "addSosServerConfig: excluding gyroscope");

                        sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                        dataProviderConf.excludedOutputs.add(sensorName);
                    } else {
                        Log.d(TAG, "addSosServerConfig: NOT excluding gyroscope");
                    }
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    if (!prefs.getBoolean("magnetometer_enable", false)
                            || !prefs.getStringSet("magnetometer_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                        Log.d(TAG, "addSosServerConfig: excluding magnetometer");

                        sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                        dataProviderConf.excludedOutputs.add(sensorName);
                    } else {
                        Log.d(TAG, "addSosServerConfig: NOT excluding magnetometer");
                    }
                    break;
                case Sensor.TYPE_ROTATION_VECTOR:
                    if (!prefs.getBoolean("orientation_enable", false)
                            || !prefs.getStringSet("orientation_options", Collections.emptySet()).contains("STORE_LOCAL")) {

                        Log.d(TAG, "addSosServerConfig: excluding orientation");

                        sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                        dataProviderConf.excludedOutputs.add(sensorName);
                        sensorName = "quat_orientation_data";
                        dataProviderConf.excludedOutputs.add(sensorName);
                        sensorName = "euler_orientation_data";
                        dataProviderConf.excludedOutputs.add(sensorName);
                    } else {
                        Log.d(TAG, "addSosServerConfig: NOT excluding orientation");
                    }
                    break;
            }
        }
        if (!prefs.getBoolean("location_enable", false)
                || !prefs.getStringSet("location_options", Collections.emptySet()).contains("STORE_LOCAL")) {

            Log.d(TAG, "addSosServerConfig: excluding location");

            sensorName = "gps_data";
            dataProviderConf.excludedOutputs.add(sensorName);
            sensorName = "network_data";
            dataProviderConf.excludedOutputs.add(sensorName);
        } else {
            Log.d(TAG, "addSosServerConfig: NOT excluding location");
        }
        if (!prefs.getBoolean("video_enable", false)
                || !prefs.getStringSet("video_options", Collections.emptySet()).contains("STORE_LOCAL")) {

            Log.d(TAG, "addSosServerConfig: excluding video");

            sensorName = "camera0_MJPEG";
            dataProviderConf.excludedOutputs.add(sensorName);
            sensorName = "camera0_H264";
            dataProviderConf.excludedOutputs.add(sensorName);
        } else {
            Log.d(TAG, "addSosServerConfig: NOT excluding video");
        }

        sosConf.dataProviders.add(dataProviderConf);
    }

    protected void addSosTConfig(SensorConfig sensorConf, String sosUser, String sosPwd) {
        if (sosUrl == null)
            return;

        SOSTClientConfig sosConfig = new SOSTClientConfig();
        sosConfig.id = sensorConf.id + "_SOST";
        sosConfig.name = sensorConf.name.replaceAll("\\[.*\\]", "");// + "SOS-T Client";
        sosConfig.autoStart = true;
        sosConfig.sensorID = sensorConf.id;
        sosConfig.sos.remoteHost = sosUrl.getHost();
        sosConfig.sos.remotePort = sosUrl.getPort();
        sosConfig.sos.resourcePath = sosUrl.getPath();
        sosConfig.sos.enableTLS = sosUrl.getProtocol().equals("https");
        sosConfig.sos.user = sosUser;
        sosConfig.sos.password = sosPwd;
        sosConfig.connection.connectTimeout = 10000;
        sosConfig.connection.usePersistentConnection = true;
        sosConfig.connection.reconnectAttempts = 9;

        if (sensorConf instanceof AndroidSensorsConfig) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            SensorManager sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

            String sensorName;
            for (Sensor sensor : deviceSensors) {
                if (sensor.isWakeUpSensor()) {
                    continue;
                }

                Log.d(TAG, "addSosTConfig: sensor: " + sensor.getName());

                switch (sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        if (!prefs.getBoolean("accelerometer_enable", false)
                                || !prefs.getStringSet("accelerometer_options", Collections.emptySet()).contains("PUSH_REMOTE")) {

                            Log.d(TAG, "addSosTConfig: excluding accelerometer");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            sosConfig.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addSosTConfig: NOT excluding accelerometer");
                        }
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                        if (!prefs.getBoolean("gyroscope_enable", false)
                                || !prefs.getStringSet("gyroscope_options", Collections.emptySet()).contains("PUSH_REMOTE")) {

                            Log.d(TAG, "addSosTConfig: excluding gyroscope");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            sosConfig.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addSosTConfig: NOT excluding gyroscope");
                        }
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        if (!prefs.getBoolean("magnetometer_enable", false)
                                || !prefs.getStringSet("magnetometer_options", Collections.emptySet()).contains("PUSH_REMOTE")) {

                            Log.d(TAG, "addSosTConfig: excluding magnetometer");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            sosConfig.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addSosTConfig: NOT excluding magnetometer");
                        }
                        break;
                    case Sensor.TYPE_ROTATION_VECTOR:
                        if (!prefs.getBoolean("orientation_enable", false)
                                || !prefs.getStringSet("orientation_options", Collections.emptySet()).contains("PUSH_REMOTE")) {

                            Log.d(TAG, "addSosTConfig: excluding orientation");

                            sensorName = sensor.getName().replaceAll(" ", "_") + "_data";
                            sosConfig.excludedOutputs.add(sensorName);
                            sensorName = "quat_orientation_data";
                            sosConfig.excludedOutputs.add(sensorName);
                            sensorName = "euler_orientation_data";
                            sosConfig.excludedOutputs.add(sensorName);
                        } else {
                            Log.d(TAG, "addSosTConfig: NOT excluding orientation");
                        }
                        break;
                }
            }
            if (!prefs.getBoolean("location_enable", false)
                    || !prefs.getStringSet("location_options", Collections.emptySet()).contains("PUSH_REMOTE")) {

                Log.d(TAG, "addSosTConfig: excluding location");

                sensorName = "gps_data";
                sosConfig.excludedOutputs.add(sensorName);
                sensorName = "network_data";
                sosConfig.excludedOutputs.add(sensorName);
            } else {
                Log.d(TAG, "addSosTConfig: NOT excluding location");
            }
            if (!prefs.getBoolean("video_enable", false)
                    || !prefs.getStringSet("video_options", Collections.emptySet()).contains("PUSH_REMOTE")) {

                Log.d(TAG, "addSosTConfig: excluding video");

                sensorName = "camera0_MJPEG";
                sosConfig.excludedOutputs.add(sensorName);
                sensorName = "camera0_H264";
                sosConfig.excludedOutputs.add(sensorName);
            } else {
                Log.d(TAG, "addSosTConfig: NOT excluding video");
            }
        }

        sensorhubConfig.add(sosConfig);
    }


    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: Should appear in logs");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textArea = (TextView) findViewById(R.id.text);

        // listen to texture view lifecycle
        TextureView textureView = (TextureView) findViewById(R.id.video);
        textureView.setSurfaceTextureListener(this);

        // bind to SensorHub service
        Intent intent = new Intent(this, SensorHubService.class);
        bindService(intent, sConn, Context.BIND_AUTO_CREATE);

        // handler to refresh sensor status in UI
        displayHandler = new Handler(Looper.getMainLooper());

        setupBroadcastReceivers();

        Log.d(TAG, "onCreate: Creating BLE Config");
        BLEBeaconConfig beaconConfig = new BLEBeaconConfig();
        beaconConfig.id =  "BLE_BEACON_SCANNER";
        beaconConfig.name = "BLE Scanner [" + deviceName + "]";
//        beaconConfig.moduleClass = BLEBeaconDriver.class.getCanonicalName();
        beaconConfig.androidContext = this.getApplicationContext();
        beaconConfig.autoStart = true;
        Log.d(TAG, "onCreate: Adding config to sensorhub config");
//        sensorhubConfig.add(beaconConfig);
        Log.d(TAG, "onCreate: BLE Config Added");
    }


    private void setupBroadcastReceivers() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String origin = intent.getStringExtra("src");
                if (!context.getPackageName().equalsIgnoreCase(origin)) {
                    String sosEndpointUrl = intent.getStringExtra("sosEndpointUrl");
                    String name = intent.getStringExtra("name");
                    String sensorId = intent.getStringExtra("sensorId");
                    ArrayList<String> properties = intent.getStringArrayListExtra("properties");

                    if (sosEndpointUrl == null || name == null || sensorId == null || properties.size() == 0) {
                        return;
                    }

                    ProxySensorConfig proxySensorConfig = (ProxySensorConfig) createSensorConfig(Sensors.ProxySensor);
                    proxySensorConfig.androidContext = getApplicationContext();
                    proxySensorConfig.sosEndpointUrl = sosEndpointUrl;
                    proxySensorConfig.name = name;
                    proxySensorConfig.id = sensorId;
                    proxySensorConfig.sensorUID = sensorId;
                    proxySensorConfig.observedProperties.addAll(properties);
                    proxySensorConfig.sosUseWebsockets = true;
                    proxySensorConfig.autoStart = true;
                    proxySensorConfigs.add(proxySensorConfig);

                    // register and "start" new sensor, data stream doesn't begin until someone requests data;
                    try {
                        boundService.stopSensorHub();
                        Thread.sleep(2000);
                        Log.d("OSHApp", "Starting Sensorhub Again");
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        updateConfig(PreferenceManager.getDefaultSharedPreferences(MainActivity.this), runName);
                        sostClients.clear();
                        boundService.startSensorHub(sensorhubConfig, showVideo, MainActivity.this);
                        if (boundService.hasVideo())
                            textArea.setBackgroundColor(0x80FFFFFF);
                    } catch (InterruptedException e) {
                        Log.e("OSHApp", "Error Loading Proxy Sensor", e);
                    }

                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BROADCAST_RECEIVER);

        registerReceiver(receiver, filter);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, UserSettingsActivity.class));
            return true;
        } else if (id == R.id.action_start) {
            if (boundService != null && boundService.getSensorHub() == null)
                showRunNamePopup();
            return true;
        } else if (id == R.id.action_stop) {
            stopListeningForEvents();
            stopRefreshingStatus();
            sostClients.clear();
            if (boundService != null)
                boundService.stopSensorHub();
            textArea.setBackgroundColor(0xFFFFFFFF);
            oshStarted = false;
            newStatusMessage("SensorHub Stopped");
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return true;
        } else if (id == R.id.action_about) {
            showAboutPopup();
        } else if (id == R.id.action_proxy) {
            testProxyBroadcast();
        } else if (id == R.id.action_stop_proxy) {
            testStopProxyBroadcast();
        }

        return super.onOptionsItemSelected(item);
    }


    protected void showRunNamePopup() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Run Name");
        alert.setMessage("Please enter the name for this run");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        input.getText().append("Run-");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        input.getText().append(formatter.format(new Date()));
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                runName = input.getText().toString();
                newStatusMessage("Starting SensorHub...");

                updateConfig(PreferenceManager.getDefaultSharedPreferences(MainActivity.this), runName);
                sostClients.clear();
                boundService.startSensorHub(sensorhubConfig, showVideo, MainActivity.this);

                if (boundService.hasVideo())
                    textArea.setBackgroundColor(0x80FFFFFF);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });

        alert.show();
    }


    protected void showAboutPopup() {
        String version = "?";

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
        }

        String message = "A software platform for building smart sensor networks and the Internet of Things\n\n";
        message += "Version: " + version + "\n";

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("OpenSensorHub");
        alert.setMessage(message);
        alert.setIcon(R.drawable.ic_launcher);
        alert.show();
    }


    protected void testProxyBroadcast() {
        ArrayList<String> testProperties = new ArrayList<String>();
        testProperties.add("http://sensorml.com/ont/swe/property/Acceleration");
        testProperties.add("http://sensorml.com/ont/swe/property/MagneticField");
//        testProperties.add("http://sensorml.com/ont/swe/property/AngularRate");

        Intent testIntent = new Intent();
        testIntent.setAction(ACTION_BROADCAST_RECEIVER);
//        testIntent.putExtra("sosEndpointUrl", "http://192.168.0.46:8585/sensorhub/sos?service=SOS&version=2.0&request=GetCapabilities");
        testIntent.putExtra("sosEndpointUrl", "http://192.168.1.195:8585/sensorhub/sos?service=SOS&version=2.0&request=GetCapabilities");
        testIntent.putExtra("name", "Android Sensors [S9]");
        testIntent.putExtra("sensorId", "urn:android:device:aa3de549fc5ae2c3");
        testIntent.putStringArrayListExtra("properties", testProperties);
        sendBroadcast(testIntent);
    }

    protected void testStopProxyBroadcast() {
        Intent testIntent = new Intent();
        testIntent.setAction("org.sofwerx.ogc.ACTION_PROXY");
        sendBroadcast(testIntent);
    }

    @Override
    public void handleEvent(Event<?> e) {
        if (e instanceof ModuleEvent) {
            // start refreshing status on first module loaded
            if (!oshStarted && ((ModuleEvent) e).getType() == ModuleEvent.Type.LOADED) {
                oshStarted = true;
                startRefreshingStatus();
                return;
            }

            // detect when SOS-T modules are connected
            else if (e.getSource() instanceof SOSTClient && ((ModuleEvent) e).getType() == ModuleEvent.Type.STATE_CHANGED) {
                switch (((ModuleEvent) e).getNewState()) {
                    case INITIALIZING:
                        sostClients.add((SOSTClient) e.getSource());
                        break;
                }
            }
        }
    }


    protected void startRefreshingStatus() {
        if (displayCallback != null)
            return;

        // handler to display async messages in UI
        displayCallback = new Runnable() {
            public void run() {
                displayStatus();
                textArea.setText(Html.fromHtml(displayText.toString()));
                displayHandler.postDelayed(this, 1000);
            }
        };

        displayHandler.post(displayCallback);
    }


    protected void stopRefreshingStatus() {
        if (displayCallback != null) {
            displayHandler.removeCallbacks(displayCallback);
            displayCallback = null;
        }
    }


    protected synchronized void displayStatus() {
        displayText.setLength(0);

        // first display error messages if any
        for (SOSTClient client : sostClients) {
            Map<ISensorDataInterface, StreamInfo> dataStreams = client.getDataStreams();
            boolean showError = (client.getCurrentError() != null);
            boolean showMsg = (dataStreams.size() == 0) && (client.getStatusMessage() != null);

            if (showError || showMsg) {
                displayText.append("<p>" + client.getName() + ":<br/>");
                if (showMsg)
                    displayText.append(client.getStatusMessage() + "<br/>");
                if (showError) {
                    Throwable errorObj = client.getCurrentError();
                    String errorMsg = errorObj.getMessage().trim();
                    if (!errorMsg.endsWith("."))
                        errorMsg += ". ";
                    if (errorObj.getCause() != null && errorObj.getCause().getMessage() != null)
                        errorMsg += errorObj.getCause().getMessage();
                    displayText.append("<font color='red'>" + errorMsg + "</font>");
                }
                displayText.append("</p>");
            }
        }

        // then display streams status
        displayText.append("<p>");
        for (SOSTClient client : sostClients) {
            Map<ISensorDataInterface, StreamInfo> dataStreams = client.getDataStreams();
            long now = System.currentTimeMillis();

            for (Entry<ISensorDataInterface, StreamInfo> stream : dataStreams.entrySet()) {
                displayText.append("<b>" + stream.getKey().getName() + " : </b>");

                long lastEventTime = stream.getValue().lastEventTime;
                long dt = now - lastEventTime;
                if (lastEventTime == Long.MIN_VALUE)
                    displayText.append("<font color='red'>NO OBS</font>");
                else if (dt > stream.getValue().measPeriodMs)
                    displayText.append("<font color='red'>NOK (" + dt + "ms ago)</font>");
                else
                    displayText.append("<font color='green'>OK (" + dt + "ms ago)</font>");

                if (stream.getValue().errorCount > 0) {
                    displayText.append("<font color='red'> (");
                    displayText.append(stream.getValue().errorCount);
                    displayText.append(")</font>");
                }

                displayText.append("<br/>");
            }
        }

        if (displayText.length() > 5)
            displayText.setLength(displayText.length() - 5); // remove last </br>
        displayText.append("</p>");
    }


    protected synchronized void newStatusMessage(String msg) {
        displayText.setLength(0);
        appendStatusMessage(msg);
    }


    protected synchronized void appendStatusMessage(String msg) {
        displayText.append(msg);

        displayHandler.post(new Runnable() {
            public void run() {
                textArea.setText(displayText.toString());
            }
        });
    }


    protected void startListeningForEvents() {
        if (boundService == null || boundService.getSensorHub() == null)
            return;

        boundService.getSensorHub().getModuleRegistry().registerListener(this);
    }


    protected void stopListeningForEvents() {
        if (boundService == null || boundService.getSensorHub() == null)
            return;

        boundService.getSensorHub().getModuleRegistry().unregisterListener(this);
    }


    protected void showVideo() {
        if (boundService.getVideoTexture() != null) {
            TextureView textureView = (TextureView) findViewById(R.id.video);
            if (textureView.getSurfaceTexture() != boundService.getVideoTexture())
                textureView.setSurfaceTexture(boundService.getVideoTexture());
        }
    }


    protected void hideVideo() {
    }


    @Override
    protected void onStart() {
        super.onStart();
    }


    @Override
    protected void onResume() {
        super.onResume();

        TextureView textureView = (TextureView) findViewById(R.id.video);
        textureView.setSurfaceTextureListener(this);

        if (oshStarted) {
            startListeningForEvents();
            startRefreshingStatus();

            if (boundService.hasVideo())
                textArea.setBackgroundColor(0x80FFFFFF);
        }
    }


    @Override
    protected void onPause() {
        stopListeningForEvents();
        stopRefreshingStatus();
        hideVideo();
        super.onPause();
    }


    @Override
    protected void onStop() {
        stopListeningForEvents();
        stopRefreshingStatus();
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        stopService(new Intent(this, SensorHubService.class));
        super.onDestroy();
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        showVideo();
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
    }


    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }


    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }
}
