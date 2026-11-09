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

package org.sensorhub.impl.sensor.controller;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.swe.SWEHelper;


/**
 * Single unified output for gamepad controller state:
 * buttons, triggers, joystick axes, and D-Pad.
 *
 * @author Kalyn Stricklin
 * @since 05/26/2024
 */
public class ControllerOutput extends AbstractSensorOutput<ControllerDriver>
{
    DataComponent dataStruct;
    DataEncoding dataEncoding;
    private static final String SENSOR_OUTPUT_NAME = "controller";
    private static final String SENSOR_OUTPUT_LABEL = "Gamepad Controller";
    private static final Logger logger = LoggerFactory.getLogger(ControllerOutput.class);

    protected ControllerOutput(ControllerDriver parent) {
        super(SENSOR_OUTPUT_NAME, parent);
    }

    public void doInit() {
        SWEHelper fac = new SWEHelper();

        dataStruct = fac.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .definition(SWEHelper.getPropertyUri("GamepadState"))
                .addField("time", fac.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Sampling Time")
                        .build())
                .addField("btnA", fac.createBoolean()
                        .label("A Button")
                        .definition(SWEHelper.getPropertyUri("ButtonA"))
                        .build())
                .addField("btnB", fac.createBoolean()
                        .label("B Button")
                        .definition(SWEHelper.getPropertyUri("ButtonB"))
                        .build())
                .addField("btnX", fac.createBoolean()
                        .label("X Button")
                        .definition(SWEHelper.getPropertyUri("ButtonX"))
                        .build())
                .addField("btnY", fac.createBoolean()
                        .label("Y Button")
                        .definition(SWEHelper.getPropertyUri("ButtonY"))
                        .build())
                .addField("btnL1", fac.createBoolean()
                        .label("Left Bumper")
                        .definition(SWEHelper.getPropertyUri("LeftBumper"))
                        .build())
                .addField("btnR1", fac.createBoolean()
                        .label("Right Bumper")
                        .definition(SWEHelper.getPropertyUri("RightBumper"))
                        .build())
                .addField("triggerL", fac.createQuantity()
                        .label("Left Trigger")
                        .definition(SWEHelper.getPropertyUri("LeftTrigger"))
                        .build())
                .addField("triggerR", fac.createQuantity()
                        .label("Right Trigger")
                        .definition(SWEHelper.getPropertyUri("RightTrigger"))
                        .build())
                .addField("btnL3", fac.createBoolean()
                        .label("Left Stick Click")
                        .definition(SWEHelper.getPropertyUri("LeftStickClick"))
                        .build())
                .addField("btnR3", fac.createBoolean()
                        .label("Right Stick Click")
                        .definition(SWEHelper.getPropertyUri("RightStickClick"))
                        .build())
                .addField("btnMode", fac.createBoolean()
                        .label("Mode Button")
                        .definition(SWEHelper.getPropertyUri("ModeButton"))
                        .build())
                .addField("btnStart", fac.createBoolean()
                        .label("Start Button")
                        .definition(SWEHelper.getPropertyUri("StartButton"))
                        .build())
                .addField("btnSelect", fac.createBoolean()
                        .label("Select Button")
                        .definition(SWEHelper.getPropertyUri("SelectButton"))
                        .build())
                .addField("dpad", fac.createCategory()
                        .label("D-Pad Direction")
                        .definition(SWEHelper.getPropertyUri("DPadDirection"))
                        .addAllowedValues("NONE", "UP", "UP_RIGHT", "RIGHT", "DOWN_RIGHT",
                                "DOWN", "DOWN_LEFT", "LEFT", "UP_LEFT")
                        .build())
                .addField("leftStickX", fac.createQuantity()
                        .label("Left Stick X")
                        .definition(SWEHelper.getPropertyUri("LeftStickX"))
                        .addAllowedInterval(-1.0, 1.0)
                        .build())
                .addField("leftStickY", fac.createQuantity()
                        .label("Left Stick Y")
                        .definition(SWEHelper.getPropertyUri("LeftStickY"))
                        .build())
                .addField("rightStickX", fac.createQuantity()
                        .label("Right Stick X")
                        .definition(SWEHelper.getPropertyUri("RightStickX"))
                        .addAllowedInterval(-1.0, 1.0)
                        .build())
                .addField("rightStickY", fac.createQuantity()
                        .label("Right Stick Y")
                        .definition(SWEHelper.getPropertyUri("RightStickY"))
                        .build())

                .build();

        dataEncoding = fac.newTextEncoding(",", "\n");
    }

    public void setData(boolean a, boolean b, boolean x, boolean y,
                        boolean l1, boolean r1, float triggerL, float triggerR,
                        boolean l3, boolean r3,
                        boolean mode, boolean start, boolean select,
                        String dpad,
                        float leftX, float leftY, float rightX, float rightY) {

        DataBlock dataBlock;
        if (latestRecord == null)
            dataBlock = dataStruct.createDataBlock();
        else
            dataBlock = latestRecord.renew();

        int idx = 0;
        dataBlock.setDoubleValue(idx++, System.currentTimeMillis() / 1000d);

        dataBlock.setBooleanValue(idx++, a);
        dataBlock.setBooleanValue(idx++, b);
        dataBlock.setBooleanValue(idx++, x);
        dataBlock.setBooleanValue(idx++, y);

        dataBlock.setBooleanValue(idx++, l1);
        dataBlock.setBooleanValue(idx++, r1);

        dataBlock.setDoubleValue(idx++, triggerL);
        dataBlock.setDoubleValue(idx++, triggerR);

        dataBlock.setBooleanValue(idx++, l3);
        dataBlock.setBooleanValue(idx++, r3);

        dataBlock.setBooleanValue(idx++, mode);
        dataBlock.setBooleanValue(idx++, start);
        dataBlock.setBooleanValue(idx++, select);

        dataBlock.setStringValue(idx++, dpad);

        dataBlock.setDoubleValue(idx++, leftX);
        dataBlock.setDoubleValue(idx++, leftY);
        dataBlock.setDoubleValue(idx++, rightX);
        dataBlock.setDoubleValue(idx++, rightY);

        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 1;
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
