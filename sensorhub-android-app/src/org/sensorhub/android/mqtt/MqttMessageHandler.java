package org.sensorhub.android.mqtt;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttMessageHandler implements IMqttActionListener, IMqttMessageListener {

    private static final String TAG = "MqttMessageHandler";

    private IMqttSubscriber subscriber;

    public MqttMessageHandler(IMqttSubscriber subscriber) {

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

        subscriber.onMessage(message.toString());
    }
}
