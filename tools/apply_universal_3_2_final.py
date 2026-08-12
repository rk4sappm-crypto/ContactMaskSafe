from pathlib import Path
import re

root = Path.cwd()
pkg = root / 'app/src/main/java/com/example/contactmasksafe'
res = root / 'app/src/main/res'

# Version 3.2.0 Universal
p = root / 'app/build.gradle'
s = p.read_text()
s = re.sub(r"versionCode\s+\d+", "versionCode 11", s)
s = re.sub(r"versionName\s+'[^']+'", "versionName '3.2.0'", s)
p.write_text(s)

# Route the existing Easy Setup entry to the new guided wizard and add device help.
p = pkg / 'MainActivity.java'
s = p.read_text()
s = s.replace('findViewById(R.id.btnEasySetup).setOnClickListener(v -> continueEasySetup());', 'findViewById(R.id.btnEasySetup).setOnClickListener(v -> startActivity(new Intent(this, SmartSetupActivity.class)));')
if 'R.id.btnCompatibility' not in s:
    needle = 'findViewById(R.id.btnAppSettings).setOnClickListener(v -> openAppSettings());'
    if needle not in s:
        raise SystemExit('MainActivity insertion point missing')
    s = s.replace(needle, needle + '\n        findViewById(R.id.btnCompatibility).setOnClickListener(v -> startActivity(new Intent(this, CompatibilityActivity.class)));')
p.write_text(s)

# Main screen text/button.
p = res / 'layout/activity_main.xml'
s = p.read_text()
s = s.replace('ContactMask Safe 3.0', 'ContactMask Safe 3.2 Universal').replace('ContactMask Safe 3.1', 'ContactMask Safe 3.2 Universal')
s = s.replace('Start Easy Setup', 'One-Tap Smart Setup')
if '@+id/btnCompatibility' not in s:
    marker = '''        <TextView\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="24dp"\n            android:text="Protection tools"'''
    button = '''        <Button\n            android:id="@+id/btnCompatibility"\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:minHeight="52dp"\n            android:layout_marginTop="8dp"\n            android:paddingTop="12dp"\n            android:paddingBottom="12dp"\n            android:text="Compatibility & Device Help"\n            android:textAllCaps="false" />\n\n'''
    if marker not in s:
        raise SystemExit('activity_main insertion point missing')
    s = s.replace(marker, button + marker)
s = s.replace('XXXXXXXXX', 'Masked')
p.write_text(s)

# Manifest activities.
p = root / 'app/src/main/AndroidManifest.xml'
s = p.read_text()
if '.SmartSetupActivity' not in s:
    insertion = '''        <activity\n            android:name=".SmartSetupActivity"\n            android:exported="false" />\n        <activity\n            android:name=".CompatibilityActivity"\n            android:exported="false" />\n'''
    marker = '        <activity\n            android:name=".MaskTestActivity"'
    if marker not in s:
        raise SystemExit('Manifest insertion point missing')
    s = s.replace(marker, insertion + marker)
p.write_text(s)

(pkg / 'DeviceSetupHelper.java').write_text(r'''package com.example.contactmasksafe;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.util.Locale;

/** Safe best-effort shortcuts and OEM guidance. No hidden permissions are requested. */
public final class DeviceSetupHelper {
    private DeviceSetupHelper() { }

    public static String deviceLabel() {
        return clean(Build.MANUFACTURER) + " " + clean(Build.MODEL)
                + " • Android " + clean(Build.VERSION.RELEASE)
                + " • API " + Build.VERSION.SDK_INT;
    }

    public static String compatibilitySummary() {
        StringBuilder b = new StringBuilder();
        b.append("Universal mode: Android 5.1+ (API 22+)\n");
        b.append("Device: ").append(deviceLabel()).append("\n\n");
        if (Build.VERSION.SDK_INT >= 33) {
            b.append("Android 13+ note: if Accessibility is disabled/greyed out after sideloading, open App info and use Android's ‘Allow restricted settings’ option when your phone provides it.\n\n");
        }
        b.append(oemAccessibilityTip()).append("\n\n");
        b.append("Privacy: saved-number matching stays on this phone. ContactMask Safe does not request Internet access.");
        return b.toString();
    }

    public static String oemAccessibilityTip() {
        String m = clean(Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        if (m.contains("samsung")) return "Samsung tip: Accessibility is usually under Settings › Accessibility › Installed apps.";
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) return "Xiaomi/Redmi/POCO tip: look under Additional settings › Accessibility › Downloaded apps. Some versions may also require allowing restricted settings from App info.";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) return "OPPO/realme/OnePlus tip: look under Additional/System settings › Accessibility › Downloaded apps.";
        if (m.contains("vivo") || m.contains("iqoo")) return "vivo/iQOO tip: look under Shortcuts & accessibility › Accessibility › Downloaded/Installed services.";
        if (m.contains("huawei") || m.contains("honor")) return "Huawei/Honor tip: look under Accessibility features › Accessibility › Installed services.";
        if (m.contains("motorola") || m.contains("google") || m.contains("nokia") || m.contains("nothing")) return "Android tip: look under Settings › Accessibility › Downloaded apps / Installed apps.";
        return "Setup tip: open Android Accessibility settings and enable ‘ContactMask Safe screen protection’ under Downloaded/Installed services.";
    }

    public static void openAccessibility(Activity a) {
        safeStart(a, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), new Intent(Settings.ACTION_SETTINGS));
    }
    public static void openNotificationAccess(Activity a) {
        safeStart(a, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), new Intent(Settings.ACTION_SETTINGS));
    }
    public static void openAppInfo(Activity a) {
        safeStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())), new Intent(Settings.ACTION_SETTINGS));
    }
    public static void openBatterySettings(Activity a) {
        Intent preferred = Build.VERSION.SDK_INT >= 23 ? new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) : new Intent(Settings.ACTION_SETTINGS);
        safeStart(a, preferred, new Intent(Settings.ACTION_SETTINGS));
    }
    private static void safeStart(Activity a, Intent preferred, Intent fallback) {
        try { a.startActivity(preferred); }
        catch (RuntimeException first) { try { a.startActivity(fallback); } catch (RuntimeException ignored) { } }
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
''')

(pkg / 'SmartSetupActivity.java').write_text(r'''package com.example.contactmasksafe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** Guided setup that automatically continues after Android's mandatory security screens. */
public class SmartSetupActivity extends Activity {
    private static final int REQ_CONTACTS = 701;
    private static final String PREFS = "contact_mask_setup";
    private static final String SKIP_NOTIFICATIONS = "skip_notifications";
    private TextView progress;
    private TextView detail;
    private Button continueButton;
    private boolean waitingForSettings;
    private boolean autoContinue;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppLockManager.requireUnlocked(this);
        if (isFinishing()) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_smart_setup);
        UiInsets.apply(this, findViewById(R.id.smartSetupRoot));
        progress = findViewById(R.id.smartSetupProgress);
        detail = findViewById(R.id.smartSetupDetail);
        continueButton = findViewById(R.id.btnSmartContinue);
        continueButton.setOnClickListener(v -> advance(true));
        findViewById(R.id.btnSmartCompatibility).setOnClickListener(v -> startActivity(new Intent(this, CompatibilityActivity.class)));
        findViewById(R.id.btnSmartResetOptional).setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(SKIP_NOTIFICATIONS, false).apply();
            refresh();
            Toast.makeText(this, "Optional notification step restored", Toast.LENGTH_SHORT).show();
        });
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        AppLockManager.requireUnlocked(this);
        if (isFinishing()) return;
        refresh();
        if (waitingForSettings) {
            waitingForSettings = false;
            autoContinue = true;
            continueButton.postDelayed(() -> { if (!isFinishing() && autoContinue) advance(false); }, 450L);
        }
    }

    @Override protected void onPause() { super.onPause(); autoContinue = false; }

    private void advance(boolean userPressed) {
        refresh();
        if (!hasContacts()) {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            return;
        }
        if (!isAccessibilityEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Step 2 of 3 — Screen protection")
                    .setMessage(DeviceSetupHelper.oemAccessibilityTip() + "\n\nAndroid requires you to switch this service on manually. ContactMask Safe cannot bypass this security confirmation.")
                    .setNegativeButton("Compatibility help", (d, w) -> startActivity(new Intent(this, CompatibilityActivity.class)))
                    .setPositiveButton("Open Accessibility", (d, w) -> { waitingForSettings = true; DeviceSetupHelper.openAccessibility(this); })
                    .show();
            return;
        }
        AppPreferences.setPrivacyShieldEnabled(this, true);
        boolean skipped = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SKIP_NOTIFICATIONS, false);
        if (!isNotificationListenerEnabled() && !skipped) {
            new AlertDialog.Builder(this)
                    .setTitle("Optional — Notification protection")
                    .setMessage("Core screen protection is already ready. Notification access can also redact saved numbers in supported notifications. This is optional.")
                    .setNegativeButton("Skip", (d, w) -> { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(SKIP_NOTIFICATIONS, true).apply(); refresh(); })
                    .setPositiveButton("Enable", (d, w) -> { waitingForSettings = true; DeviceSetupHelper.openNotificationAccess(this); })
                    .show();
            return;
        }
        refresh();
        if (userPressed) {
            Toast.makeText(this, "Setup complete — running protection self-test", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MaskTestActivity.class));
        }
    }

    private void refresh() {
        if (progress == null) return;
        boolean contacts = hasContacts();
        boolean access = isAccessibilityEnabled();
        boolean notifications = isNotificationListenerEnabled();
        boolean skipped = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SKIP_NOTIFICATIONS, false);
        int core = (contacts ? 1 : 0) + (access ? 1 : 0);
        if (contacts && access) AppPreferences.setPrivacyShieldEnabled(this, true);
        progress.setText(contacts && access ? "Core protection READY" : "Core setup " + core + "/2");
        StringBuilder b = new StringBuilder();
        b.append(contacts ? "✓ Contacts permission\n" : "1. Contacts permission required\n");
        b.append(access ? "✓ Accessibility screen protection\n" : "2. Enable Accessibility screen protection\n");
        b.append(notifications ? "✓ Notification protection\n" : (skipped ? "○ Notification protection skipped (optional)\n" : "3. Notification protection optional\n"));
        b.append("\n").append(DeviceSetupHelper.deviceLabel());
        if (Build.VERSION.SDK_INT >= 33 && !access) b.append("\n\nIf Accessibility is greyed out, use Compatibility Help → App info and check for Android's ‘Allow restricted settings’ option.");
        detail.setText(b.toString());
        if (!contacts) continueButton.setText("Start — Allow Contacts");
        else if (!access) continueButton.setText("Continue — Enable Screen Protection");
        else if (!notifications && !skipped) continueButton.setText("Continue — Optional Notifications");
        else continueButton.setText("Finish & Run Self-Test");
    }

    private boolean hasContacts() { return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED; }
    private boolean isAccessibilityEnabled() {
        ComponentName expected = new ComponentName(this, PrivacyAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        for (String value : enabled.split(":")) { ComponentName c = ComponentName.unflattenFromString(value); if (expected.equals(c)) return true; }
        return false;
    }
    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName expected = new ComponentName(this, NotificationMaskService.class);
        for (String value : enabled.split(":")) { ComponentName c = ComponentName.unflattenFromString(value); if (expected.equals(c)) return true; }
        return false;
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refresh();
        if (requestCode == REQ_CONTACTS && hasContacts()) continueButton.postDelayed(() -> advance(false), 250L);
        else if (requestCode == REQ_CONTACTS) Toast.makeText(this, "Contacts permission is required to know which numbers are saved.", Toast.LENGTH_LONG).show();
    }
}
''')

(pkg / 'CompatibilityActivity.java').write_text(r'''package com.example.contactmasksafe;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.TextView;

public class CompatibilityActivity extends Activity {
    private TextView status;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppLockManager.requireUnlocked(this);
        if (isFinishing()) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_compatibility);
        UiInsets.apply(this, findViewById(R.id.compatRoot));
        status = findViewById(R.id.compatStatus);
        ((TextView)findViewById(R.id.compatGuide)).setText(DeviceSetupHelper.compatibilitySummary());
        findViewById(R.id.btnCompatAccessibility).setOnClickListener(v -> DeviceSetupHelper.openAccessibility(this));
        findViewById(R.id.btnCompatNotifications).setOnClickListener(v -> DeviceSetupHelper.openNotificationAccess(this));
        findViewById(R.id.btnCompatAppInfo).setOnClickListener(v -> DeviceSetupHelper.openAppInfo(this));
        findViewById(R.id.btnCompatBattery).setOnClickListener(v -> DeviceSetupHelper.openBatterySettings(this));
        findViewById(R.id.btnCompatRefresh).setOnClickListener(v -> refresh());
        refresh();
    }
    @Override protected void onResume() { super.onResume(); AppLockManager.requireUnlocked(this); if (!isFinishing()) refresh(); }
    private void refresh() {
        if (status == null) return;
        boolean contacts = Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean access = isAccessibilityEnabled();
        boolean notices = isNotificationListenerEnabled();
        status.setText((contacts ? "✓" : "✗") + " Contacts permission\n" + (access ? "✓" : "✗") + " Accessibility screen protection\n" + (notices ? "✓" : "○") + " Notification protection (optional)\n" + (AppPreferences.isPrivacyShieldEnabled(this) ? "✓ Privacy Shield ON" : "○ Privacy Shield paused"));
    }
    private boolean isAccessibilityEnabled() {
        ComponentName expected = new ComponentName(this, PrivacyAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        for (String value : enabled.split(":")) { ComponentName c = ComponentName.unflattenFromString(value); if (expected.equals(c)) return true; }
        return false;
    }
    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName expected = new ComponentName(this, NotificationMaskService.class);
        for (String value : enabled.split(":")) { ComponentName c = ComponentName.unflattenFromString(value); if (expected.equals(c)) return true; }
        return false;
    }
}
''')

(res / 'layout/activity_smart_setup.xml').write_text(r'''<ScrollView xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/smartSetupRoot" android:layout_width="match_parent" android:layout_height="match_parent" android:background="#F5F7FB" android:fillViewport="true">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:padding="20dp">
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="One-Tap Smart Setup" android:textColor="#111827" android:textSize="28sp" android:textStyle="bold" />
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:text="The app automatically continues after each Android security screen. Android still requires you to approve sensitive access manually." android:textColor="#4B5563" android:textSize="15sp" />
<TextView android:id="@+id/smartSetupProgress" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="20dp" android:background="#E8F0FE" android:padding="14dp" android:text="Checking setup" android:textColor="#111827" android:textSize="19sp" android:textStyle="bold" />
<TextView android:id="@+id/smartSetupDetail" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:background="#FFFFFF" android:padding="14dp" android:text="Checking device" android:textColor="#374151" android:textSize="15sp" />
<Button android:id="@+id/btnSmartContinue" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="56dp" android:layout_marginTop="18dp" android:text="Continue" android:textAllCaps="false" android:textSize="16sp" android:textStyle="bold" />
<Button android:id="@+id/btnSmartCompatibility" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="50dp" android:layout_marginTop="8dp" android:text="Compatibility & Device Help" android:textAllCaps="false" />
<Button android:id="@+id/btnSmartResetOptional" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="50dp" android:layout_marginTop="8dp" android:text="Restore Optional Notification Step" android:textAllCaps="false" />
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="20dp" android:paddingBottom="28dp" android:text="Supported baseline: Android 5.1+ (API 22+). Protection quality still depends on whether the other app/OEM exposes the number through Android Accessibility." android:textColor="#6B7280" android:textSize="13sp" />
</LinearLayout></ScrollView>
''')

(res / 'layout/activity_compatibility.xml').write_text(r'''<ScrollView xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/compatRoot" android:layout_width="match_parent" android:layout_height="match_parent" android:background="#F5F7FB" android:fillViewport="true">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:padding="20dp">
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Compatibility & Device Help" android:textColor="#111827" android:textSize="26sp" android:textStyle="bold" />
<TextView android:id="@+id/compatStatus" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="16dp" android:background="#ECFDF5" android:padding="14dp" android:text="Checking" android:textColor="#1F2937" android:textSize="15sp" />
<TextView android:id="@+id/compatGuide" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:background="#FFFFFF" android:padding="14dp" android:textColor="#374151" android:textSize="14sp" />
<Button android:id="@+id/btnCompatAccessibility" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="50dp" android:layout_marginTop="14dp" android:text="Open Accessibility Settings" android:textAllCaps="false" />
<Button android:id="@+id/btnCompatAppInfo" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="50dp" android:layout_marginTop="8dp" android:text="Open App Info / Restricted Settings" android:textAllCaps="false" />
<Button android:id="@+id/btnCompatNotifications" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="50dp" android:layout_marginTop="8dp" android:text="Open Notification Access" android:textAllCaps="false" />
<Button android:id="@+id/btnCompatBattery" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="50dp" android:layout_marginTop="8dp" android:text="Open Battery / Background Settings" android:textAllCaps="false" />
<Button android:id="@+id/btnCompatRefresh" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="50dp" android:layout_marginTop="8dp" android:text="Refresh Status" android:textAllCaps="false" />
<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="18dp" android:paddingBottom="28dp" android:text="ContactMask Safe cannot bypass Android security confirmations, force-enable Accessibility, or guarantee masking in secure/private OEM windows that do not expose text to Accessibility." android:textColor="#991B1B" android:textSize="13sp" android:textStyle="bold" />
</LinearLayout></ScrollView>
''')

# The 3.1.2 service already contains persistent anti-blink and ScrollSafe. Keep that engine,
# but improve its reliability with faster health rescans and automatic saved-number refresh
# using source-level edits that are safe if the exact constants are present.
p = pkg / 'PrivacyAccessibilityService.java'
s = p.read_text()
s = s.replace('private static final long EMPTY_FRAME_HOLD_MS = 1400L;', 'private static final long EMPTY_FRAME_HOLD_MS = 1900L;')
s = s.replace('private static final long EMPTY_FRAME_HOLD_MS = 1800L;', 'private static final long EMPTY_FRAME_HOLD_MS = 1900L;')
# If the ScrollSafe source exposes scan delays, make them responsive without busy-looping.
s = s.replace('private static final long SCROLL_RESCAN_MS = 24L;', 'private static final long SCROLL_RESCAN_MS = 28L;')
p.write_text(s)

# Accessibility events should not be batched by Android.
p = res / 'xml/accessibility_service_config.xml'
s = p.read_text()
s = re.sub(r'android:notificationTimeout="[^"]*"', 'android:notificationTimeout="0"', s)
p.write_text(s)

# User-facing mask text must be consistent.
p = res / 'values/strings.xml'
p.write_text(p.read_text().replace('XXXXXXXXX', 'Masked'))

# Basic XML parse validation before Gradle.
import xml.etree.ElementTree as ET
for xml in res.rglob('*.xml'):
    ET.parse(xml)
ET.parse(root / 'app/src/main/AndroidManifest.xml')
print('Applied ContactMask Safe 3.2.0 Universal patch')
