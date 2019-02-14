/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.android;

import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;

import android.os.Handler;
import android.os.HandlerThread;
import net.opengis.gml.v32.AbstractFeature;
import net.opengis.sensorml.v20.PhysicalComponent;
import net.opengis.sensorml.v20.PhysicalSystem;
import net.opengis.sensorml.v20.SpatialFrame;
import net.opengis.sensorml.v20.impl.SpatialFrameImpl;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.ogc.gml.GenericFeatureImpl;
import org.vast.sensorML.SMLStaxBindings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings.Secure;


public class AndroidSensorsDriver extends AbstractSensorModule<AndroidSensorsConfig>
{
    // keep logger name short because in LogCat it's max 23 chars
    private static final Logger log = LoggerFactory.getLogger(AndroidSensorsDriver.class.getSimpleName());
    public static final String LOCAL_REF_FRAME = "LOCAL_FRAME";
    
    String localFrameURI;
    HandlerThread eventThread;
    SensorManager sensorManager;
    LocationManager locationManager;
    SensorMLBuilder smlBuilder;
    List<PhysicalComponent> smlComponents;
    
    
    public AndroidSensorsDriver()
    {
        smlComponents = new ArrayList<PhysicalComponent>();
        smlBuilder = new SensorMLBuilder();
    }
    
    
    @Override
    public synchronized void init() throws SensorHubException
    {
        Context androidContext = config.androidContext;
        
        // generate identifiers
        String deviceID = Secure.getString(androidContext.getContentResolver(), Secure.ANDROID_ID);
        this.xmlID = "ANDROID_SENSORS_" + Build.SERIAL;
        this.uniqueID = "urn:android:device:" + deviceID;
        this.localFrameURI = this.uniqueID + "#" + LOCAL_REF_FRAME;
        
        // create data interfaces for sensors
        boolean isUsingAccelerometer = false;
        boolean isUsingGyrscope = false;
        boolean isUsingMagnetometer = false;
        boolean isUsingOrientationQuat = false;
        boolean isUsingOrientationEuler = false;
        this.sensorManager = (SensorManager)androidContext.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor: deviceSensors)
        {
            log.debug("Detected sensor " + sensor.getName());
            
            switch (sensor.getType())
            {
                case Sensor.TYPE_ACCELEROMETER:
                    if (config.activateAccelerometer && !isUsingAccelerometer
                            && !sensor.getName().toLowerCase().contains("non-wakeup"))
                    {
                        useSensor(new AndroidAcceleroOutput(this, sensorManager, sensor), sensor);
                        isUsingAccelerometer = true;
                    }
                    break;
                    
                case Sensor.TYPE_GYROSCOPE:
                    if (config.activateGyrometer && !isUsingGyrscope
                            && !sensor.getName().toLowerCase().contains("non-wakeup"))
                    {
                        useSensor(new AndroidGyroOutput(this, sensorManager, sensor), sensor);
                        isUsingGyrscope = true;
                    }
                    break;
                
                case Sensor.TYPE_MAGNETIC_FIELD:
                    if (config.activateMagnetometer && !isUsingMagnetometer
                            && !sensor.getName().toLowerCase().contains("non-wakeup"))
                    {
                        useSensor(new AndroidMagnetoOutput(this, sensorManager, sensor), sensor);
                        isUsingMagnetometer = true;
                    }
                    break;
                    
                case Sensor.TYPE_ROTATION_VECTOR:
                    if (config.activateOrientationQuat && !isUsingOrientationQuat
                            && !sensor.getName().toLowerCase().contains("non-wakeup"))
                    {
                        useSensor(new AndroidOrientationQuatOutput(this, sensorManager, sensor), sensor);
                        isUsingOrientationQuat = true;
                    }
                    if (config.activateOrientationEuler && !isUsingOrientationEuler
                            && !sensor.getName().toLowerCase().contains("non-wakeup"))
                    {
                        useSensor(new AndroidOrientationEulerOutput(this, sensorManager, sensor), sensor);
                        isUsingOrientationEuler = true;
                    }
                    break;
            }
        }
        
        // create data interfaces for location providers
        if (androidContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION))
        {
            this.locationManager = (LocationManager)androidContext.getSystemService(Context.LOCATION_SERVICE);
            
            List<String> locProviders = locationManager.getAllProviders();
            for (String provName: locProviders)
            {
                log.debug("Detected location provider " + provName);
                LocationProvider locProvider = locationManager.getProvider(provName);
                
                // keep only GPS for now
                if ( (locProvider.requiresSatellite() && config.activateGpsLocation) ||
                     (locProvider.requiresNetwork() && config.activateNetworkLocation))
                    useLocationProvider(new AndroidLocationOutput(this, locationManager, locProvider), locProvider);
            }
        }
        
        // create data interfaces for cameras
        if (androidContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
            createCameraOutputs(androidContext);
    }


    @Override
    public void start() throws SensorException
    {
        /**
         * TODO: What thread does Jetty run on? Reduce the number of threads Jetty is using first.
         * TODO: Are each sensor on their own thread? Look into how android handles threads
         */
        // start event handling thread
        eventThread = new HandlerThread("SensorThread " + getName());
        eventThread.start();
        Handler eventHandler = new Handler(eventThread.getLooper());

        for (ISensorDataInterface o: getAllOutputs().values())
            ((IAndroidOutput)o).start(eventHandler);
    }
    
    
    @SuppressWarnings("deprecation")
    protected void createCameraOutputs(Context androidContext) throws SensorException
    {
        /*if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP)
        {
            CameraManager cameraManager = (CameraManager)androidContext.getSystemService(Context.CAMERA_SERVICE);
            
            try
            {
                String[] camIds = cameraManager.getCameraIdList();
                for (String cameraId: camIds)
                {
                    log.debug("Detected camera " + cameraId);
                    int camDir = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
                    if ( (camDir == CameraCharacteristics.LENS_FACING_BACK && config.activateBackCamera) ||
                         (camDir == CameraCharacteristics.LENS_FACING_FRONT && config.activateFrontCamera))
                    {
                        useCamera2(new AndroidCamera2Output(this, cameraManager, cameraId, config.camPreviewSurfaceHolder), cameraId);
                    }
                }
            }
            catch (CameraAccessException e)
            {
                throw new SensorException("Error while accessing cameras", e);
            }
        }
        else*/
        {
            for (int cameraId = 0; cameraId < android.hardware.Camera.getNumberOfCameras(); cameraId++)
            {
                android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();                    
                android.hardware.Camera.getCameraInfo(cameraId, info);

                if ( (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK && config.activateBackCamera) ||
                     (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT && config.activateFrontCamera))
                {
                    if (AndroidSensorsConfig.JPEG_CODEC.equals(config.videoCodec))
                        useCamera(new AndroidCameraOutputMJPEG(this, cameraId, config.camPreviewTexture), cameraId);
                    else if (AndroidSensorsConfig.H264_CODEC.equals(config.videoCodec))
                        useCamera(new AndroidCameraOutputH264(this, cameraId, config.camPreviewTexture), cameraId);
                    else
                        throw new SensorException("Unsupported codec " + config.videoCodec);
                }
            }
        }
    }
    
    
    protected void useSensor(ISensorDataInterface output, Sensor sensor)
    {
        addOutput(output, false);
        smlComponents.add(smlBuilder.getComponentDescription(sensorManager, sensor));
        log.info("Getting data from " + sensor.getName() + " sensor");
    }
    
    
    protected void useLocationProvider(ISensorDataInterface output, LocationProvider locProvider)
    {
        addOutput(output, false);
        smlComponents.add(smlBuilder.getComponentDescription(locationManager, locProvider));
        log.info("Getting data from " + locProvider.getName() + " location provider");
    }
    
    
    protected void useCamera(ISensorDataInterface output, int cameraId)
    {
        addOutput(output, false);
        smlComponents.add(smlBuilder.getComponentDescription(cameraId));
        log.info("Getting data from camera #" + cameraId);
    }
    
    
    protected void useCamera2(ISensorDataInterface output, String cameraId)
    {
        addOutput(output, false);
        smlComponents.add(smlBuilder.getComponentDescription(cameraId));
        log.info("Getting data from camera #" + cameraId);
    }
    
    
    @Override
    public void stop() throws SensorException
    {
        // stop all outputs
        for (ISensorDataInterface o: this.getAllOutputs().values())
            ((IAndroidOutput)o).stop();

        // stop event handling thread
        if (eventThread != null)
        {
            eventThread.quitSafely();
            eventThread = null;
        }
        
        this.removeAllOutputs();
        this.removeAllControlInputs();
    }


    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();
            
            // ref frame
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
            
            // add components
            int index = 0;
            for (PhysicalComponent comp: smlComponents)
            {
                String name = "sensor" + index++;
                ((PhysicalSystem)sensorDescription).addComponent(name, comp);
            }
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
        return true;
    }
    
    
    @Override
    public void cleanup() throws SensorHubException
    {     
    }


    @Override
    public Logger getLogger()
    {
        return log;
    }
}
