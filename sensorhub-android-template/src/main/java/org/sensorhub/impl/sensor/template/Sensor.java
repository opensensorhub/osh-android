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

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import net.opengis.sensorml.v20.PhysicalComponent;

import org.sensorhub.android.SensorHubService;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.android.SensorMLBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 *
 * @author Kalyn Stricklin
 * @since April 6, 2026
 */
public class Sensor extends AbstractSensorModule<TemplateConfig> {
    static final String UID_PREFIX = "urn:osh:sensor:template:driver:";

    private final ArrayList<PhysicalComponent> smlComponents;
    private final SensorMLBuilder smlBuilder;
    static final Logger logger = LoggerFactory.getLogger(Sensor.class.getSimpleName());
    private Context context;
    Output output;
    private HandlerThread eventThread;
    private Handler eventHandler;
    Thread processingThread;
    volatile boolean doProcessing = true;


    public Sensor() {
        this.smlComponents = new ArrayList<PhysicalComponent>();
        this.smlBuilder = new SensorMLBuilder();
    }

    @Override
    public void doInit() {
        logger.info("Initializing Sensor");
        this.xmlID = "TEMPLATE_DRIVER_" + Build.SERIAL;
        this.uniqueID = UID_PREFIX + config.getUidWithExt();

        context = SensorHubService.getContext();

        output = new Output(this);
        output.doInit();
        addOutput(output, false);

    }

    @Override
    public void doStart() throws SensorException {
        eventThread = new HandlerThread("TemplateThread");
        eventThread.start();
        eventHandler = new Handler(eventThread.getLooper());

        startProcessing();
    }

    public void startProcessing() {
        doProcessing = true;

        processingThread = new Thread(() -> {
            while (doProcessing) {
                output.setData( "Sample Data");

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        processingThread.start();
    }

    public void stopProcessing() {
        doProcessing = false;
    }

    @Override
    public void doStop() {

        if (eventThread != null) {
            eventThread.quitSafely();
            eventThread = null;
        }

        eventHandler = null;
        logger.info("Sensor stopped");
    }

    @Override
    public boolean isConnected() {
        return processingThread != null && processingThread.isAlive();
    }
}
