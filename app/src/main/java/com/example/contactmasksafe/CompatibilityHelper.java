package com.example.contactmasksafe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.Locale;

public final class CompatibilityHelper {
    private CompatibilityHelper() { }

    public static boolean isLowRamDevice(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return manager != null && Build.VERSION.SDK_INT >= 19 && manager.isLowRamDevice();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isBatteryOptimizationIgnored(Context context) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return manager != null && manager.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String deviceLabel() {
        return safe(Build.MANUFACTURER) + " " + safe(Build.MODEL)
                + " • Android " + safe(Build.VERSION.RELEASE)
                + " • API " + Build.VERSION.SDK_INT;
    }

    public static String manufacturerGuidance() {
        String maker = safe(Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        if (maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco")) {
            return "On Xiaomi/Redmi/Poco phones, keep ContactMask Safe allowed to run in the background. If your phone offers Battery saver, Background activity or Auto-start controls, choose the least restrictive option for this app.";
        }
        if (maker.contains("samsung")) {
            return "On Samsung phones, keep ContactMask Safe out of sleeping/deep-sleeping app lists if those controls are enabled, and confirm the Accessibility service remains switched on.";
        }
        if (maker.contains("oppo") || maker.contains("realme") || maker.contains("oneplus")) {
            return "On OPPO/realme/OnePlus phones, allow background activity for ContactMask Safe. If Auto-launch/Auto-start controls are available, allow this app.";
        }
        if (maker.contains("vivo") || maker.contains("iqoo")) {
            return "On vivo/iQOO phones, allow background activity and Auto-start/Startup permission if your phone provides those controls.";
        }
        if (maker.contains("huawei") || maker.contains("honor")) {
            return "On Huawei/Honor phones, keep ContactMask Safe allowed to launch and run in the background if App launch/Battery controls are present.";
        }
        if (maker.contains("motorola") || maker.contains("nokia") || maker.contains("google")) {
            return "This phone usually works with the standard Android Accessibility and battery settings. Keep the Accessibility service enabled and avoid restricting background activity.";
        }
        return "For best reliability, keep ContactMask Safe's Accessibility service enabled and avoid aggressive battery/background restrictions for this app if your phone provides such controls.";
    }

    public static String compatibilitySummary(Context context) {
        String memory = isLowRamDevice(context) ? "low-RAM adaptive mode" : "standard performance mode";
        String battery = Build.VERSION.SDK_INT < 23
                ? "system battery optimization not applicable"
                : (isBatteryOptimizationIgnored(context)
                ? "battery optimization exemption active"
                : "battery optimization may restrict background work on some OEMs");
        String restricted = Build.VERSION.SDK_INT >= 33
                ? " • Android 13+ may require App Info → Allow restricted settings for sideloaded Accessibility services"
                : "";
        return deviceLabel() + "\nCompatibility: Android 5.1+ supported • " + memory
                + "\nBackground: " + battery + restricted;
    }

    public static void openBackgroundSettings(Activity activity) {
        if (activity == null) return;
        AppLockManager.beginTrustedSettingsRoundTrip();
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                return;
            } catch (RuntimeException ignored) { }
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
            return;
        } catch (RuntimeException ignored) { }
        openAppInfo(activity);
    }

    public static void openAppInfo(Activity activity) {
        if (activity == null) return;
        AppLockManager.beginTrustedSettingsRoundTrip();
        try {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (RuntimeException ignored) {
            try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (RuntimeException ignoredAgain) { }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
