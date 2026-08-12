package com.example.contactmasksafe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stable anti-flash saved-contact masking service.
 *
 * The overlay window stays attached while Privacy Shield is enabled. Only the list of
 * rectangles drawn inside that window changes. This avoids the visible gap caused by
 * repeatedly removing and re-adding accessibility overlay windows.
 */
public class PrivacyAccessibilityService extends AccessibilityService {
    private static final int MAX_NODES = 3500;
    private static final int FAST_EVENT_NODES = 180;
    private static final int MAX_MASKS = 100;
    private static final long EMPTY_FRAME_HOLD_MS = 1400L;
    private static final long TRANSITION_GUARD_MS = 650L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MaskRegion> stableMasks = new ArrayList<>();

    private SavedNumberRepository repository;
    private WindowManager windowManager;
    private MaskLayerView maskLayerView;
    private boolean overlayAttached;
    private boolean scanQueued;
    private boolean transitionCurtain;
    private long lastConfirmedMaskAt;
    private String foregroundPackage = "";

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            scanQueued = false;
            scanVisibleWindows();
        }
    };

    private final Runnable followUpScan1 = new Runnable() {
        @Override public void run() { scanVisibleWindows(); }
    };

    private final Runnable followUpScan2 = new Runnable() {
        @Override public void run() { scanVisibleWindows(); }
    };

    private final Runnable followUpScan3 = new Runnable() {
        @Override public void run() { scanVisibleWindows(); }
    };

    private final Runnable clearTransitionCurtainRunnable = new Runnable() {
        @Override
        public void run() {
            transitionCurtain = false;
            render();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        repository = new SavedNumberRepository(this);
        repository.refreshNow();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            info.notificationTimeout = 0;
            setServiceInfo(info);
        }

        AppPreferences.recordServiceConnected(this);
        if (AppPreferences.isPrivacyShieldEnabled(this)) {
            ensureOverlayAttached();
            scheduleScans();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!AppPreferences.isPrivacyShieldEnabled(this)) {
            detachOverlay();
            return;
        }

        ensureOverlayAttached();

        String oldPackage = foregroundPackage;
        if (event != null && event.getPackageName() != null) {
            foregroundPackage = event.getPackageName().toString();
        }

        boolean packageChanged = !oldPackage.isEmpty()
                && !foregroundPackage.isEmpty()
                && !oldPackage.equals(foregroundPackage);
        boolean windowChanged = event != null
                && (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED);

        // Phone/Contacts transitions may draw the real number before their complete
        // accessibility tree is ready. A very short privacy curtain hides that first frame.
        if ((packageChanged || windowChanged) && isPhoneOrContactsPackage(foregroundPackage)) {
            beginTransitionCurtain();
        }

        // Fast path: mask directly from the incoming event source before the broader scan.
        promoteMasksFromEvent(event);
        scheduleScans();
    }

    private void scheduleScans() {
        if (!scanQueued) {
            scanQueued = true;
            handler.postAtFrontOfQueue(scanRunnable);
        }
        handler.removeCallbacks(followUpScan1);
        handler.removeCallbacks(followUpScan2);
        handler.removeCallbacks(followUpScan3);
        handler.postDelayed(followUpScan1, 40L);
        handler.postDelayed(followUpScan2, 180L);
        handler.postDelayed(followUpScan3, 650L);
    }

    private void promoteMasksFromEvent(AccessibilityEvent event) {
        if (event == null || repository == null) return;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;

        List<MaskRegion> fastMasks = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        try {
            collectMasks(source, fastMasks, keys, FAST_EVENT_NODES);
        } finally {
            // collectMasks recycles the root and all children it traverses.
        }

        if (!fastMasks.isEmpty()) {
            mergeFastMasks(fastMasks);
            lastConfirmedMaskAt = SystemClock.uptimeMillis();
            endTransitionCurtain();
            render();
        }
    }

    private void scanVisibleWindows() {
        if (!AppPreferences.isPrivacyShieldEnabled(this)
                || repository == null || windowManager == null) {
            detachOverlay();
            AppPreferences.recordAccessibilityEvent(this, 0);
            return;
        }

        ensureOverlayAttached();
        List<MaskRegion> found = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int remainingNodes = MAX_NODES;

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null && !windows.isEmpty()) {
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || found.size() >= MAX_MASKS || remainingNodes <= 0) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                int visited = collectMasks(root, found, keys, remainingNodes);
                remainingNodes -= visited;
            }
        } else {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) collectMasks(root, found, keys, remainingNodes);
        }

        long now = SystemClock.uptimeMillis();
        if (!found.isEmpty()) {
            stableMasks.clear();
            stableMasks.addAll(found);
            lastConfirmedMaskAt = now;
            endTransitionCurtain();
        } else if (!stableMasks.isEmpty() && now - lastConfirmedMaskAt > EMPTY_FRAME_HOLD_MS) {
            // Keep the last confirmed mask through short OEM/data-binding empty frames.
            stableMasks.clear();
        }

        render();
        AppPreferences.recordAccessibilityEvent(this, stableMasks.size());
    }

    /**
     * Breadth-first scan. The method owns and recycles root and all child nodes it obtains.
     * Returns the number of visited nodes.
     */
    private int collectMasks(AccessibilityNodeInfo root,
                             List<MaskRegion> output,
                             Set<String> keys,
                             int nodeLimit) {
        if (root == null || nodeLimit <= 0) return 0;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;

        while (!queue.isEmpty() && visited < nodeLimit && output.size() < MAX_MASKS) {
            AccessibilityNodeInfo node = queue.removeFirst();
            visited++;
            try {
                if (!node.isVisibleToUser()) continue;

                CharSequence text = node.getText();
                CharSequence description = node.getContentDescription();
                boolean sensitive = PhoneMasker.containsSavedNumber(text, repository)
                        || PhoneMasker.containsSavedNumber(description, repository);
                if (Build.VERSION.SDK_INT >= 26) {
                    sensitive = sensitive
                            || PhoneMasker.containsSavedNumber(node.getHintText(), repository);
                }

                if (sensitive) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    addRegion(output, keys, bounds);
                }

                int children = node.getChildCount();
                for (int i = 0; i < children
                        && visited + queue.size() < nodeLimit
                        && output.size() < MAX_MASKS; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.addLast(child);
                }
            } catch (RuntimeException ignored) {
                // Accessibility trees can mutate while being traversed.
            } finally {
                try { node.recycle(); } catch (RuntimeException ignored) { }
            }
        }

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            try { node.recycle(); } catch (RuntimeException ignored) { }
        }
        return visited;
    }

    private void addRegion(List<MaskRegion> output, Set<String> keys, Rect bounds) {
        if (bounds == null || bounds.width() < 8 || bounds.height() < 6) return;
        int l = bounds.left / 4;
        int t = bounds.top / 4;
        int r = bounds.right / 4;
        int b = bounds.bottom / 4;
        String key = l + ":" + t + ":" + r + ":" + b;
        if (keys.add(key)) output.add(new MaskRegion(new Rect(bounds)));
    }

    private void mergeFastMasks(List<MaskRegion> freshMasks) {
        for (MaskRegion fresh : freshMasks) {
            for (int i = stableMasks.size() - 1; i >= 0; i--) {
                Rect old = stableMasks.get(i).bounds;
                if (Rect.intersects(old, fresh.bounds)
                        || (Math.abs(old.centerX() - fresh.bounds.centerX()) <= 18
                        && Math.abs(old.centerY() - fresh.bounds.centerY()) <= 18)) {
                    stableMasks.remove(i);
                }
            }
            stableMasks.add(fresh);
            if (stableMasks.size() >= MAX_MASKS) break;
        }
    }

    private void beginTransitionCurtain() {
        transitionCurtain = true;
        handler.removeCallbacks(clearTransitionCurtainRunnable);
        handler.postDelayed(clearTransitionCurtainRunnable, TRANSITION_GUARD_MS);
        render();
    }

    private void endTransitionCurtain() {
        if (!transitionCurtain) return;
        transitionCurtain = false;
        handler.removeCallbacks(clearTransitionCurtainRunnable);
    }

    private boolean isPhoneOrContactsPackage(String packageName) {
        if (packageName == null) return false;
        String value = packageName.toLowerCase(Locale.ROOT);
        return value.contains("contacts")
                || value.contains("dialer")
                || value.contains("incall")
                || value.contains("telecom")
                || value.contains("calllog")
                || value.equals("com.android.phone")
                || value.endsWith(".phone");
    }

    private void ensureOverlayAttached() {
        if (windowManager == null) return;
        if (maskLayerView == null) maskLayerView = new MaskLayerView();
        if (overlayAttached) return;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            windowManager.addView(maskLayerView, lp);
            overlayAttached = true;
        } catch (RuntimeException ignored) {
            overlayAttached = false;
        }
    }

    private void render() {
        ensureOverlayAttached();
        if (maskLayerView == null) return;
        maskLayerView.setState(stableMasks, transitionCurtain);
    }

    private void detachOverlay() {
        stableMasks.clear();
        lastConfirmedMaskAt = 0L;
        transitionCurtain = false;
        handler.removeCallbacks(clearTransitionCurtainRunnable);
        if (overlayAttached && windowManager != null && maskLayerView != null) {
            try { windowManager.removeViewImmediate(maskLayerView); }
            catch (RuntimeException ignored) { }
        }
        overlayAttached = false;
    }

    @Override
    public void onInterrupt() {
        if (AppPreferences.isPrivacyShieldEnabled(this)) scheduleScans();
        else detachOverlay();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        detachOverlay();
        super.onDestroy();
    }

    private static final class MaskRegion {
        final Rect bounds;
        MaskRegion(Rect bounds) { this.bounds = bounds; }
    }

    private final class MaskLayerView extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<MaskRegion> masks = new ArrayList<>();
        private boolean curtain;

        MaskLayerView() {
            super(PrivacyAccessibilityService.this);
            setBackgroundColor(Color.TRANSPARENT);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setFocusable(false);
            setClickable(false);
            setWillNotDraw(false);
            backgroundPaint.setColor(Color.rgb(17, 24, 39));
            backgroundPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        void setState(List<MaskRegion> source, boolean showCurtain) {
            masks = new ArrayList<>(source);
            curtain = showCurtain;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (curtain) {
                canvas.drawColor(Color.rgb(17, 24, 39));
                String message = "Privacy shield checking…";
                textPaint.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float x = Math.max(24f, (getWidth() - textPaint.measureText(message)) / 2f);
                float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(message, x, y, textPaint);
                return;
            }

            for (MaskRegion region : masks) {
                Rect b = region.bounds;
                RectF area = new RectF(
                        Math.max(0, b.left - 7),
                        Math.max(0, b.top - 5),
                        Math.min(getWidth(), b.right + 7),
                        Math.min(getHeight(), b.bottom + 5)
                );
                if (area.width() <= 1 || area.height() <= 1) continue;

                canvas.drawRoundRect(area, 6f, 6f, backgroundPaint);
                String label = PhoneMasker.MASK;
                float size = Math.max(11f, Math.min(20f, area.height() * 0.46f));
                textPaint.setTextSize(size);
                while (textPaint.measureText(label) > area.width() - 14f
                        && textPaint.getTextSize() > 9f) {
                    textPaint.setTextSize(textPaint.getTextSize() - 1f);
                }
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float baseline = area.centerY() - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(label, area.left + 7f, baseline, textPaint);
            }
        }
    }
}
