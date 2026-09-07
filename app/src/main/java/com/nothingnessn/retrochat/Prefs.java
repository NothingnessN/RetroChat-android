package com.nothingnessn.retrochat;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class Prefs {

    private static final String NAME = "retrochat_prefs";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_POLL_TS = "poll_ts";
    private static final String KEY_THEME = "theme_id";
    private static final String KEY_BG_COLOR = "bg_color";
    private static final String KEY_BG_IMAGE = "bg_image";
    private static final String KEY_BG_MODE = "bg_mode";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getUUID(Context c) {
        String id = sp(c).getString(KEY_UUID, null);
        if (id == null || id.length() == 0) {
            id = UUID.randomUUID().toString();
            sp(c).edit().putString(KEY_UUID, id).apply();
        }
        return id;
    }

    public static String getUsername(Context c) {
        return sp(c).getString(KEY_USERNAME, null);
    }

    public static void setUsername(Context c, String name) {
        sp(c).edit().putString(KEY_USERNAME, name).apply();
    }

    public static boolean isLoggedIn(Context c) {
        return sp(c).getBoolean(KEY_LOGGED_IN, false) && hasUsername(c);
    }

    public static void setLoggedIn(Context c, boolean logged) {
        sp(c).edit().putBoolean(KEY_LOGGED_IN, logged).apply();
    }

    public static boolean hasUsername(Context c) {
        String u = getUsername(c);
        return u != null && u.length() >= 3;
    }

    public static long getPollTs(Context c) {
        return sp(c).getLong(KEY_POLL_TS, 0L);
    }

    public static void setPollTs(Context c, long ts) {
        sp(c).edit().putLong(KEY_POLL_TS, ts).apply();
    }

    public static int getThemeId(Context c) {
        return sp(c).getInt(KEY_THEME, 0);
    }

    public static void setThemeId(Context c, int id) {
        sp(c).edit().putInt(KEY_THEME, id).apply();
    }

    public static int getBgMode(Context c) { return sp(c).getInt(KEY_BG_MODE, 0); }
    public static void setBgMode(Context c, int mode) { sp(c).edit().putInt(KEY_BG_MODE, mode).apply(); }
    public static int getBgColor(Context c) { return sp(c).getInt(KEY_BG_COLOR, 0xFF000000); }
    public static void setBgColor(Context c, int color) { sp(c).edit().putInt(KEY_BG_COLOR, color).apply(); }
    public static String getBgImage(Context c) { return sp(c).getString(KEY_BG_IMAGE, null); }
    public static void setBgImage(Context c, String path) {
        if (path == null) sp(c).edit().remove(KEY_BG_IMAGE).apply();
        else sp(c).edit().putString(KEY_BG_IMAGE, path).apply();
    }

    public static void logout(Context c) {
        sp(c).edit()
                .remove(KEY_USERNAME)
                .putBoolean(KEY_LOGGED_IN, false)
                .apply();
    }
}
