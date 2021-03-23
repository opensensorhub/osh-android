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
import android.hardware.Camera;
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
import android.text.InputType;
import android.widget.BaseAdapter;

import org.sensorhub.impl.sensor.android.video.VideoEncoderConfig;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
        }
    }


    /*
     * Fragment for video settings
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class VideoPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_video);

            PreferenceScreen videoOptsScreen = getPreferenceScreen();
            bindPreferenceSummaryToValue(findPreference("video_codec"));

            // get possible video capture frame rates and sizes
            Camera camera = Camera.open(0);
            Camera.Parameters camParams = camera.getParameters();
            ArrayList<String> frameRateList = new ArrayList<>();
            for (int frameRate : camParams.getSupportedPreviewFrameRates())
                frameRateList.add(Integer.toString(frameRate));
            ArrayList<String> resList = new ArrayList<>();
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
        }
    }


    /*
     * Fragment for audio settings
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class AudioPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_audio);

            PreferenceScreen audioOptsScreen = getPreferenceScreen();
            bindPreferenceSummaryToValue(findPreference("audio_codec"));

            // get possible video capture frame rates and sizes
            List<String> sampleRateList = Arrays.asList("8000", "11025", "22050", "44100", "48000");
            List<String> bitRateList = Arrays.asList("32", "64", "96", "128", "160", "192");

            // add list of supported sample rates
            ListPreference sampleRatePrefList = (ListPreference)audioOptsScreen.findPreference("audio_samplerate");
            sampleRatePrefList.setEntries(sampleRateList.toArray(new String[0]));
            sampleRatePrefList.setEntryValues(sampleRateList.toArray(new String[0]));
            bindPreferenceSummaryToValue(findPreference("audio_samplerate"));

            // add list of supported bitrates
            ListPreference bitRatePrefList = (ListPreference)audioOptsScreen.findPreference("audio_bitrate");
            bitRatePrefList.setEntries(bitRateList.toArray(new String[0]));
            bitRatePrefList.setEntryValues(bitRateList.toArray(new String[0]));
            bindPreferenceSummaryToValue(findPreference("audio_samplerate"));
        }
    }


    @Override
    protected boolean isValidFragment(String fragmentName)
    {
        return true;
    }
}
