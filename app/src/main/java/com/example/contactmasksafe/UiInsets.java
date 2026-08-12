package com.example.contactmasksafe;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

public final class UiInsets {
    private UiInsets() {
    }

    public static void apply(Activity activity, View root) {
        if (activity == null || root == null || Build.VERSION.SDK_INT < 35) {
            return;
        }
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(
                        left + insets.getSystemWindowInsetLeft(),
                        top + insets.getSystemWindowInsetTop(),
                        right + insets.getSystemWindowInsetRight(),
                        bottom + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }
}
