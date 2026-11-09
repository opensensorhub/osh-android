package org.sensorhub.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecurePrefs {
    private static final String KEY_ALIAS = "osh_android_secure_key";
    private static final String SECURE_PREFS_NAME = "osh_secure_prefs";

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "client_secret", "token_endpoint", "client_id"
    ));

    private static SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            );
            keyGenerator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    private static String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());

            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decrypt(String encryptedText) {
        try {
            String[] parts = encryptedText.split(":");
            if (parts.length != 2) return null;

            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] data = Base64.decode(parts[1], Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));

            byte[] decryptedBytes = cipher.doFinal(data);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static SharedPreferences getSecureStore(Context context) {
        return context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void put(Context context, String key, String value) {
        if (value == null || value.isEmpty()) {
            getSecureStore(context).edit().remove(key).apply();
            return;
        }
        String encrypted = encrypt(value);
        if (encrypted != null) {
            getSecureStore(context).edit().putString(key, encrypted).apply();
        }
    }

    public static String get(Context context, String key, String defaultValue) {
        String encrypted = getSecureStore(context).getString(key, null);
        if (encrypted == null) return defaultValue;

        String decrypted = decrypt(encrypted);
        return decrypted != null ? decrypted : defaultValue;
    }

    public static void remove(Context context, String key) {
        getSecureStore(context).edit().remove(key).apply();
    }

    public static boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.contains(key) || key.startsWith("profile_");
    }

    public static void removeByPrefix(Context context, String prefix) {
        SharedPreferences secureStore = getSecureStore(context);
        SharedPreferences.Editor editor = secureStore.edit();
        for (String key : secureStore.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

}
