/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.flir;

import java.util.EnumSet;
import javax.xml.namespace.QName;
import net.opengis.gml.v32.AbstractFeature;
import net.opengis.sensorml.v20.PhysicalSystem;
import net.opengis.sensorml.v20.SpatialFrame;
import net.opengis.sensorml.v20.impl.SpatialFrameImpl;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.ogc.gml.GenericFeatureImpl;
import org.vast.sensorML.SMLStaxBindings;
import com.flir.flironesdk.Device;
import com.flir.flironesdk.Device.TuningState;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage.ImageType;
import android.content.Context;
import android.provider.Settings.Secure;


/**
 * <p>
 * Main driver class for Android FLIR One Thermal Camera 
 * </p>
 *
 * @author Alex Robin
 * @since Apr 13, 2016
 */
public class FlirOneCameraDriver extends AbstractSensorModule<FlirOneCameraConfig> implements Device.Delegate
{
    // keep logger name short because in LogCat it's max 23 chars
    private static final Logger log = LoggerFactory.getLogger(FlirOneCameraDriver.class.getSimpleName());
    public static final String LOCAL_REF_FRAME = "LOCAL_FRAME";
    
    volatile boolean connected;
    FlirOneCameraOutput camOutput;
    
        
    public FlirOneCameraDriver()
    {
    }
    
    
    @Override
    public void init() throws SensorHubException
    {
        super.init();
        
        // generate identifiers
        String deviceID = Secure.getString(config.androidContext.getContentResolver(), Secure.ANDROID_ID);
        this.uniqueID = "urn:flir:cam:flirone:android:" + deviceID;
        this.xmlID = "FLIRONE_CAMERA_" + deviceID;
        
        // create output
        camOutput = new FlirOneCameraOutput(this, config.camPreviewSurfaceHolder);
        camOutput.init();
        this.addOutput(camOutput, false);
    }


    @Override
    public void start() throws SensorException
    {
        // we call stop() to cleanup just in case we weren't properly stopped
        //stop();
        
        Context context = config.androidContext;      
        new FrameProcessor(context, camOutput, EnumSet.of(ImageType.ThermalRGBA8888Image));
        Device.startDiscovery(context, this);        
    }
    
    
    @Override
    public void stop() throws SensorException
    {
        if (camOutput != null)
            camOutput.stop();
        Device.stopDiscovery();
    }


    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();
            
            SpatialFrame localRefFrame = new SpatialFrameImpl();
            localRefFrame.setId("LOCAL_FRAME");
            localRefFrame.setOrigin("Center of the device screen");
            localRefFrame.addAxis("x", "The X axis is in the plane of the screen and points to the right");
            localRefFrame.addAxis("y", "The Y axis is in the plane of the screen and points up");
            localRefFrame.addAxis("z", "The Z axis points towards the outside of the front face of the screen");
            ((PhysicalSystem)sensorDescription).addLocalReferenceFrame(localRefFrame);
            
            // add FOI
            AbstractFeature foi = getCurrentFeatureOfInterest();
            if (foi != null)
                sensorDescription.getFeaturesOfInterest().addFeature(foi);
        }
    }
    
    
    @Override
    public AbstractFeature getCurrentFeatureOfInterest()
    {
        if (config.runName != null && config.runName.length() > 0)
        {
            AbstractFeature foi = new GenericFeatureImpl(new QName(SMLStaxBindings.NS_URI, "Feature", "sml"));
            String uid = "urn:android:foi:" + config.runName.replaceAll("[ |']", "");
            foi.setUniqueIdentifier(uid);
            foi.setName(config.runName);
            foi.setDescription(config.runDescription);
            return foi;
        }
        
        return null;
    }


    @Override
    public boolean isConnected()
    {
        return true;//connected;
    }
    
    
    @Override
    public void cleanup() throws SensorHubException
    {     
    }


    @Override
    public void onDeviceConnected(Device dev)
    {
        log.info("FLIR One camera connected");
        connected = true;
        camOutput.start(config.androidContext, dev);
    }


    @Override
    public void onDeviceDisconnected(Device dev)
    {
        log.info("FLIR One camera disconnected");
        connected = false;
    }


    @Override
    public void onAutomaticTuningChanged(boolean arg0)
    {
        
    }


    @Override
    public void onTuningStateChanged(TuningState state)
    {
        
    }
}
