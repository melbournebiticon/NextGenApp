package com.example.nextgen;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;


public class SessionManager {

    private static final String PREF_NAME = "user_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_FULL_NAME = "full_name";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void saveSession(String userId, String role) {
        saveSession(userId, role, "Unknown");
    }

    public void saveSession(String userId, String role, String fullName) {
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_ROLE, role);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.apply();
    }

    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    public String getRole() {
        return pref.getString(KEY_ROLE, null);
    }

    public String getFullName() {
        return pref.getString(KEY_FULL_NAME, "Unknown");
    }

    public boolean isLoggedIn() {
        return getUserId() != null;
    }

    public void saveCourseIds(List<String> courseIds) {
        editor.putStringSet("courseIds", new HashSet<>(courseIds));
        editor.apply();
    }

    public List<String> getCourseIds() {
        return new ArrayList<>(pref.getStringSet("courseIds", new HashSet<>()));
    }
    // ✅ Static helper method for quick access
    public static String getStudentId(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return pref.getString(KEY_USER_ID, null);
    }



    public void clearSession() {
        editor.clear();
        editor.apply();
    }
}
