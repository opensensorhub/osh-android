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

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sensorhub.android.R;

public class TaskAcceptanceListener implements DialogInterface.OnClickListener {

    private static final String TAG = "TaskAcceptanceListener";

    WebClient webClient;

    SpotReportActivity spotReportActivity;
    String taskMessage;

    public TaskAcceptanceListener(SpotReportActivity activity, String task, double longitude, double latitude) {

        webClient = new WebClient(activity, longitude, latitude);
        taskMessage = task;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        if (spotReportActivity.getCurrentForm() != SpotReportActivity.Forms.WEB) {

            spotReportActivity.setCurrentForm(SpotReportActivity.Forms.WEB);

            spotReportActivity.getActionBar().hide();
            spotReportActivity.setContentView(R.layout.spot_report_web_view);
            WebView webView = spotReportActivity.findViewById(R.id.webView);
            WebView.setWebContentsDebuggingEnabled(true);
            webView.getSettings().setJavaScriptEnabled(true);
            spotReportActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            webView.loadUrl("http://scira.georobotix.io:8181/");

            webView.setWebViewClient(webClient);
        }

        try {

            JSONObject receivedTask = new JSONObject(taskMessage);
            JSONObject observation = receivedTask.getJSONObject("result");
            String id = observation.getString("id");
            String type = observation.getString("observationType");

            JSONObject params = observation.getJSONObject("params");

            JSONObject location = params.getJSONObject("location");
            JSONObject geometry = location.getJSONObject("geometry");
            JSONArray coords = geometry.getJSONArray("coordinates");
            String longitude = coords.getString(0);
            String latitude = coords.getString(1);

            if("aid".equalsIgnoreCase(type)) {

                String aidType = params.getString("aidType");
                String urgency = params.getString("urgency");
                String numPersons = params.getString("aidPersons");
                String description = params.getString("description");
                String reporter = params.getString("reporter");
                int radius = geometry.getInt("radius");

                spotReportActivity.sendAidMessage(latitude, longitude, radius, aidType,
                        numPersons, urgency, description, reporter, "ongoing", id);

                spotReportActivity.taskAccepted(id, type);
            }
            else if("flood".equalsIgnoreCase(type)) {

                String featureType = params.getString("featureType");
                String method = params.getString("method");
                double depth = params.getDouble("obsDepth");
                int radius = geometry.getInt("radius");

                spotReportActivity.sendFloodMessage(latitude, longitude, radius, featureType,
                        (int)depth, method, "ongoing", id);

                spotReportActivity.taskAccepted(id, type);
            }
//            else if ("med".equalsIgnoreCase(type)) {
//
//                String description = params.getString("description");
//                int radius = geometry.getInt("radius");
//
//                spotReportActivity.sendMedicalMessage(latitude, longitude, radius, description,
//                        String measure, boolean emergency, "ongoing", id);
//
//                spotReportActivity.taskAccepted(id, type);
//            }
            else {

                String closureType = params.getString("closureType");
                int radius = geometry.getInt("radius");

                spotReportActivity.sendStreetClosureMessage(latitude, longitude, radius, closureType,
                        "ongoing", id);

                spotReportActivity.taskAccepted(id, type);
            }

        } catch(JSONException e) {

            Log.d(TAG, "Failed parsing JSON message");
        }

    }
}
