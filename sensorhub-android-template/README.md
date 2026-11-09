# Template Driver Integration

## 1. Add the Template Module
- Duplciate the template directory
- Rename it appropriately

## 2. Add dependency to App Module:
 - In 'sensorhub-android-app' `build.gradle` we need to include the project as a dependency:
```groovy
    implementation project(':sensorhub-android-template')
```
## 3. Add Preferences UI
- In `res/xml/pref_sensors.xml`, add:
```xml
    <SwitchPreferenceCompat
    android:key="template_enabled"
    android:defaultValue="false"
    android:summary="Enable streaming of template driver"
    android:title="Template Driver Data"
    android:layout="@layout/preference_switch_item" />

    <MultiSelectListPreference
        android:key="template_options"
        android:visible="false"
        android:title="Template Driver Output Options"
        android:summary="Options for pushing sensor data"
        android:entries="@array/sos_option_list"
        android:entryValues="@array/sos_option_values"
        android:defaultValue="@array/sos_option_defaults"
        android:layout="@layout/preference_list_item"/>
```

- In `SensorsFragment.java`, include the "enabled" and "options" in the SWITCH_DEPENDENTS map.
- 
- **Note:** If the driver uses BLE to connect you must also add the ability to select the devices 'BLE Address' (Examples: Kestrel, Trupulse, Meshtastic,+ Polar)
- Add device selection:
```xml
 <Preference
            android:key="template_device_address"
            android:visible="false"
            android:title="Select Device"
            android:summary="Tap to select or enter device address"
            android:layout="@layout/preference_list_item" />
```
- In `SensorsFragment.java`, include the "template_device_address" under the BT_DEVICE_PREF_KEYS

## 4. Update `MainActivity` 
- Import the drivers Config class
`import org.sensorhub.impl.sensor.template.TemplateConfig;`
- Add to `Sensors Enum`
```
Template
```

- Enable Push check
Update `isPushingSensors(Sensors sensor)`:
```
if (Sensors.Template.equals(sensor)) {
            return prefs.getBoolean("template_enabled", false)
                    && prefs.getStringSet("template_options", Collections.emptySet()).contains("PUSH_REMOTE");
        }
```
- Add to updateConfig(...)
```
 // Template Driver
        enabled = prefs.getBoolean("template_enabled", false);
        if (enabled) {
            SensorConfig templateConfig = new SensorConfig();
            templateConfig.id = "TEMPLATE_DRIVER_";
            templateConfig.name = "Template [" + deviceName + "]";
            templateConfig.autoStart = true;
            templateConfig.lastUpdated = ANDROID_SENSORS_LAST_UPDATED;
            templateConfig.uid_extension = prefs.getString("uid_extension", "");
            sensorhubConfig.add(templateConfig);
        }
```


### Adding External Modules (osh-addons/osh-core/...)
This is slightly different process then local modules
1. Include the module in `settings.gradle` 
```groovy
'sensors/positioning/sensorhub-driver-trupulse'
```
**>**: Ensure the module path in settings.gradle matches the project folder structure exactly, and you include the correct submodule repository

2. Add Dependency in `sensorhub-android-lib`
```groovy
api project(':sensorhub-driver-kestrel')
```
3. Repeat steps 3-5 in the first set of instructions