package com.example.contactmasksafe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CONTACTS = 100;
    private static final int REQ_NOTIFICATIONS = 101;

    private TextView contactStatus;
    private TextView accessibilityStatus;
    private TextView notificationStatus;
    private TextView setupStatus;
    private TextView diagnosticStatus;
    private TextView deviceStatus;
    private Switch masterSwitch;
    private SavedNumberRepository repository;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        UiInsets.apply(this, findViewById(R.id.rootScroll));

        repository = new SavedNumberRepository(this);
        contactStatus = findViewById(R.id.contactPermissionStatus);
        accessibilityStatus = findViewById(R.id.accessibilityStatus);
        notificationStatus = findViewById(R.id.notificationStatus);
        setupStatus = findViewById(R.id.setupProgressStatus);
        diagnosticStatus = findViewById(R.id.diagnosticStatus);
        deviceStatus = findViewById(R.id.deviceStatus);
        masterSwitch = findViewById(R.id.masterSwitch);

        masterSwitch.setChecked(AppPreferences.isPrivacyShieldEnabled(this));
        masterSwitch.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setPrivacyShieldEnabled(this, checked);
            Toast.makeText(this, checked ? "Privacy shield enabled" : "Privacy shield paused", Toast.LENGTH_SHORT).show();
            updateStatus();
        });

        findViewById(R.id.btnEasySetup).setOnClickListener(v -> continueEasySetup());
        findViewById(R.id.btnAccessibility).setOnClickListener(v -> showAccessibilityDisclosure());
        findViewById(R.id.btnNotificationAccess).setOnClickListener(v -> showNotificationDisclosure());
        findViewById(R.id.btnAppSettings).setOnClickListener(v -> openAppSettings());
        findViewById(R.id.btnContacts).setOnClickListener(v -> {
            if (hasContacts()) startActivity(new Intent(this, ContactsActivity.class));
            else requestContacts();
        });
        findViewById(R.id.btnSelfTest).setOnClickListener(v -> {
            if (!hasContacts()) requestContacts();
            else if (!isAccessibilityEnabled()) showAccessibilityDisclosure();
            else startActivity(new Intent(this, MaskTestActivity.class));
        });
        findViewById(R.id.btnOpenPhone).setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_DIAL)); }
            catch (RuntimeException ex) { Toast.makeText(this, "No compatible Phone app found", Toast.LENGTH_LONG).show(); }
        });
        findViewById(R.id.btnRefreshStatus).setOnClickListener(v -> {
            repository.invalidate();
            updateStatus();
            Toast.makeText(this, "Protection status refreshed", Toast.LENGTH_SHORT).show();
        });
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void continueEasySetup() {
        if (!hasContacts()) {
            requestContacts();
            return;
        }
        if (!isAccessibilityEnabled()) {
            showAccessibilityDisclosure();
            return;
        }
        AppPreferences.setPrivacyShieldEnabled(this, true);
        masterSwitch.setChecked(true);
        Toast.makeText(this, "Core setup complete. Run the self-test.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, MaskTestActivity.class));
    }

    private void updateStatus() {
        boolean contacts = hasContacts();
        boolean accessibility = isAccessibilityEnabled();
        boolean notifications = isNotificationListenerEnabled();
        int count = 0;
        if (contacts) {
            repository.invalidate();
            repository.refreshNow();
            count = repository.getSavedNumberCount();
        }
        contactStatus.setText(contacts ? "✓ Contacts: granted • " + count + " saved phone numbers indexed" : "1. Contacts: permission required");
        accessibilityStatus.setText(accessibility ? "✓ Screen protection: enabled" : "2. Screen protection: enable Accessibility service");
        notificationStatus.setText(notifications ? "✓ Notification protection: enabled" : "Optional: notification protection not enabled");
        setupStatus.setText(contacts && accessibility ? "Core setup: READY" : "Core setup: " + ((contacts ? 1 : 0) + (accessibility ? 1 : 0)) + "/2 complete");
        deviceStatus.setText("Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL) + " • Android " + Build.VERSION.RELEASE + " • API " + Build.VERSION.SDK_INT);
        diagnosticStatus.setText("Live diagnostic\nShield: " + (AppPreferences.isPrivacyShieldEnabled(this) ? "ON" : "PAUSED")
                + "\nAccessibility service: " + (accessibility ? "connected/enabled" : "disabled")
                + "\nLast scan masks: " + AppPreferences.getLastMaskCount(this)
                + "\nIndexed saved numbers: " + count
                + "\nInternet permission: NOT REQUESTED");
    }

    private boolean hasContacts() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestContacts() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
    }

    private void showAccessibilityDisclosure() {
        new AlertDialog.Builder(this)
                .setTitle("Enable screen protection")
                .setMessage("ContactMask Safe uses Android Accessibility only to inspect visible text on supported screens and place a non-touchable XXXXXXXXX cover over saved contact phone numbers. Contact matching stays on this device. The app does not request Internet access. Android requires you to enable this service manually.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (d, w) -> openAccessibilitySettings())
                .show();
    }

    private void showNotificationDisclosure() {
        if (isNotificationListenerEnabled()) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            } else {
                Toast.makeText(this, "Notification protection is already enabled", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Optional notification protection")
                .setMessage("If enabled, ContactMask Safe can inspect notification text for saved phone numbers and attempt to replace unsafe notifications with redacted copies. Processing stays on this device.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (d, w) -> openNotificationSettings())
                .show();
    }

    private void openAccessibilitySettings() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (RuntimeException ex) { openAppSettings(); }
    }

    private void openNotificationSettings() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (RuntimeException ex) { openAppSettings(); }
    }

    private void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (RuntimeException ex) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean isAccessibilityEnabled() {
        ComponentName expected = new ComponentName(this, PrivacyAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        for (String value : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (expected.equals(component)) return true;
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName expected = new ComponentName(this, NotificationMaskService.class);
        for (String value : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (expected.equals(component)) return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        repository.invalidate();
        updateStatus();
        if (requestCode == REQ_CONTACTS && hasContacts()) Toast.makeText(this, "Contacts permission granted. Continue Easy Setup.", Toast.LENGTH_LONG).show();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
