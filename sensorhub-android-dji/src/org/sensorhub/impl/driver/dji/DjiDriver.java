/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.dji;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.namespace.QName;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.util.CommonCallbacks.*;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
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
import android.content.Context;
import android.provider.Settings.Secure;


/**
 * <p>
 * Main driver class for DJI Mobile SDK
 * </p>
 *
 * @author Alex Robin
 * @since May 5, 2018
 */
public class DjiDriver extends AbstractSensorModule<DjiConfig>
{
    // keep logger name short because in LogCat it's max 23 chars
    private static final Logger log = LoggerFactory.getLogger(DjiDriver.class.getSimpleName());
    public static final String LOCAL_REF_FRAME = "LOCAL_FRAME";

    AtomicBoolean isStarting = new AtomicBoolean(false);
    Aircraft aircraft;
    DjiCameraOutput camOutput;
    
        
    public DjiDriver()
    {
    }
    
    
    @Override
    public void init() throws SensorHubException
    {
        super.init();
        initDjiSdk();

        // generate identifiers
        aircraft.getFlightController().getSerialNumber(new CompletionCallbackWith<String>() {
            @Override
            public void onSuccess(String serialNumber) {
                String uavModel = aircraft.getModel().name();
                DjiDriver.this.uniqueID = "urn:dji:uav:" + uavModel + ":" + serialNumber;
                DjiDriver.this.xmlID = "DJI_UAV_" + serialNumber;
                reportStatus(String.format("Aircraft is %s (%s)", uavModel, serialNumber));
            }

            @Override
            public void onFailure(DJIError djiError) {
                reportError("Cannot read aircraft serial number", djiError);
            }
        });
        
        // create output
        //camOutput = new DjiCameraOutput(this, config.camPreviewTexture);
        //camOutput.init();
        //this.addOutput(camOutput, false);
    }


    private void initDjiSdk()
    {
        if (isStarting.compareAndSet(false, true))
        {
            if (!DJISDKManager.getInstance().hasSDKRegistered())
            {
                DJISDKManager.getInstance().registerApp(config.androidContext, new DJISDKManager.SDKManagerCallback() {
                    @Override
                    public void onRegister(DJIError djiError) {
                        if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                            reportStatus("Registered with DJI SDK");
                            DJISDKManager.getInstance().startConnectionToProduct();
                        } else {
                            reportError("SDK registration failed", djiError);
                        }
                    }

                    @Override
                    public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                        reportStatus("Connected to DJI Aircraft");

                        if (isFlightControllerSupported()) {
                            aircraft = (Aircraft) DJISDKManager.getInstance().getProduct();
                            //aircraft.setBaseProductListener(mDJIBaseProductListener);
                        }
                        //notifyStatusChange();
                    }
                });
            }
        }
    }


    private boolean isFlightControllerSupported()
    {
        return DJISDKManager.getInstance().getProduct() != null &&
                DJISDKManager.getInstance().getProduct() instanceof Aircraft &&
                ((Aircraft) DJISDKManager.getInstance().getProduct()).getFlightController() != null;
    }


    @Override
    public void start() throws SensorException
    {

    }
    
    
    @Override
    public void stop() throws SensorException
    {
        if (camOutput != null)
            camOutput.stop();
    }


    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();
            
            SpatialFrame localRefFrame = new SpatialFrameImpl();
            localRefFrame.setId(LOCAL_REF_FRAME);
            localRefFrame.setOrigin("UAV center of mass");
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


    private void reportError(String msg, DJIError error)
    {
        reportError(msg, new IllegalStateException(error.getDescription()));
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
}
