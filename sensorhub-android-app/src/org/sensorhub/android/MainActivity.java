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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import android.view.*;
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
//import org.sensorhub.impl.driver.dji.DjiConfig;
import org.sensorhub.impl.driver.flir.FlirOneCameraConfig;
import org.sensorhub.impl.module.InMemoryConfigDb;
import org.sensorhub.impl.sensor.android.AndroidSensorsConfig;
import org.sensorhub.impl.sensor.angel.AngelSensorConfig;
import org.sensorhub.impl.sensor.trupulse.TruPulseConfig;
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


public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, IEventListener
{
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
        sensorsConfig.videoCodec = prefs.getString("video_codec", AndroidSensorsConfig.JPEG_CODEC);
        sensorsConfig.androidContext = this.getApplicationContext();
        sensorsConfig.camPreviewTexture = boundService.getVideoTexture();
        sensorsConfig.runName = runName;
        sensorhubConfig.add(sensorsConfig);
        addSosTConfig(sensorsConfig, sosUser, sosPwd);

        // TruPulse sensor
        boolean enabled = prefs.getBoolean("trupulse_enabled", false);
        if (enabled)
        {
            TruPulseConfig trupulseConfig = new TruPulseConfig();
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
