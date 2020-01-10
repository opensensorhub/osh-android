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

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * <p>
 * Android Service wrapping MQTT
 * </p>
 * <p>
 * https://www.hivemq.com/blog/mqtt-client-library-enyclopedia-hivemq-mqtt-client/
 * </p>
 *
 * @author Nick Garay <nicolasgaray@icloud.com></nicolasgaray@icloud.com>
 * @since Jan 10, 2020
 */
public class MqttService extends Service {

    final IBinder binder = new MqttService.LocalBinder();
    private Mqtt3AsyncClient client;
    private boolean connected = false;

    public void connect(String host, int port, List<String> topics) throws NullPointerException {

        if (null == host) {

            throw new NullPointerException("Null server host URL");
        }

        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(UUID.randomUUID().toString())
                .serverHost(host)
                .serverPort(port)
                .buildAsync();

        client.connectWith()
                .simpleAuth()
                    .username("botts")
                    .password("scira04".getBytes())
                .applySimpleAuth()
                .send()
                .whenComplete((connAck, throwable) -> {

                    if (throwable != null) {

                        // Handle connection failure
                        Log.e("MqttService", "Failed to connect: " + host + ":" + port);

                    } else {

                        // Setup subscribes or start publishing
                        connected = true;
                        Log.i("MqttService", "Connected: " + host + ":" + port);

                        for (String topic: topics) {

                            try {

                                this.subscribe(topic);
                            }
                            catch(Exception e) {

                                // Handle connection failure
                                Log.d("MqttService", "Failed to subscribe on connect: " + topic);
                            }
                        }
                    }
                });
    }

    public void subscribe(String topic) throws NullPointerException, IOException {

        if (null == topic) {

            throw new NullPointerException("Null topic for subscribe");

        } else if (!connected) {

            throw new IOException("No connection for subscription");
        }

        client.subscribeWith()
                .topicFilter(topic)
                .callback(publish -> {

                    // Process the received message
                    Log.d("MqttService", "What has been received?");

                })
                .send()
                .whenComplete((subAck, throwable) -> {

                    if (throwable != null) {

                        Log.e("MqttService", "Failed to subscribe to topic: " + topic);

                    } else {

                        // Handle successful subscription, e.g. logging or incrementing a metric
                        Log.i("MqttService", "Subscribe to topic: " + topic);
                    }
                });
    }

    public void unsubscribe(String topic) throws NullPointerException, IOException {

        if (null == topic) {

            throw new NullPointerException("Null topic for unsubscribe");

        } else if (!connected) {

            throw new IOException("No connection, unsubscribe cannot proceed");
        }

        client.unsubscribeWith()
                .topicFilter(topic)
                .send();
    }

    @Override
    public void onCreate() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        client.disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {

        return binder;
    }

    public class LocalBinder extends Binder {

        MqttService getService() {

            return MqttService.this;
        }
    }
}
