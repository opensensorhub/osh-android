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

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import android.location.LocationManager;
import android.location.LocationProvider;
import android.preference.DialogPreference;
import android.util.Log;
import android.view.*;
import org.sensorhub.android.comm.BluetoothCommProvider;
import org.sensorhub.android.comm.BluetoothCommProviderConfig;
import org.sensorhub.android.comm.ble.BleConfig;
import org.sensorhub.android.comm.ble.BleNetwork;
import org.sensorhub.api.common.Event;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.data.IStreamingDataInterface;
import org.sensorhub.api.module.IModuleConfigRepository;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.impl.client.sost.SOSTClient;
import org.sensorhub.impl.client.sost.SOSTClient.StreamInfo;
import org.sensorhub.impl.client.sost.SOSTClientConfig;
//import org.sensorhub.impl.driver.dji.DjiConfig;
import org.sensorhub.impl.driver.flir.FlirOneCameraConfig;
import org.sensorhub.impl.module.InMemoryConfigDb;
import org.sensorhub.impl.sensor.android.AndroidSensorsConfig;
import org.sensorhub.impl.sensor.android.AndroidSensorsDriver;
import org.sensorhub.impl.sensor.android.video.VideoEncoderConfig;
import org.sensorhub.impl.sensor.android.video.VideoEncoderConfig.VideoPreset;
import org.sensorhub.impl.sensor.angel.AngelSensorConfig;
import org.sensorhub.impl.sensor.trupulse.TruPulseConfig;
import org.sensorhub.impl.sensor.trupulse.TruPulseWithGeolocConfig;
import org.sensorhub.impl.service.HttpServerConfig;
import org.sensorhub.test.sensor.trupulse.SimulatedDataStream;
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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, IEventListener
{
    TextView mainInfoArea;
    TextView videoInfoArea;
    SensorHubService boundService;
    IModuleConfigRepository sensorhubConfig;
    Handler displayHandler;
    Runnable displayCallback;
    StringBuffer mainInfoText = new StringBuffer();
    StringBuffer videoInfoText = new StringBuffer();
    boolean oshStarted = false;
    ArrayList<SOSTClient> sostClients = new ArrayList<SOSTClient>();
    AndroidSensorsDriver androidSensors;
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

        // disable SSL check if requested
        boolean disableSslCheck = prefs.getBoolean("sos_disable_ssl_check", false);
        if (disableSslCheck)
        {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            X509Certificate[] myTrustedAnchors = new X509Certificate[0];
                            return myTrustedAnchors;
                        }
                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // Install the all-trusting trust manager
            try {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String arg0, SSLSession arg1) {
                        return true;
                    }
                });
            } catch (Exception e) {
            }
        }

        // Setup HTTPServerConfig for enabling more complete node functionality
        HttpServerConfig serverConfig = new HttpServerConfig();
        serverConfig.proxyBaseUrl = "";
        serverConfig.httpPort = 8585;
        serverConfig.autoStart = true;
        sensorhubConfig.add(serverConfig);

        // get device name
        String deviceID = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        String deviceName = prefs.getString("device_name", null);
        if (deviceName == null || deviceName.length() < 2)
            deviceName = deviceID;

        // Android sensors
        AndroidSensorsConfig sensorsConfig = new AndroidSensorsConfig();
        sensorsConfig.name = "Android Sensors [" + deviceName + "]";
        sensorsConfig.id = "ANDROID_SENSORS";
        sensorsConfig.autoStart = true;
        sensorsConfig.activateAccelerometer = prefs.getBoolean("accel_enabled", false);
        sensorsConfig.activateGyrometer = prefs.getBoolean("gyro_enabled", false);
        sensorsConfig.activateMagnetometer = prefs.getBoolean("mag_enabled", false);
        sensorsConfig.activateOrientationQuat = prefs.getBoolean("orient_quat_enabled", false);
        sensorsConfig.activateOrientationEuler = prefs.getBoolean("orient_euler_enabled", false);
        sensorsConfig.activateGpsLocation = prefs.getBoolean("gps_enabled", false);
        sensorsConfig.activateNetworkLocation = prefs.getBoolean("netloc_enabled", false);
        sensorsConfig.activateBackCamera = prefs.getBoolean("cam_enabled", false);
        if (sensorsConfig.activateBackCamera || sensorsConfig.activateFrontCamera)
            showVideo = true;

        // video settings
        sensorsConfig.videoConfig.codec = prefs.getString("video_codec", VideoEncoderConfig.JPEG_CODEC);
        sensorsConfig.videoConfig.frameRate = Integer.parseInt(prefs.getString("video_framerate", "30"));

        // selected preset or AUTO mode
        String selectedPreset = prefs.getString("video_preset", "0");
        if ("AUTO".equals(selectedPreset)) {
            sensorsConfig.videoConfig.autoPreset = true;
            sensorsConfig.videoConfig.selectedPreset = 0;
        }
        else {
            sensorsConfig.videoConfig.autoPreset = false;
            sensorsConfig.videoConfig.selectedPreset = Integer.parseInt(selectedPreset);
        }

        // video preset list
        int resIdx = 1;
        ArrayList<VideoPreset> presetList = new ArrayList<>();
        while (prefs.contains("video_size" + resIdx))
        {
            String resString = prefs.getString("video_size" + resIdx, "Disabled");
            String[] tokens = resString.split("x");
            VideoPreset preset = new VideoPreset();
            preset.width = Integer.parseInt(tokens[0]);
            preset.height = Integer.parseInt(tokens[1]);
            preset.minBitrate = Integer.parseInt(prefs.getString("video_min_bitrate" + resIdx, "3000"));
            preset.maxBitrate = Integer.parseInt(prefs.getString("video_max_bitrate" + resIdx, "3000"));
            preset.selectedBitrate = preset.maxBitrate;
            presetList.add(preset);
            resIdx++;
        }
        sensorsConfig.videoConfig.presets = presetList.toArray(new VideoPreset[0]);

        sensorsConfig.outputVideoRoll = prefs.getBoolean("video_roll_enabled", false);
        sensorsConfig.runName = runName;
        sensorhubConfig.add(sensorsConfig);
        addSosTConfig(sensorsConfig, sosUser, sosPwd);

        // TruPulse sensor
        boolean enabled = prefs.getBoolean("trupulse_enabled", false);
        if (enabled)
        {
            TruPulseConfig trupulseConfig = new TruPulseConfig();

            // add target geolocation processing if GPS is enabled
            if (sensorsConfig.activateGpsLocation)
            {
                String gpsOutputName = null;
                if (getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION))
                {
                    LocationManager locationManager = (LocationManager)getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                    List<String> locProviders = locationManager.getAllProviders();
                    for (String provName: locProviders)
                    {
                        LocationProvider locProvider = locationManager.getProvider(provName);
                        if (locProvider.requiresSatellite())
                            gpsOutputName = locProvider.getName().replaceAll(" ", "_") + "_data";
                    }
                }

                trupulseConfig = new TruPulseWithGeolocConfig();
                ((TruPulseWithGeolocConfig)trupulseConfig).locationSourceID = sensorsConfig.id;
                ((TruPulseWithGeolocConfig)trupulseConfig).locationOutputName = gpsOutputName;
            }

            trupulseConfig.id = "TRUPULSE_SENSOR";
            trupulseConfig.name = "TruPulse Range Finder [" + deviceName + "]";
            trupulseConfig.autoStart = true;
            trupulseConfig.serialNumber = deviceID;
            BluetoothCommProviderConfig btConf = new BluetoothCommProviderConfig();
            btConf.protocol.deviceName = "TP360RB.*";
            if (prefs.getBoolean("trupulse_simu", false))
                btConf.moduleClass = SimulatedDataStream.class.getCanonicalName();
            else
                btConf.moduleClass = BluetoothCommProvider.class.getCanonicalName();
            trupulseConfig.commSettings = btConf;
            sensorhubConfig.add(trupulseConfig);
            addSosTConfig(trupulseConfig, sosUser, sosPwd);
        }

        // AngelSensor
        enabled = prefs.getBoolean("angel_enabled", false);
        if (enabled)
        {
            BleConfig bleConf = new BleConfig();
            bleConf.id = "BLE";
            bleConf.moduleClass = BleNetwork.class.getCanonicalName();
            bleConf.androidContext = this.getApplicationContext();
            bleConf.autoStart = true;
            sensorhubConfig.add(bleConf);

            AngelSensorConfig angelConfig = new AngelSensorConfig();
            angelConfig.id = "ANGEL_SENSOR";
            angelConfig.name = "Angel Sensor [" + deviceName + "]";
            angelConfig.autoStart = true;
            angelConfig.networkID = bleConf.id;
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
            FlirOneCameraConfig flironeConfig = new FlirOneCameraConfig();
            flironeConfig.id = "FLIRONE_SENSOR";
            flironeConfig.name = "FLIR One Camera [" + deviceName + "]";
            flironeConfig.autoStart = true;
            flironeConfig.androidContext = this.getApplicationContext();
            flironeConfig.camPreviewTexture = boundService.getVideoTexture();
            showVideo = true;
            sensorhubConfig.add(flironeConfig);
            addSosTConfig(flironeConfig, sosUser, sosPwd);
        }

        // DJI Drone
        /*enabled = prefs.getBoolean("dji_enabled", false);
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
        }*/
    }


    protected void addSosTConfig(SensorConfig sensorConf, String sosUser, String sosPwd)
    {
        if (sosUrl == null)
            return;

        SOSTClientConfig sosConfig = new SOSTClientConfig();
        sosConfig.id = sensorConf.id + "_SOST";
        sosConfig.name = sensorConf.name.replaceAll("\\[.*\\]", "");// + "SOS-T Client";
        sosConfig.autoStart = true;
        sosConfig.dataSourceID = sensorConf.id;
        sosConfig.sos.remoteHost = sosUrl.getHost();
        sosConfig.sos.remotePort = sosUrl.getPort() < 0 ? sosUrl.getDefaultPort() : sosUrl.getPort();
        sosConfig.sos.resourcePath = sosUrl.getPath();
        sosConfig.sos.enableTLS = sosUrl.getProtocol().equals("https");
        sosConfig.sos.user = sosUser;
        sosConfig.sos.password = sosPwd;
        sosConfig.connection.connectTimeout = 10000;
        sosConfig.connection.usePersistentConnection = true;
        sosConfig.connection.reconnectAttempts = 9;
        sosConfig.connection.maxQueueSize = 100;
        sensorhubConfig.add(sosConfig);
    }


    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainInfoArea = (TextView) findViewById(R.id.main_info);
        videoInfoArea = (TextView) findViewById(R.id.video_info);

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
            mainInfoArea.setBackgroundColor(0xFFFFFFFF);
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

                AndroidSensorsConfig androidSensorConfig = (AndroidSensorsConfig) sensorhubConfig.get("ANDROID_SENSORS");
                VideoEncoderConfig videoConfig = androidSensorConfig.videoConfig;

                if ((androidSensorConfig.activateBackCamera || androidSensorConfig.activateFrontCamera) && videoConfig.selectedPreset < 0 || videoConfig.selectedPreset >= videoConfig.presets.length) {
                    showVideoConfigErrorPopup();
                    newStatusMessage("Video Config Error: Check Settings");
                } else {
                    sostClients.clear();
                    boundService.startSensorHub(sensorhubConfig, showVideo, MainActivity.this);

                    if (boundService.hasVideo())
                        mainInfoArea.setBackgroundColor(0x80FFFFFF);
                }

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

    protected void showVideoConfigErrorPopup()
    {
        String message = "Check Video Settings and ensure the resolution for the selected preset has been set.";

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("OpenSensorHub");
        alert.setMessage(message);
        alert.setPositiveButton("OK", new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id){
                // user accepted
            }
        });
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

            // detect when Android sensor driver is started
            else if (e.getSource() instanceof AndroidSensorsDriver)
            {
                this.androidSensors = (AndroidSensorsDriver)e.getSource();
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
                mainInfoArea.setText(Html.fromHtml(mainInfoText.toString()));
                videoInfoArea.setText(Html.fromHtml(videoInfoText.toString()));
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
        mainInfoText.setLength(0);
        
        // first display error messages if any
        for (SOSTClient client: sostClients)
        {
            Map<IStreamingDataInterface, StreamInfo> dataStreams = client.getDataStreams();
            boolean showError = (client.getCurrentError() != null);
            boolean showMsg = (dataStreams.size() == 0) && (client.getStatusMessage() != null);
            
            if (showError || showMsg)
            {
                mainInfoText.append("<p>" + client.getName() + ":<br/>");
                if (showMsg)
                    mainInfoText.append(client.getStatusMessage() + "<br/>");
                if (showError)
                {
                    Throwable errorObj = client.getCurrentError();
                    String errorMsg = errorObj.getMessage().trim();
                    if (!errorMsg.endsWith("."))
                        errorMsg += ". ";
                    if (errorObj.getCause() != null && errorObj.getCause().getMessage() != null)
                        errorMsg += errorObj.getCause().getMessage();
                    mainInfoText.append("<font color='red'>" + errorMsg + "</font>");
                }
                mainInfoText.append("</p>");
            }
        }
        
        // then display streams status
        mainInfoText.append("<p>");
        for (SOSTClient client: sostClients)
        {
            Map<IStreamingDataInterface, StreamInfo> dataStreams = client.getDataStreams();
            long now = System.currentTimeMillis();
            
            for (Entry<IStreamingDataInterface, StreamInfo> stream : dataStreams.entrySet())
            {
                mainInfoText.append("<b>" + stream.getKey().getName() + " : </b>");

                long lastEventTime = stream.getValue().lastEventTime;
                long dt = now - lastEventTime;
                if (lastEventTime == Long.MIN_VALUE)
                    mainInfoText.append("<font color='red'>NO OBS</font>");
                else if (dt > stream.getValue().measPeriodMs)
                    mainInfoText.append("<font color='red'>NOK (" + dt + "ms ago)</font>");
                else
                    mainInfoText.append("<font color='green'>OK (" + dt + "ms ago)</font>");

                if (stream.getValue().errorCount > 0)
                {
                    mainInfoText.append("<font color='red'> (");
                    mainInfoText.append(stream.getValue().errorCount);
                    mainInfoText.append(")</font>");
                }

                mainInfoText.append("<br/>");
            }
        }

        if (mainInfoText.length() > 5)
            mainInfoText.setLength(mainInfoText.length()-5); // remove last </br>
        mainInfoText.append("</p>");

        // show video info
        if (androidSensors != null && boundService.hasVideo())
        {
            // TODO: Fix crash resulting from this (620)
            try {
                VideoEncoderConfig config = androidSensors.getConfiguration().videoConfig;
                VideoPreset preset = config.presets[config.selectedPreset];
                videoInfoText.setLength(0);
                videoInfoText.append("")
                        .append(config.codec).append(", ")
                        .append(preset.width).append("x").append(preset.height).append(", ")
                        .append(config.frameRate).append(" fps, ")
                        .append(preset.selectedBitrate).append(" kbits/s")
                        .append("");
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    
    
    protected synchronized void newStatusMessage(String msg)
    {
        mainInfoText.setLength(0);
        appendStatusMessage(msg);
    }
    
    
    protected synchronized void appendStatusMessage(String msg)
    {
        mainInfoText.append(msg);

        displayHandler.post(new Runnable()
        {
            public void run()
            {
                mainInfoArea.setText(mainInfoText.toString());
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
                mainInfoArea.setBackgroundColor(0x80FFFFFF);
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
