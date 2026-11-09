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
import org.vast.swe.helper.GeoPosHelper;

/**
 * Output for 3-axis accelerometer data from Polar H10.
 * Streams motion data at configurable rates (25/50/100/200 Hz).
 *
 * @author Kalyn Stricklin
 * @since 2025
 */
public class AccelerometerOutput extends AbstractSensorOutput<Polar> {

    private static final String SENSOR_OUTPUT_NAME = "accelerometer";
    private static final String SENSOR_OUTPUT_LABEL = "Accelerometer Output";
    private static final Logger logger = LoggerFactory.getLogger(AccelerometerOutput.class);

    private static final double MG_TO_MS2 = 9.80665 / 1000.0;

    DataRecord dataStruct;
    DataEncoding dataEncoding;

    protected AccelerometerOutput(Polar parent) {
        super(SENSOR_OUTPUT_NAME, parent);
    }

    public void doInit() {
        GeoPosHelper fac = new GeoPosHelper();

        dataStruct = fac.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .description("3-axis accelerometer data from Polar H10")
                .addField("samplingTime", fac.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Sample Time"))
                .addField("acceleration", fac.createAccelerationVector("m/s2"))
                .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 1.0 / 50.0;
    }

    @Override
    public DataComponent getRecordDescription() {
        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    /**
     * @param xMg X-axis acceleration in milligravity (mG)
     * @param yMg Y-axis acceleration in milligravity (mG)
     * @param zMg Z-axis acceleration in milligravity (mG)
     */
    public void setData(int xMg, int yMg, int zMg) {
        DataBlock dataBlock = dataStruct.createDataBlock();

        dataBlock.setDoubleValue(0, System.currentTimeMillis() / 1000d);
        dataBlock.setDoubleValue(1, xMg * MG_TO_MS2);
        dataBlock.setDoubleValue(2, yMg * MG_TO_MS2);
        dataBlock.setDoubleValue(3, zMg * MG_TO_MS2);

        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
    }
}
