package com.example.contactmasksafe;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPreferences {
    private static final String PREFS = "contact_mask_safe";
    private static final String KEY_ENABLED = "privacy_shield_enabled";
    private static final String KEY_SERVICE_CONNECTED_AT = "service_connected_at";
    private static final String KEY_LAST_EVENT_AT = "last_event_at";
    private static final String KEY_LAST_MASK_COUNT = "last_mask_count";
    private static final String KEY_GUIDED_SETUP_ACTIVE = "guided_setup_active";
    private static final String KEY_SETUP_COMPLETED_ONCE = "setup_completed_once";

    private AppPreferences() { }

    public static boolean isPrivacyShieldEnabled(Context context) {
        return preferences(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setPrivacyShieldEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static void setGuidedSetupActive(Context context, boolean active) {
        preferences(context).edit().putBoolean(KEY_GUIDED_SETUP_ACTIVE, active).apply();
    }

    public static boolean isGuidedSetupActive(Context context) {
        return preferences(context).getBoolean(KEY_GUIDED_SETUP_ACTIVE, false);
    }

    public static void markSetupCompleted(Context context) {
        preferences(context).edit()
                .putBoolean(KEY_GUIDED_SETUP_ACTIVE, false)
                .putBoolean(KEY_SETUP_COMPLETED_ONCE, true)
                .apply();
    }

    public static boolean wasSetupCompletedOnce(Context context) {
        return preferences(context).getBoolean(KEY_SETUP_COMPLETED_ONCE, false);
    }

    public static void recordServiceConnected(Context context) {
        preferences(context).edit().putLong(KEY_SERVICE_CONNECTED_AT, System.currentTimeMillis()).apply();
    }

    public static long getServiceConnectedAt(Context context) {
        return preferences(context).getLong(KEY_SERVICE_CONNECTED_AT, 0L);
    }

    public static void recordAccessibilityEvent(Context context, int maskCount) {
        preferences(context).edit()
                .putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis())
                .putInt(KEY_LAST_MASK_COUNT, Math.max(maskCount, 0))
                .apply();
    }

    public static long getLastEventAt(Context context) {
        return preferences(context).getLong(KEY_LAST_EVENT_AT, 0L);
    }

    public static int getLastMaskCount(Context context) {
        return preferences(context).getInt(KEY_LAST_MASK_COUNT, 0);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
