from pathlib import Path
import re
root=Path.cwd()
pkg=root/'app/src/main/java/com/example/contactmasksafe'
res=root/'app/src/main/res'

# version
p=root/'app/build.gradle'; s=p.read_text(); s=re.sub(r'versionCode\s+\d+','versionCode 15',s); s=re.sub(r"versionName\s+'[^']+'","versionName '3.3.3'",s); p.write_text(s)

# Replace keepalive service with independent persistent watchdog
(pkg/'VivoKeepAliveService.java').write_text(r'''package com.example.contactmasksafe;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.Locale;

/**
 * Persistent companion service for older Vivo/Funtouch builds.
 *
 * Important: this service is intentionally independent of the AccessibilityService lifecycle.
 * If Vivo tears down/rebinds the accessibility service, this foreground service remains alive
 * and keeps the application process eligible to stay resident.
 */
public class VivoKeepAliveService extends Service {
    public static final String ACTION_RESTART = "com.example.contactmasksafe.action.RESTART_VIVO_KEEPALIVE";
    private static final String CHANNEL = "makoo_vivo_protection";
    private static final int NOTIFICATION_ID = 3303;
    private static final int RESTART_REQUEST = 3304;

    public static boolean shouldRun() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.ROOT);
        return (manufacturer.contains("vivo") || brand.contains("vivo")) && Build.VERSION.SDK_INT <= 30;
    }

    public static void ensureRunning(Context context) {
        if (!shouldRun() || !AppPreferences.isPrivacyShieldEnabled(context)) return;
        try {
            Intent intent = new Intent(context, VivoKeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException ignored) { }
    }

    public static void stopRunning(Context context) {
        try { context.stopService(new Intent(context, VivoKeepAliveService.class)); }
        catch (RuntimeException ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        if (!shouldRun() || !AppPreferences.isPrivacyShieldEnabled(this)) {
            stopSelf();
            return;
        }
        startAsForeground();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!shouldRun() || !AppPreferences.isPrivacyShieldEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        return START_STICKY;
    }

    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, "Makoo background protection", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Keeps Makoo contact masking active on older Vivo phones");
                channel.setShowBadge(false);
                channel.setSound(null, null);
                manager.createNotificationChannel(channel);
            }
        }

        Intent open = new Intent(this, UnlockActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, open, pFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Makoo protection active")
                .setContentText("Background protection is running")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE);
        startForeground(NOTIFICATION_ID, builder.build());
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        if (shouldRun() && AppPreferences.isPrivacyShieldEnabled(this)) scheduleRestart();
        super.onDestroy();
    }

    private void scheduleRestart() {
        try {
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm == null) return;
            Intent restart = new Intent(this, VivoKeepAliveRestartReceiver.class);
            restart.setAction(ACTION_RESTART);
            int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) pFlags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(this, RESTART_REQUEST, restart, pFlags);
            long when = SystemClock.elapsedRealtime() + 1500L;
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pending);
            else alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pending);
        } catch (RuntimeException ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
''')

(pkg/'VivoKeepAliveRestartReceiver.java').write_text(r'''package com.example.contactmasksafe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts Vivo foreground protection after task/process removal. */
public class VivoKeepAliveRestartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || VivoKeepAliveService.ACTION_RESTART.equals(intent.getAction())) {
            VivoKeepAliveService.ensureRunning(context);
        }
    }
}
''')

(pkg/'BootReceiver.java').write_text(r'''package com.example.contactmasksafe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the Vivo keep-alive companion after a normal device boot. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            VivoKeepAliveService.ensureRunning(context);
        }
    }
}
''')

# Start companion from Application whenever shield is already enabled.
p=pkg/'ContactMaskApplication.java'; s=p.read_text()
if 'VivoKeepAliveService.ensureRunning(this);' not in s:
    s=s.replace('registerActivityLifecycleCallbacks(this);','registerActivityLifecycleCallbacks(this);\n        if (AppPreferences.isPrivacyShieldEnabled(this)) VivoKeepAliveService.ensureRunning(this);')
p.write_text(s)

# AppPreferences owns keepalive state: enabling shield starts, disabling stops.
p=pkg/'AppPreferences.java'; s=p.read_text()
s=s.replace('''    public static void setPrivacyShieldEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }''','''    public static void setPrivacyShieldEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) VivoKeepAliveService.ensureRunning(context.getApplicationContext());
        else VivoKeepAliveService.stopRunning(context.getApplicationContext());
    }''')
p.write_text(s)

# Accessibility should ensure companion is running, but never stop it on accessibility destruction.
p=pkg/'PrivacyAccessibilityService.java'; s=p.read_text()
s=s.replace('if (legacyVivoMode) startVivoKeepAlive();','if (legacyVivoMode) VivoKeepAliveService.ensureRunning(this);')
s=s.replace('    private void startVivoKeepAlive(){ if(!VivoKeepAliveService.shouldRun())return; try{Intent i=new Intent(this,VivoKeepAliveService.class); if(Build.VERSION.SDK_INT>=26)startForegroundService(i); else startService(i);}catch(RuntimeException ignored){} }\n','')
s=s.replace('    private void stopVivoKeepAlive(){ if(!legacyVivoMode)return; try{stopService(new Intent(this,VivoKeepAliveService.class));}catch(RuntimeException ignored){} }\n','')
s=s.replace('        stopVivoKeepAlive();\n','')
p.write_text(s)

# Manifest receivers and boot permission.
p=root/'app/src/main/AndroidManifest.xml'; s=p.read_text()
if 'android.permission.RECEIVE_BOOT_COMPLETED' not in s:
    s=s.replace('<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />','<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />\n    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />')
if '.VivoKeepAliveRestartReceiver' not in s:
    insert='''
        <receiver android:name=".VivoKeepAliveRestartReceiver" android:exported="false" />
        <receiver android:name=".BootReceiver" android:enabled="true" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
'''
    s=s.replace('        <service android:name=".VivoKeepAliveService" android:exported="false" android:stopWithTask="false" />', '        <service android:name=".VivoKeepAliveService" android:exported="false" android:stopWithTask="false" />'+insert)
p.write_text(s)

# Main screen version/diagnostic copy.
p=res/'layout/activity_main.xml'; s=p.read_text(); s=s.replace('Makoo 3.3.2 VivoFix','Makoo 3.3.3 Persistent Vivo',1); p.write_text(s)
(root/'MAKOO_3_3_3_PERSISTENT_VIVO_NOTES.txt').write_text('Makoo 3.3.3 Persistent Vivo: fixes keep-alive lifecycle bug. Vivo foreground companion is independent of AccessibilityService, restarts after task removal, starts when Privacy Shield is enabled, and restores after boot.\n')
print('Applied Makoo 3.3.3 Persistent Vivo')
