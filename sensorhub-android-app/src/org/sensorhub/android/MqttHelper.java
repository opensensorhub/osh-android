/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.android;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Android MQTT wrapper
 * </p>
 *
 * @author Nick Garay <nicolasgaray@icloud.com></nicolasgaray@icloud.com>
 * @since Jan 10, 2020
 */
class MqttHelper {

    private final String TAG = "MqttHelper";
    private MqttAndroidClient client;
    private List<String> subscribedTopics = new ArrayList<>();

    MqttHelper(Context context, String endpoint, String username, String password, List<String> topics) {

        String clientId = MqttClient.generateClientId();

        client = new MqttAndroidClient(context, endpoint, clientId);

        connect(topics, username, password);
    }

    void connect(List<String> topics, String username, String password) {

        try {

            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(3000);
            options.setKeepAliveInterval(300);
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setUserName(username);
            options.setPassword(password.toCharArray());

            client.connect(options, null, new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.d(TAG, "Connected");

                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    client.setBufferOpts(disconnectedBufferOptions);

                    for (String topic: topics) {

                        subscribe(topic);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                    Log.d(TAG, "Connection failure", exception);
                }
            });

        }catch(Exception e) {

            Log.d(TAG, "Connection failure", e);
        }
    }

    void setCallback(MqttCallbackExtended callback) {

        client.setCallback(callback);
    }

    private void subscribe(String topic) {

        try {

            client.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.d(TAG,"Subscribed");
                    subscribedTopics.add(topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                    Log.d(TAG, "Subscribe fail", exception);
                }
            },
            new IMqttMessageListener() {

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {

                    Log.d(TAG, "Topic: " + topic + "\n" + message.toString());
                }
            });

        } catch (MqttException ex) {

            Log.d(TAG, "Subscribe fail", ex);
        }
    }

    void unsubscribe(String topic) {

        try {

            client.unsubscribe(topic);

        } catch (MqttException ex) {

            Log.d(TAG, "Unsubscribe fail", ex);
        }
    }

    void disconnect() {

        try {

            client.disconnect().setActionCallback(new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.d(TAG, "Disconnected");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                    Log.d(TAG, "Failed to disconnect", exception);
                }
            });

        } catch(Exception e) {

            Log.d(TAG, "Failed to disconnect", e);
        }
    }
}
