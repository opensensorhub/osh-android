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

import java.io.ByteArrayOutputStream;

import net.opengis.swe.v20.Boolean;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataStream;
import net.opengis.swe.v20.Text;
import net.opengis.swe.v20.Time;
import net.opengis.swe.v20.Vector;

import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.videocam.VideoCamHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.GeoPosHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationProvider;

/**
 * <p>
 * Implementation of data interface for Spot Reports
 * </p>
 *
 * @author Nicolas Garay <nicolasgaray@icloud.com>
 * @since Nov 9, 2019
 */
public class SpotReportOutput extends AbstractSensorOutput<SpotReportDriver> {

    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(SpotReportOutput.class.getSimpleName());

    // Data Associated with Broadcast Receivers and Intents
    private static final String ACTION_SUBMIT_REPORT = "org.sensorhub.android.intent.SPOT_REPORT";
    private static final String ACTION_SUBMIT_REPORT_RESPONSE = "org.sensorhub.android.intent.SPOT_REPORT_RESPONSE";
    private static final String RESPONSE_CODE = "code";
    private static final int RESPONSE_SUCCESS = 1;
    private static final int RESPONSE_FAILURE = 0;
    private static final String DATA_LOC= "location";
    private static final String DATA_REPORT_NAME = "report name";
    private static final String DATA_REPORT_DESCRIPTION = "report description";
    private static final String DATA_REPORTING_ITEM = "reporting item";
    private static final String DATA_REPORTING_IMAGE = "image";
    private SpotReportReceiver broadCastReceiver = new SpotReportReceiver();

    // SWE DataBlock elements
    private static final String DATA_RECORD_TIME_LABEL = "time";
    private static final String DATA_RECORD_LOC_LABEL = "location";
    private static final String DATA_RECORD_REPORT_NAME_LABEL = "report name";
    private static final String DATA_RECORD_REPORT_DESCRIPTION_LABEL = "report description";
    private static final String DATA_RECORD_REPORTING_ITEM_LABEL = "reporting item";
    private static final String DATA_RECORD_REPORTING_CONTAINS_IMAGE_LABEL = "image";
    private static final String DATA_RECORD_REPORTING_IMAGE_LABEL = "image";

    private static final String DATA_RECORD_NAME = "Spot Report";
    private static final String DATA_RECORD_DESCRIPTION =
            "A report generated by visual observance and classification which is accompanied by a" +
                    " location, description, and optionally an image";
    private static final String DATA_RECORD_DEFINITION =
            SWEHelper.getPropertyUri("SpotReport");
    private int imgHeight;
    private int imgWidth;
    private ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
    private DataComponent spotReport;
    private DataEncoding dataEncoding;


    private Context context;
    private LocationProvider locProvider;
    private String name;


    protected SpotReportOutput(SpotReportDriver parentModule, LocationProvider locProvider) {

        super(parentModule);
        this.name = "spot_report_data";

        this.locProvider = locProvider;

        this.imgWidth = parentModule.getConfiguration().imgWidth;
        this.imgHeight = parentModule.getConfiguration().imgHeight;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    protected void init() throws SensorException {

        SWEHelper sweHelper = new SWEHelper();
        spotReport = sweHelper.newDataRecord(6);
        spotReport.setDescription(DATA_RECORD_DESCRIPTION);
        spotReport.setDefinition(DATA_RECORD_DEFINITION);
        spotReport.setName(DATA_RECORD_NAME);

        // Add the time stamp component of the data record
        Time time = sweHelper.newTimeStampIsoUTC();
        spotReport.addComponent(DATA_RECORD_TIME_LABEL, time);

        // Add the location component of the data record
        GeoPosHelper geoPosHelper = new GeoPosHelper();
        Vector vec = geoPosHelper.newLocationVectorLLA(null);
        vec.setLocalFrame(parentSensor.localFrameURI);
        spotReport.addComponent(DATA_RECORD_LOC_LABEL, vec);

        // Add the report name component of the data record
        Text name = sweHelper.newText();
        spotReport.addComponent(DATA_RECORD_REPORT_NAME_LABEL, name);

        // Add the report description component of the data record
        Text description = sweHelper.newText();
        spotReport.addComponent(DATA_RECORD_REPORT_DESCRIPTION_LABEL, description);

        // Add the reporting item component of the data record
        Text reportingItem = sweHelper.newText();
        spotReport.addComponent(DATA_RECORD_REPORTING_ITEM_LABEL, reportingItem);

        // Add image data block
        Boolean containsImage = sweHelper.newBoolean();
        spotReport.addComponent(DATA_RECORD_REPORTING_CONTAINS_IMAGE_LABEL, containsImage);

        VideoCamHelper videoCamHelper = new VideoCamHelper();
        DataStream videoStream = videoCamHelper.newVideoOutputMJPEG(getName(), this.imgWidth, this.imgHeight);
        videoStream.setDefinition(SWEHelper.getPropertyUri("Image"));
        spotReport.addComponent(DATA_RECORD_REPORTING_IMAGE_LABEL, videoStream);

        // output encoding
        dataEncoding = sweHelper.newTextEncoding(",", "\n");
    }

    private void submitReport(String reportingItem, String locationSource, String reportName, String reportDescription, byte[] imageBuffer) {

        // Create and transmit report response
        Intent submitReportIntent = new Intent(ACTION_SUBMIT_REPORT_RESPONSE);
        submitReportIntent.putExtra(RESPONSE_CODE, RESPONSE_SUCCESS);
        context.sendBroadcast(submitReportIntent);
    }

    public void start(Context context) {

        this.context = context;
        context.registerReceiver(broadCastReceiver, new IntentFilter(ACTION_SUBMIT_REPORT));
    }
    
    @Override
    public void stop() {

        context.unregisterReceiver(broadCastReceiver);
    }

    @Override
    public double getAverageSamplingPeriod() {

        return 1;
    }

    @Override
    public DataComponent getRecordDescription() {

        return spotReport;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {

        return dataEncoding;
    }
    
    @Override
    public DataBlock getLatestRecord() {

        return latestRecord;
    }
    
    @Override
    public long getLatestRecordTime() {

        return latestRecordTime;
    }

    private class SpotReportReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals(ACTION_SUBMIT_REPORT)) {

                String reportingItem = intent.getStringExtra(DATA_REPORTING_ITEM);
                String locationSource = intent.getStringExtra(DATA_LOC);
                String reportName = intent.getStringExtra(DATA_REPORT_NAME);
                String reportDescription = intent.getStringExtra(DATA_REPORT_DESCRIPTION);
                byte[] imageBuffer = intent.getByteArrayExtra(DATA_REPORTING_IMAGE);

                submitReport(reportingItem, locationSource, reportName, reportDescription, imageBuffer);
            }
        }
    }
}
