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

import android.provider.Settings.Secure;

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
    private static final String LOCAL_REF_FRAME = "LOCAL_FRAME";

    protected String localFrameURI;
//    private SpotReportImageOutput spotReportImageOutput;
    private SpotReportAidOutput spotReportAidOutput;
    private SpotReportFloodingOutput spotReportFloodingOutput;
    private SpotReportMedicalOutput spotReportMedicalOutput;
    private SpotReportStreetClosureOutput spotReportStreetClosureOutput;
    private SpotReportTrackingOutput spotReportTrackingOutput;

    public SpotReportDriver() {
    }
    
    @Override
    public void init() throws SensorHubException {

        super.init();

        // generate identifiers
        String deviceID = Secure.getString(config.androidContext.getContentResolver(), Secure.ANDROID_ID);
        this.uniqueID = "urn:android:spotreport:" + deviceID;
        this.xmlID = "SPOT_REPORT_" + deviceID;
        this.localFrameURI = this.uniqueID + "#" + LOCAL_REF_FRAME;

        // create outputs
//        spotReportImageOutput = new SpotReportImageOutput(this);
//        this.addOutput(spotReportImageOutput, false);
//        spotReportImageOutput.init();

        spotReportAidOutput = new SpotReportAidOutput(this);
        this.addOutput(spotReportAidOutput, false);
        spotReportAidOutput.init();

        spotReportFloodingOutput = new SpotReportFloodingOutput(this);
        this.addOutput(spotReportFloodingOutput, false);
        spotReportFloodingOutput.init();

        spotReportMedicalOutput = new SpotReportMedicalOutput(this);
        this.addOutput(spotReportMedicalOutput, false);
        spotReportMedicalOutput.init();

        spotReportStreetClosureOutput = new SpotReportStreetClosureOutput(this);
        this.addOutput(spotReportStreetClosureOutput, false);
        spotReportStreetClosureOutput.init();

        spotReportTrackingOutput = new SpotReportTrackingOutput(this);
        this.addOutput(spotReportTrackingOutput, false);
        spotReportTrackingOutput.init();
    }

    @Override
    public void start() throws SensorException {

//        spotReportImageOutput.start();
        spotReportAidOutput.start();
        spotReportFloodingOutput.start();
        spotReportMedicalOutput.start();
        spotReportStreetClosureOutput.start();
        spotReportTrackingOutput.start();
    }
    
    @Override
    public void stop() throws SensorException {

//        if (spotReportImageOutput != null) {
//
//            spotReportImageOutput.stop();
//        }

        if (spotReportAidOutput != null) {

            spotReportAidOutput.stop();
        }

        if (spotReportFloodingOutput != null) {

            spotReportFloodingOutput.stop();
        }

        if (spotReportMedicalOutput != null) {

            spotReportMedicalOutput.stop();
        }

        if (spotReportStreetClosureOutput != null) {

            spotReportStreetClosureOutput.stop();
        }

        if (spotReportTrackingOutput != null) {

            spotReportTrackingOutput.stop();
        }
    }

    @Override
    protected void updateSensorDescription() {

        synchronized (sensorDescLock) {

            super.updateSensorDescription();

            sensorDescription.setDescription("Spot Reports, providing the user the ability to submit manual observations.");

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
            String uid = "urn:android:spotreport:foi:" + config.runName.replaceAll("[ |']", "");
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
