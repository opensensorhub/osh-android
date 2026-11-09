/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.impl.sensor.swe.ProxySensor.mqtt;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttMessageHandler extends Handler implements IMqttActionListener, IMqttMessageListener {

    private static final String TAG = "MqttMessageHandler";

    private IMqttSubscriber subscriber;

    public MqttMessageHandler(IMqttSubscriber subscriber) {

        super();

        this.subscriber = subscriber;
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {

        Log.d(TAG,"Subscribed - Topic: " + asyncActionToken.getTopics().toString());
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

        Log.d(TAG, "Subscribe fail", exception);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {

        Log.d(TAG, "Topic: " + topic);
        Bundle bundle = new Bundle();
        bundle.putString("mqtt-message", message.toString());
        Message msg = new Message();
        msg.setData(bundle);
        this.sendMessage(msg);
    }

    @Override
    public void handleMessage(Message message) {

        String mqttMessage = message.getData().getString("mqtt-message");
        subscriber.onMessage(mqttMessage);
    }
}
