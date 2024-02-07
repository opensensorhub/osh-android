package org.sensorhub.android;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class AppStatusActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_status);

        Intent intent = getIntent();
        Context appContext = getApplicationContext();

        String sosStatus = intent.getStringExtra("sosService");
        String httpStatus = intent.getStringExtra("httpStatus");
        String sensorStatus = intent.getStringExtra("androidSensorStatus");
        String sensorStorageStatus = intent.getStringExtra("sensorStorageStatus");

        TextView sosStatusView = (TextView) findViewById(R.id.sos_service_state);
        TextView httpStatusView = (TextView) findViewById(R.id.http_service_state);
        TextView sensorStatusView = (TextView) findViewById(R.id.sensor_service_state);
        TextView storageStatusView = (TextView) findViewById(R.id.storage_service_state);

        sosStatusView.setText(sosStatus);
        httpStatusView.setText(httpStatus);
        sensorStatusView.setText(sensorStatus);
        storageStatusView.setText(sensorStorageStatus);
    }
}