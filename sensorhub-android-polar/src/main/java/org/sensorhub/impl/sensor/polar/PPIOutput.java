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

package org.sensorhub.impl.sensor.polar;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;

import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.swe.SWEHelper;

/**
 * Output for Pulse-to-Pulse Interval (PPI) data from Polar devices.
 * Provides beat-to-beat intervals for heart rate variability (HRV) analysis.
 *
 * @author Kalyn Stricklin
 * @since 2025
 */
public class PPIOutput extends AbstractSensorOutput<Polar> {

    private static final String SENSOR_OUTPUT_NAME = "ppi";
    private static final String SENSOR_OUTPUT_LABEL = "PPI / RR Interval Output";
    private static final Logger logger = LoggerFactory.getLogger(PPIOutput.class);

    DataRecord dataStruct;
    DataEncoding dataEncoding;

    protected PPIOutput(Polar parent) {
        super(SENSOR_OUTPUT_NAME, parent);
    }

    public void doInit() {
        SWEHelper fac = new SWEHelper();

        dataStruct = fac.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .definition(SWEHelper.getPropertyUri("PulseToPulseInterval"))
                .description("Beat-to-beat pulse interval data for HRV analysis")
                .addField("samplingTime", fac.createTime().asSamplingTimeIsoUTC())
                .addField("ppiInterval", fac.createQuantity()
                        .label("PPI Interval")
                        .definition(SWEHelper.getPropertyUri("PulseToPulseInterval"))
                        .description("Pulse-to-pulse interval (RR interval)")
                        .uom("ms")
                        .build())
                .addField("heartRate", fac.createQuantity()
                        .label("Heart Rate")
                        .definition(SWEHelper.getPropertyUri("HeartRate"))
                        .description("Heart rate derived from PPI")
                        .uom("1/min")
                        .build())
                .addField("skinContactSupported", fac.createBoolean()
                        .label("Skin Contact Supported")
                        .definition(SWEHelper.getPropertyUri("SkinContactSupported"))
                        .description("Whether the device supports skin contact detection")
                        .build())
                .addField("skinContactStatus", fac.createBoolean()
                        .label("Skin Contact Status")
                        .definition(SWEHelper.getPropertyUri("SkinContactStatus"))
                        .description("Whether the device has skin contact")
                        .build())
                .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 1.0;
    }

    @Override
    public DataComponent getRecordDescription() {
        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    public void setData(int ppiMs, int hr, boolean skinContactSupported, boolean skinContactStatus) {
        DataBlock dataBlock = dataStruct.createDataBlock();

        dataBlock.setDoubleValue(0, System.currentTimeMillis() / 1000d);
        dataBlock.setIntValue(1, ppiMs);
        dataBlock.setIntValue(2, hr);
        dataBlock.setBooleanValue(3, skinContactSupported);
        dataBlock.setBooleanValue(4, skinContactStatus);

        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
    }
}
