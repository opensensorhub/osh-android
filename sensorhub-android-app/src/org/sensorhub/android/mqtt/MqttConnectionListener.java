/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.android.mqtt;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

public class MqttConnectionListener implements IMqttActionListener {

    private static final String TAG = "MqttConnectionListener";

    private MqttHelper mqttHelper;
    private IMqttSubscriber subscriber;

    public MqttConnectionListener(MqttHelper helper, IMqttSubscriber subscriber) {

        mqttHelper = helper;

        this.subscriber = subscriber;
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {

        Log.d(TAG, "Connected");

        DisconnectedBufferOptions options = new DisconnectedBufferOptions();
        options.setBufferEnabled(true);
        options.setBufferSize(100);
        options.setPersistBuffer(false);
        options.setDeleteOldestMessages(false);
        mqttHelper.setDsiconnectedBufferOptions(options);

        subscriber.subscribeToTopics();
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

        Log.d(TAG, "Connection failure", exception);
    }
}
