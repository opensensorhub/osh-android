/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020 Botts Innovative Research, Inc. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.android.spotreport;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sensorhub.android.R;
import org.sensorhub.android.mqtt.IMqttSubscriber;
import org.sensorhub.android.mqtt.MqttConnectionListener;
import org.sensorhub.android.mqtt.MqttHelper;
import org.sensorhub.android.mqtt.MqttMessageHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class SpotReportActivity extends Activity implements IMqttSubscriber {

    private static final String TAG = "SpotReportActivity";

    // Data Associated with Broadcast Receivers and Intents
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private final SubmitRequestResultReceiver submitRequestResultReceiver =
            new SubmitRequestResultReceiver(this, new Handler());

    // MQTT Messaging data
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

    private final MqttMessageHandler mqttMessageHandler = new MqttMessageHandler(this);

    // UI components and data structures
    private ImageView imageView;
    private Bitmap imageBitmap = null;
    private Uri imageUri;
    private String lastAction = null;
    private List<String> aidReportIds = new ArrayList<>();
    private List<String> streetClosureReportIds = new ArrayList<>();
    private List<String> floodReportIds = new ArrayList<>();
    private List<String> medicalReportIds = new ArrayList<>();

    enum Forms {

        NONE,
        WEB,
        AID,
        STREET_CLOSURE,
        FLOOD,
        MEDICAL,
        TRACK,
        IMAGE
    }

    Forms currentForm = Forms.NONE;

    private final ReportTypeListener reportTypeListener = new ReportTypeListener(this);

    private static final String APP_PREFERENCES = "SpotReportPreferences";
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_spot_report);
        Spinner spinner = findViewById(R.id.reportType);
        spinner.setOnItemSelectedListener(reportTypeListener);

        mqttHelper = new MqttHelper();

        IMqttToken connection = mqttHelper.connect(this, MQTT_USER, MQTT_PASSWORD, MQTT_URL);
        connection.setActionCallback(new MqttConnectionListener(mqttHelper, this));

        sharedPreferences = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE);

        Set<String> aidReports = sharedPreferences.getStringSet("aidReportIds", null);

        if(null != aidReports) {
            for (String id : aidReports) {

                aidReportIds.add(id);
            }
        }

        Set<String> closureReports =
                sharedPreferences.getStringSet("streetClosureReportIds", null);

        if(null != closureReports) {
            for (String id : closureReports) {

                streetClosureReportIds.add(id);
            }
        }

        Set<String> floodReports = sharedPreferences.getStringSet("floodReportIds", null);

        if(null != floodReports) {
            for (String id : floodReports) {

                floodReportIds.add(id);
            }
        }

        Set<String> medReports = sharedPreferences.getStringSet("medicalReportIds", null);

        if(null != medReports) {
            for (String id : medReports) {

                medicalReportIds.add(id);
            }
        }
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

    @Override
    public void onBackPressed() {

        if (currentForm == Forms.WEB) {

            currentForm = Forms.NONE;
            if(!getActionBar().isShowing()) {

                getActionBar().show();
            }
            setContentView(R.layout.activity_spot_report);
            Spinner spinner = findViewById(R.id.reportType);
            spinner.setOnItemSelectedListener(reportTypeListener);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        else {

            super.onBackPressed();
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

    @Override
    public void subscribeToTopics() {

        Log.d(TAG, "subscribeToTopics");

        for(String topic : topics) {

            mqttHelper.subscribe(topic, mqttMessageHandler);
        }
    }

    @Override
    public void onMessage(String message) {

        Log.d(TAG, "onMessage");

        try {

            List<String> reportIds = null;
            Spinner reportIdsSpinner = findViewById(R.id.spotReportId);

            JSONObject receivedTask = new JSONObject(message);
            JSONObject observation = receivedTask.getJSONObject("result");
            String id = observation.getString("id");
            String timeStamp = observation.getString("timeStamp");
            String type = observation.getString("observationType");

            JSONObject params = observation.getJSONObject("params");
            String action = params.getString("action");

            JSONObject location = params.getJSONObject("location");
            JSONObject geometry = location.getJSONObject("geometry");
            JSONArray coords = geometry.getJSONArray("coordinates");
            double longitude = coords.getDouble(0);
            double latitude = coords.getDouble(1);

            boolean showAlert = true;
            String title = "Tasking Notification";

            StringBuilder taskingNotification = new StringBuilder();
            taskingNotification.append("Type: ");

            if(type.equalsIgnoreCase("streetclosure")) {

                taskingNotification.append("Street Closure");
                reportIds = streetClosureReportIds;
            }
            else if (type.equalsIgnoreCase("flood")) {

                taskingNotification.append("Flood Report");
                reportIds = floodReportIds;
            }
//            else if (type.equalsIgnoreCase("med")) {
//
//                taskingNotification.append("Medical Emergency");
//                reportIds = medicalReportIds;
//            }
            else if (type.equalsIgnoreCase("aid")) {

                taskingNotification.append("Assist Person(s)");
                reportIds = aidReportIds;
            }
            else {

                showAlert = false;
            }

            taskingNotification
                    .append("\n")
                    .append("Time: ")
                    .append(timeStamp)
                    .append("\n")
                    .append("Location: \n")
                    .append("\tLon: ")
                    .append(longitude)
                    .append("\n")
                    .append("\tLat: ")
                    .append(latitude);

            if(action.equalsIgnoreCase("open") && showAlert && currentForm != Forms.WEB) {

                // Ad the id to the list of ids
                reportIds.add(id);

                if(null != reportIdsSpinner) {

                    ((ArrayAdapter)reportIdsSpinner.getAdapter()).notifyDataSetChanged();
                }

                // Pop up error dialog, noting fields need to be corrected
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(taskingNotification.toString())
                        .setCancelable(true)
                        .setNegativeButton("DISMISS", null)
                        .setPositiveButton("ACCEPT",
                                new TaskAcceptanceListener(this, message, longitude, latitude))
                        .show();

            } else if(action.equalsIgnoreCase("close")) {

                reportIds.remove(id);

                if(null != reportIdsSpinner) {

                    ((ArrayAdapter)reportIdsSpinner.getAdapter()).notifyDataSetChanged();
                }
            }

            persistReports();

        } catch (JSONException exception) {

            Log.d(TAG, "Failed parsing JSON message");
        }
    }

    void persistReports() {

        sharedPreferences.edit().clear();
        Set<String> data = new HashSet<>();
        data.addAll(aidReportIds);
        sharedPreferences.edit().putStringSet("aidReportIds", data);
        data = new HashSet<>();
        data.addAll(streetClosureReportIds);
        sharedPreferences.edit().putStringSet("streetClosureReportIds", data);
        data = new HashSet<>();
        data.addAll(floodReportIds);
        sharedPreferences.edit().putStringSet("floodReportIds", data);
        data = new HashSet<>();
        data.addAll(medicalReportIds);
        sharedPreferences.edit().putStringSet("medicalReportIds", data);
    }

    void taskAccepted(String id, String type) {

        List<String> reportIds = null;
        Spinner reportIdsSpinner = findViewById(R.id.spotReportId);

        if(type.equalsIgnoreCase("streetclosure")) {

            reportIds = streetClosureReportIds;
        }
        else if (type.equalsIgnoreCase("flood")) {

            reportIds = floodReportIds;
        }
        else if (type.equalsIgnoreCase("med")) {

            reportIds = medicalReportIds;
        }
        else if (type.equalsIgnoreCase("aid")) {

            reportIds = aidReportIds;
        }

        // Ad the id to the list of ids
        reportIds.remove(id);

        if(null != reportIdsSpinner) {

            ((ArrayAdapter)reportIdsSpinner.getAdapter()).notifyDataSetChanged();
        }

        persistReports();
    }

    Forms getCurrentForm() {

        return currentForm;
    }

    void setCurrentForm(Forms currentForm) {

        this.currentForm = currentForm;
    }

    void setLastAction(String action) {

        lastAction = action;
    }

    String getLastAction() {

        return lastAction;
    }

    void clearLastAction() {

        lastAction = null;
    }

    void setImageView() {

        imageView = findViewById(R.id.imageView);
    }

    Uri getImageUri() {

        return imageUri;
    }

    void clearImageData() {

        imageView.setImageBitmap(null);
        imageUri = null;
        imageBitmap = null;
    }

    void sendAidMessage(String latString, String lonString, int radius, String aidType,
                        String numPersons, String urgency, String description,
                        String reporter, String action, String spotReportId) {

        // Create and transmit report
        setLastAction(SpotReportActions.ACTION_SUBMIT_AID_REPORT);
        Intent submitReportIntent = new Intent(SpotReportActions.ACTION_SUBMIT_AID_REPORT);
        submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        submitReportIntent.putExtra("lat", latString);
        submitReportIntent.putExtra("lon", lonString);
        submitReportIntent.putExtra("radius", radius);
        submitReportIntent.putExtra("aidType", aidType);
        submitReportIntent.putExtra("numPersons", numPersons);
        submitReportIntent.putExtra("urgency", urgency);
        submitReportIntent.putExtra("description", description);
        submitReportIntent.putExtra("reporter", reporter);
        submitReportIntent.putExtra("action", action);
        submitReportIntent.putExtra("id", spotReportId);
        submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
        sendBroadcast(submitReportIntent);
    }

    void sendFloodMessage(String latString, String lonString, int radius, String featureType,
                          int depth, String method, String action, String spotReportId) {

        // Create and transmit report
        setLastAction(SpotReportActions.ACTION_SUBMIT_FLOODING_REPORT);
        Intent submitReportIntent = new Intent(SpotReportActions.ACTION_SUBMIT_FLOODING_REPORT);
        submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        submitReportIntent.putExtra("lat", latString);
        submitReportIntent.putExtra("lon", lonString);
        submitReportIntent.putExtra("radius", radius);
        submitReportIntent.putExtra("featureType", featureType);
        submitReportIntent.putExtra("depth", depth);
        submitReportIntent.putExtra("method", method);
        submitReportIntent.putExtra("action", action);
        submitReportIntent.putExtra("id", spotReportId);
        submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
        sendBroadcast(submitReportIntent);
    }

    void sendMedicalMessage(String latString, String lonString, int radius, String description,
                            String measure, boolean emergency, String action, String spotReportId) {

        // Create and transmit report
        setLastAction(SpotReportActions.ACTION_SUBMIT_MEDICAL_REPORT);
        Intent submitReportIntent = new Intent(SpotReportActions.ACTION_SUBMIT_MEDICAL_REPORT);
        submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        submitReportIntent.putExtra("lat", latString);
        submitReportIntent.putExtra("lon", lonString);
        submitReportIntent.putExtra("radius", radius);
        submitReportIntent.putExtra("description", description);
        submitReportIntent.putExtra("measure", measure);
        submitReportIntent.putExtra("emergency", emergency);
        submitReportIntent.putExtra("action", action);
        submitReportIntent.putExtra("id", spotReportId);
        submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
        sendBroadcast(submitReportIntent);
    }

    void sendStreetClosureMessage(String latString, String lonString, int radius, String type,
                                  String action, String spotReportId) {

        // Create and transmit report
        setLastAction(SpotReportActions.ACTION_SUBMIT_STREET_CLOSURE_REPORT);
        Intent submitReportIntent = new Intent(SpotReportActions.ACTION_SUBMIT_STREET_CLOSURE_REPORT);
        submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        submitReportIntent.putExtra("lat", latString);
        submitReportIntent.putExtra("lon", lonString);
        submitReportIntent.putExtra("radius", radius);
        submitReportIntent.putExtra("type", type);
        submitReportIntent.putExtra("action", action);
        submitReportIntent.putExtra("id", spotReportId);
        submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
        sendBroadcast(submitReportIntent);
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

    void initializeAidLayout() {

        currentForm = Forms.AID;

        if(!getActionBar().isShowing()) {

            getActionBar().show();
        }
        setContentView(R.layout.spot_report_aid);

        ((Spinner)findViewById(R.id.action)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                String selectedItem = parent.getItemAtPosition(position).toString();

                View reportIdView = findViewById(R.id.spotReportId);
                reportIdView.setEnabled(false);

                if (selectedItem.equalsIgnoreCase("close")) {

                    reportIdView.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        findViewById(R.id.aidLatitude).setEnabled(false);
        findViewById(R.id.aidLongitude).setEnabled(false);

        Spinner closureIds = findViewById(R.id.spotReportId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, streetClosureReportIds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        closureIds.setAdapter(adapter);

        EditText text = findViewById(R.id.aidRadiusNum);
        text.setEnabled(false);
        text.setText(String.format(Locale.ENGLISH, "%d", 0));

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

        ((Spinner)findViewById(R.id.locationSource)).setOnItemSelectedListener(
                new LocationTypeListener(this, R.id.aidLatitude, R.id.aidLongitude));

        Button submitReportButton = findViewById(R.id.submitReport);
        submitReportButton.setOnClickListener((View view)-> {

            String latString = ((EditText)findViewById(R.id.aidLatitude)).getText().toString();
            String lonString = ((EditText)findViewById(R.id.aidLongitude)).getText().toString();
            int radius = Integer.parseInt(((EditText)findViewById(R.id.aidRadiusNum)).getText().toString());
            String aidType = ((Spinner)findViewById(R.id.aidType)).getSelectedItem().toString();
            int aidPosition = ((Spinner)findViewById(R.id.aidType)).getSelectedItemPosition();
            String numPersons = ((EditText)findViewById(R.id.aidNum)).getText().toString();
            String urgency = ((Spinner)findViewById(R.id.aidUrgency)).getSelectedItem().toString();
            int urgencyPosition = ((Spinner)findViewById(R.id.aidUrgency)).getSelectedItemPosition();
            String description = ((EditText)findViewById(R.id.aidDescription)).getText().toString();
            String reporter = ((EditText)findViewById(R.id.aidReporter)).getText().toString();
            String action = ((Spinner)findViewById(R.id.action)).getSelectedItem().toString();
            Object spotReportId = ((Spinner)findViewById(R.id.spotReportId)).getSelectedItem();

            String reportId = null;
            if(action.equalsIgnoreCase("open") || spotReportId == null){

                // Generate a new report id
                reportId = UUID.randomUUID().toString();
            }
            else {

                reportId = spotReportId.toString();
            }

            ArrayList<String> errors = new ArrayList<>();

            if(!action.equalsIgnoreCase("open") && !action.equalsIgnoreCase("close")) {

                errors.add("Specify report action\n" +
                        "\tOpen - Submit a new report\n" +
                        "\tClose - Close task by Ref Id.\n\n");
            }

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

                sendAidMessage(latString, lonString, radius, aidType, numPersons, urgency,
                        description, reporter, action, reportId);
            }
            else {

                StringBuilder messageBuilder = new StringBuilder();
                messageBuilder.append("The following fields have errors:\n\n");

                for(String error: errors) {

                    messageBuilder.append(error);
                }

                // Pop up error dialog, noting fields need to be corrected
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete Form")
                        .setMessage(messageBuilder.toString())
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    void initializeStreetClosureLayout() {

        currentForm = Forms.STREET_CLOSURE;

        if(!getActionBar().isShowing()) {

            getActionBar().show();
        }
        setContentView(R.layout.spot_report_streetclosure);

        ((Spinner)findViewById(R.id.action)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                String selectedItem = parent.getItemAtPosition(position).toString();

                View reportIdView = findViewById(R.id.spotReportId);
                reportIdView.setEnabled(false);

                if (selectedItem.equalsIgnoreCase("close")) {

                    reportIdView.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        findViewById(R.id.scLatitude).setEnabled(false);
        findViewById(R.id.scLongitude).setEnabled(false);

        Spinner closureIds = findViewById(R.id.spotReportId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, streetClosureReportIds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        closureIds.setAdapter(adapter);

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

        ((Spinner)findViewById(R.id.locationSource)).setOnItemSelectedListener(
                new LocationTypeListener(this, R.id.scLatitude, R.id.scLongitude));

        Button submitReportButton = findViewById(R.id.submitReport);
        submitReportButton.setOnClickListener((View view)-> {

            String latString = ((EditText)findViewById(R.id.scLatitude)).getText().toString();
            String lonString = ((EditText)findViewById(R.id.scLongitude)).getText().toString();
            int radius = Integer.parseInt(((EditText)findViewById(R.id.scRadiusNum)).getText().toString());
            String type = ((Spinner)findViewById(R.id.closureType)).getSelectedItem().toString();
            int typePosition = ((Spinner)findViewById(R.id.closureType)).getSelectedItemPosition();
            int actionPosition = ((Spinner)findViewById(R.id.action)).getSelectedItemPosition();
            String action = ((Spinner)findViewById(R.id.action)).getSelectedItem().toString();
            Object spotReportId = ((Spinner)findViewById(R.id.spotReportId)).getSelectedItem();

            String reportId = null;
            if(action.equalsIgnoreCase("open") || spotReportId == null){

                // Generate a new report id
                reportId = UUID.randomUUID().toString();
            }
            else {

                reportId = spotReportId.toString();
            }

            ArrayList<String> errors = new ArrayList<>();

            if(!action.equalsIgnoreCase("open") && !action.equalsIgnoreCase("close")) {

                errors.add("Specify report action\n" +
                        "\tOpen - Submit a new report\n" +
                        "\tClose - Close task by Ref Id.\n\n");
            }

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

                sendStreetClosureMessage(latString, lonString, radius, type,
                        action, reportId);
            }
            else {

                StringBuilder messageBuilder = new StringBuilder();
                messageBuilder.append("The following fields have errors:\n\n");

                for(String error: errors) {

                    messageBuilder.append(error);
                }

                // Pop up error dialog, noting fields need to be corrected
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete Form")
                        .setMessage(messageBuilder.toString())
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    void initializeFloodLayout() {

        currentForm = Forms.FLOOD;

        if(!getActionBar().isShowing()) {

            getActionBar().show();
        }
        setContentView(R.layout.spot_report_flooding);

        ((Spinner)findViewById(R.id.action)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                String selectedItem = parent.getItemAtPosition(position).toString();

                View reportIdView = findViewById(R.id.spotReportId);
                reportIdView.setEnabled(false);

                if (selectedItem.equalsIgnoreCase("close")) {

                    reportIdView.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        findViewById(R.id.floodLatitude).setEnabled(false);
        findViewById(R.id.floodLongitude).setEnabled(false);

        Spinner closureIds = findViewById(R.id.spotReportId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, streetClosureReportIds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        closureIds.setAdapter(adapter);

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
                new LocationTypeListener(this, R.id.floodLatitude, R.id.floodLongitude));

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
            String action = ((Spinner)findViewById(R.id.action)).getSelectedItem().toString();
            Object spotReportId = ((Spinner)findViewById(R.id.spotReportId)).getSelectedItem();

            String reportId = null;
            if(action.equalsIgnoreCase("open") || spotReportId == null){

                // Generate a new report id
                reportId = UUID.randomUUID().toString();
            }
            else {

                reportId = spotReportId.toString();
            }

            ArrayList<String> errors = new ArrayList<>();

            if(!action.equalsIgnoreCase("open") && !action.equalsIgnoreCase("close")) {

                errors.add("Specify report action\n" +
                        "\tOpen - Submit a new report\n" +
                        "\tClose - Close task by Ref Id.\n\n");
            }

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

                sendFloodMessage(latString, lonString, radius, featureType, depth, method,
                        action, reportId);
            }
            else {

                StringBuilder messageBuilder = new StringBuilder();
                messageBuilder.append("The following fields have errors:\n\n");

                for(String error: errors) {

                    messageBuilder.append(error);
                }

                // Pop up error dialog, noting fields need to be corrected
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete Form")
                        .setMessage(messageBuilder.toString())
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    void initializeMedicalLayout() {

        currentForm = Forms.MEDICAL;

        if(!getActionBar().isShowing()) {

            getActionBar().show();
        }
        setContentView(R.layout.spot_report_medical);

        ((Spinner)findViewById(R.id.action)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                String selectedItem = parent.getItemAtPosition(position).toString();

                View reportIdView = findViewById(R.id.spotReportId);
                reportIdView.setEnabled(false);

                if (selectedItem.equalsIgnoreCase("close")) {

                    reportIdView.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        findViewById(R.id.medLatitude).setEnabled(false);
        findViewById(R.id.medLongitude).setEnabled(false);

        Spinner closureIds = findViewById(R.id.spotReportId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, streetClosureReportIds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        closureIds.setAdapter(adapter);

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

        ((Spinner)findViewById(R.id.locationSource)).setOnItemSelectedListener(
                new LocationTypeListener(this, R.id.medLatitude, R.id.medLongitude));

        Button submitReportButton = findViewById(R.id.submitReport);
        submitReportButton.setOnClickListener((View view)-> {

            String latString = ((EditText)findViewById(R.id.medLatitude)).getText().toString();
            String lonString = ((EditText)findViewById(R.id.medLongitude)).getText().toString();
            int radius = Integer.parseInt(((EditText)findViewById(R.id.medRadiusNum)).getText().toString());
            String description = ((EditText)findViewById(R.id.medSign)).getText().toString();
            String measure = ((EditText)findViewById(R.id.medValue)).getText().toString();
            boolean emergency = ((CheckBox)findViewById(R.id.isEmergency)).isChecked();
            String action = ((Spinner)findViewById(R.id.action)).getSelectedItem().toString();
            Object spotReportId = ((Spinner)findViewById(R.id.spotReportId)).getSelectedItem();

            String reportId = null;
            if(action.equalsIgnoreCase("open") || spotReportId == null){

                // Generate a new report id
                reportId = UUID.randomUUID().toString();
            }
            else {

                reportId = spotReportId.toString();
            }

            ArrayList<String> errors = new ArrayList<>();

            if(!action.equalsIgnoreCase("open") && !action.equalsIgnoreCase("close")) {

                errors.add("Specify report action\n" +
                        "\tOpen - Submit a new report\n" +
                        "\tClose - Close task by Ref Id.\n\n");
            }

            if(radius <= 0) {

                errors.add("Radius - Specify a radius > 0\n\n");
            }

            if(0 == description.length()) {

                errors.add("Description - Describe medical condition\n\n");
            }

            if(0 == measure.length()) {

                errors.add("Measure - Enter measurement (heart rate, bp, etc).\n\n");
            }

            if(errors.isEmpty()) {

                sendMedicalMessage(latString, lonString, radius, description, measure, emergency,
                        action, reportId);
            }
            else {

                StringBuilder messageBuilder = new StringBuilder();
                messageBuilder.append("The following fields have errors:\n\n");

                for(String error: errors) {

                    messageBuilder.append(error);
                }

                // Pop up error dialog, noting fields need to be corrected
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete Form")
                        .setMessage(messageBuilder.toString())
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    void initializeTrackLayout() {

        currentForm = Forms.MEDICAL;

        if(!getActionBar().isShowing()) {

            getActionBar().show();
        }
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

        ((Spinner)findViewById(R.id.locationSource)).setOnItemSelectedListener(
                new LocationTypeListener(this, R.id.trackLatitude, R.id.trackLongitude));

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
                setLastAction(SpotReportActions.ACTION_SUBMIT_TRACK_REPORT);
                Intent submitReportIntent = new Intent(SpotReportActions.ACTION_SUBMIT_TRACK_REPORT);
                submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                submitReportIntent.putExtra("lat", latString);
                submitReportIntent.putExtra("lon", lonString);
                submitReportIntent.putExtra("confidence", confidence);
                submitReportIntent.putExtra("type", resourceType);
                submitReportIntent.putExtra("resourceId", resourceId);
                submitReportIntent.putExtra("resourceLabel", resourceLabel);
                submitReportIntent.putExtra("method", trackingMethod);
                submitReportIntent.putExtra("action", "open");
                submitReportIntent.putExtra("id", UUID.randomUUID().toString());
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
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete Form")
                        .setMessage(messageBuilder.toString())
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    void initializeImageCaptureLayout() {

        currentForm = Forms.IMAGE;

        setImageView();

        if(!getActionBar().isShowing()) {

            getActionBar().show();
        }
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
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete Form")
                        .setMessage(messageBuilder.toString())
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .show();
            }
            else {

                // Create and transmit report
                setLastAction(SpotReportActions.ACTION_SUBMIT_IMAGE_REPORT);
                Intent submitReportIntent = new Intent(SpotReportActions.ACTION_SUBMIT_IMAGE_REPORT);
                submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                submitReportIntent.putExtra("item", category);
                submitReportIntent.putExtra("location", locationSource);
                submitReportIntent.putExtra("name", reportName);
                submitReportIntent.putExtra("description", reportDescription);
                String uriString = null;
                Uri imageUri = getImageUri();
                if (null != imageUri) {

                    uriString = imageUri.toString();
                }
                submitReportIntent.putExtra("uriString", uriString);
                submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
                sendBroadcast(submitReportIntent);
            }
        });
    }
}
