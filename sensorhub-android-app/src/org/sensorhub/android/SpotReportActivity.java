package org.sensorhub.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;


import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

public class SpotReportActivity extends Activity {

    // Data Associated with Broadcast Receivers and Intents
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private static final int SUBMIT_REPORT_FAILURE = 0;
    private static final int SUBMIT_REPORT_SUCCESS = 1;

    private static final String ACTION_SUBMIT_REPORT = "org.sensorhub.android.intent.SPOT_REPORT";
    private static final String DATA_LOC = "location";
    private static final String DATA_REPORT_NAME = "name";
    private static final String DATA_REPORT_DESCRIPTION = "description";
    private static final String DATA_REPORT_CATEGORY = "item";
    private static final String DATA_REPORT_IMAGE = "image";

    private ImageView imageView;
    private Bitmap imageBitmap = null;
    private Uri imageUri;

    private SubmitRequestResultReceiver submitRequestResultReceiver;

    private ReportTypeListener reportTypeListener = new ReportTypeListener(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spot_report);

        Button submitReportButton = findViewById(R.id.submitReport);
        submitReportButton.setOnClickListener((View view)-> onSubmitReport());

        submitRequestResultReceiver = new SubmitRequestResultReceiver(this, new Handler());

        Spinner spinner = findViewById(R.id.reportType);

        spinner.setOnItemSelectedListener(reportTypeListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void onSubmitReport() {

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
        if ((categoryPos == 0) || reportName.isEmpty()) {

            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append("The following fields have errors:\n\n");

            if (categoryPos == 0) {
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
            Intent submitReportIntent = new Intent(ACTION_SUBMIT_REPORT);
            submitReportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            submitReportIntent.putExtra(DATA_REPORT_CATEGORY, category);
            submitReportIntent.putExtra(DATA_LOC, locationSource);
            submitReportIntent.putExtra(DATA_REPORT_NAME, reportName);
            submitReportIntent.putExtra(DATA_REPORT_DESCRIPTION, reportDescription);
            String uriString = null;
            if (imageUri != null) {
                uriString = imageUri.toString();
            }
            submitReportIntent.putExtra(DATA_REPORT_IMAGE, uriString);
            submitReportIntent.putExtra(Intent.EXTRA_RESULT_RECEIVER, submitRequestResultReceiver);
            sendBroadcast(submitReportIntent);
        }
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

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            try {

                if (createImageFile()) {

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

                }

            } catch (IOException e) {

                // Error occurred while creating the File
                Log.e("SpotReport", e.toString());
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

                    Log.e("SpotReport", e.toString());
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

        Activity parent;

        ReportTypeListener(Activity parent) {

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
        }

        private void initializeImageCaptureLayout() {

            imageView = findViewById(R.id.imageView);

            setContentView(R.layout.spot_report_image_capture);

            Button captureImageButton = findViewById(R.id.captureImage);
            captureImageButton.setOnClickListener((View v)-> dispatchTakePictureIntent());
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

                clickListener = (DialogInterface dialogInterface, int i) -> {
                        ((TextView) findViewById(R.id.reportName)).setText(null);
                        ((TextView) findViewById(R.id.description)).setText(null);
                        imageView.setImageBitmap(null);
                        imageUri = null;
                        imageBitmap = null;
                    };
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
