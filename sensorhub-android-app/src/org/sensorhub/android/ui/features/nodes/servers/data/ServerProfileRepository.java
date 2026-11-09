package org.sensorhub.android.ui.features.nodes.servers.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.sensorhub.android.SecurePrefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerProfileRepository implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String KEY_PROFILES_JSON = "server_profiles_json";

    private static volatile ServerProfileRepository instance;

    private final Context context;
    private final SharedPreferences prefs;
    private volatile List<ServerProfileItem> cachedProfiles;

    private ServerProfileRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public static ServerProfileRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ServerProfileRepository.class) {
                if (instance == null) {
                    instance = new ServerProfileRepository(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (KEY_PROFILES_JSON.equals(key)) {
            cachedProfiles = null;
        }
    }

    public List<ServerProfileItem> getAll() {
        List<ServerProfileItem> cached = cachedProfiles;
        if (cached != null) return Collections.unmodifiableList(cached);

        List<ServerProfileItem> profiles = new ArrayList<>();
        String json = prefs.getString(KEY_PROFILES_JSON, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    profiles.add(ServerProfileItem.Companion.fromJson(arr.getJSONObject(i)));
                }
            } catch (JSONException e) {
                // corrupted data, return empty
            }
        }
        cachedProfiles = profiles;
        return Collections.unmodifiableList(profiles);
    }

    public List<ServerProfileItem> getEnabled() {
        List<ServerProfileItem> enabled = new ArrayList<>();
        for (ServerProfileItem p : getAll()) {
            if (p.getEnabled()) enabled.add(p);
        }
        return enabled;
    }

    public ServerProfileItem getById(String id) {
        for (ServerProfileItem p : getAll()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    public void save(ServerProfileItem profile) {
        List<ServerProfileItem> all = new ArrayList<>(getAll());
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(profile.getId())) {
                all.set(i, profile);
                found = true;
                break;
            }
        }
        if (!found) all.add(profile);
        persist(all);
    }

    public void delete(String id) {
        List<ServerProfileItem> all = new ArrayList<>(getAll());
        all.removeIf(p -> p.getId().equals(id));
        persist(all);
        SecurePrefs.removeByPrefix(context, "profile_" + id + "_");
    }

    public void setEnabled(String id, boolean enabled) {
        ServerProfileItem p = getById(id);
        if (p != null) {
            save(p.withEnabled(enabled));
        }
    }

    public String getPassword(String profileId) {
        return SecurePrefs.get(context, "profile_" + profileId + "_password", "");
    }

    public void setPassword(String profileId, String password) {
        SecurePrefs.put(context, "profile_" + profileId + "_password", password);
    }

    public String getOAuthTokenEndpoint(String profileId) {
        return SecurePrefs.get(context, "profile_" + profileId + "_oauth_token_endpoint", "");
    }

    public void setOAuthTokenEndpoint(String profileId, String value) {
        SecurePrefs.put(context, "profile_" + profileId + "_oauth_token_endpoint", value);
    }

    public String getOAuthClientId(String profileId) {
        return SecurePrefs.get(context, "profile_" + profileId + "_oauth_client_id", "");
    }

    public void setOAuthClientId(String profileId, String value) {
        SecurePrefs.put(context, "profile_" + profileId + "_oauth_client_id", value);
    }

    public String getOAuthClientSecret(String profileId) {
        return SecurePrefs.get(context, "profile_" + profileId + "_oauth_client_secret", "");
    }

    public void setOAuthClientSecret(String profileId, String value) {
        SecurePrefs.put(context, "profile_" + profileId + "_oauth_client_secret", value);
    }

    private void persist(List<ServerProfileItem> profiles) {
        JSONArray arr = new JSONArray();
        for (ServerProfileItem p : profiles) {
            arr.put(p.toJson());
        }
        prefs.edit().putString(KEY_PROFILES_JSON, arr.toString()).apply();
        cachedProfiles = profiles;
    }
}
