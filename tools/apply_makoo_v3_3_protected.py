from pathlib import Path
import re

root = Path.cwd()
app = root / 'app'
pkg = app / 'src/main/java/com/example/contactmasksafe'
res = app / 'src/main/res'

# Version 3.3.0
p = app / 'build.gradle'
s = p.read_text()
s = re.sub(r'versionCode\s+\d+', 'versionCode 13', s)
s = re.sub(r"versionName\s+'[^']+'", "versionName '3.3.0'", s)
p.write_text(s)

# Persist One-Tap setup state across Settings/process recreation.
p = pkg / 'AppPreferences.java'
s = p.read_text()
if 'KEY_EASY_SETUP_ACTIVE' not in s:
    s = s.replace('    private static final String KEY_LAST_MASK_COUNT = "last_mask_count";', '    private static final String KEY_LAST_MASK_COUNT = "last_mask_count";\n    private static final String KEY_EASY_SETUP_ACTIVE = "easy_setup_active";\n    private static final String KEY_ACCESSIBILITY_ROUNDTRIP = "accessibility_roundtrip";')
    methods = '''\n    public static boolean isEasySetupActive(Context context) {\n        return preferences(context).getBoolean(KEY_EASY_SETUP_ACTIVE, false);\n    }\n\n    public static void setEasySetupActive(Context context, boolean active) {\n        preferences(context).edit().putBoolean(KEY_EASY_SETUP_ACTIVE, active).apply();\n    }\n\n    public static boolean wasAccessibilityRoundTripStarted(Context context) {\n        return preferences(context).getBoolean(KEY_ACCESSIBILITY_ROUNDTRIP, false);\n    }\n\n    public static void setAccessibilityRoundTripStarted(Context context, boolean started) {\n        preferences(context).edit().putBoolean(KEY_ACCESSIBILITY_ROUNDTRIP, started).apply();\n    }\n'''
    s = s.replace('    private static SharedPreferences preferences(Context context) {', methods + '\n    private static SharedPreferences preferences(Context context) {')
p.write_text(s)

# Main activity: persistent guided setup and status.
p = pkg / 'MainActivity.java'
s = p.read_text()
if 'AppPreferences.isEasySetupActive(this)' not in s:
    s = s.replace('        masterSwitch = findViewById(R.id.masterSwitch);', '        masterSwitch = findViewById(R.id.masterSwitch);\n        easySetupActive = AppPreferences.isEasySetupActive(this);\n        accessibilitySettingsOpened = AppPreferences.wasAccessibilityRoundTripStarted(this);')
    s = s.replace('''        if (easySetupActive && accessibilitySettingsOpened) {\n            accessibilitySettingsOpened = false;\n            if (hasContacts() && isAccessibilityEnabled()) finishEasySetup();\n        }''', '''        if (easySetupActive) {\n            if (hasContacts() && isAccessibilityEnabled()) {\n                finishEasySetup();\n            } else if (accessibilitySettingsOpened) {\n                accessibilitySettingsOpened = false;\n                AppPreferences.setAccessibilityRoundTripStarted(this, false);\n                updateStatus();\n            }\n        }''')
    s = s.replace('        easySetupActive = true;\n        AppPreferences.setPrivacyShieldEnabled(this, true);', '        easySetupActive = true;\n        AppPreferences.setEasySetupActive(this, true);\n        AppPreferences.setPrivacyShieldEnabled(this, true);', 1)
    s = s.replace('        easySetupActive = false;\n        accessibilitySettingsOpened = false;', '        easySetupActive = false;\n        accessibilitySettingsOpened = false;\n        AppPreferences.setEasySetupActive(this, false);\n        AppPreferences.setAccessibilityRoundTripStarted(this, false);', 1)
    s = s.replace('        accessibilitySettingsOpened = true;\n        AppLockManager.beginTrustedSettingsRoundTrip();', '        accessibilitySettingsOpened = true;\n        if (easySetupActive) {\n            AppPreferences.setEasySetupActive(this, true);\n            AppPreferences.setAccessibilityRoundTripStarted(this, true);\n        }\n        AppLockManager.beginTrustedSettingsRoundTrip();')
    s = s.replace('                easySetupActive = false;\n                Toast.makeText(this, "Contacts permission is required to recognise saved numbers.", Toast.LENGTH_LONG).show();', '                easySetupActive = false;\n                AppPreferences.setEasySetupActive(this, false);\n                AppPreferences.setAccessibilityRoundTripStarted(this, false);\n                Toast.makeText(this, "Contacts permission is required to recognise saved numbers.", Toast.LENGTH_LONG).show();')
s = s.replace('"Setup complete. Saved contact numbers will show as Masked on supported screens."', '"Setup complete. Makoo is ready: saved numbers are masked, and opened contact-number screens are protected."')
if 'Protected Contact Screen: ON' not in s:
    s = s.replace('+ "\\nInternet permission: NOT REQUESTED"', '+ "\\nProtected Contact Screen: ON"\n                + "\\nInternet permission: NOT REQUESTED"')
p.write_text(s)

# Main UI: logo + guided setup wording.
p = res / 'layout/activity_main.xml'
s = p.read_text()
if 'android:text="Makoo 3.3"' not in s:
    old = '<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Makoo 3.1" android:textColor="#111827" android:textSize="29sp" android:textStyle="bold" />'
    new = '''<ImageView\n            android:layout_width="96dp"\n            android:layout_height="96dp"\n            android:layout_gravity="center_horizontal"\n            android:adjustViewBounds="true"\n            android:contentDescription="@string/logo_content_description"\n            android:scaleType="fitCenter"\n            android:src="@drawable/makoo_logo" />\n        <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:text="Makoo 3.3" android:textColor="#111827" android:textSize="29sp" android:textStyle="bold" />'''
    if old not in s:
        raise SystemExit('main title insertion point missing')
    s = s.replace(old, new, 1)
s = s.replace('One-Tap Setup starts the guided setup. Android will still show its own Contacts and Accessibility confirmation screens; those security taps cannot be bypassed.', 'Tap One-Tap Guided Setup once. Makoo will automatically move through every required app step and open the next Android settings screen for you. Android still requires you to approve its own Contacts and Accessibility security confirmations.')
s = s.replace('android:text="One-Tap Setup"', 'android:text="One-Tap Guided Setup"')
s = s.replace('Privacy: Makoo does not request Internet access and does not edit, delete or upload contacts. It indexes saved phone numbers locally and masks supported visible saved numbers with the word Masked. Unsaved numbers remain visible.', 'Privacy: Makoo does not request Internet access and does not edit, delete or upload contacts. Saved numbers are masked in lists. When a saved phone number is opened or focused for viewing/editing in a supported app, Makoo covers the entire display with a black Protected Contact Screen, your logo, and a Go Back button.')
p.write_text(s)

# Unlock UI logo.
p = res / 'layout/activity_unlock.xml'
s = p.read_text()
if '@drawable/makoo_logo' not in s:
    old = '<TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="56dp" android:text="Makoo" android:textAlignment="center" android:textColor="#111827" android:textSize="30sp" android:textStyle="bold" />'
    new = '<ImageView android:layout_width="112dp" android:layout_height="112dp" android:layout_marginTop="40dp" android:contentDescription="@string/logo_content_description" android:scaleType="fitCenter" android:src="@drawable/makoo_logo" />\n        <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="Makoo" android:textAlignment="center" android:textColor="#111827" android:textSize="30sp" android:textStyle="bold" />'
    if old in s:
        s = s.replace(old, new, 1)
p.write_text(s)

# Accessibility disclosure.
p = res / 'values/strings.xml'
s = p.read_text()
s = s.replace('Masks saved contact phone numbers with the word Masked on supported screens and can replace copied saved numbers with Masked. Processing stays on-device and no data is sent to the Internet.', 'Masks saved contact phone numbers and, when a saved number is opened or focused for viewing/editing on a supported screen, can cover the entire screen with a black Makoo privacy screen and Go Back control. Processing stays on-device and no data is sent to the Internet.')
p.write_text(s)

# Protected Contact Screen engine.
p = pkg / 'PrivacyAccessibilityService.java'
s = p.read_text()
if 'CONTACT_GATE_ARM_MS' not in s:
    s = s.replace('import android.view.WindowManager;\n', 'import android.view.WindowManager;\nimport android.widget.Button;\nimport android.widget.FrameLayout;\nimport android.widget.ImageView;\nimport android.widget.LinearLayout;\nimport android.widget.TextView;\n')
    s = s.replace('    private static final long SCROLL_RESCAN_MS = 24L;\n', '    private static final long SCROLL_RESCAN_MS = 24L;\n    private static final long CONTACT_GATE_ARM_MS = 2400L;\n    private static final long CONTACT_GATE_MIN_SHOW_MS = 350L;\n    private static final long CONTACT_GATE_SUPPRESS_AFTER_BACK_MS = 1400L;\n')
    s = s.replace('    private String foregroundPackage = "";\n', '    private String foregroundPackage = "";\n\n    private View contactGateView;\n    private boolean contactGateAttached;\n    private long contactGateArmedUntil;\n    private long contactGateShownAt;\n    private long contactGateSuppressUntil;\n')

    event_old = '''        boolean windowChanged = event != null\n                && (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED\n                || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED);\n\n        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {'''
    event_new = '''        boolean windowChanged = event != null\n                && (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED\n                || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED);\n\n        long eventNow = SystemClock.uptimeMillis();\n        boolean ownPackage = isOwnPackage(foregroundPackage);\n        boolean userOpenOrEditIntent = eventType == AccessibilityEvent.TYPE_VIEW_CLICKED\n                || eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED\n                || eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED\n                || eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED\n                || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED\n                || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED;\n        if (!ownPackage && userOpenOrEditIntent) {\n            contactGateArmedUntil = eventNow + CONTACT_GATE_ARM_MS;\n        }\n        if (contactGateAttached && windowChanged\n                && eventNow - contactGateShownAt >= CONTACT_GATE_MIN_SHOW_MS) {\n            hideContactGate();\n            contactGateSuppressUntil = eventNow + 650L;\n        }\n\n        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {'''
    if event_old not in s:
        raise SystemExit('service event insertion point missing')
    s = s.replace(event_old, event_new, 1)

    s = s.replace('        List<MaskRegion> fastMasks = new ArrayList<>();\n        Set<String> keys = new HashSet<>();\n        try {\n            collectMasks(source, fastMasks, keys, FAST_EVENT_NODES);', '        List<MaskRegion> fastMasks = new ArrayList<>();\n        Set<String> keys = new HashSet<>();\n        ScanSignals fastSignals = new ScanSignals();\n        try {\n            collectMasks(source, fastMasks, keys, FAST_EVENT_NODES, fastSignals);', 1)
    s = s.replace('''        if (!fastMasks.isEmpty()) {\n            mergeFastMasks(fastMasks);\n            lastConfirmedMaskAt = SystemClock.uptimeMillis();\n            endTransitionCurtain();\n            render();\n        }\n    }''', '''        if (!fastMasks.isEmpty()) {\n            mergeFastMasks(fastMasks);\n            long now = SystemClock.uptimeMillis();\n            lastConfirmedMaskAt = now;\n            endTransitionCurtain();\n            render();\n            if (fastSignals.editableSensitive && !isOwnPackage(foregroundPackage)\n                    && now >= contactGateSuppressUntil) {\n                showContactGate();\n            }\n        }\n    }''', 1)

    s = s.replace('        Set<String> keys = new HashSet<>();\n        int remainingNodes = MAX_NODES;', '        Set<String> keys = new HashSet<>();\n        ScanSignals signals = new ScanSignals();\n        int remainingNodes = MAX_NODES;', 1)
    s = s.replace('int visited = collectMasks(root, found, keys, remainingNodes);', 'int visited = collectMasks(root, found, keys, remainingNodes, signals);', 1)
    s = s.replace('if (root != null) collectMasks(root, found, keys, remainingNodes);', 'if (root != null) collectMasks(root, found, keys, remainingNodes, signals);', 1)
    s = s.replace('''        } else if (!stableMasks.isEmpty() && now - lastConfirmedMaskAt > EMPTY_FRAME_HOLD_MS) {\n            // Keep the last confirmed mask through short OEM/data-binding empty frames.\n            stableMasks.clear();\n        }\n\n        render();''', '''        } else if (!stableMasks.isEmpty() && now - lastConfirmedMaskAt > EMPTY_FRAME_HOLD_MS) {\n            // Keep the last confirmed mask through short OEM/data-binding empty frames.\n            stableMasks.clear();\n        }\n\n        boolean sensitiveVisible = !found.isEmpty();\n        boolean shouldOpenGate = sensitiveVisible\n                && !isOwnPackage(foregroundPackage)\n                && now >= contactGateSuppressUntil\n                && (signals.editableSensitive || now <= contactGateArmedUntil);\n        if (contactGateAttached) {\n            if (!sensitiveVisible || isOwnPackage(foregroundPackage)) hideContactGate();\n        } else if (shouldOpenGate) {\n            showContactGate();\n        }\n\n        render();''', 1)
    s = s.replace('    private int collectMasks(AccessibilityNodeInfo root,\n                             List<MaskRegion> output,\n                             Set<String> keys,\n                             int nodeLimit) {', '    private int collectMasks(AccessibilityNodeInfo root,\n                             List<MaskRegion> output,\n                             Set<String> keys,\n                             int nodeLimit,\n                             ScanSignals signals) {', 1)
    s = s.replace('''                if (sensitive) {\n                    Rect bounds = new Rect();''', '''                if (sensitive) {\n                    if (signals != null) {\n                        signals.sensitiveFound = true;\n                        if (node.isEditable()) signals.editableSensitive = true;\n                    }\n                    Rect bounds = new Rect();''', 1)

    gate_marker = '    private void ensureOverlayAttached() {'
    gate_code = r'''    private boolean isOwnPackage(String packageName) {
        return packageName != null && packageName.equals(getPackageName());
    }

    private void showContactGate() {
        if (contactGateAttached || windowManager == null) return;
        if (contactGateView == null) contactGateView = buildContactGateView();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            windowManager.addView(contactGateView, lp);
            contactGateAttached = true;
            contactGateShownAt = SystemClock.uptimeMillis();
            contactGateArmedUntil = 0L;
        } catch (RuntimeException ignored) { contactGateAttached = false; }
    }

    private View buildContactGateView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClickable(true);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.makoo_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(logo, new LinearLayout.LayoutParams(dp(170), dp(170)));
        TextView title = new TextView(this);
        title.setText("Protected Contact Screen"); title.setTextColor(Color.WHITE); title.setTextSize(22f); title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); titleLp.topMargin = dp(24); content.addView(title, titleLp);
        TextView message = new TextView(this);
        message.setText("Makoo is hiding this saved contact number. Use Go Back to return without revealing it."); message.setTextColor(Color.LTGRAY); message.setTextSize(15f); message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); messageLp.topMargin = dp(10); content.addView(message, messageLp);
        Button back = new Button(this); back.setText("Go Back"); back.setTextSize(18f); back.setTextColor(Color.BLACK); back.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)); backLp.topMargin = dp(34); backLp.leftMargin = dp(22); backLp.rightMargin = dp(22); content.addView(back, backLp);
        back.setOnClickListener(v -> {
            long now = SystemClock.uptimeMillis();
            contactGateSuppressUntil = now + CONTACT_GATE_SUPPRESS_AFTER_BACK_MS;
            contactGateArmedUntil = 0L;
            hideContactGate();
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(followUpScan1, 80L);
            handler.postDelayed(followUpScan2, 300L);
        });
        TextView credit = new TextView(this); credit.setText("Designed and Developed by UCPL Technologies"); credit.setTextColor(Color.GRAY); credit.setTextSize(13f); credit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams creditLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); creditLp.topMargin = dp(28); content.addView(credit, creditLp);
        return root;
    }

    private void hideContactGate() {
        if (contactGateAttached && windowManager != null && contactGateView != null) {
            try { windowManager.removeViewImmediate(contactGateView); } catch (RuntimeException ignored) { }
        }
        contactGateAttached = false;
        contactGateShownAt = 0L;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

'''
    if gate_marker not in s:
        raise SystemExit('gate insertion point missing')
    s = s.replace(gate_marker, gate_code + gate_marker, 1)
    s = s.replace('    private void detachOverlay() {\n        stableMasks.clear();', '    private void detachOverlay() {\n        hideContactGate();\n        contactGateArmedUntil = 0L;\n        contactGateSuppressUntil = 0L;\n        stableMasks.clear();', 1)
    s = s.replace('    private static final class MaskRegion {', '    private static final class ScanSignals {\n        boolean sensitiveFound;\n        boolean editableSensitive;\n    }\n\n    private static final class MaskRegion {', 1)
p.write_text(s)

(root / 'MAKOO_3_3_PROTECTED_CONTACT_NOTES.txt').write_text('Makoo 3.3.0 Protected Contact Screen\n- Saved numbers stay Masked in lists.\n- Opening/focusing a saved number can trigger a full black accessibility privacy screen.\n- Black screen shows the Makoo logo and Go Back control.\n- Cross-app best effort: works where Android Accessibility exposes the relevant text.\n- One-Tap Guided Setup persists across Android Settings round-trips.\n- Android still requires the user to approve system permission/accessibility confirmations.\n')
print('Makoo 3.3 Protected Contact Screen applied')
