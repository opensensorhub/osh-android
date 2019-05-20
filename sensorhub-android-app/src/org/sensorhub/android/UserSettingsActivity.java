/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.android;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.List;

public class UserSettingsActivity extends PreferenceActivity
{
    @Override
    public void onBuildHeaders(List<Header> target)
    {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    
    /*
     * A preference value change listener that updates the preference's summary to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener()
    {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value)
        {
            String stringValue = value.toString();
            
            if (preference instanceof ListPreference)
            {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
            }
            else
            {
                preference.setSummary(stringValue);
            }
            
            // detect errors
            if (preference.getKey().equals("sos_uri"))
            {
                try
                {
                    URL url = new URL(value.toString());
                    if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https"))
                        throw new Exception("SOS URL must be HTTP or HTTPS");
                }
                catch (Exception e)
                {
                    AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(preference.getContext());
                    dlgAlert.setMessage("Invalid SOS URL");
                    dlgAlert.setTitle(e.getMessage());
                    dlgAlert.setPositiveButton("OK", null);
                    dlgAlert.setCancelable(true);
                    dlgAlert.create().show();                
                }
            }
            
            return true;
        }
    };


    /*
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference)
    {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }
    

    /*
     * Fragment for general preferences
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            bindPreferenceSummaryToValue(findPreference("device_name"));
            bindPreferenceSummaryToValue(findPreference("sos_uri"));
            bindPreferenceSummaryToValue(findPreference("sos_username"));
            bindPreferenceSummaryToValue(findPreference("sost_uri"));
            bindPreferenceSummaryToValue(findPreference("sost_username"));

            WifiManager wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

            // Convert little-endian to big-endianif needed
            if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                ipAddress = Integer.reverseBytes(ipAddress);
            }

            byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

            String ipAddressString;
            try {
                ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
            } catch (UnknownHostException ex) {
                ipAddressString = "Unable to get IP Address";
            }

            Preference ipAddressLabel = getPreferenceScreen().findPreference("nop_ipAddress");
            ipAddressLabel.setSummary(ipAddressString);
        }
    }


    /*
     * Fragment for sensor preferences
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SensorPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_sensors);
            bindPreferenceSummaryToValue(findPreference("video_codec"));
            bindPreferenceSummaryToValue(findPreference("angel_address"));

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

            Preference accelerometerEnable = getPreferenceScreen().findPreference("accelerometer_enable");
            Preference accelerometerOptions = getPreferenceScreen().findPreference("accelerometer_options");
            accelerometerOptions.setEnabled(prefs.getBoolean(accelerometerEnable.getKey(), false));
            accelerometerEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                accelerometerOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference gyroscopeEnable = getPreferenceScreen().findPreference("gyroscope_enable");
            Preference gyroscopeOptions = getPreferenceScreen().findPreference("gyroscope_options");
            gyroscopeOptions.setEnabled(prefs.getBoolean(gyroscopeEnable.getKey(), false));
            gyroscopeEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                gyroscopeOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference magnetometerEnable = getPreferenceScreen().findPreference("magnetometer_enable");
            Preference magnetometerOptions = getPreferenceScreen().findPreference("magnetometer_options");
            magnetometerOptions.setEnabled(prefs.getBoolean(magnetometerEnable.getKey(), false));
            magnetometerEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                magnetometerOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference orientationEnable = getPreferenceScreen().findPreference("orientation_enable");
            Preference orientationOptions = getPreferenceScreen().findPreference("orientation_options");
            Preference orientationAngles = getPreferenceScreen().findPreference("orientation_angles");
            orientationOptions.setEnabled(prefs.getBoolean(orientationEnable.getKey(), false));
            orientationAngles.setEnabled(prefs.getBoolean(orientationEnable.getKey(), false));
            orientationEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                orientationOptions.setEnabled((boolean) newValue);
                orientationAngles.setEnabled((boolean) newValue);
                return true;
            });

            Preference locationEnable = getPreferenceScreen().findPreference("location_enable");
            Preference locationOptions = getPreferenceScreen().findPreference("location_options");
            Preference locationType = getPreferenceScreen().findPreference("location_type");
            locationOptions.setEnabled(prefs.getBoolean(locationEnable.getKey(), false));
            locationType.setEnabled(prefs.getBoolean(locationEnable.getKey(), false));
            locationEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                locationOptions.setEnabled((boolean) newValue);
                locationType.setEnabled((boolean) newValue);
                return true;
            });

            Preference videoEnable = getPreferenceScreen().findPreference("video_enable");
            Preference videoOptions = getPreferenceScreen().findPreference("video_options");
            Preference videoCodec = getPreferenceScreen().findPreference("video_codec");
            videoOptions.setEnabled(prefs.getBoolean(videoEnable.getKey(), false));
            videoCodec.setEnabled(prefs.getBoolean(videoEnable.getKey(), false));
            videoEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                videoOptions.setEnabled((boolean) newValue);
                videoCodec.setEnabled((boolean) newValue);
                return true;
            });

            Preference trupulseEnable = getPreferenceScreen().findPreference("trupulse_enable");
            Preference trupulseOptions = getPreferenceScreen().findPreference("trupulse_options");
            Preference trupulseDatasource = getPreferenceScreen().findPreference("trupulse_datasource");
            trupulseOptions.setEnabled(prefs.getBoolean(trupulseEnable.getKey(), false));
            trupulseDatasource.setEnabled(prefs.getBoolean(trupulseEnable.getKey(), false));
            trupulseEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                trupulseOptions.setEnabled((boolean) newValue);
                trupulseDatasource.setEnabled((boolean) newValue);
                return true;
            });
        }
    }


    @Override
    protected boolean isValidFragment(String fragmentName)
    {
        return true;
    }
}
