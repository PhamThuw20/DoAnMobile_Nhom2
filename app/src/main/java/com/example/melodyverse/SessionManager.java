package com.example.melodyverse;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Quản lý phiên đăng nhập cục bộ (Bài 8 - SharedPreferences).
 * Lưu username chuẩn (đã xác thực) của người dùng đang đăng nhập để mọi màn hình dùng chung.
 */
public class SessionManager {

    private static final String PREF_NAME = "melodyverse_session";
    private static final String KEY_USERNAME = "logged_in_username";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void login(Context context, String username) {
        prefs(context).edit().putString(KEY_USERNAME, username).apply();
    }

    public static void logout(Context context) {
        prefs(context).edit().remove(KEY_USERNAME).apply();
    }

    /** Username đang đăng nhập, hoặc null nếu chưa đăng nhập. */
    public static String getCurrentUsername(Context context) {
        return prefs(context).getString(KEY_USERNAME, null);
    }

    public static boolean isCurrentUser(Context context, String username) {
        String current = getCurrentUsername(context);
        return current != null && username != null && current.equalsIgnoreCase(username.replace("@", "").trim());
    }
}
