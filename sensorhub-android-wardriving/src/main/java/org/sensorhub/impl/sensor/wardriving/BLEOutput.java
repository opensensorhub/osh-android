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

package org.sensorhub.impl.sensor.wardriving;

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
 * Output for BLE device scan results.
 *
 * @author Kalyn Stricklin
 * @since April 6, 2026
 */
public class BLEOutput extends AbstractSensorOutput<Wardriving>
{
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    private static final String SENSOR_OUTPUT_NAME = "bleScan";
    private static final String SENSOR_OUTPUT_LABEL = "BLE Device Scan";
    private static final Logger logger = LoggerFactory.getLogger(BLEOutput.class);

    protected BLEOutput(Wardriving parent) {
        super(SENSOR_OUTPUT_NAME, parent);
    }

    public void doInit() {
        GeoPosHelper fac = new GeoPosHelper();

        dataStruct = fac.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .definition(SWEHelper.getPropertyUri("BLEScanResult"))
                .addField("time", fac.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Sampling Time")
                        .build())
                .addField("deviceAddress", fac.createText()
                        .label("Device Address")
                        .definition(SWEHelper.getPropertyUri("NetworkAddress"))
                        .description("MAC address of the BLE device")
                        .build())
                .addField("deviceName", fac.createText()
                        .label("Device Name")
                        .definition(SWEHelper.getPropertyUri("DeviceName"))
                        .description("Advertised name of the BLE device")
                        .build())
                .addField("rssi", fac.createQuantity()
                        .label("Signal Strength")
                        .definition(SWEHelper.getPropertyUri("SignalStrength"))
                        .description("Received signal strength indicator")
                        .build())
                .addField("location", fac.newLocationVectorLLA(
                        SWEHelper.getPropertyUri("SensorLocation")))
                .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    public void setData(String deviceAddress, String deviceName, int rssi, double lat, double lon, double alt) {
        DataBlock dataBlock;
        if (latestRecord == null)
            dataBlock = dataStruct.createDataBlock();
        else
            dataBlock = latestRecord.renew();

        int idx = 0;
        dataBlock.setDoubleValue(idx++, System.currentTimeMillis() / 1000d);
        dataBlock.setStringValue(idx++, deviceAddress != null ? deviceAddress : "");
        dataBlock.setStringValue(idx++, deviceName != null ? deviceName : "");
        dataBlock.setIntValue(idx++, rssi);
        dataBlock.setDoubleValue(idx++, lat);
        dataBlock.setDoubleValue(idx++, lon);
        dataBlock.setDoubleValue(idx++, alt);

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
