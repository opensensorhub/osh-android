package org.sensorhub.android;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.SurfaceTexture;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.module.IModuleConfigRepository;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.SensorHubConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.xml.XMLImplFinder;

import javax.xml.parsers.DocumentBuilderFactory;

public class SensorHubService extends Service
{
    private static final String ANDROID_LOG_TAG = "SensorHubStartup";
    private static final Logger log = LoggerFactory.getLogger(SensorHubService.class);
    final IBinder binder = new LocalBinder();
    private HandlerThread msgThread;
    private Handler msgHandler;
    volatile SensorHubAndroid sensorhub;
    volatile boolean hasVideo;
    public enum HubState { STOPPED, STARTING, RUNNING, STOPPING, ERROR }
    private volatile HubState hubState = HubState.STOPPED;
    public HubState getHubState() { return hubState; }
    private static Context appContext;
    private static SurfaceTexture videoTex;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private static final String CHANNEL_ID = "sensorhub_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private LocationManager locationManager;
    private LocationListener locationListener;

    public class LocalBinder extends Binder {
        public SensorHubService getService() {
            return SensorHubService.this;
        }
    }


    @Override
    public void onCreate() {

        try
        {
            log.info("Initializing SensorHub Android service");
            SensorHubService.appContext = getApplicationContext();

            SensorHubService.videoTex = new SurfaceTexture(1);
            SensorHubService.videoTex.detachFromGLContext();

            // load external dex file containing stax API
            //Dexter.loadFromAssets(this.getApplicationContext(), "stax-api-1.0-2.dex");

            // set default StAX implementation
            XMLImplFinder.setStaxInputFactory(WstxInputFactory.class.newInstance());
            XMLImplFinder.setStaxOutputFactory(WstxOutputFactory.class.newInstance());

            // set default DOM implementation
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            XMLImplFinder.setDOMImplementation(dbf.newDocumentBuilder().getDOMImplementation());

            // start handler thread
            msgThread = new HandlerThread("SensorHubService", Process.THREAD_PRIORITY_BACKGROUND);
            msgThread.start();
            msgHandler = new Handler(msgThread.getLooper());

            log.info("SensorHub Android service initialized");
        }
        catch (Exception e)
        {
            Log.e(ANDROID_LOG_TAG, "SensorHub Android service initialization failed", e);
        }
    }

    private void startForegroundService(boolean usesLocation, boolean usesCamera, boolean usesMicrophone) {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SensorHub Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps sensor data collection running");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent notificationIntent = new Intent();
        notificationIntent.setClassName(
                getApplicationContext(),
                "org.sensorhub.android.ComposeMainActivity"
        );

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );
        }

        // Create notification
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("SensorHub Running")
                    .setContentText("Collecting and transmitting sensor data")
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("SensorHub Running")
                    .setContentText("Collecting and transmitting sensor data")
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_LOW)
                    .build();
        }

        int foregroundServiceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        if (usesLocation) {
            foregroundServiceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        }
        if (usesCamera) {
            foregroundServiceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        }
        if (usesMicrophone) {
            foregroundServiceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        }

        // Start foreground
        startForeground(NOTIFICATION_ID, notification, foregroundServiceTypes);
    }

    public synchronized void startSensorHub(
            final IModuleConfigRepository config,
            final boolean usesLocation,
            final boolean hasVideo,
            final boolean usesMicrophone
    )
    {
        Log.i(ANDROID_LOG_TAG, "SensorHub start requested; video=" + hasVideo);
        if (hubState == HubState.STARTING || hubState == HubState.RUNNING || hubState == HubState.STOPPING) {
            log.warn("Ignoring SensorHub start request while hub is {}", hubState);
            return;
        }
        log.info("Starting SensorHub (video enabled: {})", hasVideo);
        hubState = HubState.STARTING;

        this.hasVideo = hasVideo;

        if (hasVideo) {
            if (videoTex != null) {
                videoTex.release();
            }
            videoTex = new SurfaceTexture(1);
            videoTex.detachFromGLContext();
        }

        try {
            acquireWakeLocks();
            startForegroundService(usesLocation, hasVideo, usesMicrophone);
            if (msgHandler == null)
                throw new IllegalStateException("SensorHub service did not initialize");
        } catch (RuntimeException e) {
            hubState = HubState.ERROR;
            this.hasVideo = false;
            releaseWakeLocks();
            Log.e(ANDROID_LOG_TAG, "SensorHub startup failed before background initialization", e);
            throw e;
        }

        msgHandler.post(new Runnable() {
            public void run() {
                try {
                    Log.i(ANDROID_LOG_TAG, "Creating and starting SensorHub modules");
                    log.info("Creating SensorHub instance");
                    sensorhub = new SensorHubAndroid(new SensorHubConfig(), config);
                    log.info("Starting SensorHub modules");
                    sensorhub.start();
                    hubState = HubState.RUNNING;
                    log.info("SensorHub started successfully");
                } catch (Throwable e) {
                    Log.e(ANDROID_LOG_TAG, "SensorHub startup failed", e);
                    try {
                        if (sensorhub != null) sensorhub.stop();
                    } catch (Throwable ex) {
                        Log.e(ANDROID_LOG_TAG, "Error stopping failed SensorHub", ex);
                    }
                    sensorhub = null;
                    SensorHubService.this.hasVideo = false;
                    releaseWakeLocks();
                    hubState = HubState.ERROR;
                }
            }
        });
    }

    private void acquireWakeLocks() {
        // Acquire partial wake lock to keep CPU active
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SensorHub::DataCollection"
            );
            wakeLock.acquire();
        }

        // Acquire WiFi lock to keep WiFi active
        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                    "SensorHub::WiFiLock"
            );
            wifiLock.acquire();
        }
    }

    private void releaseWakeLocks() {
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }

        // Release WiFi lock
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    public synchronized void stopSensorHub()
    {
        hubState = HubState.STOPPING;
        msgHandler.post(() -> {
            if (sensorhub != null) {
                try {
                    sensorhub.stop();
                } catch (Exception e) {
                    log.error("Error stopping SensorHub", e);
                }
                sensorhub = null;
            }
            hubState = HubState.STOPPED;
        });

        this.hasVideo = false;

        releaseWakeLocks();

        stopForeground(true);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }


    @Override
    public void onDestroy()
    {
        stopSensorHub();
        msgThread.quitSafely();
        if (SensorHubService.videoTex != null) {
            SensorHubService.videoTex.release();
            SensorHubService.videoTex = null;
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        log.info("Task removed, scheduling restart");
        Intent restartIntent = new Intent(getApplicationContext(), SensorHubService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 1000, pendingIntent);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }


    public SensorHub getSensorHub()
    {
        return sensorhub;
    }


    public boolean hasVideo()
    {
        return hasVideo;
    }


    public static SurfaceTexture getVideoTexture()
    {
        return videoTex;
    }


    public static Context getContext()
    {
        return appContext;
    }
}
