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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.sensorhub.android.mqtt.IMqttSubscriber;
import org.sensorhub.android.mqtt.MqttConnectionListener;
import org.sensorhub.android.mqtt.MqttHelper;
import org.sensorhub.android.mqtt.MqttMessageHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class SpotReportActivity extends Activity implements IMqttSubscriber {

    private static final String TAG = "SpotReportActivity";

    // Data Associated with Broadcast Receivers and Intents
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private static final int SUBMIT_REPORT_FAILURE = 0;
    private static final int SUBMIT_REPORT_SUCCESS = 1;

    private static final String ACTION_SUBMIT_AID_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_AID";
    private static final String ACTION_SUBMIT_FLOODING_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_FLOODING";
    private static final String ACTION_SUBMIT_IMAGE_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_IMAGE";
    private static final String ACTION_SUBMIT_MEDICAL_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_MEDICAL";
    private static final String ACTION_SUBMIT_STREET_CLOSURE_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_STREET_CLOSURE";
    private static final String ACTION_SUBMIT_TRACK_REPORT = "org.sensorhub.android.intent.SPOT_REPORT_TRACK";

    private ImageView imageView;
    private Bitmap imageBitmap = null;
    private Uri imageUri;
    private String lastAction = null;

    private MqttHelper mqttHelper;

    private static final String MQTT_USER = "botts";
    private static final String MQTT_PASSWORD = "scira04";
    private static final String MQTT_URL = "tcp://ogc-hub.compusult.com:1883";

    private static final String FLOODING_TOPIC_ID = "Datastreams(192)/Observations";
    private static final String STREET_CLOSURE_TOPIC_ID = "Datastreams(232)/Observations";
    private static final String AID_TOPIC_ID = "Datastreams(235)/Observations";
    private static final String TRACK_TOPIC_ID = "Datastreams(236)/Observations";
    private static final String MED_TOPIC_ID = "Datastreams(237)/Observations";

    private static final String[] topics = {
            "Observations",
            FLOODING_TOPIC_ID,
            STREET_CLOSURE_TOPIC_ID,
            AID_TOPIC_ID,
            TRACK_TOPIC_ID,
            MED_TOPIC_ID
        };

    private final MqttMessageHandler mqttMessageListener = new MqttMessageHandler(this);

    private SubmitRequestResultReceiver submitRequestResultReceiver;

    private ReportTypeListener reportTypeListener = new ReportTypeListener(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spot_report);
//        setContentView(R.layout.spot_report_web_view);
//        WebView webView = findViewById(R.id.webView);
//        webView.getSettings().setJavaScriptEnabled(true);
//        webView.loadUrl("http://scira.georobotix.io:8181/");

        submitRequestResultReceiver = new SubmitRequestResultReceiver(this, new Handler());

        Spinner spinner = findViewById(R.id.reportType);

        spinner.setOnItemSelectedListener(reportTypeListener);

        mqttHelper = new MqttHelper();

        IMqttToken connection = mqttHelper.connect(getApplicationContext(), MQTT_USER, MQTT_PASSWORD, MQTT_URL);
        connection.setActionCallback(new MqttConnectionListener(mqttHelper, this));
    }

    @Override
    public void subscribeToTopics() {

        Log.d(TAG, "subscribeToTopics");

        for(String topic : topics) {

            mqttHelper.subscribe(topic, mqttMessageListener);
        }
    }

    @Override
    public void onMessage(String message) {

        Log.d(TAG, "onMessage");
    }

    @Override
    protected void onDestroy() {

        for (String topic : topics) {

            try {

                mqttHelper.unsubscribe(topic);

            } catch (Exception e) {

                Log.e(TAG, "Failed on unsubscribe of topic:" + topic);
            }
        }

        mqttHelper.disconnect();

        super.onDestroy();
    }

    private boolean createImageFile() throws IOException {

        String timeStamp =
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());

        String imageFileName = "SpotReport_IMG_" + timeStamp + ".jpg";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File file = new File(storageDir + "/" + imageFileName);

        boolean result = file.createNewFile();

        if (result) {

            imageUri = FileProvider.getUriForFile(this,"org.sensorhub.android.provider", file);

        }

        return result;
    }

    private void dispatchTakePictureIntent() {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (null != takePictureIntent.resolveActivity(getPackageManager())) {

            try {

                if (createImageFile()) {

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

                }

            } catch (IOException e) {

                // Error occurred while creating the File
                Log.e(TAG, e.toString());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE) {

            if (resultCode == Activity.RESULT_OK) {

                try {

                    imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);

                    imageView.setImageBitmap(imageBitmap);

                } catch (Exception e) {

                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        if (null != imageView) {

            imageView.setImageBitmap(imageBitmap);
        }
    }

    /**
     *
     */
    private class LocationTypeListener implements AdapterView.OnItemSelectedListener {

        private int latId;
        private int lonId;

        Activity parent;

        LocationTypeListener(Activity parent, int latId, int lonId) {

            this.parent = parent;
            this.latId = latId;
            this.lonId = lonId;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
            Location location = null;

            String selectedItem = parent.getItemAtPosition(position).toString();

            if (PackageManager.PERMISSION_GRANTED ==
                    ContextCompat.checkSelfPermission(parent.getContext(), Manifest.permission.ACCESS_FINE_LOCATION)) {

                LocationManager locationManager = (LocationManager) this.parent.getSystemService(LOCATION_SERVICE);

                if (selectedItem.equalsIgnoreCase("GPS")) {

                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                } else if (selectedItem.equalsIgnoreCase("Network")) {

                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (null != location) {

                    if(latId != 0) {

                        ((TextView) findViewById(latId)).setText(
                                String.format(Locale.ENGLISH, "%f", location.getLatitude()));
                    }

                    if(lonId != 0) {

                        ((TextView) findViewById(lonId)).setText(
                                String.format(Locale.ENGLISH, "%f", location.getLongitude()));
                    }
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent)
        {

        }
    }

    /**
     *
     */
    private class ReportTypeListener implements AdapterView.OnItemSelectedListener {

        private boolean layoutSwitched = false;

        SpotReportActivity parent;

        ReportTypeListener(SpotReportActivity parent) {

            this.parent = parent;
        }

        private void initializeAidLayout() {

            setContentView(R.layout.spot_report_aid);
            EditText text = findViewById(R.id.aidRadiusNum);
            text.setEnabled(false);
            text.setText(String.format(Locale.ENGLISH, "%d", 0));
            findViewById(R.id.aidLatitude).setEnabled(false);
            findViewById(R.id.aidLongitude).setEnabled(false);
            ((SeekBar)findViewById(R.id.aidRadiusValue)).setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            ((EditText)findViewById(R.id.aidRadiusNum)).setText(
                                    String.format(Locale.ENGLISH, "%d", progress));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    }
            );

            ((Spinner) findViewById(R.id.locationSource)).setOnItemSelectedListener(
                    new LocationTypeListener(this.parent, R.id.aidLatitude, R.id.aidLongitude));

            Button submitReportButton = findViewById(R.id.submitReport);
            submitReportButton.setOnClickListener((View view)-> {

                String latString = ((EditText)findViewById(R.id.aidLatitude)).getText().toString();
                String lonString = ((EditText)findViewById(R.id.aidLongitude)).getText().toString();
                int radius = Integer.parseInt(((EditText)findViewById(R.id.aidRadiusNum)).getText().toString());
                String aidType = ((Spinner)findViewById(R.id.aidType)).getSelectedItem().toString();
                int aidPosition = ((Spinner)findViewById(R.id.aidType)).getSelectedItemPosition();
                String numPersons = ((EditText)findViewById(R.id.aidNum)).getText().toString();
                String urgency = ((Spinner)findViewById(R.id.aidUrgency)).getSelectedItem().toString();
                int urgencyPosition = ((Spinner)findViewById(R.id.aidType)).getSelectedItemPosition();
                String description = ((EditText)findViewById(R.id.aidDescription)).getText().toString();
                String reporter = ((EditText)findViewById(R.id.aidReporter)).getText().toString();

                ArrayList<String> errors = new ArrayList<>();

                if (radius <= 0) {

                    errors.add("Radius - Specify a radius > 0\n\n");
                }

                if(0 == numPersons.length()) {

                    errors.add("Num Persons - Specify the number of persons\n\n");
                }

                if (0 == aidPosition) {

                    errors.add("Aid Type - Specify aid needed\n\n");
                }

                if (0 == urgencyPosition) {

                    errors.add("Urgency - Specify urgency level\n\n");
                }

                if(0 == description.length()) {

                    errors.add("Description - Describe the need\n\n");
                }

                if(0 == reporter.length()) {

                    errors.add("Reporter - Enter your name or id\n\n");
                }

                if(errors.isEmpty()) {

                    // Create and transmit report
                    parent.lastAction = ACTION_SUBMIT_AID_REPORT;
                    Intent submitReportIntent = new Intent(ACTION_SUBMIT_AID_REPORT);
                    submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    submitReportIntent.putExtra("lat", latString);
                    submitReportIntent.putExtra("lon", lonString);
                    submitReportIntent.putExtra("radius", radius);
                    submitReportIntent.putExtra("aidType", aidType);
                    submitReportIntent.putExtra("numPersons", numPersons);
                    submitReportIntent.putExtra("urgency", urgency);
                    submitReportIntent.putExtra("description", description);
                    submitReportIntent.putExtra("reporter", reporter);
                    submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
                    sendBroadcast(submitReportIntent);
                }
                else {

                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("The following fields have errors:\n\n");

                    for(String error: errors) {

                        messageBuilder.append(error);
                    }

                    // Pop up error dialog, noting fields need to be corrected
                    new AlertDialog.Builder(this.parent)
                            .setTitle("Incomplete Form")
                            .setMessage(messageBuilder.toString())
                            .setCancelable(true)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }

        private void initializeMedicalLayout() {

            setContentView(R.layout.spot_report_medical);
            findViewById(R.id.medLatitude).setEnabled(false);
            findViewById(R.id.medLongitude).setEnabled(false);

            EditText text = findViewById(R.id.medRadiusNum);
            text.setEnabled(false);
            text.setText(String.format(Locale.ENGLISH, "%d", 0));
            ((SeekBar)findViewById(R.id.medRadiusValue)).setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            ((EditText)findViewById(R.id.medRadiusNum)).setText(
                                    String.format(Locale.ENGLISH, "%d", progress));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    }
            );

            ((Spinner) findViewById(R.id.locationSource)).setOnItemSelectedListener(
                    new LocationTypeListener(this.parent, R.id.medLatitude, R.id.medLongitude));

            Button submitReportButton = findViewById(R.id.submitReport);
            submitReportButton.setOnClickListener((View view)-> {

                String latString = ((EditText)findViewById(R.id.medLatitude)).getText().toString();
                String lonString = ((EditText)findViewById(R.id.medLongitude)).getText().toString();
                int radius = Integer.parseInt(((EditText)findViewById(R.id.medRadiusNum)).getText().toString());
                String description = ((EditText)findViewById(R.id.medSign)).getText().toString();
                String measure = ((EditText)findViewById(R.id.medValue)).getText().toString();
                boolean emergency = ((CheckBox)findViewById(R.id.isEmergency)).isChecked();

                ArrayList<String> errors = new ArrayList<>();

                if (radius <= 0) {

                    errors.add("Radius - Specify a radius > 0\n\n");
                }

                if(0 == description.length()) {

                    errors.add("Description - Describe medical condition\n\n");
                }

                if(0 == measure.length()) {

                    errors.add("Measure - Enter measurement (heart rate, bp, etc).\n\n");
                }

                if(errors.isEmpty()) {

                    // Create and transmit report
                    parent.lastAction = ACTION_SUBMIT_MEDICAL_REPORT;
                    Intent submitReportIntent = new Intent(ACTION_SUBMIT_MEDICAL_REPORT);
                    submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    submitReportIntent.putExtra("lat", latString);
                    submitReportIntent.putExtra("lon", lonString);
                    submitReportIntent.putExtra("radius", radius);
                    submitReportIntent.putExtra("description", description);
                    submitReportIntent.putExtra("measure", measure);
                    submitReportIntent.putExtra("emergency", emergency);
                    submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
                    sendBroadcast(submitReportIntent);
                }
                else {

                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("The following fields have errors:\n\n");

                    for(String error: errors) {

                        messageBuilder.append(error);
                    }

                    // Pop up error dialog, noting fields need to be corrected
                    new AlertDialog.Builder(this.parent)
                            .setTitle("Incomplete Form")
                            .setMessage(messageBuilder.toString())
                            .setCancelable(true)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }

        private void initializeFloodLayout() {

            setContentView(R.layout.spot_report_flooding);
            findViewById(R.id.floodLatitude).setEnabled(false);
            findViewById(R.id.floodLongitude).setEnabled(false);

            EditText text = findViewById(R.id.floodRadiusNum);
            text.setEnabled(false);
            text.setText(String.format(Locale.ENGLISH, "%d", 0));
            ((SeekBar)findViewById(R.id.floodRadiusValue)).setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            ((EditText)findViewById(R.id.floodRadiusNum)).setText(
                                    String.format(Locale.ENGLISH, "%d", progress));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    }
            );

            EditText depthText = findViewById(R.id.floodDepthNum);
            depthText.setEnabled(false);
            depthText.setText(String.format(Locale.ENGLISH, "%d", 0));
            ((SeekBar)findViewById(R.id.floodDepthValue)).setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            ((EditText)findViewById(R.id.floodDepthNum)).setText(
                                    String.format(Locale.ENGLISH, "%d", progress));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    }
            );

            ((Spinner) findViewById(R.id.locationSource)).setOnItemSelectedListener(
                    new LocationTypeListener(this.parent, R.id.floodLatitude, R.id.floodLongitude));

            Button submitReportButton = findViewById(R.id.submitReport);
            submitReportButton.setOnClickListener((View view)-> {

                String latString = ((EditText)findViewById(R.id.floodLatitude)).getText().toString();
                String lonString = ((EditText)findViewById(R.id.floodLongitude)).getText().toString();
                int radius = Integer.parseInt(((EditText)findViewById(R.id.floodRadiusNum)).getText().toString());
                String featureType = ((Spinner)findViewById(R.id.featureType)).getSelectedItem().toString();
                int featureTypePosition = ((Spinner)findViewById(R.id.featureType)).getSelectedItemPosition();
                int depth = Integer.parseInt(((EditText)findViewById(R.id.floodDepthNum)).getText().toString());
                String method = ((Spinner)findViewById(R.id.observationMode)).getSelectedItem().toString();
                int methodPosition = ((Spinner)findViewById(R.id.observationMode)).getSelectedItemPosition();

                ArrayList<String> errors = new ArrayList<>();

                if (radius <= 0) {

                    errors.add("Radius - Specify a radius > 0\n\n");
                }

                if(0 == featureTypePosition) {

                    errors.add("Feature Type - Specify a type\n\n");
                }

                if(depth <= 0) {

                    errors.add("Depth - Specify a depth measurement > 0\n\n");
                }

                if(0 == methodPosition) {

                    errors.add("Method - Specify method of observation\n\n");
                }

                if(errors.isEmpty()) {

                    // Create and transmit report
                    parent.lastAction = ACTION_SUBMIT_FLOODING_REPORT;
                    Intent submitReportIntent = new Intent(ACTION_SUBMIT_FLOODING_REPORT);
                    submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    submitReportIntent.putExtra("lat", latString);
                    submitReportIntent.putExtra("lon", lonString);
                    submitReportIntent.putExtra("radius", radius);
                    submitReportIntent.putExtra("featureType", featureType);
                    submitReportIntent.putExtra("depth", depth);
                    submitReportIntent.putExtra("method", method);
                    submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
                    sendBroadcast(submitReportIntent);
                }
                else {

                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("The following fields have errors:\n\n");

                    for(String error: errors) {

                        messageBuilder.append(error);
                    }

                    // Pop up error dialog, noting fields need to be corrected
                    new AlertDialog.Builder(this.parent)
                            .setTitle("Incomplete Form")
                            .setMessage(messageBuilder.toString())
                            .setCancelable(true)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }

        private void initializeStreetClosureLayout() {

            setContentView(R.layout.spot_report_streetclosure);
            findViewById(R.id.scLatitude).setEnabled(false);
            findViewById(R.id.scLongitude).setEnabled(false);

            EditText text = findViewById(R.id.scRadiusNum);
            text.setEnabled(false);
            text.setText(String.format(Locale.ENGLISH, "%d", 0));
            ((SeekBar)findViewById(R.id.scRadiusValue)).setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            ((EditText)findViewById(R.id.scRadiusNum)).setText(
                                    String.format(Locale.ENGLISH, "%d", progress));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    }
            );

            ((Spinner) findViewById(R.id.locationSource)).setOnItemSelectedListener(
                    new LocationTypeListener(this.parent, R.id.scLatitude, R.id.scLongitude));

            Button submitReportButton = findViewById(R.id.submitReport);
            submitReportButton.setOnClickListener((View view)-> {

                String latString = ((EditText)findViewById(R.id.scLatitude)).getText().toString();
                String lonString = ((EditText)findViewById(R.id.scLongitude)).getText().toString();
                int radius = Integer.parseInt(((EditText)findViewById(R.id.scRadiusNum)).getText().toString());
                String type = ((Spinner)findViewById(R.id.closureType)).getSelectedItem().toString();
                int typePosition = ((Spinner)findViewById(R.id.closureType)).getSelectedItemPosition();
                String action = ((Spinner)findViewById(R.id.closureAction)).getSelectedItem().toString();
                int actionPosition = ((Spinner)findViewById(R.id.closureAction)).getSelectedItemPosition();
                Object referenceId = ((Spinner)findViewById(R.id.closureReference)).getSelectedItem();

                String referenceIdString = null;

                if (null != referenceId) {

                    referenceIdString = referenceId.toString();
                }

                ArrayList<String> errors = new ArrayList<>();

                if (radius <= 0) {

                    errors.add("Radius - Specify a radius > 0\n\n");
                }

                if(0 == typePosition) {

                    errors.add("Type - Specify a type\n\n");
                }

                if(0 == actionPosition) {

                    errors.add("Action - Specify an action\n\n");
                }

                if(errors.isEmpty()) {

                    // Create and transmit report
                    parent.lastAction = ACTION_SUBMIT_STREET_CLOSURE_REPORT;
                    Intent submitReportIntent = new Intent(ACTION_SUBMIT_STREET_CLOSURE_REPORT);
                    submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    submitReportIntent.putExtra("lat", latString);
                    submitReportIntent.putExtra("lon", lonString);
                    submitReportIntent.putExtra("radius", radius);
                    submitReportIntent.putExtra("type", type);
                    submitReportIntent.putExtra("action", action);
                    submitReportIntent.putExtra("referenceId", referenceIdString);
                    submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
                    sendBroadcast(submitReportIntent);
                }
                else {

                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("The following fields have errors:\n\n");

                    for(String error: errors) {

                        messageBuilder.append(error);
                    }

                    // Pop up error dialog, noting fields need to be corrected
                    new AlertDialog.Builder(this.parent)
                            .setTitle("Incomplete Form")
                            .setMessage(messageBuilder.toString())
                            .setCancelable(true)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }

        private void initializeTrackLayout() {

            setContentView(R.layout.spot_report_track);
            findViewById(R.id.trackLatitude).setEnabled(false);
            findViewById(R.id.trackLongitude).setEnabled(false);

            EditText text = findViewById(R.id.trackConfidenceNum);
            text.setEnabled(false);
            text.setText(String.format(Locale.ENGLISH, "%d", 0));
            ((SeekBar)findViewById(R.id.trackConfidenceValue)).setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            ((EditText)findViewById(R.id.trackConfidenceNum)).setText(
                                    String.format(Locale.ENGLISH, "%d", progress));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    }
            );

            ((Spinner) findViewById(R.id.locationSource)).setOnItemSelectedListener(
                    new LocationTypeListener(this.parent, R.id.trackLatitude, R.id.trackLongitude));

            Button submitReportButton = findViewById(R.id.submitReport);
            submitReportButton.setOnClickListener((View view)-> {

                double latString = Double.valueOf(((EditText)findViewById(R.id.trackLatitude)).getText().toString());
                double lonString = Double.valueOf(((EditText)findViewById(R.id.trackLongitude)).getText().toString());
                double confidence = Double.valueOf(((EditText)findViewById(R.id.trackConfidenceNum)).getText().toString());
                String resourceType = ((Spinner)findViewById(R.id.trackedResource)).getSelectedItem().toString();
                int typePosition = ((Spinner)findViewById(R.id.trackedResource)).getSelectedItemPosition();
                String resourceId = ((EditText)findViewById(R.id.trackResourceId)).getText().toString();
                String resourceLabel = ((EditText)findViewById(R.id.trackLabel)).getText().toString();
                String trackingMethod = ((Spinner)findViewById(R.id.trackedMethod)).getSelectedItem().toString();
                int methodPosition = ((Spinner)findViewById(R.id.trackedMethod)).getSelectedItemPosition();

                ArrayList<String> errors = new ArrayList<>();

                if (confidence <= 0) {

                    errors.add("confidence - Specify a confidence > 0\n\n");
                }

                if(0 == typePosition) {

                    errors.add("Type - Specify a type\n\n");
                }

                if(0 == resourceId.length()) {

                    errors.add("Resource Id - Specify a resource id\n\n");
                }

                if(0 == resourceLabel.length()) {

                    errors.add("Resource Label - Specify a resource label\n\n");
                }

                if(0 == methodPosition) {

                    errors.add("Tracking Method - Specify a method\n\n");
                }

                if(errors.isEmpty()) {

                    // Create and transmit report
                    parent.lastAction = ACTION_SUBMIT_TRACK_REPORT;
                    Intent submitReportIntent = new Intent(ACTION_SUBMIT_TRACK_REPORT);
                    submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    submitReportIntent.putExtra("lat", latString);
                    submitReportIntent.putExtra("lon", lonString);
                    submitReportIntent.putExtra("confidence", confidence);
                    submitReportIntent.putExtra("type", resourceType);
                    submitReportIntent.putExtra("resourceId", resourceId);
                    submitReportIntent.putExtra("resourceLabel", resourceLabel);
                    submitReportIntent.putExtra("method", trackingMethod);
                    submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
                    sendBroadcast(submitReportIntent);
                }
                else {

                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("The following fields have errors:\n\n");

                    for(String error: errors) {

                        messageBuilder.append(error);
                    }

                    // Pop up error dialog, noting fields need to be corrected
                    new AlertDialog.Builder(this.parent)
                            .setTitle("Incomplete Form")
                            .setMessage(messageBuilder.toString())
                            .setCancelable(true)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }

        private void initializeImageCaptureLayout() {

            imageView = findViewById(R.id.imageView);

            setContentView(R.layout.spot_report_image_capture);

            Button captureImageButton = findViewById(R.id.captureImage);
            captureImageButton.setOnClickListener((View v)-> dispatchTakePictureIntent());

            Button submitReportButton = findViewById(R.id.submitReport);
            submitReportButton.setOnClickListener((View view)-> {

                // Get data from incident type field
                Spinner spinner = findViewById(R.id.reportType);
                int categoryPos = spinner.getSelectedItemPosition();
                String category = spinner.getSelectedItem().toString();

                // Get location data from selected source
                String locationSource = ((Spinner)findViewById(R.id.locationSource)).getSelectedItem().toString();

                // Get the name of the report/observation
                String reportName = ((TextView)findViewById(R.id.reportName)).getText().toString();

                // Get the description
                String reportDescription = ((TextView)findViewById(R.id.description)).getText().toString();

                // If the user has filled out the form completely
                if ((0 == categoryPos) || reportName.isEmpty()) {

                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("The following fields have errors:\n\n");

                    if (0 == categoryPos) {
                        messageBuilder.append("Report Category - Select report category\n\n");
                    }

                    if (reportName.isEmpty()) {
                        messageBuilder.append("Report Name - Enter name\n\n");
                    }

                    // Pop up error dialog, noting fields need to be corrected
                    new AlertDialog.Builder(this.parent)
                            .setTitle("Incomplete Form")
                            .setMessage(messageBuilder.toString())
                            .setCancelable(true)
                            .setPositiveButton("OK", null)
                            .show();
                }
                else {

                    // Create and transmit report
                    parent.lastAction = ACTION_SUBMIT_IMAGE_REPORT;
                    Intent submitReportIntent = new Intent(ACTION_SUBMIT_IMAGE_REPORT);
                    submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    submitReportIntent.putExtra("item", category);
                    submitReportIntent.putExtra("location", locationSource);
                    submitReportIntent.putExtra("name", reportName);
                    submitReportIntent.putExtra("description", reportDescription);
                    String uriString = null;
                    if (null != imageUri) {

                        uriString = imageUri.toString();
                    }
                    submitReportIntent.putExtra("uriString", uriString);
                    submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
                    sendBroadcast(submitReportIntent);
                }
            });
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
            if (!layoutSwitched) {

                String selectedItem = parent.getItemAtPosition(position).toString();

                if (selectedItem.equalsIgnoreCase("Street Closure")) {

                    initializeStreetClosureLayout();
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Flooding")) {

                    initializeFloodLayout();
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Medical")) {

                    initializeMedicalLayout();
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Aid")) {

                    initializeAidLayout();
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Track")) {

                    initializeTrackLayout();
                    layoutSwitched = true;

                } else if(selectedItem.equalsIgnoreCase("Image Capture")) {

                    initializeImageCaptureLayout();
                    layoutSwitched = true;
                }

                ((Spinner) findViewById(R.id.reportType)).setSelection(position);
                ((Spinner) findViewById(R.id.reportType)).setOnItemSelectedListener(this);

            } else {

                layoutSwitched = false;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent)
        {

        }
    }

    /**
     *
     */
    private class SubmitRequestResultReceiver extends ResultReceiver {

        SpotReportActivity activity;

        SubmitRequestResultReceiver(SpotReportActivity activity, Handler handler) {

            super(handler);
            this.activity = activity;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);

            DialogInterface.OnClickListener clickListener = null;
            String title = null;
            String message = null;

            if (resultCode == SUBMIT_REPORT_FAILURE) {

                title = "Report Submission Failed";
                message = "Report failed to be submit, check general settings.";
            }
            else if (resultCode == SUBMIT_REPORT_SUCCESS) {

                title = "Report Submitted";
                message = "Report Submitted Successfully";

                if (lastAction == ACTION_SUBMIT_IMAGE_REPORT) {

                    clickListener = (DialogInterface dialogInterface, int i) -> {
                        ((TextView) findViewById(R.id.reportName)).setText(null);
                        ((TextView) findViewById(R.id.description)).setText(null);
                        imageView.setImageBitmap(null);
                        imageUri = null;
                        imageBitmap = null;
                    };

                }
                else if (lastAction == ACTION_SUBMIT_MEDICAL_REPORT) {

                    clickListener = (DialogInterface dialogInterface, int i) -> {
                        ((EditText) findViewById(R.id.medSign)).setText(null);
                        ((EditText) findViewById(R.id.medValue)).setText(null);
                    };
                }
                else {

                    clickListener = (DialogInterface dialogInterface, int i) -> { };
                }

                lastAction = null;
            }

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton("OK", clickListener)
                    .show();
        }
    }
}
