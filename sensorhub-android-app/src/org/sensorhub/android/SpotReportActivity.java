package org.sensorhub.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

public class SpotReportActivity extends Activity {

    // Data Associated with Broadcast Receivers and Intents
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final String ACTION_SUBMIT_REPORT = "org.sensorhub.android.intent.SPOT_REPORT";
    private static final String ACTION_SUBMIT_REPORT_RESPONSE = "org.sensorhub.android.intent.SPOT_REPORT_RESPONSE";
    private static final String RESPONSE_CODE = "code";
    private static final int RESPONSE_SUCCESS = 1;
    private static final String DATA_LOC = "location";
    private static final String DATA_REPORT_NAME = "report name";
    private static final String DATA_REPORT_DESCRIPTION = "report description";
    private static final String DATA_REPORTING_ITEM = "reporting item";
    private static final String DATA_REPORTING_IMAGE = "image";
    private SpotReportReceiver broadCastReceiver = new SpotReportReceiver();

    private ImageView imageView;
    private Bitmap imageBitmap = null;
    private Button submitReportButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recon);

        imageView = findViewById(R.id.imageView);

        Button captureImageButton = findViewById(R.id.captureImage);
        captureImageButton.setOnClickListener((View view)-> {
                    dispatchTakePictureIntent();
                });

        submitReportButton = findViewById(R.id.submitReport);
        submitReportButton.setOnClickListener((View view)-> {
                    onSubmitReport();
                });

        registerReceiver(broadCastReceiver, new IntentFilter(ACTION_SUBMIT_REPORT_RESPONSE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadCastReceiver);
    }

    private void onSubmitReport() {

        // Get data from incident type field
        int reportTypePos = ((Spinner)findViewById(R.id.reportType)).getSelectedItemPosition();
        String reportType = ((Spinner)findViewById(R.id.reportType)).getSelectedItem().toString();

        // Get location data from selected source
        String locationSource = ((Spinner)findViewById(R.id.locationSource)).getSelectedItem().toString();

        // Get the name of the report/observation
        String reportName = ((TextView)findViewById(R.id.reportName)).getText().toString();

        // Get the description
        String reportDescription = ((TextView)findViewById(R.id.description)).getText().toString();

        // If the user has filled out the form completely
        if ((reportTypePos == 0) || reportName.isEmpty()) {

            StringBuffer messageBuilder = new StringBuffer();
            messageBuilder.append("The following fields have errors:\n\n");

            if (reportTypePos == 0) {
                messageBuilder.append("Report Item - Select report type\n\n");
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
            submitReportIntent.putExtra(DATA_REPORTING_ITEM, reportType);
            submitReportIntent.putExtra(DATA_LOC, locationSource);
            submitReportIntent.putExtra(DATA_REPORT_NAME, reportName);
            submitReportIntent.putExtra(DATA_REPORT_DESCRIPTION, reportDescription);
            submitReportIntent.putExtra(DATA_REPORTING_IMAGE, new byte[50]);
            sendBroadcast(submitReportIntent);
        }
    }

    private class SpotReportReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals(ACTION_SUBMIT_REPORT_RESPONSE)) {

                if (intent.getIntExtra(RESPONSE_CODE, 0) == RESPONSE_SUCCESS) {

                    // Pop up error dialog, noting fields need to be corrected
                    new AlertDialog.Builder(context)
                            .setTitle("Report Submitted")
                            .setMessage("Report Submitted Successfully")
                            .setCancelable(true)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    ((TextView) findViewById(R.id.reportName)).setText(null);
                                    ((TextView) findViewById(R.id.description)).setText(null);
                                    imageView.setImageBitmap(null);
                                }
                            })
                            .show();
                }
            }
        }
    }


    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            imageView.setImageBitmap(imageBitmap);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        imageView.setImageBitmap(imageBitmap);
    }
}
