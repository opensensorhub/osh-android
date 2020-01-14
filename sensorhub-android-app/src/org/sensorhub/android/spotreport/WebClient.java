package org.sensorhub.android.spotreport;

import android.Manifest;
import android.content.Context;
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

public class WebClient extends WebViewClient {

    private static final String TAG = "WebClient";

    SpotReportActivity spotReportActivity;
    double toLongitude;
    double toLatitude;

    public WebClient(SpotReportActivity activity, double longitude, double latitude) {

        spotReportActivity = activity;
        toLongitude = longitude;
        toLatitude = latitude;
    }

    private JSONObject buildRequest() {

        JSONObject jsonRequest = null;

        try
        {
            int grant = ContextCompat.checkSelfPermission(spotReportActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION);

            if (PackageManager.PERMISSION_GRANTED == grant) {

                LocationManager locationManager =
                        (LocationManager)spotReportActivity.getSystemService(Context.LOCATION_SERVICE);

                Location location =
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                JSONArray jsonStartCoord = new JSONArray();
                jsonStartCoord.put(location.getLongitude());
                jsonStartCoord.put(location.getLatitude());

                JSONArray jsonStopCoord = new JSONArray();
                jsonStopCoord.put(toLongitude);
                jsonStopCoord.put(toLatitude);

                JSONArray jsonCoordArray = new JSONArray();
                jsonCoordArray.put(jsonStartCoord);
                jsonCoordArray.put(jsonStopCoord);

                JSONObject waypointsJson = new JSONObject();
                waypointsJson.put("type", "MultiPoint");
                waypointsJson.put("coordinates", jsonCoordArray);

                jsonRequest = new JSONObject();
                jsonRequest.put("name", "TestRoute");
                jsonRequest.put("waypoints", waypointsJson);
                jsonRequest.put("dataset", "SCIRA");
                jsonRequest.put("context", "evacuation");
            }

        } catch (JSONException e) {

            Log.e(TAG, "Failed to build JSON payload for route");
        }

        return jsonRequest;
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                          String host, String realm) {

        handler.proceed("user", "user@SCIRA");
    }

    @Override
    public void onPageFinished(WebView view, String url) {

        JSONObject jsonRequest = buildRequest();

        if (null != jsonRequest) {

            String routeUrl = "'http://skymanticswps.eastus.azurecontainer.io:8080/scira/routes?mode=sync'";

            StringBuilder request = new StringBuilder();
            request.append("javascript:makeRouteRequest(")
                    .append(routeUrl)
                    .append(",")
                    .append("'")
                    .append(jsonRequest.toString())
                    .append("'")
                    .append(")");

            Log.d(TAG, request.toString());

            view.evaluateJavascript(request.toString(), null);
        }
    }
}
