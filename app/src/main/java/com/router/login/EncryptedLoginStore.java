package com.router.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the router username/password locally. Only an Android Keystore-backed
 * AES key can decrypt the data, and the key is never uploaded to GitHub.
 * No password is intercepted from router HTML or sent to outside services.
 */
public final class EncryptedLoginStore {
    private static final String KEY_ALIAS = "DLinkWiFiLoginKeyV1";
    private static final String PREFS = "dlink_wifi_private_login";
    private static final String PAYLOAD = "ciphertext";
    private static final String NONCE = "nonce";
    private final SharedPreferences preferences;

    public static final class Login {
        public final String username;
        public final String password;
        public Login(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public EncryptedLoginStore(Context context) {
        preferences = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry = store.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }

    public Login read() {
        String cipherText = preferences.getString(PAYLOAD, "");
        String iv = preferences.getString(NONCE, "");
        if (cipherText.isEmpty() || iv.isEmpty()) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(
                128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] bytes = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
            JSONObject data = new JSONObject(new String(
                bytes, java.nio.charset.StandardCharsets.UTF_8));
            String username = data.optString("username", "");
            String password = data.optString("password", "");
            if (username.isEmpty() || password.isEmpty()) return null;
            return new Login(username, password);
        } catch (Exception error) {
            // Never print credentials or ciphertext to logs.
            return null;
        }
    }

    public boolean save(String username, String password) {
        if (username == null || username.trim().isEmpty()
            || password == null || password.isEmpty()) return false;
        try {
            JSONObject data = new JSONObject();
            data.put("username", username.trim());
            data.put("password", password);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(data.toString().getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
            return preferences.edit()
                .putString(PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(NONCE, Base64.encodeToString(
                    cipher.getIV(), Base64.NO_WRAP))
                .commit();
        } catch (Exception error) {
            return false;
        }
    }
}
