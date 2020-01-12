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

import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import org.sensorhub.android.R;

/**
 *
 */
class ReportTypeListener implements AdapterView.OnItemSelectedListener {

    private boolean layoutSwitched = false;

    private SpotReportActivity activity;

    ReportTypeListener(SpotReportActivity activity) {

        this.activity = activity;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
    {
        if (!layoutSwitched) {

            String selectedItem = parent.getItemAtPosition(position).toString();

            if (selectedItem.equalsIgnoreCase("Street Closure")) {

                activity.initializeStreetClosureLayout();
                layoutSwitched = true;

            } else if (selectedItem.equalsIgnoreCase("Flooding")) {

                activity.initializeFloodLayout();
                layoutSwitched = true;

            } else if (selectedItem.equalsIgnoreCase("Medical")) {

                activity.initializeMedicalLayout();
                layoutSwitched = true;

            } else if (selectedItem.equalsIgnoreCase("Aid")) {

                activity.initializeAidLayout();
                layoutSwitched = true;

            } else if (selectedItem.equalsIgnoreCase("Track")) {

                activity.initializeTrackLayout();
                layoutSwitched = true;

            } else if(selectedItem.equalsIgnoreCase("Image Capture")) {

                activity.initializeImageCaptureLayout();
                layoutSwitched = true;
            }

            ((Spinner) activity.findViewById(R.id.reportType)).setSelection(position);
            ((Spinner) activity.findViewById(R.id.reportType)).setOnItemSelectedListener(this);

        } else {

            layoutSwitched = false;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {

    }
}
