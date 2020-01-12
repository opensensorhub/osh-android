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
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import java.util.Locale;

/**
 *
 */
class LocationTypeListener implements AdapterView.OnItemSelectedListener {

    private int latId;
    private int lonId;

    SpotReportActivity activity;

    LocationTypeListener(SpotReportActivity activity, int latId, int lonId) {

        this.activity = activity;
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

            LocationManager locationManager = (LocationManager) this.activity.getSystemService(Context.LOCATION_SERVICE);

            if (selectedItem.equalsIgnoreCase("GPS")) {

                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            } else if (selectedItem.equalsIgnoreCase("Network")) {

                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (null != location) {

                if(latId != 0) {

                    ((TextView) activity.findViewById(latId)).setText(
                            String.format(Locale.ENGLISH, "%f", location.getLatitude()));
                }

                if(lonId != 0) {

                    ((TextView) activity.findViewById(lonId)).setText(
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
