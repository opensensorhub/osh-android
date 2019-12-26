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
import android.widget.ImageView;
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

        imageView = findViewById(R.id.imageView);

        Button captureImageButton = findViewById(R.id.captureImage);
        captureImageButton.setOnClickListener((View view)-> dispatchTakePictureIntent());

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

        imageView.setImageBitmap(imageBitmap);
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

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
            if (!layoutSwitched) {

                String selectedItem = parent.getItemAtPosition(position).toString();

                int contentViewId = 0;
                int latId = 0;
                int lonId = 0;

                if (selectedItem.equalsIgnoreCase("Street Closure")) {

                    contentViewId = R.layout.spot_report_streetclosure;
                    latId = R.id.scLatitude;
                    lonId = R.id.scLongitude;
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Flooding")) {

                    contentViewId = R.layout.spot_report_flooding;
                    latId = R.id.floodLatitude;
                    lonId = R.id.floodLongitude;
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Medical")) {

                    contentViewId = R.layout.spot_report_medical;
                    latId = R.id.medLatitude;
                    lonId = R.id.medLongitude;
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Aid")) {

                    contentViewId = R.layout.spot_report_aid;
                    latId = R.id.aidLatitude;
                    lonId = R.id.aidLongitude;
                    layoutSwitched = true;

                } else if (selectedItem.equalsIgnoreCase("Track")) {

                    contentViewId = R.layout.spot_report_track;
                    latId = R.id.trackLatitude;
                    lonId = R.id.trackLongitude;
                    layoutSwitched = true;
                }

                if(layoutSwitched) {

                    setContentView(contentViewId);

                    LocationManager locationManager = (LocationManager) this.parent.getSystemService(LOCATION_SERVICE);

                    String locationSource = ((Spinner)findViewById(R.id.locationSource)).getSelectedItem().toString();

                    Location location = null;

                    if(locationSource.equalsIgnoreCase("GPS")) {

                        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                    } else if (locationSource.equalsIgnoreCase("Network")) {

                        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }

                    if (null != location) {

                        ((TextView) findViewById(latId)).setText("" + location.getLatitude());
                        ((TextView) findViewById(lonId)).setText("" + location.getLongitude());
                    }
                }

                ((Spinner) findViewById(R.id.reportType)).setSelection(position);
                ((Spinner) findViewById(R.id.reportType)).setOnItemSelectedListener(this);
            }
            else {

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
