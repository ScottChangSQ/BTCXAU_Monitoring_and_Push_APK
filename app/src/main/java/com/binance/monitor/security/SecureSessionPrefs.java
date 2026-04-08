/*
 * 本地安全会话偏好，负责用 Android Keystore 加密保存最近一次远程会话摘要。
 * 这里只保存账号摘要和列表缓存，不保存明文密码，供账户页恢复远程会话 UI 使用。
 */
package com.binance.monitor.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.ui.account.AccountRemoteSessionCoordinator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecureSessionPrefs implements AccountRemoteSessionCoordinator.SessionSummaryStore {
    private static final String PREF_NAME = "secure_session_prefs";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_IV = "iv";
    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String KEY_ALIAS = "mt5_remote_session_summary_key";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences preferences;

    // 创建安全会话偏好实例。
    public SecureSessionPrefs(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void saveSession(@Nullable RemoteAccountProfile activeAccount,
                            @NonNull List<RemoteAccountProfile> savedAccounts,
                            boolean active) {
        SessionCache cache = readCache();
        cache.active = active;
        cache.activeAccount = activeAccount;
        cache.savedAccounts = savedAccounts == null ? new ArrayList<>() : new ArrayList<>(savedAccounts);
        writeCache(cache);
    }

    @Override
    public void clearSession() {
        SessionCache cache = readCache();
        cache.active = false;
        cache.activeAccount = null;
        writeCache(cache);
    }

    // 保存最近输入的账号和服务器，便于下次打开面板时直接回填。
    public void saveDraftIdentity(@Nullable String account, @Nullable String server) {
        SessionCache cache = readCache();
        cache.draftAccount = safeText(account);
        cache.draftServer = safeText(server);
        writeCache(cache);
    }

    // 返回当前是否缓存了激活会话标记。
    public boolean isSessionMarkedActive() {
        return readCache().active;
    }

    // 返回最近一次激活账号摘要。
    @Nullable
    public RemoteAccountProfile getActiveAccount() {
        return readCache().activeAccount;
    }

    // 返回最近一次已保存账号列表缓存。
    @NonNull
    public List<RemoteAccountProfile> getSavedAccounts() {
        return Collections.unmodifiableList(readCache().savedAccounts);
    }

    @Override
    @NonNull
    public List<RemoteAccountProfile> getSavedAccountsSnapshot() {
        return new ArrayList<>(readCache().savedAccounts);
    }

    // 返回最近输入的账号。
    @NonNull
    public String getDraftAccount(@Nullable String fallback) {
        String value = safeText(readCache().draftAccount);
        return value.isEmpty() ? safeText(fallback) : value;
    }

    // 返回最近输入的服务器。
    @NonNull
    public String getDraftServer(@Nullable String fallback) {
        String value = safeText(readCache().draftServer);
        return value.isEmpty() ? safeText(fallback) : value;
    }

    // 读取并解密本地会话缓存。
    @NonNull
    private SessionCache readCache() {
        String encodedPayload = safeText(preferences.getString(KEY_PAYLOAD, ""));
        String encodedIv = safeText(preferences.getString(KEY_IV, ""));
        if (encodedPayload.isEmpty() || encodedIv.isEmpty()) {
            return new SessionCache();
        }
        try {
            byte[] iv = Base64.getDecoder().decode(encodedIv);
            byte[] payload = Base64.getDecoder().decode(encodedPayload);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(payload);
            JSONObject root = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            return SessionCache.fromJson(root);
        } catch (Exception ignored) {
            preferences.edit().remove(KEY_PAYLOAD).remove(KEY_IV).apply();
            return new SessionCache();
        }
    }

    // 加密并写回本地会话缓存。
    private void writeCache(@NonNull SessionCache cache) {
        try {
            JSONObject root = cache.toJson();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
            byte[] encrypted = cipher.doFinal(root.toString().getBytes(StandardCharsets.UTF_8));
            String encodedPayload = Base64.getEncoder().encodeToString(encrypted);
            String encodedIv = Base64.getEncoder().encodeToString(cipher.getIV());
            preferences.edit()
                    .putString(KEY_PAYLOAD, encodedPayload)
                    .putString(KEY_IV, encodedIv)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    // 读取或创建 Android Keystore 对称密钥。
    @NonNull
    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_NAME);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false);
        }
        generator.init(builder.build());
        return generator.generateKey();
    }

    @NonNull
    private static String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SessionCache {
        private boolean active;
        private RemoteAccountProfile activeAccount;
        private List<RemoteAccountProfile> savedAccounts = new ArrayList<>();
        private String draftAccount = "";
        private String draftServer = "";

        // 把缓存对象转换成 JSON。
        @NonNull
        JSONObject toJson() throws Exception {
            JSONObject root = new JSONObject();
            root.put("active", active);
            root.put("draftAccount", safeText(draftAccount));
            root.put("draftServer", safeText(draftServer));
            root.put("activeAccount", activeAccount == null ? JSONObject.NULL : profileToJson(activeAccount));
            JSONArray accountsArray = new JSONArray();
            for (RemoteAccountProfile item : savedAccounts) {
                if (item == null) {
                    continue;
                }
                accountsArray.put(profileToJson(item));
            }
            root.put("savedAccounts", accountsArray);
            return root;
        }

        // 从 JSON 恢复缓存对象。
        @NonNull
        static SessionCache fromJson(@Nullable JSONObject root) {
            SessionCache cache = new SessionCache();
            if (root == null) {
                return cache;
            }
            cache.active = root.optBoolean("active", false);
            cache.draftAccount = safeText(root.optString("draftAccount", ""));
            cache.draftServer = safeText(root.optString("draftServer", ""));
            cache.activeAccount = profileFromJson(root.optJSONObject("activeAccount"));
            JSONArray accountsArray = root.optJSONArray("savedAccounts");
            if (accountsArray != null) {
                for (int i = 0; i < accountsArray.length(); i++) {
                    RemoteAccountProfile item = profileFromJson(accountsArray.optJSONObject(i));
                    if (item != null) {
                        cache.savedAccounts.add(item);
                    }
                }
            }
            return cache;
        }

        @NonNull
        private static JSONObject profileToJson(@NonNull RemoteAccountProfile profile) throws Exception {
            JSONObject object = new JSONObject();
            object.put("profileId", profile.getProfileId());
            object.put("login", profile.getLogin());
            object.put("loginMasked", profile.getLoginMasked());
            object.put("server", profile.getServer());
            object.put("displayName", profile.getDisplayName());
            object.put("active", profile.isActive());
            object.put("state", profile.getState());
            return object;
        }

        @Nullable
        private static RemoteAccountProfile profileFromJson(@Nullable JSONObject object) {
            if (object == null) {
                return null;
            }
            String state = object.optString("state", "");
            return new RemoteAccountProfile(
                    object.optString("profileId", ""),
                    object.optString("login", ""),
                    object.optString("loginMasked", ""),
                    object.optString("server", ""),
                    object.optString("displayName", ""),
                    RemoteAccountProfile.resolveActiveFlag(object.optBoolean("active", false), state),
                    state
            );
        }
    }
}
