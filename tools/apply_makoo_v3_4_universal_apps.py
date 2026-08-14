from pathlib import Path
import re, sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
app = root/'app'
java = app/'src/main/java/com/example/contactmasksafe'

p = java/'SavedNumberRepository.java'
s = p.read_text()
s = s.replace('    private volatile Map<String, String> namesByKey = Collections.emptyMap();\n',
'''    private volatile Map<String, String> namesByKey = Collections.emptyMap();
    private volatile Set<String> savedContactNames = Collections.emptySet();
''')
s = s.replace('            HashMap<String, String> names = new HashMap<>();\n',
'''            HashMap<String, String> names = new HashMap<>();
            HashSet<String> contactNames = new HashSet<>();
''')
s = s.replace('                            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;\n',
'''                            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                            String normalizedName = normalizeContactName(name);
                            if (normalizedName.length() >= 2) contactNames.add(normalizedName);
''')
s = s.replace('            namesByKey = Collections.unmodifiableMap(names);\n',
'''            namesByKey = Collections.unmodifiableMap(names);
            savedContactNames = Collections.unmodifiableSet(contactNames);
''')
insert = '''
    public boolean isSavedContactName(CharSequence candidate) {
        ensureFresh();
        String normalized = normalizeContactName(candidate == null ? null : candidate.toString());
        return normalized.length() >= 2 && savedContactNames.contains(normalized);
    }

    private static String normalizeContactName(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        boolean space = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
                space = false;
            } else if (Character.isWhitespace(c) || c == '-' || c == '_' || c == '.') {
                if (out.length() > 0 && !space) { out.append(' '); space = true; }
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') end--;
        return out.substring(0, end);
    }
'''
s = s.replace('    private void ensureFresh() {\n', insert + '\n    private void ensureFresh() {\n')
p.write_text(s)

p = java/'PhoneMasker.java'
s = p.read_text()
s = s.replace('\\\\p{Nd}\\\\s\\\\u00A0().\\\\-–—/\\\\u200E\\\\u200F\\\\u202A-\\\\u202E',
              '\\\\p{Nd}\\\\s\\\\u00A0\\\\u202F().\\\\-–—/·•\\\\u200E\\\\u200F\\\\u202A-\\\\u202E\\\\u2066-\\\\u2069')
old = '''        while (matcher.find()) {
            String candidate = matcher.group();
            int digitCount = digitsOnly(candidate).length();
            if (digitCount >= 7 && digitCount <= 16 && repository.isSavedNumber(candidate)) return true;
        }
        return false;
'''
new = '''        while (matcher.find()) {
            String candidate = matcher.group();
            int digitCount = digitsOnly(candidate).length();
            if (digitCount >= 7 && digitCount <= 16 && repository.isSavedNumber(candidate)) return true;
        }
        String raw = text.toString().trim();
        String digits = digitsOnly(raw);
        if (digits.length() >= 7 && digits.length() <= 16 && raw.length() <= 48) {
            return repository.isSavedNumber(raw);
        }
        return false;
'''
if old in s: s = s.replace(old, new, 1)
p.write_text(s)

p = java/'PrivacyAccessibilityService.java'
s = p.read_text()
anchor = '    private static final long COPY_CLIPBOARD_GUARD_MS = 900L;\n'
if 'UNIVERSAL_CONTACT_CONTEXT_MS' not in s:
    s = s.replace(anchor, anchor + '''    private static final long UNIVERSAL_CONTACT_CONTEXT_MS = 6500L;
    private static final int AGGREGATE_DEPTH = 2;
    private static final int AGGREGATE_NODE_LIMIT = 18;
    private static final int AGGREGATE_CHAR_LIMIT = 260;
    private static final long COPY_GUARD_TICK_MS = 120L;
''')
anchor = '    private long ignoreClipboardEventsUntil;\n'
if 'universalContactContextUntil' not in s:
    s = s.replace(anchor, anchor + '''    private long universalContactContextUntil;
    private long copyGuardStrongUntil;
''')
anchor = '    private final Runnable copyMaskRetry3 = new Runnable() { @Override public void run() { writeMaskedClipboard(); } };\n'
if 'copyClipboardGuardRunnable' not in s:
    s = s.replace(anchor, anchor + '''    private final Runnable copyClipboardGuardRunnable = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            if (now > copyProtectionArmedUntil || now > copyGuardStrongUntil) return;
            writeMaskedClipboard();
            handler.postDelayed(this, COPY_GUARD_TICK_MS);
        }
    };
''')
needle = '        AppPreferences.recordServiceConnected(this);\n'
if 'TYPE_VIEW_SELECTED' not in s[s.find('protected void onServiceConnected'):s.find('public void onAccessibilityEvent')]:
    s = s.replace(needle, '''        try {
            AccessibilityServiceInfo universalInfo = getServiceInfo();
            if (universalInfo != null) {
                universalInfo.eventTypes |= AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                        | AccessibilityEvent.TYPE_VIEW_SELECTED
                        | AccessibilityEvent.TYPE_ANNOUNCEMENT;
                if (Build.VERSION.SDK_INT >= 23) universalInfo.eventTypes |= AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED;
                setServiceInfo(universalInfo);
            }
        } catch (RuntimeException ignored) { }

''' + needle, 1)
needle = '''        if (!ownPackage && userOpenOrEditIntent) {
            contactGateArmedUntil = eventNow + CONTACT_GATE_ARM_MS;
        }
'''
if 'handleUniversalContactContextEvent(event, eventNow)' not in s:
    s = s.replace(needle, needle + '        if (!ownPackage) handleUniversalContactContextEvent(event, eventNow);\n', 1)
s = s.replace('''        if ((packageChanged || windowChanged) && isPhoneOrContactsPackage(foregroundPackage)) {
            beginTransitionCurtain();
        }
''', '''        if ((packageChanged || windowChanged)
                && (isPhoneOrContactsPackage(foregroundPackage)
                || (isKnownContactAppPackage(foregroundPackage)
                && eventNow <= universalContactContextUntil))) {
            beginTransitionCurtain();
        }
''')
old = '''        boolean sensitiveVisible = !found.isEmpty();
        boolean shouldOpenGate = sensitiveVisible
                && !isOwnPackage(foregroundPackage)
                && now >= contactGateSuppressUntil
                && (signals.editableSensitive || now <= contactGateArmedUntil);
        if (contactGateAttached) {
            if (!sensitiveVisible || isOwnPackage(foregroundPackage)) hideContactGate();
        } else if (shouldOpenGate) {
            showContactGate(false);
        }
'''
new = '''        boolean sensitiveVisible = !found.isEmpty();
        boolean inferredContactDetail = !isOwnPackage(foregroundPackage)
                && isKnownContactAppPackage(foregroundPackage)
                && signals.savedContactNameFound
                && signals.contactDetailMarkerFound;
        if (sensitiveVisible || inferredContactDetail) {
            universalContactContextUntil = now + UNIVERSAL_CONTACT_CONTEXT_MS;
            copyProtectionArmedUntil = Math.max(copyProtectionArmedUntil, now + UNIVERSAL_CONTACT_CONTEXT_MS);
        }
        boolean protectedContextVisible = sensitiveVisible || inferredContactDetail;
        boolean shouldOpenGate = protectedContextVisible
                && !isOwnPackage(foregroundPackage)
                && now >= contactGateSuppressUntil
                && (signals.editableSensitive || inferredContactDetail
                || now <= contactGateArmedUntil || now <= universalContactContextUntil);
        if (contactGateAttached) {
            if (!protectedContextVisible || isOwnPackage(foregroundPackage)) hideContactGate();
        } else if (shouldOpenGate) {
            showContactGate(false);
        }
'''
s = s.replace(old, new, 1)
old = '''                CharSequence text = node.getText();
                CharSequence description = node.getContentDescription();
                boolean sensitive = PhoneMasker.containsSavedNumber(text, repository)
                        || PhoneMasker.containsSavedNumber(description, repository);
                if (Build.VERSION.SDK_INT >= 26) {
                    sensitive = sensitive
                            || PhoneMasker.containsSavedNumber(node.getHintText(), repository);
                }

                if (sensitive) {
                    if (signals != null) {
                        signals.sensitiveFound = true;
                        if (node.isEditable()) signals.editableSensitive = true;
                    }
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    addRegion(output, keys, bounds);
                }
'''
new = '''                String directText = collectNodeText(node);
                boolean sensitive = PhoneMasker.containsSavedNumber(directText, repository);
                Rect sensitiveBounds = new Rect();
                node.getBoundsInScreen(sensitiveBounds);
                if (signals != null) {
                    if (nodeContainsSavedContactName(node)) signals.savedContactNameFound = true;
                    if (matchesContactDetailLabel(directText)) signals.contactDetailMarkerFound = true;
                }
                if (!sensitive && node.getChildCount() > 0 && node.getChildCount() <= 12) {
                    NodeAggregate aggregate = buildNodeAggregate(node, AGGREGATE_DEPTH, AGGREGATE_NODE_LIMIT, AGGREGATE_CHAR_LIMIT);
                    if (aggregate != null) {
                        if (PhoneMasker.containsSavedNumber(aggregate.text, repository)) {
                            sensitive = true;
                            if (!aggregate.bounds.isEmpty()) sensitiveBounds.set(aggregate.bounds);
                        }
                        if (signals != null && matchesContactDetailLabel(aggregate.text)) signals.contactDetailMarkerFound = true;
                    }
                }
                if (sensitive) {
                    if (signals != null) {
                        signals.sensitiveFound = true;
                        if (node.isEditable() || nodeSupportsTextEditing(node)) signals.editableSensitive = true;
                    }
                    addRegion(output, keys, sensitiveBounds);
                }
'''
s = s.replace(old, new, 1)
old = '''        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) return;
'''
new = '''        boolean contextClick = Build.VERSION.SDK_INT >= 23 && type == AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED;
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && !contextClick) return;
'''
s = s.replace(old, new, 1)
old = '''        if (copyAction && (sourceSensitive || nearSensitive || !stableMasks.isEmpty()
                || now <= copyProtectionArmedUntil)) {
            copyProtectionArmedUntil = now + COPY_CONTEXT_ARM_MS;
            protectCopiedNumber();
        }
'''
new = '''        boolean universalContext = now <= universalContactContextUntil && isKnownContactAppPackage(foregroundPackage);
        boolean selectionIntent = type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED || contextClick;
        if ((copyAction || selectionIntent)
                && (sourceSensitive || nearSensitive || !stableMasks.isEmpty()
                || now <= copyProtectionArmedUntil || universalContext)) {
            copyProtectionArmedUntil = now + UNIVERSAL_CONTACT_CONTEXT_MS;
            protectCopiedNumber();
        }
'''
s = s.replace(old, new, 1)
s = s.replace('''        for (CharSequence value : event.getText()) {
            if (PhoneMasker.containsSavedNumber(value, repository)) return true;
        }
        AccessibilityNodeInfo source = event.getSource();
''', '''        for (CharSequence value : event.getText()) {
            if (PhoneMasker.containsSavedNumber(value, repository)) return true;
        }
        if (PhoneMasker.containsSavedNumber(event.getContentDescription(), repository)) return true;
        if (PhoneMasker.containsSavedNumber(event.getBeforeText(), repository)) return true;
        AccessibilityNodeInfo source = event.getSource();
''', 1)
s = s.replace('''            if (PhoneMasker.containsSavedNumber(source.getText(), repository)) return true;
            if (PhoneMasker.containsSavedNumber(source.getContentDescription(), repository)) return true;
            if (Build.VERSION.SDK_INT >= 26
                    && PhoneMasker.containsSavedNumber(source.getHintText(), repository)) return true;
            return false;
''', '''            if (PhoneMasker.containsSavedNumber(collectNodeText(source), repository)) return true;
            NodeAggregate aggregate = buildNodeAggregate(source, AGGREGATE_DEPTH, AGGREGATE_NODE_LIMIT, AGGREGATE_CHAR_LIMIT);
            return aggregate != null && PhoneMasker.containsSavedNumber(aggregate.text, repository);
''', 1)
old = '''        AccessibilityNodeInfo source = event.getSource();
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
'''
new = '''        if (matchesCopyLabel(event.getContentDescription()) || matchesCopyLabel(event.getBeforeText())) return true;
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            try {
                if (nodeLooksLikeCopyAction(source)) return true;
                AccessibilityNodeInfo parent = source.getParent();
                if (parent != null) {
                    try { if (nodeLooksLikeCopyAction(parent)) return true; }
                    finally { try { parent.recycle(); } catch (RuntimeException ignored) { } }
                }
            } catch (RuntimeException ignored) {
            } finally {
                try { source.recycle(); } catch (RuntimeException ignored) { }
            }
        }
        for (CharSequence value : event.getText()) if (matchesCopyLabel(value)) return true;
        return false;
'''
s = s.replace(old, new, 1)
s = s.replace('''        copyProtectionArmedUntil = SystemClock.uptimeMillis() + COPY_CONTEXT_ARM_MS;
        writeMaskedClipboard();
        handler.removeCallbacks(copyMaskRetry1);
        handler.removeCallbacks(copyMaskRetry2);
        handler.removeCallbacks(copyMaskRetry3);
        handler.postDelayed(copyMaskRetry1, 45L);
        handler.postDelayed(copyMaskRetry2, 160L);
        handler.postDelayed(copyMaskRetry3, 520L);
        showContactGate(true);
''', '''        long now = SystemClock.uptimeMillis();
        copyProtectionArmedUntil = now + UNIVERSAL_CONTACT_CONTEXT_MS;
        copyGuardStrongUntil = now + 2600L;
        writeMaskedClipboard();
        handler.removeCallbacks(copyMaskRetry1);
        handler.removeCallbacks(copyMaskRetry2);
        handler.removeCallbacks(copyMaskRetry3);
        handler.removeCallbacks(copyClipboardGuardRunnable);
        handler.postDelayed(copyMaskRetry1, 35L);
        handler.postDelayed(copyMaskRetry2, 140L);
        handler.postDelayed(copyMaskRetry3, 480L);
        handler.postDelayed(copyClipboardGuardRunnable, COPY_GUARD_TICK_MS);
        showContactGate(true);
''', 1)
marker = '    private void beginScrollGuard(AccessibilityEvent event) {\n'
if 'private void handleUniversalContactContextEvent' not in s:
    helpers = '''    private void handleUniversalContactContextEvent(AccessibilityEvent event, long now) {
        if (event == null || repository == null || !isKnownContactAppPackage(foregroundPackage)) return;
        int type = event.getEventType();
        boolean openIntent = type == AccessibilityEvent.TYPE_VIEW_CLICKED || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_FOCUSED || type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
        if (!openIntent) return;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;
        try {
            String sourceText = collectNodeText(source);
            boolean savedNumber = PhoneMasker.containsSavedNumber(sourceText, repository);
            boolean savedName = nodeContainsSavedContactName(source);
            boolean detailLabel = matchesContactDetailLabel(sourceText);
            Rect bounds = new Rect(); source.getBoundsInScreen(bounds);
            int screenTopZone = Math.max(dp(180), getResources().getDisplayMetrics().heightPixels / 5);
            boolean headerTap = bounds.top >= 0 && bounds.top <= screenTopZone;
            if (savedNumber || detailLabel || (savedName && headerTap)) {
                universalContactContextUntil = now + UNIVERSAL_CONTACT_CONTEXT_MS;
                contactGateArmedUntil = now + UNIVERSAL_CONTACT_CONTEXT_MS;
                copyProtectionArmedUntil = now + UNIVERSAL_CONTACT_CONTEXT_MS;
                if (savedName && headerTap) beginTransitionCurtain();
            }
        } catch (RuntimeException ignored) { }
        finally { try { source.recycle(); } catch (RuntimeException ignored) { } }
    }

    private boolean isKnownContactAppPackage(String packageName) {
        if (packageName == null) return false;
        if (isPhoneOrContactsPackage(packageName)) return true;
        String p = packageName.toLowerCase(Locale.ROOT);
        return p.equals("com.whatsapp") || p.equals("com.whatsapp.w4b") || p.contains("whatsapp")
                || p.equals("org.telegram.messenger") || p.startsWith("org.telegram.")
                || p.equals("org.thoughtcrime.securesms") || p.equals("com.facebook.orca")
                || p.equals("com.facebook.katana") || p.equals("com.instagram.android")
                || p.equals("com.google.android.apps.messaging") || p.equals("com.samsung.android.messaging")
                || p.equals("com.viber.voip") || p.startsWith("com.imo.") || p.contains("truecaller")
                || p.equals("com.skype.raider") || p.startsWith("com.microsoft.teams")
                || p.equals("com.slack") || p.equals("com.linkedin.android")
                || p.equals("us.zoom.videomeetings") || p.equals("com.google.android.apps.tachyon");
    }

    private boolean matchesContactDetailLabel(CharSequence text) {
        if (text == null) return false;
        String v = text.toString().toLowerCase(Locale.ROOT).replace('\\n', ' ').replace('\\r', ' ').trim();
        String[] terms = new String[]{"contact info","contact information","contact details","view contact","edit contact","business info","business information","profile info","user info","phone number","mobile number","telephone number","about and phone","about & phone","add to contacts","add to contact","संपर्क जानकारी","संपर्क विवरण","फ़ोन नंबर","फोन नंबर","मोबाइल नंबर","información de contacto","detalles de contacto","número de teléfono","informations du contact","numéro de téléphone","kontaktinfo","telefonnummer","informações de contato","número de telefone","informazioni contatto","numero di telefono","معلومات جهة الاتصال","رقم الهاتف","联系信息","电话号码","聯絡資訊","電話號碼","連絡先情報","電話番号","연락처 정보","전화번호","informasi kontak","nomor telepon"};
        for (String term : terms) if (v.contains(term)) return true;
        return false;
    }

    private boolean nodeContainsSavedContactName(AccessibilityNodeInfo node) {
        if (node == null || repository == null) return false;
        try {
            if (repository.isSavedContactName(node.getText()) || repository.isSavedContactName(node.getContentDescription())) return true;
            if (Build.VERSION.SDK_INT >= 26 && repository.isSavedContactName(node.getHintText())) return true;
            if (Build.VERSION.SDK_INT >= 28 && (repository.isSavedContactName(node.getPaneTitle()) || repository.isSavedContactName(node.getTooltipText()))) return true;
            if (Build.VERSION.SDK_INT >= 30 && repository.isSavedContactName(node.getStateDescription())) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    private boolean nodeSupportsTextEditing(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            if (node.isEditable() || (node.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT) != 0) return true;
            CharSequence className = node.getClassName();
            return className != null && className.toString().toLowerCase(Locale.ROOT).contains("edittext");
        } catch (RuntimeException ignored) { return false; }
    }

    private boolean nodeLooksLikeCopyAction(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            if (matchesCopyLabel(node.getText()) || matchesCopyLabel(node.getContentDescription())) return true;
            if (Build.VERSION.SDK_INT >= 26 && matchesCopyLabel(node.getHintText())) return true;
            if (Build.VERSION.SDK_INT >= 28 && matchesCopyLabel(node.getTooltipText())) return true;
            String viewId = node.getViewIdResourceName();
            if (viewId != null) { String id = viewId.toLowerCase(Locale.ROOT); if (id.contains("copy") || id.contains("clipboard")) return true; }
            List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
            if (actions != null) for (AccessibilityNodeInfo.AccessibilityAction action : actions) if (action != null && matchesCopyLabel(action.getLabel())) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    private String collectNodeText(AccessibilityNodeInfo node) {
        StringBuilder out = new StringBuilder();
        if (node == null) return "";
        try {
            appendText(out, node.getText()); appendText(out, node.getContentDescription()); appendText(out, node.getError());
            if (Build.VERSION.SDK_INT >= 26) appendText(out, node.getHintText());
            if (Build.VERSION.SDK_INT >= 28) { appendText(out, node.getPaneTitle()); appendText(out, node.getTooltipText()); }
            if (Build.VERSION.SDK_INT >= 30) appendText(out, node.getStateDescription());
        } catch (RuntimeException ignored) { }
        return out.toString();
    }

    private void appendText(StringBuilder out, CharSequence value) {
        if (value == null || value.length() == 0 || out.length() >= AGGREGATE_CHAR_LIMIT) return;
        if (out.length() > 0) out.append(' ');
        int remaining = AGGREGATE_CHAR_LIMIT - out.length();
        if (value.length() <= remaining) out.append(value); else out.append(value, 0, Math.max(0, remaining));
    }

    private NodeAggregate buildNodeAggregate(AccessibilityNodeInfo root, int maxDepth, int maxNodes, int maxChars) {
        if (root == null || maxNodes <= 0 || maxChars <= 0) return null;
        StringBuilder text = new StringBuilder(); Rect union = new Rect(); int[] visited = new int[]{0};
        collectAggregateChildren(root, 0, maxDepth, maxNodes, maxChars, visited, text, union);
        return new NodeAggregate(text.toString(), union);
    }

    private void collectAggregateChildren(AccessibilityNodeInfo node, int depth, int maxDepth, int maxNodes, int maxChars, int[] visited, StringBuilder out, Rect union) {
        if (node == null || visited[0] >= maxNodes || out.length() >= maxChars) return;
        visited[0]++;
        try {
            if (node.isVisibleToUser()) {
                String own = collectNodeText(node);
                if (!own.isEmpty()) {
                    if (out.length() > 0) out.append(' ');
                    int remaining = maxChars - out.length();
                    if (own.length() <= remaining) out.append(own); else out.append(own, 0, Math.max(0, remaining));
                    Rect b = new Rect(); node.getBoundsInScreen(b); if (!b.isEmpty()) { if (union.isEmpty()) union.set(b); else union.union(b); }
                }
            }
            if (depth >= maxDepth) return;
            int count = Math.min(node.getChildCount(), 12);
            for (int i = 0; i < count && visited[0] < maxNodes && out.length() < maxChars; i++) {
                AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
                try { collectAggregateChildren(child, depth + 1, maxDepth, maxNodes, maxChars, visited, out, union); }
                finally { try { child.recycle(); } catch (RuntimeException ignored) { } }
            }
        } catch (RuntimeException ignored) { }
    }

'''
    s = s.replace(marker, helpers + marker, 1)
s = s.replace('        copyProtectionArmedUntil = 0L;\n        ignoreClipboardEventsUntil = 0L;\n', '        copyProtectionArmedUntil = 0L;\n        universalContactContextUntil = 0L;\n        copyGuardStrongUntil = 0L;\n        ignoreClipboardEventsUntil = 0L;\n')
s = s.replace('        handler.removeCallbacks(copyMaskRetry3);\n', '        handler.removeCallbacks(copyMaskRetry3);\n        handler.removeCallbacks(copyClipboardGuardRunnable);\n')
s = s.replace('''    private static final class ScanSignals {
        boolean sensitiveFound;
        boolean editableSensitive;
    }

    private static final class MaskRegion {
''', '''    private static final class ScanSignals {
        boolean sensitiveFound;
        boolean editableSensitive;
        boolean savedContactNameFound;
        boolean contactDetailMarkerFound;
    }

    private static final class NodeAggregate {
        final String text; final Rect bounds;
        NodeAggregate(String text, Rect bounds) { this.text = text == null ? "" : text; this.bounds = bounds == null ? new Rect() : new Rect(bounds); }
    }

    private static final class MaskRegion {
''')
p.write_text(s)

p = app/'src/main/res/values/strings.xml'
s = p.read_text()
s = re.sub(r'<string name="accessibility_service_description">.*?</string>', '<string name="accessibility_service_description">Makoo protects saved contact phone numbers across Phone, Contacts, WhatsApp, WhatsApp Business, messaging, social, browser and other apps when Android exposes the screen through Accessibility. It masks visible saved numbers, protects contact-info/edit screens, and replaces protected copies with Masked. Processing stays on-device.</string>', s, flags=re.S)
p.write_text(s)

p = app/'src/main/res/layout/activity_main.xml'
s = p.read_text()
if 'WhatsApp / Universal App Protection' not in s:
    marker = '        <TextView\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="18dp"\n            android:gravity="center"\n            android:paddingTop="10dp"\n            android:paddingBottom="24dp"\n            android:text="@string/developer_credit"'
    note = '''        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:background="#ECFDF5"
            android:padding="12dp"
            android:text="WhatsApp / Universal App Protection: Makoo also checks contact cards, split phone-number text and copy/long-press actions in WhatsApp, WhatsApp Business and other apps that expose their screen to Android Accessibility."
            android:textColor="#065F46"
            android:textSize="13sp" />

'''
    if marker in s: s = s.replace(marker, note + marker, 1)
p.write_text(s)

p = app/'build.gradle'; s = p.read_text(); is_vivo = (java/'VivoKeepAliveService.java').exists()
s = re.sub(r'versionCode\s+\d+', 'versionCode 16' if is_vivo else 'versionCode 15', s)
s = re.sub(r"versionName\s+'[^']+'", "versionName '3.4.0'", s); p.write_text(s)
(root/'MAKOO_3_4_UNIVERSAL_APPS_NOTES.txt').write_text('Makoo 3.4.0 Universal App Protection\nVariant: '+('Vivo Persistent' if is_vivo else 'Standard / Non-Vivo')+'\nWhatsApp/WhatsApp Business, split-node phone reconstruction, cross-app contact-info detection and stronger CopySafe protection.\n')
