package com.example.contactmasksafe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SavedNumberRepository {
    private static final long REFRESH_INTERVAL_MS = 600000L;

    private final Context appContext;
    private final Object lock = new Object();
    private volatile Set<String> savedKeys = Collections.emptySet();
    private volatile Set<String> suffix8 = Collections.emptySet();
    private volatile Set<String> suffix9 = Collections.emptySet();
    private volatile Set<String> suffix10 = Collections.emptySet();
    private volatile Map<String, String> namesByKey = Collections.emptyMap();
    private volatile int savedNumberCount;
    private volatile String sampleNumber;
    private volatile String sampleName;
    private volatile long lastRefreshElapsed;
    private volatile boolean invalidated = true;

    public SavedNumberRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void invalidate() {
        invalidated = true;
    }

    public void refreshNow() {
        synchronized (lock) {
            HashSet<String> keys = new HashSet<>();
            HashSet<String> localSuffix8 = new HashSet<>();
            HashSet<String> localSuffix9 = new HashSet<>();
            HashSet<String> localSuffix10 = new HashSet<>();
            HashMap<String, String> names = new HashMap<>();
            HashSet<String> uniqueNumbers = new HashSet<>();
            String firstNumber = null;
            String firstName = null;

            if (Build.VERSION.SDK_INT < 23
                    || appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED) {
                Cursor cursor = null;
                try {
                    cursor = appContext.getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            new String[]{
                                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
                            },
                            null,
                            null,
                            null
                    );
                    if (cursor != null) {
                        int numberIndex = cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER);
                        int normalizedIndex = cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
                        int nameIndex = cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY);

                        while (cursor.moveToNext()) {
                            String number = numberIndex >= 0 ? cursor.getString(numberIndex) : null;
                            String normalized = normalizedIndex >= 0
                                    ? cursor.getString(normalizedIndex) : null;
                            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                            addNumber(keys, localSuffix8, localSuffix9, localSuffix10,
                                    names, uniqueNumbers, number, name);
                            addNumber(keys, localSuffix8, localSuffix9, localSuffix10,
                                    names, uniqueNumbers, normalized, name);
                            if (firstNumber == null && number != null
                                    && PhoneMasker.digitsOnly(number).length() >= 7) {
                                firstNumber = number;
                                firstName = name;
                            }
                        }
                    }
                } catch (SecurityException ignored) {
                    // Permission may have been revoked while the query was running.
                } finally {
                    if (cursor != null) cursor.close();
                }
            }

            savedKeys = Collections.unmodifiableSet(keys);
            suffix8 = Collections.unmodifiableSet(localSuffix8);
            suffix9 = Collections.unmodifiableSet(localSuffix9);
            suffix10 = Collections.unmodifiableSet(localSuffix10);
            namesByKey = Collections.unmodifiableMap(names);
            savedNumberCount = uniqueNumbers.size();
            sampleNumber = firstNumber;
            sampleName = firstName;
            lastRefreshElapsed = android.os.SystemClock.elapsedRealtime();
            invalidated = false;
        }
    }

    public boolean isSavedNumber(String candidate) {
        ensureFresh();
        String digits = PhoneMasker.digitsOnly(candidate);
        if (digits.length() < 7 || digits.length() > 16) {
            return false;
        }

        for (String key : variants(candidate)) {
            if (savedKeys.contains(key)) return true;
        }

        // Fast conservative suffix matching for UI strings that expose only a local form.
        if (digits.length() >= 10 && suffix10.contains(lastDigits(digits, 10))) return true;
        if (digits.length() == 9 && suffix9.contains(digits)) return true;
        return digits.length() == 8 && suffix8.contains(digits);
    }

    public String findSavedName(String candidate) {
        ensureFresh();
        for (String key : variants(candidate)) {
            String name = namesByKey.get(key);
            if (name != null && !name.trim().isEmpty()) return name;
        }
        return null;
    }

    public int getSavedNumberCount() {
        ensureFresh();
        return savedNumberCount;
    }

    public String getSampleNumber() {
        ensureFresh();
        return sampleNumber;
    }

    public String getSampleName() {
        ensureFresh();
        return sampleName;
    }

    private void ensureFresh() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (invalidated || now - lastRefreshElapsed > REFRESH_INTERVAL_MS) refreshNow();
    }

    private static void addNumber(Set<String> keys,
                                  Set<String> suffix8,
                                  Set<String> suffix9,
                                  Set<String> suffix10,
                                  Map<String, String> names,
                                  Set<String> uniqueNumbers,
                                  String number,
                                  String name) {
        String digits = PhoneMasker.digitsOnly(number);
        if (digits.length() >= 7) {
            String canonical = digits.length() > 10 ? lastDigits(digits, 10) : digits;
            uniqueNumbers.add(canonical);
            if (digits.length() >= 8) suffix8.add(lastDigits(digits, 8));
            if (digits.length() >= 9) suffix9.add(lastDigits(digits, 9));
            if (digits.length() >= 10) suffix10.add(lastDigits(digits, 10));
        }
        for (String key : variants(number)) {
            if (key.length() >= 7) {
                keys.add(key);
                if (name != null && !name.trim().isEmpty()) names.put(key, name.trim());
            }
        }
    }

    private static Set<String> variants(String value) {
        HashSet<String> result = new HashSet<>();
        String digits = PhoneMasker.digitsOnly(value);
        if (digits.isEmpty()) return result;

        result.add(digits);
        if (digits.startsWith("00") && digits.length() > 2) result.add(digits.substring(2));
        if (digits.length() == 11 && digits.startsWith("0")) result.add(digits.substring(1));
        if (digits.length() >= 10) result.add(lastDigits(digits, 10));
        if (digits.length() == 9) result.add(digits);
        if (digits.length() == 8) result.add(digits);
        return result;
    }

    private static String lastDigits(String value, int count) {
        return value.length() <= count ? value : value.substring(value.length() - count);
    }
}
