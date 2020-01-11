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
