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

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import net.opengis.sensorml.v20.PhysicalComponent;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.android.SensorMLBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;


/**
 * Android gamepad controller driver. Captures button presses, trigger axes, joystick axes and D-Pad input from any connected gamepad
 *
 * @author Kalyn Stricklin
 * @since 05/26/2024
 */
public class ControllerDriver extends AbstractSensorModule<ControllerConfig> implements InputManager.InputDeviceListener {
    private final ArrayList<PhysicalComponent> smlComponents;
    private final SensorMLBuilder smlBuilder;
    static final String UID_PREFIX = "urn:osh:sensor:controller:";
    static final Logger logger = LoggerFactory.getLogger(ControllerDriver.class.getSimpleName());
    private Context context;
    ControllerOutput output;
    private HandlerThread eventThread;
    private Handler eventHandler;
    private InputManager inputManager;
    private int controllerDeviceId = -1;

    private boolean btnA, btnB, btnX, btnY,
            btnL1, btnR1, btnL3, btnR3,
            btnMode, btnStart, btnSelect;
    private float triggerL, triggerR,
            leftX, leftY, rightX, rightY;
    private String dpad = "NONE";
    public ControllerDriver() {
        this.smlComponents = new ArrayList<PhysicalComponent>();
        this.smlBuilder = new SensorMLBuilder();
    }

    @Override
    public void doInit() {
        logger.info("Initializing Controller Sensor");
        this.xmlID = "CONTROLLER_" + Build.SERIAL;
        this.uniqueID = UID_PREFIX + config.getUidWithExt();

        context = SensorHubService.getContext();
        inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);

        findController();

        output = new ControllerOutput(this);
        output.doInit();
        addOutput(output, false);
    }

    @Override
    public void doStart() throws SensorException {
        eventThread = new HandlerThread("ControllerThread");
        eventThread.start();
        eventHandler = new Handler(eventThread.getLooper());

        inputManager.registerInputDeviceListener(this, eventHandler);

        logger.info("Controller sensor started, device ID: {}", controllerDeviceId);
    }

    private void findController() {
        int[] deviceIds = inputManager.getInputDeviceIds();
        for (int id : deviceIds) {
            InputDevice device = inputManager.getInputDevice(id);
            if (device != null && isGamepad(device)) {
                controllerDeviceId = id;
                logger.info("Found controller: {} (id={})", device.getName(), id);
                return;
            }
        }
        logger.warn("No gamepad controller connected");
    }

    private boolean isGamepad(InputDevice device) {
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK);
    }

    public boolean onKeyEvent(KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == 0
                && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0)
            return false;

        if (event.getRepeatCount() > 0)
            return true;

        boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;
        int keyCode = event.getKeyCode();

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:      btnA = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_B:      btnB = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_X:      btnX = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_Y:      btnY = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_L1:     btnL1 = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_R1:     btnR1 = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: btnL3 = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: btnR3 = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_MODE:   btnMode = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_START:  btnStart = pressed; break;
            case KeyEvent.KEYCODE_BUTTON_SELECT: btnSelect = pressed; break;
            default: return false;
        }

        logger.info("Button: {} {}", keyCodeName(keyCode), pressed ? "PRESSED" : "RELEASED");
        publishState();
        return true;
    }

    public boolean onMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0)
            return false;
        if (event.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        InputDevice device = event.getDevice();

        leftX = getCenteredAxis(event, device, MotionEvent.AXIS_X);
        leftY = getCenteredAxis(event, device, MotionEvent.AXIS_Y);
        rightX = getCenteredAxis(event, device, MotionEvent.AXIS_Z);
        rightY = getCenteredAxis(event, device, MotionEvent.AXIS_RZ);

        triggerL = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
        triggerR = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);

        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        dpad = hatToDpad(hatX, hatY);

        publishState();
        return true;
    }

    private void publishState() {
        output.setData(
                btnA, btnB, btnX, btnY,
                btnL1, btnR1, triggerL, triggerR,
                btnL3, btnR3,
                btnMode, btnStart, btnSelect,
                dpad,
                leftX, leftY, rightX, rightY
        );
    }

    private static float getCenteredAxis(MotionEvent event, InputDevice device, int axis) {
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range != null) {
            float flat = range.getFlat();
            float value = event.getAxisValue(axis);
            if (Math.abs(value) > flat)
                return value;
        }
        return 0;
    }

    private static String hatToDpad(float hatX, float hatY) {
        boolean left  = Float.compare(hatX, -1.0f) == 0;
        boolean right = Float.compare(hatX, 1.0f) == 0;
        boolean up    = Float.compare(hatY, -1.0f) == 0;
        boolean down  = Float.compare(hatY, 1.0f) == 0;

        if (up && left)    return "UP_LEFT";
        if (up && right)   return "UP_RIGHT";
        if (down && left)  return "DOWN_LEFT";
        if (down && right) return "DOWN_RIGHT";
        if (up)            return "UP";
        if (down)          return "DOWN";
        if (left)          return "LEFT";
        if (right)         return "RIGHT";
        return "NONE";
    }

    private static String keyCodeName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "MODE";
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            default: return "KEY_" + keyCode;
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice device = inputManager.getInputDevice(deviceId);
        if (device != null && isGamepad(device)) {
            controllerDeviceId = deviceId;
            logger.info("Controller connected: {} (id={})", device.getName(), deviceId);
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        if (deviceId == controllerDeviceId) {
            logger.info("Controller disconnected (id={})", deviceId);
            controllerDeviceId = -1;
        }
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        logger.debug("Input device changed: {}", deviceId);
    }

    @Override
    public void doStop() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }

        if (eventThread != null) {
            eventThread.quitSafely();
            eventThread = null;
        }

        eventHandler = null;
        logger.info("Controller sensor stopped");
    }

    @Override
    public boolean isConnected() {
        return controllerDeviceId >= 0;
    }
}
