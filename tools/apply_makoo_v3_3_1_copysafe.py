from pathlib import Path

root = Path.cwd()
svc = root / 'app/src/main/java/com/example/contactmasksafe/PrivacyAccessibilityService.java'
main = root / 'app/src/main/java/com/example/contactmasksafe/MainActivity.java'
layout = root / 'app/src/main/res/layout/activity_main.xml'
gradle = root / 'app/build.gradle'


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit('CopySafe marker missing: ' + label)
    return text.replace(old, new, 1)

# Version
s = gradle.read_text()
s = s.replace('versionCode 13', 'versionCode 14').replace("versionName '3.3.0'", "versionName '3.3.1'")
gradle.write_text(s)

# Clipboard / CopySafe engine
s = svc.read_text()
s = replace_once(s,
    'import android.accessibilityservice.AccessibilityServiceInfo;\n',
    'import android.accessibilityservice.AccessibilityServiceInfo;\nimport android.content.ClipData;\nimport android.content.ClipboardManager;\n',
    'clipboard imports')

s = replace_once(s,
    '    private static final long CONTACT_GATE_SUPPRESS_AFTER_BACK_MS = 1400L;\n',
    '    private static final long CONTACT_GATE_SUPPRESS_AFTER_BACK_MS = 1400L;\n'
    '    private static final long COPY_CONTEXT_ARM_MS = 5000L;\n'
    '    private static final long COPY_CLIPBOARD_GUARD_MS = 900L;\n',
    'copy constants')

s = replace_once(s,
    '    private String foregroundPackage = "";\n\n    private View contactGateView;\n',
    '    private String foregroundPackage = "";\n\n'
    '    private ClipboardManager clipboardManager;\n'
    '    private long copyProtectionArmedUntil;\n'
    '    private long ignoreClipboardEventsUntil;\n\n'
    '    private View contactGateView;\n',
    'clipboard fields')

s = replace_once(s,
    '    private long contactGateSuppressUntil;\n\n    private final Runnable scanRunnable',
    '''    private long contactGateSuppressUntil;
    private boolean contactGateCopyMode;
    private TextView contactGateTitleView;
    private TextView contactGateMessageView;
    private TextView contactGateCreditView;

    private final ClipboardManager.OnPrimaryClipChangedListener clipboardListener = new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
            long now = SystemClock.uptimeMillis();
            if (now < ignoreClipboardEventsUntil) return;
            if (now <= copyProtectionArmedUntil) protectCopiedNumber();
        }
    };

    private final Runnable copyMaskRetry1 = new Runnable() { @Override public void run() { writeMaskedClipboard(); } };
    private final Runnable copyMaskRetry2 = new Runnable() { @Override public void run() { writeMaskedClipboard(); } };
    private final Runnable copyMaskRetry3 = new Runnable() { @Override public void run() { writeMaskedClipboard(); } };

    private final Runnable scanRunnable''',
    'clipboard listener')

s = replace_once(s,
    '        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);\n\n        AccessibilityServiceInfo info',
    '''        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            try { clipboardManager.addPrimaryClipChangedListener(clipboardListener); }
            catch (RuntimeException ignored) { }
        }

        AccessibilityServiceInfo info''',
    'clipboard startup')

s = replace_once(s,
    '        // Fast path: mask directly from the incoming event source before the broader scan.\n        promoteMasksFromEvent(event);',
    '        handleCopyProtectionEvent(event);\n\n        // Fast path: mask directly from the incoming event source before the broader scan.\n        promoteMasksFromEvent(event);',
    'copy event hook')

# Existing full-screen gate calls are normal view/edit mode.
s = s.replace('showContactGate();', 'showContactGate(false);')

insert_marker = '    private void beginScrollGuard(AccessibilityEvent event) {'
if insert_marker not in s:
    raise SystemExit('CopySafe method insertion marker missing')
copy_methods = r'''    private void handleCopyProtectionEvent(AccessibilityEvent event) {
        if (event == null || repository == null || isOwnPackage(foregroundPackage)) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) return;

        long now = SystemClock.uptimeMillis();
        boolean sourceSensitive = eventSourceContainsSavedNumber(event);
        boolean nearSensitive = isEventNearStableMask(event);
        boolean copyAction = isCopyAction(event);

        if (sourceSensitive || nearSensitive) {
            copyProtectionArmedUntil = now + COPY_CONTEXT_ARM_MS;
        }

        if (copyAction && (sourceSensitive || nearSensitive || !stableMasks.isEmpty()
                || now <= copyProtectionArmedUntil)) {
            copyProtectionArmedUntil = now + COPY_CONTEXT_ARM_MS;
            protectCopiedNumber();
        }
    }

    private boolean eventSourceContainsSavedNumber(AccessibilityEvent event) {
        if (event == null || repository == null) return false;
        for (CharSequence value : event.getText()) {
            if (PhoneMasker.containsSavedNumber(value, repository)) return true;
        }
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return false;
        try {
            if (PhoneMasker.containsSavedNumber(source.getText(), repository)) return true;
            if (PhoneMasker.containsSavedNumber(source.getContentDescription(), repository)) return true;
            if (Build.VERSION.SDK_INT >= 26
                    && PhoneMasker.containsSavedNumber(source.getHintText(), repository)) return true;
            return false;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            try { source.recycle(); } catch (RuntimeException ignored) { }
        }
    }

    private boolean isEventNearStableMask(AccessibilityEvent event) {
        if (event == null || stableMasks.isEmpty()) return false;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return false;
        try {
            Rect clicked = new Rect();
            source.getBoundsInScreen(clicked);
            if (clicked.isEmpty()) return false;
            int maxDistance = dp(180);
            for (MaskRegion region : stableMasks) {
                Rect b = region.bounds;
                int dx = Math.max(0, Math.max(b.left - clicked.right, clicked.left - b.right));
                int dy = Math.max(0, Math.max(b.top - clicked.bottom, clicked.top - b.bottom));
                if (dx <= maxDistance && dy <= dp(96)) return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            try { source.recycle(); } catch (RuntimeException ignored) { }
        }
        return false;
    }

    private boolean isCopyAction(AccessibilityEvent event) {
        if (event == null) return false;
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            try {
                if (matchesCopyLabel(source.getText()) || matchesCopyLabel(source.getContentDescription())) return true;
                String viewId = source.getViewIdResourceName();
                if (viewId != null) {
                    String id = viewId.toLowerCase(Locale.ROOT);
                    if (id.contains("copy") || id.contains("clipboard")) return true;
                }
            } catch (RuntimeException ignored) {
            } finally {
                try { source.recycle(); } catch (RuntimeException ignored) { }
            }
        }
        for (CharSequence value : event.getText()) {
            if (matchesCopyLabel(value)) return true;
        }
        return false;
    }

    private boolean matchesCopyLabel(CharSequence text) {
        if (text == null) return false;
        String value = text.toString().trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.contains("copyright")) return false;
        String[] terms = new String[]{
                "copy", "clipboard", "कॉपी", "प्रतिलिपि", "copiar", "copier", "kopieren",
                "copia", "копир", "نسخ", "复制", "複製", "コピー", "복사", "kopya",
                "salin", "sao chép", "คัดลอก"
        };
        for (String term : terms) {
            if (value.equals(term) || value.startsWith(term + " ") || value.endsWith(" " + term)
                    || value.contains(" " + term + " ") || value.contains(term + ":")) return true;
        }
        return false;
    }

    private void protectCopiedNumber() {
        copyProtectionArmedUntil = SystemClock.uptimeMillis() + COPY_CONTEXT_ARM_MS;
        writeMaskedClipboard();
        handler.removeCallbacks(copyMaskRetry1);
        handler.removeCallbacks(copyMaskRetry2);
        handler.removeCallbacks(copyMaskRetry3);
        handler.postDelayed(copyMaskRetry1, 45L);
        handler.postDelayed(copyMaskRetry2, 160L);
        handler.postDelayed(copyMaskRetry3, 520L);
        showContactGate(true);
    }

    private void writeMaskedClipboard() {
        if (clipboardManager == null) return;
        try {
            ignoreClipboardEventsUntil = SystemClock.uptimeMillis() + COPY_CLIPBOARD_GUARD_MS;
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Makoo", PhoneMasker.MASK));
        } catch (SecurityException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

'''
s = s.replace(insert_marker, copy_methods + insert_marker, 1)

s = replace_once(s,
    '    private void showContactGate() {\n        if (contactGateAttached || windowManager == null) return;\n        if (contactGateView == null) contactGateView = buildContactGateView();',
    '''    private void showContactGate(boolean copyMode) {
        if (windowManager == null) return;
        contactGateCopyMode = copyMode;
        if (contactGateView == null) contactGateView = buildContactGateView();
        configureContactGate(copyMode);
        if (contactGateAttached) return;''',
    'gate signature')

s = replace_once(s,
    '        TextView title = new TextView(this);\n        title.setText("Protected Contact Screen");',
    '        TextView title = new TextView(this);\n        contactGateTitleView = title;\n        title.setText("Protected Contact Screen");',
    'gate title ref')
s = replace_once(s,
    '        TextView message = new TextView(this);\n        message.setText("Makoo is hiding this saved contact number. Use Go Back to return without revealing it.");',
    '        TextView message = new TextView(this);\n        contactGateMessageView = message;\n        message.setText("Makoo is hiding this saved contact number. Use Go Back to return without revealing it.");',
    'gate message ref')
s = replace_once(s,
    '        TextView credit = new TextView(this); credit.setText("Designed and Developed by UCPL Technologies");',
    '        TextView credit = new TextView(this); contactGateCreditView = credit; credit.setText("Designed and Developed by UCPL Technologies");',
    'gate credit ref')
s = replace_once(s,
    '            long now = SystemClock.uptimeMillis();\n            contactGateSuppressUntil = now + CONTACT_GATE_SUPPRESS_AFTER_BACK_MS;',
    '            long now = SystemClock.uptimeMillis();\n            if (contactGateCopyMode) writeMaskedClipboard();\n            contactGateSuppressUntil = now + CONTACT_GATE_SUPPRESS_AFTER_BACK_MS;',
    'gate back CopySafe')

hide_marker = '    private void hideContactGate() {'
if hide_marker not in s:
    raise SystemExit('CopySafe gate configuration marker missing')
s = s.replace(hide_marker, r'''    private void configureContactGate(boolean copyMode) {
        if (contactGateTitleView == null || contactGateMessageView == null || contactGateCreditView == null) return;
        if (copyMode) {
            contactGateTitleView.setVisibility(View.GONE);
            contactGateMessageView.setVisibility(View.GONE);
            contactGateCreditView.setVisibility(View.GONE);
        } else {
            contactGateTitleView.setVisibility(View.VISIBLE);
            contactGateMessageView.setVisibility(View.VISIBLE);
            contactGateCreditView.setVisibility(View.VISIBLE);
        }
    }

''' + hide_marker, 1)

s = replace_once(s,
    '        contactGateAttached = false;\n        contactGateShownAt = 0L;\n',
    '        contactGateAttached = false;\n        contactGateShownAt = 0L;\n        contactGateCopyMode = false;\n',
    'gate reset')
s = replace_once(s,
    '        contactGateSuppressUntil = 0L;\n        stableMasks.clear();',
    '        contactGateSuppressUntil = 0L;\n        copyProtectionArmedUntil = 0L;\n        ignoreClipboardEventsUntil = 0L;\n        stableMasks.clear();',
    'copy reset')
s = replace_once(s,
    '        handler.removeCallbacks(rapidScrollScanRunnable);\n        if (overlayAttached',
    '        handler.removeCallbacks(rapidScrollScanRunnable);\n        handler.removeCallbacks(copyMaskRetry1);\n        handler.removeCallbacks(copyMaskRetry2);\n        handler.removeCallbacks(copyMaskRetry3);\n        if (overlayAttached',
    'copy retry cleanup')
s = replace_once(s,
    '    public void onDestroy() {\n        handler.removeCallbacksAndMessages(null);\n        detachOverlay();',
    '''    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (clipboardManager != null) {
            try { clipboardManager.removePrimaryClipChangedListener(clipboardListener); }
            catch (RuntimeException ignored) { }
        }
        detachOverlay();''',
    'clipboard listener cleanup')

svc.write_text(s)

# UI diagnostics / setup completion
s = main.read_text()
s = s.replace(
    'Setup complete. Makoo is ready: saved numbers are masked, and opened contact-number screens are protected.',
    'Setup complete. Makoo is ready: saved numbers are masked, copied numbers become Masked, and opened contact-number screens are protected.')
s = s.replace(
    '                + "\\nProtected Contact Screen: ON"\n',
    '                + "\\nProtected Contact Screen: ON"\n                + "\\nCopySafe clipboard protection: ON • copies Masked"\n')
main.write_text(s)

s = layout.read_text()
s = s.replace('Makoo 3.3</', 'Makoo 3.3.1</')
s = s.replace(
    'Saved numbers are masked in lists. When a saved phone number is opened or focused for viewing/editing in a supported app, Makoo covers the entire display with a black Protected Contact Screen, your logo, and a Go Back button.',
    'Saved numbers are masked in lists. CopySafe replaces copied saved phone numbers with the text Masked. When Copy is used, Makoo blacks the display and shows only the Makoo logo and Go Back. Viewing/editing a saved phone number remains protected by the full-screen privacy gate.')
layout.write_text(s)

(root / 'MAKOO_3_3_1_COPYSAFE_NOTES.txt').write_text('''Makoo 3.3.1 CopySafe
- Copied saved contact numbers are overwritten with the text Masked.
- Copy action shows a full black Accessibility overlay with only Makoo logo + Go Back.
- Multi-delay clipboard reassertion handles OEM apps that write clipboard after click events.
- Clipboard-change guard catches unlabeled OEM copy icons when a saved-number context was armed.
- Existing Anti-Blink, ScrollSafe, Protected Contact Screen, password protection and One-Tap Guided Setup retained.
- Android security confirmations still require user approval; no ordinary APK can silently enable Accessibility.
''')

print('Applied Makoo 3.3.1 CopySafe')
