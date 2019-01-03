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

import javax.xml.parsers.DocumentBuilderFactory;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.os.*;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.WindowManager;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.module.IModuleConfigRepository;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.SensorHubConfig;
import org.sensorhub.impl.common.EventBus;
import org.sensorhub.impl.module.ModuleRegistry;
import org.vast.xml.XMLImplFinder;
import android.app.Service;
import android.content.Intent;


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
    SensorHub sensorhub;
    SurfaceTexture videoTex;
    boolean hasVideo;


    public class LocalBinder extends Binder {
        SensorHubService getService() {
            return SensorHubService.this;
        }
    }

        
    @Override
    public void onCreate() {
        
        try
        {
            // load external dex file containing stax API
            //Dexter.loadFromAssets(this.getApplicationContext(), "stax-api-1.0-2.dex");
            
            // set default StAX implementation
            XMLImplFinder.setStaxInputFactory(com.ctc.wstx.stax.WstxInputFactory.class.newInstance());
            XMLImplFinder.setStaxOutputFactory(com.ctc.wstx.stax.WstxOutputFactory.class.newInstance());
            
            // set default DOM implementation
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            XMLImplFinder.setDOMImplementation(dbf.newDocumentBuilder().getDOMImplementation());

            // create video surface texture here so it's not destroyed when pausing the app
            videoTex = new SurfaceTexture(1);
            videoTex.detachFromGLContext();

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
            public void run()
            {
                // create and start sensorhub instance
                EventBus eventBus = new EventBus();
                ModuleRegistry reg = new ModuleRegistry(config, eventBus);
                reg.registerListener(listener);
                sensorhub = SensorHub.createInstance(new SensorHubConfig(), reg, eventBus);
                sensorhub.start();
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
                SensorHub.clearInstance();
                sensorhub = null;
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
        videoTex.release();
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


    public SurfaceTexture getVideoTexture()
    {
        return videoTex;
    }


    public boolean hasVideo()
    {
        return hasVideo;
    }
    
}
