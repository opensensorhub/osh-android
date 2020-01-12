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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.widget.EditText;
import android.widget.TextView;

import org.sensorhub.android.R;

/**
 *
 */
class SubmitRequestResultReceiver extends ResultReceiver {

    private static final int SUBMIT_REPORT_FAILURE = 0;
    private static final int SUBMIT_REPORT_SUCCESS = 1;

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

            if (activity.getLastAction().equals(SpotReportActions.ACTION_SUBMIT_IMAGE_REPORT)) {

                clickListener = (DialogInterface dialogInterface, int i) -> {
                    ((TextView) activity.findViewById(R.id.reportName)).setText(null);
                    ((TextView) activity.findViewById(R.id.description)).setText(null);
                    activity.clearImageData();
                };

            }
            else if (activity.getLastAction().equals(SpotReportActions.ACTION_SUBMIT_MEDICAL_REPORT)) {

                clickListener = (DialogInterface dialogInterface, int i) -> {
                    ((EditText) activity.findViewById(R.id.medSign)).setText(null);
                    ((EditText) activity.findViewById(R.id.medValue)).setText(null);
                };
            }
            else {

                clickListener = (DialogInterface dialogInterface, int i) -> { };
            }

            activity.clearLastAction();
        }

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("OK", clickListener)
                .show();
    }
}
