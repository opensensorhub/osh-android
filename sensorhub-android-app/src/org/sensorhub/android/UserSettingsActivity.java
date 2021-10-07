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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.view.menu.ListMenuPresenter;
import android.text.InputType;
import android.text.PrecomputedText;
import android.util.Log;
import android.widget.BaseAdapter;

import org.sensorhub.impl.sensor.android.video.VideoEncoderConfig;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.prefs.Preferences;


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
            else if (preference.getKey().startsWith("video_res"))
            {
                PreferenceScreen presetSettings = (PreferenceScreen)preference;
                String frameSize = ((ListPreference)presetSettings.getPreference(0)).getValue();
                String minBitrate = ((EditTextPreference)presetSettings.getPreference(1)).getText();
                String maxBitrate = ((EditTextPreference)presetSettings.getPreference(2)).getText();
                presetSettings.setSummary(frameSize + " @ " + minBitrate + "-" + maxBitrate + " kbits/s");
                ((BaseAdapter)presetSettings.getRootAdapter()).notifyDataSetChanged();
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

        // for preference screens, call listener when screen is closed
        if (preference instanceof PreferenceScreen) {
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ((PreferenceScreen)preference).getDialog().setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, "");
                        }
                    });
                    return true;
                }
            });
        }

        // Trigger the listener immediately with the preference's current value.
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
            bindPreferenceSummaryToValue(findPreference("angel_address"));

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

            Preference accelerometerEnable = getPreferenceScreen().findPreference("accel_enabled");
            Preference accelerometerOptions = getPreferenceScreen().findPreference("accel_options");
            accelerometerOptions.setEnabled(prefs.getBoolean(accelerometerEnable.getKey(), false));
            accelerometerEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                accelerometerOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference gyroEnabled = getPreferenceScreen().findPreference("gyro_enabled");
            Preference gyroOptions = getPreferenceScreen().findPreference("gyro_options");
            gyroOptions.setEnabled(prefs.getBoolean(gyroEnabled.getKey(), false));
            gyroEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                gyroOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference magEnabled = getPreferenceScreen().findPreference("mag_enabled");
            Preference magOptions = getPreferenceScreen().findPreference("mag_options");
            magOptions.setEnabled(prefs.getBoolean(magEnabled.getKey(), false));
            magEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                magOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference orientQuatEnabled = getPreferenceScreen().findPreference("orient_quat_enabled");
            Preference orientQuatOptions = getPreferenceScreen().findPreference("orient_quat_options");
            orientQuatOptions.setEnabled(prefs.getBoolean(orientQuatEnabled.getKey(), false));
            orientQuatEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                orientQuatOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference orientEulerEnabled = getPreferenceScreen().findPreference("orient_euler_enabled");
            Preference orientEulerOptions = getPreferenceScreen().findPreference("orient_euler_options");
            orientEulerOptions.setEnabled(prefs.getBoolean(orientEulerEnabled.getKey(), false));
            orientEulerEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                orientEulerOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference gpsEnabled = getPreferenceScreen().findPreference("gps_enabled");
            Preference gpsOptions = getPreferenceScreen().findPreference("gps_options");
            gpsOptions.setEnabled(prefs.getBoolean(gpsEnabled.getKey(), false));
            gpsEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                gpsOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference netlocEnabled = getPreferenceScreen().findPreference("netloc_enabled");
            Preference netlocOptions = getPreferenceScreen().findPreference("netloc_options");
            netlocOptions.setEnabled(prefs.getBoolean(netlocEnabled.getKey(), false));
            netlocEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                netlocOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference camEnabled = getPreferenceScreen().findPreference("cam_enabled");
            Preference camOptions = getPreferenceScreen().findPreference("cam_options");
            camOptions.setEnabled(prefs.getBoolean(camEnabled.getKey(), false));
            camEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                camOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference videoRollEnabled = getPreferenceScreen().findPreference("video_roll_enabled");
            Preference videoRollOptions = getPreferenceScreen().findPreference("video_roll_options");
            videoRollOptions.setEnabled(prefs.getBoolean(videoRollEnabled.getKey(), false));
            videoRollEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                videoRollOptions.setEnabled((boolean) newValue);
                return true;
            });

            Preference trupulseEnabled = getPreferenceScreen().findPreference("trupulse_enabled");
            Preference trupulseOptions = getPreferenceScreen().findPreference("trupulse_options");
            Preference trupulseDatasource = getPreferenceScreen().findPreference("trupulse_datasource");
            trupulseOptions.setEnabled(prefs.getBoolean(trupulseEnabled.getKey(), false));
            trupulseDatasource.setEnabled(prefs.getBoolean(trupulseEnabled.getKey(), false));
            trupulseEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                trupulseOptions.setEnabled((boolean) newValue);
                trupulseDatasource.setEnabled((boolean) newValue);
                return true;
            });

//            Preference bleEnable = getPreferenceScreen().findPreference("ble_enabled");
//            Preference bleLocationMethod = getPreferenceScreen().findPreference("ble_loc_method");
//            Preference bleOptions = getPreferenceScreen().findPreference("ble_options");
//            Preference bleConfigURL = getPreferenceScreen().findPreference("ble_config_url");
//            bleLocationMethod.setEnabled(prefs.getBoolean(bleEnable.getKey(), false));
//            bleOptions.setEnabled((prefs.getBoolean(bleEnable.getKey(), false)));
//            bleConfigURL.setEnabled((prefs.getBoolean(bleEnable.getKey(), false)));
//            bleEnable.setOnPreferenceChangeListener(((preference, newValue) -> {
//                bleLocationMethod.setEnabled((boolean) newValue);
//                bleOptions.setEnabled((boolean) newValue);
//                bleConfigURL.setEnabled((boolean) newValue);
//                return true;
//            }));

            // TODO: introduce FLIR and ANGEL sensors
        }
    }


    /*
     * Fragment for video settings
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class VideoPreferenceFragment extends PreferenceFragment
    {
        ArrayList<String> frameRateList = new ArrayList<>();
        ArrayList<String> resList = new ArrayList<>();

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_video);

            PreferenceScreen videoOptsScreen = getPreferenceScreen();

            // Create camera selection preference
            ArrayList<String> cameras = new ArrayList<>();
            for(int i = 0; i < Camera.getNumberOfCameras(); i++)
            {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                cameras.add(Integer.toString(i));
            }
            ListPreference cameraSelectList = new ListPreference(videoOptsScreen.getContext());
            cameraSelectList.setKey("camera_select");
            cameraSelectList.setTitle("Selected Camera");
            cameraSelectList.setEntries(cameras.toArray(new String[0]));
            cameraSelectList.setEntryValues(cameras.toArray(new String[0]));
//            cameraSelectList.setDefaultValue(0);
            bindPreferenceSummaryToValue(cameraSelectList);
            videoOptsScreen.addPreference(cameraSelectList);

            bindPreferenceSummaryToValue(findPreference("video_codec"));

            // TODO: verify that this works in cases where a camera might not be available, and also works on the default value
            // get possible video capture frame rates and sizes
            Camera camera = Camera.open(0);
            Camera.Parameters camParams = camera.getParameters();
//            ArrayList<String> frameRateList = new ArrayList<>();
            for (int frameRate : camParams.getSupportedPreviewFrameRates())
                frameRateList.add(Integer.toString(frameRate));
//            ArrayList<String> resList = new ArrayList<>();
            for (Camera.Size imgSize : camParams.getSupportedPreviewSizes())
                resList.add(imgSize.width + "x" + imgSize.height);
            camera.release();

            // add list of supported frame rates
            ListPreference frameRatePrefList = (ListPreference)videoOptsScreen.findPreference("video_framerate");
            frameRatePrefList.setEntries(frameRateList.toArray(new String[0]));
            frameRatePrefList.setEntryValues(frameRateList.toArray(new String[0]));
            bindPreferenceSummaryToValue(findPreference("video_framerate"));

            // add list of configurable presets
            ArrayList<String> presetNames = new ArrayList<>();
            ArrayList<String> presetIndexes = new ArrayList<>();
            for (int i = 1; i <= 5; i++)
            {
                PreferenceScreen prefScreen = getPreferenceManager().createPreferenceScreen(videoOptsScreen.getContext());
                prefScreen.setKey("video_res" + i);
                String presetName = "Video Preset #" + i;
                prefScreen.setTitle(presetName);
                presetNames.add(presetName);
                presetIndexes.add(String.valueOf(i-1));

                ListPreference sizeList = new ListPreference(prefScreen.getContext());
                sizeList.setKey("video_size" + i);
                sizeList.setTitle("Frame Size");
                sizeList.setEntries(resList.toArray(new String[0]));
                sizeList.setEntryValues(resList.toArray(new String[0]));
                bindPreferenceSummaryToValue(sizeList);
                prefScreen.addPreference(sizeList);

                EditTextPreference minBitrate = new EditTextPreference(prefScreen.getContext());
                minBitrate.setKey("video_min_bitrate" + i);
                minBitrate.setTitle("Min Bitrate (kbits/s)");
                minBitrate.getEditText().setSingleLine();
                minBitrate.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
                minBitrate.setDefaultValue("3000");
                bindPreferenceSummaryToValue(minBitrate);
                prefScreen.addPreference(minBitrate);

                EditTextPreference maxBitrate = new EditTextPreference(prefScreen.getContext());
                maxBitrate.setKey("video_max_bitrate" + i);
                maxBitrate.setTitle("Max Bitrate (kbits/s)");
                maxBitrate.getEditText().setSingleLine();
                maxBitrate.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
                maxBitrate.setDefaultValue("3000");
                bindPreferenceSummaryToValue(maxBitrate);
                prefScreen.addPreference(maxBitrate);

                bindPreferenceSummaryToValue(prefScreen);
                videoOptsScreen.addPreference(prefScreen);
            }

            // add list of selectable presets
            ListPreference selectedPresetList = (ListPreference)videoOptsScreen.findPreference("video_preset");
            presetNames.add("Auto select");
            presetIndexes.add("AUTO");
            selectedPresetList.setEntries(presetNames.toArray(new String[0]));
            selectedPresetList.setEntryValues(presetIndexes.toArray(new String[0]));

            // Setup Camera Listener
            cameraSelectList.setOnPreferenceChangeListener((preference, newValue) -> {
                Log.d("CAMERA_SELECT", "New Camera Selected: " + newValue);
                updateCameraSettings(Integer.parseInt((String) newValue));
                return true;
            });
        }

        protected void updateCameraSettings(Integer cameraId){
            Camera camera = Camera.open(cameraId);
            Camera.Parameters camParams = camera.getParameters();
            for (int frameRate : camParams.getSupportedPreviewFrameRates())
                frameRateList.add(Integer.toString(frameRate));
            for (Camera.Size imgSize : camParams.getSupportedPreviewSizes())
                resList.add(imgSize.width + "x" + imgSize.height);
            camera.release();
        }
    }


    @Override
    protected boolean isValidFragment(String fragmentName)
    {
        return true;
    }
}
