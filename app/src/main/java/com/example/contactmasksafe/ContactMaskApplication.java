package com.example.contactmasksafe;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public final class ContactMaskApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private int startedActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        AppLockManager.lock();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        startedActivities++;
        if (activity instanceof MainActivity || activity instanceof UnlockActivity) {
            AppLockManager.finishTrustedSettingsRoundTrip();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0 && !activity.isChangingConfigurations()
                && !AppLockManager.shouldKeepUnlockedForTrustedSettings()) {
            AppLockManager.lock();
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
