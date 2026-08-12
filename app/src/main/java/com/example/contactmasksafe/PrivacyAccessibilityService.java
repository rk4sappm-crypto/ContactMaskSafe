package com.example.contactmasksafe;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class PrivacyAccessibilityService extends AccessibilityService {
    private static final int MAX_NODES = 3500;
    private static final int MAX_MASKS = 100;
    private static final long SCAN_DELAY_MS = 70L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TextView> overlays = new ArrayList<>();
    private SavedNumberRepository repository;
    private WindowManager windowManager;
    private boolean scanScheduled;

    private final Runnable scanRunnable = new Runnable() {
        @Override public void run() {
            scanScheduled = false;
            scanNow();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        repository = new SavedNumberRepository(this);
        repository.refreshNow();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        AppPreferences.recordServiceConnected(this);
        scheduleScan();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!AppPreferences.isPrivacyShieldEnabled(this)) {
            clearMasks();
            return;
        }
        scheduleScan();
    }

    private void scheduleScan() {
        if (scanScheduled) return;
        scanScheduled = true;
        handler.postDelayed(scanRunnable, SCAN_DELAY_MS);
    }

    private void scanNow() {
        clearMasks();
        if (!AppPreferences.isPrivacyShieldEnabled(this) || repository == null || windowManager == null) {
            AppPreferences.recordAccessibilityEvent(this, 0);
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            AppPreferences.recordAccessibilityEvent(this, 0);
            return;
        }

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        int masked = 0;

        while (!queue.isEmpty() && visited < MAX_NODES && masked < MAX_MASKS) {
            AccessibilityNodeInfo node = queue.removeFirst();
            visited++;
            try {
                CharSequence text = node.getText();
                CharSequence description = node.getContentDescription();
                boolean sensitive = PhoneMasker.containsSavedNumber(text, repository)
                        || PhoneMasker.containsSavedNumber(description, repository);
                if (sensitive) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    if (addMask(bounds)) masked++;
                }
                int children = node.getChildCount();
                for (int i = 0; i < children && queue.size() + visited < MAX_NODES; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.addLast(child);
                }
            } catch (RuntimeException ignored) {
                // OEM accessibility trees can change while they are being traversed.
            }
        }
        AppPreferences.recordAccessibilityEvent(this, masked);
    }

    private boolean addMask(Rect bounds) {
        if (bounds == null || bounds.width() < 12 || bounds.height() < 8) return false;
        TextView cover = new TextView(this);
        cover.setText(PhoneMasker.MASK);
        cover.setTextColor(Color.WHITE);
        cover.setBackgroundColor(Color.BLACK);
        cover.setGravity(Gravity.CENTER);
        cover.setTextSize(14f);
        cover.setSingleLine(true);
        cover.setImportantForAccessibility(TextView.IMPORTANT_FOR_ACCESSIBILITY_NO);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Math.max(bounds.width(), dp(72)),
                Math.max(bounds.height(), dp(28)),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(0, bounds.left);
        lp.y = Math.max(0, bounds.top);
        try {
            windowManager.addView(cover, lp);
            overlays.add(cover);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void clearMasks() {
        if (windowManager == null) {
            overlays.clear();
            return;
        }
        for (TextView view : overlays) {
            try { windowManager.removeViewImmediate(view); }
            catch (RuntimeException ignored) { }
        }
        overlays.clear();
    }

    @Override
    public void onInterrupt() {
        clearMasks();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        clearMasks();
        super.onDestroy();
    }
}
