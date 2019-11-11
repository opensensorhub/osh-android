/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2019 Botts Innovative Research, Inc. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.spotreport;

import javax.xml.namespace.QName;
import net.opengis.gml.v32.AbstractFeature;
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
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.provider.Settings.Secure;

import java.util.List;


/**
 * <p>
 * Main driver class for Android Spot Reports
 * </p>
 *
 * @author Nicolas Garay <nicolasgaray@icloud.com>
 * @since Nov 9, 2019
 */
public class SpotReportDriver extends AbstractSensorModule<SpotReportConfig> {

    // keep logger name short because in LogCat it's max 23 chars
    private static final Logger log = LoggerFactory.getLogger(SpotReportDriver.class.getSimpleName());
    public static final String LOCAL_REF_FRAME = "LOCAL_FRAME";

    String localFrameURI;
    LocationProvider locationProvider;
    SpotReportOutput spotReportOutput;
        
    public SpotReportDriver() {

        Context androidContext = config.androidContext;
        this.localFrameURI = this.uniqueID + "#" + LOCAL_REF_FRAME;

        // create data interfaces for location providers
        if (androidContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION)) {

            LocationManager locationManager = (LocationManager)androidContext.getSystemService(Context.LOCATION_SERVICE);

            List<String> locProviders = locationManager.getAllProviders();

            for (String provName: locProviders) {

                log.debug("Detected location provider " + provName);
                LocationProvider locProvider = locationManager.getProvider(provName);

                // Set selected location provider
                this.locationProvider = null;
            }
        }
    }
    
    @Override
    public void init() throws SensorHubException {

        super.init();
        
        // generate identifiers
        String deviceID = Secure.getString(config.androidContext.getContentResolver(), Secure.ANDROID_ID);
        this.uniqueID = "urn:spotreport:android:" + deviceID;
        this.xmlID = "SPOT_REPORT_" + deviceID;
        
        // create output
        spotReportOutput = new SpotReportOutput(this, locationProvider);
        spotReportOutput.init();
        this.addOutput(spotReportOutput, false);
    }


    @Override
    public void start() throws SensorException {

        spotReportOutput.start(config.androidContext);
    }
    
    
    @Override
    public void stop() throws SensorException {

        if (spotReportOutput != null) {

            spotReportOutput.stop();
        }
    }


    @Override
    protected void updateSensorDescription() {

        synchronized (sensorDescLock) {

            super.updateSensorDescription();
            
            SpatialFrame localRefFrame = new SpatialFrameImpl();
            localRefFrame.setId(LOCAL_REF_FRAME);
            localRefFrame.setOrigin("Center of the device screen");
            localRefFrame.addAxis("x", "The X axis is in the plane of the screen and points to the right");
            localRefFrame.addAxis("y", "The Y axis is in the plane of the screen and points up");
            localRefFrame.addAxis("z", "The Z axis points towards the outside of the front face of the screen");
            sensorDescription.addLocalReferenceFrame(localRefFrame);

            // add FOI
            AbstractFeature foi = getCurrentFeatureOfInterest();

            if (foi != null) {
                sensorDescription.getFeaturesOfInterest().addFeature(foi);
            }
        }
    }
    
    
    @Override
    public AbstractFeature getCurrentFeatureOfInterest() {

        if (config.runName != null && config.runName.length() > 0) {

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
    public boolean isConnected() {
        return true;
    }
    
    
    @Override
    public void cleanup() throws SensorHubException {
    }
}
