package org.androiddaisyreader.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

/**
 * サピエ図書館の認証情報を EncryptedSharedPreferences で安全に管理するユーティリティ。
 */
public class SapiePreferences {

    private static final String PREF_FILE = "sapie_credentials";
    private static final String KEY_LOGIN_ID = "login_id";
    private static final String KEY_PASSWORD = "password";

    private SapiePreferences() {}

    /**
     * 認証情報を保存する。
     */
    public static void save(Context context, String loginId, String password) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;
        prefs.edit()
                .putString(KEY_LOGIN_ID, loginId)
                .putString(KEY_PASSWORD, password)
                .apply();
    }

    /**
     * サピエIDを取得する。
     */
    public static String getLoginId(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null ? prefs.getString(KEY_LOGIN_ID, "") : "";
    }

    /**
     * パスワードを取得する。
     */
    public static String getPassword(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null ? prefs.getString(KEY_PASSWORD, "") : "";
    }

    /**
     * 認証情報が設定されているか。
     */
    public static boolean hasCredentials(Context context) {
        String loginId = getLoginId(context);
        return loginId != null && !loginId.isEmpty();
    }

    /**
     * 認証情報をクリアする。
     */
    public static void clear(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;
        prefs.edit().clear().apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    PREF_FILE,
                    masterKeyAlias,
                    context.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            android.util.Log.e("SapiePreferences", "Failed to create EncryptedSharedPreferences", e);
            return null;
        }
    }
}
