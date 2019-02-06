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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.util.Log;
import android.view.*;

import org.sensorhub.android.comm.BluetoothCommProvider;
import org.sensorhub.android.comm.BluetoothCommProviderConfig;
import org.sensorhub.android.comm.ble.BleConfig;
import org.sensorhub.android.comm.ble.BleNetwork;
import org.sensorhub.api.common.Event;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.common.SensorHubException;
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
import org.sensorhub.impl.persistence.h2.MVObsStorageImpl;
import org.sensorhub.impl.persistence.h2.MVStorageConfig;
import org.sensorhub.impl.sensor.android.AndroidSensorsConfig;
import org.sensorhub.impl.sensor.angel.AngelSensorConfig;
import org.sensorhub.impl.sensor.trupulse.TruPulseConfig;
import org.sensorhub.impl.service.sos.ISOSDataProviderFactory;
import org.sensorhub.impl.service.sos.SOSServiceConfig;
import org.sensorhub.impl.service.sos.SOSServlet;
import org.sensorhub.impl.service.sos.SensorDataProvider;
import org.sensorhub.impl.service.sos.SensorDataProviderConfig;
import org.sensorhub.impl.service.sos.StorageDataProvider;
import org.sensorhub.impl.service.sos.StorageDataProviderConfig;
import org.sensorhub.impl.service.sos.StreamDataProviderConfig;
import org.sensorhub.impl.service.sos.video.MP4Serializer;
import org.sensorhub.impl.service.sos.video.MJPEGSerializer;
import org.sensorhub.test.sensor.trupulse.SimulatedDataStream;
import org.sensorhub.impl.service.HttpServerConfig;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.text.Html;
import android.widget.EditText;
import android.widget.TextView;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, IEventListener
{
    String deviceID;
    String deviceName;
    String runName;

    enum Sensors {
        Android,
        TruPulse,
        TruPulseSim,
        Angel,
        FlirOne,
    }

    TextView textArea;
    SensorHubService boundService;
    IModuleConfigRepository sensorhubConfig;
    Handler displayHandler;
    Runnable displayCallback;
    StringBuffer displayText = new StringBuffer();
    boolean oshStarted = false;
    ArrayList<SOSTClient> sostClients = new ArrayList<SOSTClient>();
    URL sosUrl = null;
    boolean showVideo;

    private ServiceConnection sConn = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            boundService = ((SensorHubService.LocalBinder) service).getService();
        }

        public void onServiceDisconnected(ComponentName className)
        {
            boundService = null;
        }
    };


    protected void updateConfig(SharedPreferences prefs, String runName)
    {
        // get device name
        deviceID = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        deviceName = prefs.getString("device_name", null);
        if (deviceName == null || deviceName.length() < 2)
            deviceName = deviceID;
        this.runName = runName;

        sensorhubConfig = new InMemoryConfigDb();

        // get SOS URL from config
        String sosUriConfig = prefs.getString("sos_uri", "");
        String sosUser = prefs.getString("sos_username", null);
        String sosPwd = prefs.getString("sos_password", null);
        if (sosUriConfig != null && sosUriConfig.trim().length() > 0)
        {
            try
            {
                sosUrl = new URL(sosUriConfig);
            }
            catch (MalformedURLException e)
            {
                e.printStackTrace();
            }
        }

        // HTTP Server Config
        HttpServerConfig serverConfig = new HttpServerConfig();
        serverConfig.autoStart = true;
        sensorhubConfig.add(serverConfig);

        // SOS Config
        SOSServiceConfig sosConfig = new SOSServiceConfig();
        sosConfig.autoStart = true;
        sosConfig.enableTransactional = true;

        // Push Sensors Config
        AndroidSensorsConfig androidSensorsConfig = (AndroidSensorsConfig) createSensorConfig(Sensors.Android);

        androidSensorsConfig.activateAccelerometer = prefs.getBoolean("accelerometer_enable", false);
        androidSensorsConfig.activateGyrometer = prefs.getBoolean("gyroscope_enable", false);
        androidSensorsConfig.activateMagnetometer = prefs.getBoolean("magnetometer_enable", false);
        if (prefs.getBoolean("orientation_enable", false))
        {
            androidSensorsConfig.activateOrientationQuat = prefs.getStringSet("orientation_angles", Collections.emptySet()).contains("QUATERNION");
            androidSensorsConfig.activateOrientationEuler = prefs.getStringSet("orientation_angles", Collections.emptySet()).contains("EULER");
        }
        if (prefs.getBoolean("location_enable", false))
        {
            androidSensorsConfig.activateGpsLocation = prefs.getStringSet("location_type", Collections.emptySet()).contains("GPS");
            androidSensorsConfig.activateNetworkLocation = prefs.getStringSet("location_type", Collections.emptySet()).contains("NETWORK");
        }
        if (prefs.getBoolean("video_enable", false))
        {
            showVideo = true;
            androidSensorsConfig.activateBackCamera = true;
            androidSensorsConfig.videoCodec = prefs.getString("video_codec", AndroidSensorsConfig.JPEG_CODEC);
        }

        sensorhubConfig.add(androidSensorsConfig);
        addSosTConfig(androidSensorsConfig, sosUser, sosPwd);

        StreamStorageConfig androidStreamStorageConfig = createStreamStorageConfig(androidSensorsConfig);
        sensorhubConfig.add(androidStreamStorageConfig);

        SensorDataProviderConfig androidDataProviderConfig = createDataProviderConfig(androidSensorsConfig);
        androidDataProviderConfig.storageID = androidStreamStorageConfig.id;
        sosConfig.dataProviders.add(androidDataProviderConfig);

        // TruPulse sensor
        boolean enabled = prefs.getBoolean("trupulse_enabled", false);
        if (enabled)
        {
            TruPulseConfig truPulseConfig= prefs.getBoolean("trupulse_simu", false)
                    ? (TruPulseConfig) createSensorConfig(Sensors.TruPulse)
                    : (TruPulseConfig) createSensorConfig(Sensors.TruPulse);
            sensorhubConfig.add(truPulseConfig);
            addSosTConfig(truPulseConfig, sosUser, sosPwd);
        }

        // AngelSensor
        enabled = prefs.getBoolean("angel_enabled", false);
        if (enabled)
        {
            AngelSensorConfig angelConfig = (AngelSensorConfig) createSensorConfig(Sensors.Angel);
            //angelConfig.btAddress = "00:07:80:79:04:AF"; // mike
            //angelConfig.btAddress = "00:07:80:03:0E:0A"; // alex
            angelConfig.btAddress = prefs.getString("angel_address", null);
            sensorhubConfig.add(angelConfig);
            addSosTConfig(angelConfig, sosUser, sosPwd);
        }

        // FLIR One sensor
        enabled = prefs.getBoolean("flirone_enabled", false);
        if (enabled)
        {
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

        sensorhubConfig.add(sosConfig);
    }

    private SensorDataProviderConfig createDataProviderConfig(AndroidSensorsConfig sensorConfig) {
        SensorDataProviderConfig dataProviderConfig = new SensorDataProviderConfig();
        dataProviderConfig.offeringID = sensorConfig.id+":offering";
        dataProviderConfig.sensorID = sensorConfig.id;
        dataProviderConfig.enabled = true;
        dataProviderConfig.liveDataTimeout = 600.0;
        dataProviderConfig.maxFois = 10;

        return dataProviderConfig;
    }

    private StreamStorageConfig createStreamStorageConfig(AndroidSensorsConfig sensorConfig) {
        // H2 Storage Config
        File dbFile = new File(getApplicationContext().getFilesDir()+"/db/", deviceID+"_h2.dat");
        dbFile.getParentFile().mkdirs();
        if(!dbFile.exists())
        {
            try
            {
                dbFile.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        MVStorageConfig storageConfig = new MVStorageConfig();
        storageConfig.moduleClass = MVMultiStorageImpl.class.getCanonicalName();
        storageConfig.storagePath = dbFile.getPath();
        storageConfig.autoStart = true;
        storageConfig.memoryCacheSize = 102400;
        storageConfig.autoCommitBufferSize = 1024;

        // Auto Purge Config
        MaxAgeAutoPurgeConfig autoPurgeConfig = new MaxAgeAutoPurgeConfig();
        autoPurgeConfig.enabled = true;
        autoPurgeConfig.purgePeriod = 600.0;
        autoPurgeConfig.maxRecordAge = 864000;

        // Stream Storage Config
        StreamStorageConfig streamStorageConfig = new StreamStorageConfig();
        streamStorageConfig.moduleClass = GenericStreamStorage.class.getCanonicalName();
        streamStorageConfig.id = sensorConfig.id + ":stream-storage";
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
        if (Sensors.Android.equals(sensor))
        {
            sensorConfig = new AndroidSensorsConfig();
            sensorConfig.id = "urn:device:android:" + deviceID;
            sensorConfig.name = "Android Sensors [" + deviceName + "]";
            sensorConfig.autoStart = true;

            ((AndroidSensorsConfig) sensorConfig).androidContext = this.getApplicationContext();
            ((AndroidSensorsConfig) sensorConfig).camPreviewTexture = boundService.getVideoTexture();
            ((AndroidSensorsConfig) sensorConfig).runName = runName;
        }
        else if (Sensors.TruPulse.equals(sensor))
        {
            sensorConfig = new TruPulseConfig();
            sensorConfig.id = "TRUPULSE_SENSOR";
            sensorConfig.name = "TruPulse Range Finder [" + deviceName + "]";
            sensorConfig.autoStart = true;

            BluetoothCommProviderConfig btConf = new BluetoothCommProviderConfig();
            btConf.protocol.deviceName = "TP360RB.*";
            btConf.moduleClass = BluetoothCommProvider.class.getCanonicalName();
            ((TruPulseConfig) sensorConfig).commSettings = btConf;
            ((TruPulseConfig) sensorConfig).serialNumber = deviceID;
        }
        else if (Sensors.TruPulseSim.equals(sensor))
        {
            sensorConfig = new TruPulseConfig();
            sensorConfig.id = "TRUPULSE_SENSOR_SIMULATED";
            sensorConfig.name = "Simulated TruPulse Range Finder [" + deviceName + "]";
            sensorConfig.autoStart = true;

            BluetoothCommProviderConfig btConf = new BluetoothCommProviderConfig();
            btConf.protocol.deviceName = "TP360RB.*";
            btConf.moduleClass = SimulatedDataStream.class.getCanonicalName();
            ((TruPulseConfig) sensorConfig).commSettings = btConf;
            ((TruPulseConfig) sensorConfig).serialNumber = deviceID;
        }
        else if (Sensors.Angel.equals(sensor))
        {
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
        }
        else if (Sensors.FlirOne.equals(sensor))
        {
            sensorConfig = new FlirOneCameraConfig();
            sensorConfig.id = "FLIRONE_SENSOR";
            sensorConfig.name = "FLIR One Camera [" + deviceName + "]";
            sensorConfig.autoStart = true;

            ((FlirOneCameraConfig) sensorConfig).androidContext = this.getApplicationContext();
            ((FlirOneCameraConfig) sensorConfig).camPreviewTexture = boundService.getVideoTexture();
        }
        else
        {
            sensorConfig = new SensorConfig();
        }

        return sensorConfig;
    }

    protected void addStorageConfig() {
    }

    protected void addSosServerConfig() {
    }

    protected void addSosTConfig(SensorConfig sensorConf, String sosUser, String sosPwd)
    {
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

        if (sensorConf instanceof AndroidSensorsConfig)
        {
            /*
            sosConfig.excludeOutputs.add(
            */
        }

        sensorhubConfig.add(sosConfig);
    }


    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
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
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings)
        {
            startActivity(new Intent(this, UserSettingsActivity.class));
            return true;
        }
        else if (id == R.id.action_start)
        {
            if (boundService != null && boundService.getSensorHub() == null)
                showRunNamePopup();
            return true;
        }
        else if (id == R.id.action_stop)
        {
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
        }
        else if (id == R.id.action_about)
        {
            showAboutPopup();
        }

        return super.onOptionsItemSelected(item);
    }


    protected void showRunNamePopup()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Run Name");
        alert.setMessage("Please enter the name for this run");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        input.getText().append("Run-");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        input.getText().append(formatter.format(new Date()));
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                String runName = input.getText().toString();
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


    protected void showAboutPopup()
    {
        String version = "?";

        try
        {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        }
        catch (PackageManager.NameNotFoundException e)
        {
        }

        String message = "A software platform for building smart sensor networks and the Internet of Things\n\n";
        message += "Version: " + version + "\n";

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("OpenSensorHub");
        alert.setMessage(message);
        alert.setIcon(R.drawable.ic_launcher);
        alert.show();
    }


    @Override
    public void handleEvent(Event<?> e)
    {
        if (e instanceof ModuleEvent)
        {
            // start refreshing status on first module loaded
            if (!oshStarted && ((ModuleEvent) e).getType() == ModuleEvent.Type.LOADED)
            {
                oshStarted = true;
                startRefreshingStatus();
                return;
            }

            // detect when SOS-T modules are connected
            else if (e.getSource() instanceof SOSTClient && ((ModuleEvent)e).getType() == ModuleEvent.Type.STATE_CHANGED)
            {
                switch (((ModuleEvent)e).getNewState())
                {
                    case INITIALIZING:
                        sostClients.add((SOSTClient)e.getSource());
                        break;
                }
            }
        }
    }
    
    
    protected void startRefreshingStatus()
    {
        if (displayCallback != null)
            return;

        // handler to display async messages in UI
        displayCallback = new Runnable()
        {
            public void run()
            {
                displayStatus();
                textArea.setText(Html.fromHtml(displayText.toString()));
                displayHandler.postDelayed(this, 1000);
            }
        };

        displayHandler.post(displayCallback);
    }
    
    
    protected void stopRefreshingStatus()
    {
        if (displayCallback != null)
        {
            displayHandler.removeCallbacks(displayCallback);
            displayCallback = null;
        }
    }

    
    protected synchronized void displayStatus()
    {
        displayText.setLength(0);
        
        // first display error messages if any
        for (SOSTClient client: sostClients)
        {
            Map<ISensorDataInterface, StreamInfo> dataStreams = client.getDataStreams();
            boolean showError = (client.getCurrentError() != null);
            boolean showMsg = (dataStreams.size() == 0) && (client.getStatusMessage() != null);
            
            if (showError || showMsg)
            {
                displayText.append("<p>" + client.getName() + ":<br/>");
                if (showMsg)
                    displayText.append(client.getStatusMessage() + "<br/>");
                if (showError)
                {
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
        for (SOSTClient client: sostClients)
        {
            Map<ISensorDataInterface, StreamInfo> dataStreams = client.getDataStreams();            
            long now = System.currentTimeMillis();
            
            for (Entry<ISensorDataInterface, StreamInfo> stream : dataStreams.entrySet())
            {
                displayText.append("<b>" + stream.getKey().getName() + " : </b>");

                long lastEventTime = stream.getValue().lastEventTime;
                long dt = now - lastEventTime;
                if (lastEventTime == Long.MIN_VALUE)
                    displayText.append("<font color='red'>NO OBS</font>");
                else if (dt > stream.getValue().measPeriodMs)
                    displayText.append("<font color='red'>NOK (" + dt + "ms ago)</font>");
                else
                    displayText.append("<font color='green'>OK (" + dt + "ms ago)</font>");

                if (stream.getValue().errorCount > 0)
                {
                    displayText.append("<font color='red'> (");
                    displayText.append(stream.getValue().errorCount);
                    displayText.append(")</font>");
                }
                
                displayText.append("<br/>");
            }
        }

        if (displayText.length() > 5)
            displayText.setLength(displayText.length()-5); // remove last </br>
        displayText.append("</p>");
    }
    
    
    protected synchronized void newStatusMessage(String msg)
    {
        displayText.setLength(0);
        appendStatusMessage(msg);
    }
    
    
    protected synchronized void appendStatusMessage(String msg)
    {
        displayText.append(msg);

        displayHandler.post(new Runnable()
        {
            public void run()
            {
                textArea.setText(displayText.toString());
            }
        });
    }
    
    
    protected void startListeningForEvents()
    {
        if (boundService == null || boundService.getSensorHub() == null)
            return;
        
        boundService.getSensorHub().getModuleRegistry().registerListener(this);
    }
    
    
    protected void stopListeningForEvents()
    {
        if (boundService == null || boundService.getSensorHub() == null)
            return;

        boundService.getSensorHub().getModuleRegistry().unregisterListener(this);
    }


    protected void showVideo()
    {
        if (boundService.getVideoTexture() != null)
        {
            TextureView textureView = (TextureView) findViewById(R.id.video);
            if (textureView.getSurfaceTexture() != boundService.getVideoTexture())
                textureView.setSurfaceTexture(boundService.getVideoTexture());
        }
    }


    protected void hideVideo()
    {
    }


    @Override
    protected void onStart()
    {
        super.onStart();
    }


    @Override
    protected void onResume()
    {
        super.onResume();

        TextureView textureView = (TextureView) findViewById(R.id.video);
        textureView.setSurfaceTextureListener(this);

        if (oshStarted)
        {
            startListeningForEvents();
            startRefreshingStatus();

            if (boundService.hasVideo())
                textArea.setBackgroundColor(0x80FFFFFF);
        }
    }


    @Override
    protected void onPause()
    {
        stopListeningForEvents();
        stopRefreshingStatus();
        hideVideo();
        super.onPause();
    }


    @Override
    protected void onStop()
    {
        stopListeningForEvents();
        stopRefreshingStatus();
        super.onStop();
    }


    @Override
    protected void onDestroy()
    {
        stopService(new Intent(this, SensorHubService.class));
        super.onDestroy();
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1)
    {
        showVideo();
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1)
    {
    }


    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture)
    {
        return false;
    }


    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture)
    {
    }
}
