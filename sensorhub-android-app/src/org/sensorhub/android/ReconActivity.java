package org.sensorhub.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

public class ReconActivity extends Activity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private ImageView imageView;
    Bitmap imageBitmap = null;
    Button submitReportButton;

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

            // Pop up error dialog, noting fields need to be corrected
            new AlertDialog.Builder(this)
                    .setTitle("Report Submitted")
                    .setMessage("Report Submitted Successfully")
                    .setCancelable(true)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ((TextView)findViewById(R.id.reportName)).setText(null);
                            ((TextView)findViewById(R.id.description)).setText(null);
                            imageView.setImageBitmap(null);
                        }
                    })
                    .show();
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
