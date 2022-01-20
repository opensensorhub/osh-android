/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.event.IEventListener;
import org.sensorhub.api.module.IModuleConfigRepository;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.SensorHubConfig;
import org.sensorhub.impl.event.EventBus;
import org.sensorhub.impl.module.ModuleRegistry;
import org.vast.xml.XMLImplFinder;

import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;


/**
 * <p>
 * Android Service wrapping the sensorhub instance
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Jan 24, 2015
 */
public class SensorHubService extends Service
{
    final IBinder binder = new LocalBinder();
    private HandlerThread msgThread;
    private Handler msgHandler;
    SensorHubAndroid sensorhub;
    boolean hasVideo;
    static Context context;
    static SurfaceTexture videoTex;
    String serverId;


    public class LocalBinder extends Binder
    {
        SensorHubService getService() {
            return SensorHubService.this;
        }
    }

        
    @Override
    public void onCreate() {
        
        try
        {
            // keep handle to Android context so it can be retrieved by OSH components
            SensorHubService.context = getApplicationContext();

            // create video surface texture here so it's not destroyed when pausing the app
            SensorHubService.videoTex = new SurfaceTexture(1);
            SensorHubService.videoTex.detachFromGLContext();

            // load external dex file containing stax API
            //Dexter.loadFromAssets(this.getApplicationContext(), "stax-api-1.0-2.dex");
            
            // set default StAX implementation
            XMLImplFinder.setStaxInputFactory(com.ctc.wstx.stax.WstxInputFactory.class.newInstance());
            XMLImplFinder.setStaxOutputFactory(com.ctc.wstx.stax.WstxOutputFactory.class.newInstance());
            
            // set default DOM implementation
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            XMLImplFinder.setDOMImplementation(dbf.newDocumentBuilder().getDOMImplementation());

            // start handler thread
            msgThread = new HandlerThread("SensorHubService", Process.THREAD_PRIORITY_BACKGROUND);
            msgThread.start();
            msgHandler = new Handler(msgThread.getLooper());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }        
    }
    
    
    public synchronized void startSensorHub(final IModuleConfigRepository config, final boolean hasVideo, final IEventListener listener)
    {
        if (sensorhub != null)
            return;

        this.hasVideo = hasVideo;

        msgHandler.post(new Runnable() {

            // TODO: Make sure this isn't breaking anything - Do we need to register a listener or should it subscribe now?
            public void run() {
                // create and start sensorhub instance
                sensorhub = new SensorHubAndroid(new SensorHubConfig(), config);

//                ModuleRegistry reg = new ModuleRegistry(sensorhub, config);

                try {
                    sensorhub.start();
                } catch (SensorHubException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    
    public synchronized void stopSensorHub()
    {
        if (sensorhub == null)
            return;

        this.hasVideo = false;

        msgHandler.post(new Runnable() {
            public void run() {
                sensorhub.stop();
                sensorhub = null;

               /* // Make sure the server gets cleaned up
                try {
                    // Check modules for an HTTPServer
                    ModuleRegistry reg = sensorhub.getModuleRegistry();
                    if (serverId != null) {
                        reg.stopModule(serverId);
                    }
                } catch (SensorHubException e) {
                    e.printStackTrace();
                }*/
            }
        });
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
        SensorHubService.videoTex.release();
        SensorHubService.videoTex = null;
        SensorHubService.context = null;
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
        return context;
    }
    
}
