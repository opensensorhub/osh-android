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
 * Output for raw ECG waveform data from Polar H10.
 * Streams electrocardiogram voltage samples at 130 Hz.
 *
 * @author Kalyn Stricklin
 * @since 2025
 */
public class ECGOutput extends AbstractSensorOutput<Polar> {

    private static final String SENSOR_OUTPUT_NAME = "ecg";
    private static final String SENSOR_OUTPUT_LABEL = "ECG Output";
    private static final Logger logger = LoggerFactory.getLogger(ECGOutput.class);

    DataRecord dataStruct;
    DataEncoding dataEncoding;

    protected ECGOutput(Polar parent) {
        super(SENSOR_OUTPUT_NAME, parent);
    }

    public void doInit() {
        SWEHelper fac = new SWEHelper();

        dataStruct = fac.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .definition(SWEHelper.getPropertyUri("ECGWaveform"))
                .description("Raw electrocardiogram waveform from Polar H10")
                .addField("samplingTime", fac.createTime().asSamplingTimeIsoUTC())
                .addField("voltage", fac.createQuantity()
                        .label("ECG Voltage")
                        .definition(SWEHelper.getPropertyUri("Voltage"))
                        .description("ECG voltage sample")
                        .uom("uV")
                        .build())
                .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 1.0 / 130.0;
    }

    @Override
    public DataComponent getRecordDescription() {
        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    public void setData(int voltageUv) {
        DataBlock dataBlock = dataStruct.createDataBlock();

        dataBlock.setDoubleValue(0, System.currentTimeMillis() / 1000d);
        dataBlock.setIntValue(1, voltageUv);

        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
    }
}
