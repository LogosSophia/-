package com.logos.clipboarddemo;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String PREF = "logos_clipboard_settings";
    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    private static final String KEY_AUTO_CAPTURE = "auto_capture";
    private static final String KEY_DIRECT_PASTE = "direct_paste";
    private static final String KEY_MASK_SENSITIVE = "mask_sensitive";

    private AppPrefs() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean overlayEnabled(Context context) {
        return prefs(context).getBoolean(KEY_OVERLAY_ENABLED, false);
    }

    public static void setOverlayEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply();
    }

    public static boolean autoCapture(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_CAPTURE, true);
    }

    public static void setAutoCapture(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_CAPTURE, value).apply();
    }

    public static boolean directPaste(Context context) {
        return prefs(context).getBoolean(KEY_DIRECT_PASTE, true);
    }

    public static void setDirectPaste(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_DIRECT_PASTE, value).apply();
    }

    public static boolean maskSensitive(Context context) {
        return prefs(context).getBoolean(KEY_MASK_SENSITIVE, true);
    }

    public static void setMaskSensitive(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_MASK_SENSITIVE, value).apply();
    }
}
