package org.sensorhub.android.mqtt;

public interface IMqttSubscriber {

    void subscribeToTopics();

    void onMessage(String message);
}
