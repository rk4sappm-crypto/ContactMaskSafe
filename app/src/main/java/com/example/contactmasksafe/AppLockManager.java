package com.example.contactmasksafe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AppLockManager {
    private static final String PREFS = "contact_mask_safe_lock";
    private static final String KEY_FAILED = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 30_000L;
    private static final long TRUSTED_SETTINGS_RETURN_MS = 120_000L;
    private static final String PASSWORD_SHA256 = "2f22d55a8447bfbd7cf043a30c5b6368988d039a2a91a7b4ec44d563882b91e8";
    private static volatile boolean unlocked;
    private static volatile long trustedSettingsReturnUntil;

    private AppLockManager() { }

    public static boolean isUnlocked() { return unlocked; }

    public static void lock() {
        unlocked = false;
        trustedSettingsReturnUntil = 0L;
    }

    public static void beginTrustedSettingsRoundTrip() {
        if (unlocked) trustedSettingsReturnUntil = System.currentTimeMillis() + TRUSTED_SETTINGS_RETURN_MS;
    }

    public static boolean shouldKeepUnlockedForTrustedSettings() {
        if (!unlocked || trustedSettingsReturnUntil <= 0L) return false;
        if (System.currentTimeMillis() <= trustedSettingsReturnUntil) return true;
        trustedSettingsReturnUntil = 0L;
        return false;
    }

    public static void finishTrustedSettingsRoundTrip() {
        trustedSettingsReturnUntil = 0L;
    }

    public static boolean verifyAndUnlock(Context context, String password) {
        if (isTemporarilyLocked(context)) return false;
        boolean matches = secureEquals(PASSWORD_SHA256, sha256(password == null ? "" : password));
        if (matches) {
            unlocked = true;
            trustedSettingsReturnUntil = 0L;
            preferences(context).edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply();
            return true;
        }
        recordFailure(context);
        return false;
    }

    public static boolean verifyPassword(Context context, String password) {
        if (isTemporarilyLocked(context)) return false;
        boolean matches = secureEquals(PASSWORD_SHA256, sha256(password == null ? "" : password));
        if (matches) {
            preferences(context).edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply();
            return true;
        }
        recordFailure(context);
        return false;
    }

    public static long getLockoutRemainingMs(Context context) {
        long until = preferences(context).getLong(KEY_LOCKOUT_UNTIL, 0L);
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L && until != 0L) {
            preferences(context).edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply();
            return 0L;
        }
        return Math.max(0L, remaining);
    }

    public static boolean isTemporarilyLocked(Context context) {
        return getLockoutRemainingMs(context) > 0L;
    }

    public static boolean requireUnlocked(Activity activity) {
        if (unlocked) return true;
        Intent intent = new Intent(activity, UnlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
        return false;
    }

    private static void recordFailure(Context context) {
        SharedPreferences prefs = preferences(context);
        int failed = prefs.getInt(KEY_FAILED, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        if (failed >= MAX_FAILED_ATTEMPTS) {
            editor.putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MS);
        } else {
            editor.putInt(KEY_FAILED, failed);
        }
        editor.apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean secureEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
