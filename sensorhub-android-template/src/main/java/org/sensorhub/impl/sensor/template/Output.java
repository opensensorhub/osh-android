/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 The Initial Developer is Botts Innovative Research Inc. Portions created by the Initial
 Developer are Copyright (C) 2025 the Initial Developer. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.template;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.GeoPosHelper;


/**
 * Output for template WiFi access point scan results
 *
 * @author Kalyn Stricklin
 * @since April 6, 2026
 */
public class Output extends AbstractSensorOutput<Sensor>
{
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    private static final String SENSOR_OUTPUT_NAME = "wifiScan";
    private static final String SENSOR_OUTPUT_LABEL = "WiFi Access Point Scan";
    private static final Logger logger = LoggerFactory.getLogger(Output.class);

    protected Output(Sensor parent) {
        super(SENSOR_OUTPUT_NAME, parent);
    }

    public void doInit() {
        GeoPosHelper fac = new GeoPosHelper();

        dataStruct = fac.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .definition(SWEHelper.getPropertyUri("WifiScanResult"))
                .addField("time", fac.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Sampling Time")
                        .build())
                .addField("text", fac.createText()
                        .label("Text")
                        .definition(SWEHelper.getPropertyUri("Text"))
                        .description("Example text field")
                        .build())
                .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }


    public void setData(String text) {

        DataBlock dataBlock;
        if (latestRecord == null)
            dataBlock = dataStruct.createDataBlock();
        else
            dataBlock = latestRecord.renew();

        int idx = 0;
        dataBlock.setDoubleValue(idx++, System.currentTimeMillis() / 1000d);
        dataBlock.setStringValue(idx++, text);

        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 10.0;
    }

    @Override
    public DataComponent getRecordDescription() {
        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }
}
