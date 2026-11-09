/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

The Initial Developer is GeoRobotix Innovative Research Inc.. Portions created by the Initial
Developer are Copyright (C) 2026 the Initial Developer. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.android;


import android.hardware.Camera;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;

import org.sensorhub.api.command.CommandException;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorControl;
import org.vast.swe.SWEHelper;


public class AndroidCameraControl extends AbstractSensorControl<AndroidSensorsDriver>
{
    private final DataComponent commandDescription;

    // field indices in the command DataBlock
    private static final int CAMERA_FACING_IDX = 0;
    private static final int ZOOM_LEVEL_IDX = 1;


    public AndroidCameraControl(AndroidSensorsDriver parentSensor)
    {
        super("cameraControl", parentSensor);

        SWEHelper fac = new SWEHelper();
        commandDescription = fac.createRecord()
            .name("cameraControl")
            .addField("cameraFacing", fac.createCategory()
                .definition(SWEHelper.getPropertyUri("CameraSelector"))
                .label("Camera Facing Direction")
                .description("Select front or back camera")
                .addAllowedValues("FRONT", "BACK")
                .build())
            .addField("zoomLevel", fac.createCount()
                .definition(SWEHelper.getPropertyUri("ZoomLevel"))
                .label("Zoom Level")
                .description("Camera zoom level (0 = no zoom, max depends on device)")
                .build())
            .build();
    }


    @Override
    public DataComponent getCommandDescription()
    {
        return commandDescription;
    }


    @Override
    protected boolean execCommand(DataBlock command) throws CommandException
    {
        try
        {
            // handle camera facing change
            String facing = command.getStringValue(CAMERA_FACING_IDX);
            if (facing != null && !facing.isEmpty())
            {
                int newCameraId = findCameraId(facing);
                parentSensor.switchCamera(newCameraId);
            }

            // handle zoom level change
            int zoomLevel = command.getIntValue(ZOOM_LEVEL_IDX);
            if (zoomLevel >= 0)
            {
                parentSensor.setCameraZoom(zoomLevel);
            }

            return true;
        }
        catch (SensorException e)
        {
            throw new CommandException("Failed to execute camera command", e);
        }
    }


    private int findCameraId(String facing) throws CommandException
    {
        int targetFacing = "FRONT".equals(facing)
            ? Camera.CameraInfo.CAMERA_FACING_FRONT
            : Camera.CameraInfo.CAMERA_FACING_BACK;

        for (int i = 0; i < Camera.getNumberOfCameras(); i++)
        {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == targetFacing)
                return i;
        }

        throw new CommandException("No " + facing + " camera found on this device");
    }
}
