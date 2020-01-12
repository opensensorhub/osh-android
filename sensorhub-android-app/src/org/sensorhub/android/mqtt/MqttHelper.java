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

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * <p>
 * Android MQTT wrapper
 * </p>
 *
 * @author Nick Garay <nicolasgaray@icloud.com></nicolasgaray@icloud.com>
 * @since Jan 10, 2020
 */
public class MqttHelper {

    private final String TAG = "MqttHelper";
    private MqttAndroidClient client;

    public MqttHelper() {
    }

    public IMqttToken connect(Context context, String username, String password, String url) {

        IMqttToken connectionToken = null;

        String clientId = MqttClient.generateClientId();

        client = new MqttAndroidClient(context, url, clientId);

        try {

            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(3000);
            options.setKeepAliveInterval(300);
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setUserName(username);
            options.setPassword(password.toCharArray());

            connectionToken = client.connect(options);

        }catch(Exception e) {

            Log.d(TAG, "Connection failure", e);
        }

        return connectionToken;
    }

    public void setDisconnectedBufferOptions(DisconnectedBufferOptions options) {

        client.setBufferOpts(options);
    }

    public IMqttToken subscribe(String topic, MqttMessageHandler listener) {

        IMqttToken subscribeToken = null;

        try {

            subscribeToken = client.subscribe(topic, 0, null, listener, listener);

        } catch (MqttException ex) {

            Log.d(TAG, "Subscribe fail", ex);
        }

        return subscribeToken;
    }

    public void unsubscribe(String topic) {

        try {

            client.unsubscribe(topic);

        } catch (MqttException ex) {

            Log.d(TAG, "Unsubscribe fail", ex);
        }
    }

    public void disconnect() {

        try {

            client.disconnect().setActionCallback(new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.d(TAG, "Disconnected");
                    client.unregisterResources();
                    client.close();
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
