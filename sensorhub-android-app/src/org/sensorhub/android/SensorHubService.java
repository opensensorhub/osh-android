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
import org.sensorhub.api.module.IModuleConfigRepository;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.SensorHubConfig;
import org.sensorhub.impl.common.EventBus;
import org.sensorhub.impl.module.ModuleRegistry;
import org.vast.xml.XMLImplFinder;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;


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
    private Thread bgThread;
    SensorHub sensorhub;
    
    
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
            Dexter.loadFromAssets(this.getApplicationContext(), "stax-api-1.0-2.dex");
            
            // set default StAX implementation
            XMLImplFinder.setStaxInputFactory(com.ctc.wstx.stax.WstxInputFactory.class.newInstance());
            XMLImplFinder.setStaxOutputFactory(com.ctc.wstx.stax.WstxOutputFactory.class.newInstance());
            
            // set default DOM implementation
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            XMLImplFinder.setDOMImplementation(dbf.newDocumentBuilder().getDOMImplementation());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }        
    }
    
    
    public synchronized void startSensorHub(final IModuleConfigRepository config)
    {
        if (bgThread == null)
        {
            bgThread = new Thread() {
                
                public void run() 
                {
                    // create sensorhub instance
                    EventBus eventBus = new EventBus();
                    ModuleRegistry reg = new ModuleRegistry(config, eventBus);
                    sensorhub = SensorHub.createInstance(new SensorHubConfig(), reg, eventBus);
                    
                    // notify waithing thread that sensorhub instance is available
                    synchronized (SensorHubService.this)
                    {
                        SensorHubService.this.notify();
                    }
                    
                    // start sensorhub    
                    sensorhub.start();
                }        
            };
            
            bgThread.start();
        }
        
        try
        {
            while (sensorhub == null)
                wait();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    
    public synchronized void stopSensorHub()
    {
        if (bgThread != null)
        {
            sensorhub.stop();
            SensorHub.clearInstance();
            sensorhub = null;
            bgThread = null;
        }
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
    }


    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }


    /**
     * @return the SensorHub instance
     */
    public SensorHub getSensorHub()
    {
        return sensorhub;
    }
    
}
