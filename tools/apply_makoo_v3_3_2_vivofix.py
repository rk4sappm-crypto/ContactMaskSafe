from pathlib import Path
import re
r=Path.cwd(); app=r/'app'; src=app/'src/main'; pkg=src/'java/com/example/contactmasksafe'; res=src/'res'

# Version
p=app/'build.gradle'; s=p.read_text(); s=re.sub(r'versionCode\s+\d+','versionCode 15',s); s=re.sub(r"versionName\s+'[^']+'","versionName '3.3.2'",s); p.write_text(s)

# Manifest + legacy Vivo foreground helper
p=src/'AndroidManifest.xml'; s=p.read_text()
if 'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS' not in s:
    s=s.replace('<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />','<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />')
if '.VivoKeepAliveService' not in s:
    marker='        <service\n            android:name=".PrivacyAccessibilityService"'
    s=s.replace(marker,'        <service android:name=".VivoKeepAliveService" android:exported="false" android:stopWithTask="false" />\n\n'+marker)
p.write_text(s)

# Conservative Accessibility configuration for older OEM frameworks.
(res/'xml/accessibility_service_config.xml').write_text('''<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"\n    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewTextChanged|typeViewFocused|typeViewScrolled|typeViewTextSelectionChanged|typeViewClicked|typeViewLongClicked|typeWindowsChanged"\n    android:accessibilityFeedbackType="feedbackVisual"\n    android:accessibilityFlags="flagReportViewIds"\n    android:canRetrieveWindowContent="true"\n    android:description="@string/accessibility_service_description"\n    android:notificationTimeout="50" />\n''')

(pkg/'VivoKeepAliveService.java').write_text(r'''package com.example.contactmasksafe;
import android.app.*; import android.content.*; import android.os.*;
public class VivoKeepAliveService extends Service {
  private static final String C="makoo_vivo_protection"; private static final int ID=3302;
  static boolean shouldRun(){ String m=Build.MANUFACTURER==null?"":Build.MANUFACTURER.toLowerCase(); String b=Build.BRAND==null?"":Build.BRAND.toLowerCase(); return (m.contains("vivo")||b.contains("vivo"))&&Build.VERSION.SDK_INT<=30; }
  @Override public void onCreate(){ super.onCreate(); if(!shouldRun()){stopSelf();return;} if(Build.VERSION.SDK_INT>=26){ NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(n!=null){NotificationChannel c=new NotificationChannel(C,"Makoo Vivo protection",NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); n.createNotificationChannel(c);} } Intent o=new Intent(this,UnlockActivity.class); int f=PendingIntent.FLAG_UPDATE_CURRENT; if(Build.VERSION.SDK_INT>=23)f|=PendingIntent.FLAG_IMMUTABLE; PendingIntent pi=PendingIntent.getActivity(this,3302,o,f); Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,C):new Notification.Builder(this); b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("Makoo protection active").setContentText("Vivo compatibility mode is keeping screen protection available").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).setPriority(Notification.PRIORITY_LOW); startForeground(ID,b.build()); }
  @Override public int onStartCommand(Intent i,int f,int id){return shouldRun()?START_STICKY:START_NOT_STICKY;} @Override public IBinder onBind(Intent i){return null;}
}
''')

# Accessibility service: Vivo Android 11 stability mode.
p=pkg/'PrivacyAccessibilityService.java'; s=p.read_text()
if 'import android.content.Intent;' not in s: s=s.replace('import android.content.ClipboardManager;','import android.content.ClipboardManager;\nimport android.content.Intent;')
if 'legacyVivoMode' not in s: s=s.replace('    private String foregroundPackage = "";','    private String foregroundPackage = "";\n    private boolean legacyVivoMode;')
s=s.replace('        repository = new SavedNumberRepository(this);','        legacyVivoMode = isLegacyVivoMode();\n        repository = new SavedNumberRepository(this);',1)
old='''        AccessibilityServiceInfo info = getServiceInfo();\n        if (info != null) {\n            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;\n            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;\n            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;\n            info.notificationTimeout = 0;\n            setServiceInfo(info);\n        }'''
new='''        AccessibilityServiceInfo info = getServiceInfo();\n        if (info != null) { try {\n            if (legacyVivoMode) { info.flags &= ~AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS; info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS; info.notificationTimeout = 50; }\n            else { info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS; info.notificationTimeout=0; }\n            setServiceInfo(info);\n        } catch (RuntimeException ignored) { } }\n        if (legacyVivoMode) startVivoKeepAlive();'''
s=s.replace(old,new)
s=s.replace('handler.postDelayed(this, SCROLL_RESCAN_MS);','handler.postDelayed(this, legacyVivoMode ? 70L : SCROLL_RESCAN_MS);')
s=s.replace('handler.postDelayed(followUpScan1, 40L);\n        handler.postDelayed(followUpScan2, 180L);\n        handler.postDelayed(followUpScan3, 650L);','handler.postDelayed(followUpScan1, legacyVivoMode ? 90L : 40L);\n        handler.postDelayed(followUpScan2, legacyVivoMode ? 320L : 180L);\n        handler.postDelayed(followUpScan3, legacyVivoMode ? 950L : 650L);')
old='''        int remainingNodes = MAX_NODES;\n\n        List<AccessibilityWindowInfo> windows = getWindows();\n        if (windows != null && !windows.isEmpty()) {\n            for (AccessibilityWindowInfo window : windows) {\n                if (window == null || found.size() >= MAX_MASKS || remainingNodes <= 0) continue;\n                AccessibilityNodeInfo root = window.getRoot();\n                if (root == null) continue;\n                int visited = collectMasks(root, found, keys, remainingNodes, signals);\n                remainingNodes -= visited;\n            }\n        } else {\n            AccessibilityNodeInfo root = getRootInActiveWindow();\n            if (root != null) collectMasks(root, found, keys, remainingNodes, signals);\n        }'''
new='''        int remainingNodes = legacyVivoMode ? 1400 : MAX_NODES;\n        if (legacyVivoMode) { AccessibilityNodeInfo root=null; try{root=getRootInActiveWindow();}catch(RuntimeException ignored){} if(root!=null) collectMasks(root,found,keys,remainingNodes,signals); }\n        else { List<AccessibilityWindowInfo> windows=getWindows(); if(windows!=null&&!windows.isEmpty()){ for(AccessibilityWindowInfo window:windows){ if(window==null||found.size()>=MAX_MASKS||remainingNodes<=0)continue; AccessibilityNodeInfo root=window.getRoot(); if(root==null)continue; int visited=collectMasks(root,found,keys,remainingNodes,signals); remainingNodes-=visited; } } else { AccessibilityNodeInfo root=getRootInActiveWindow(); if(root!=null)collectMasks(root,found,keys,remainingNodes,signals); } }'''
s=s.replace(old,new)
marker='    private boolean isPhoneOrContactsPackage(String packageName) {'
if 'private boolean isLegacyVivoMode()' not in s:
    helper='''    private boolean isLegacyVivoMode() { String m=Build.MANUFACTURER==null?"":Build.MANUFACTURER.toLowerCase(Locale.ROOT); String b=Build.BRAND==null?"":Build.BRAND.toLowerCase(Locale.ROOT); return (m.contains("vivo")||b.contains("vivo"))&&Build.VERSION.SDK_INT<=30; }\n    private void startVivoKeepAlive(){ if(!VivoKeepAliveService.shouldRun())return; try{Intent i=new Intent(this,VivoKeepAliveService.class); if(Build.VERSION.SDK_INT>=26)startForegroundService(i); else startService(i);}catch(RuntimeException ignored){} }\n    private void stopVivoKeepAlive(){ if(!legacyVivoMode)return; try{stopService(new Intent(this,VivoKeepAliveService.class));}catch(RuntimeException ignored){} }\n\n'''
    s=s.replace(marker,helper+marker)
s=s.replace('        detachOverlay();\n        super.onDestroy();','        detachOverlay();\n        stopVivoKeepAlive();\n        super.onDestroy();')
p.write_text(s)

# Setup state.
p=pkg/'AppPreferences.java'; s=p.read_text()
if 'KEY_VIVO_SETUP_STAGE' not in s:
    s=s.replace('private static final String KEY_ACCESSIBILITY_ROUNDTRIP = "accessibility_roundtrip";','private static final String KEY_ACCESSIBILITY_ROUNDTRIP = "accessibility_roundtrip";\n    private static final String KEY_VIVO_SETUP_STAGE = "vivo_setup_stage";\n    private static final String KEY_OEM_ROUNDTRIP = "oem_roundtrip";')
    marker='    private static SharedPreferences preferences(Context context) {'
    s=s.replace(marker,'    public static int getVivoSetupStage(Context c){return preferences(c).getInt(KEY_VIVO_SETUP_STAGE,0);}\n    public static void setVivoSetupStage(Context c,int v){preferences(c).edit().putInt(KEY_VIVO_SETUP_STAGE,v).apply();}\n    public static boolean wasOemRoundTripStarted(Context c){return preferences(c).getBoolean(KEY_OEM_ROUNDTRIP,false);}\n    public static void setOemRoundTripStarted(Context c,boolean v){preferences(c).edit().putBoolean(KEY_OEM_ROUNDTRIP,v).apply();}\n\n'+marker)
p.write_text(s)

# Main screen and guided repair.
p=pkg/'MainActivity.java'; s=p.read_text()
if 'import android.os.PowerManager;' not in s: s=s.replace('import android.os.Bundle;','import android.os.Bundle;\nimport android.os.PowerManager;')
if 'import android.content.pm.ResolveInfo;' not in s: s=s.replace('import android.content.pm.PackageManager;','import android.content.pm.PackageManager;\nimport android.content.pm.ResolveInfo;')
if 'import java.util.List;' not in s: s=s.replace('public class MainActivity extends Activity {','import java.util.List;\nimport java.util.Locale;\n\npublic class MainActivity extends Activity {')
if 'btnVivoRepair' not in s: s=s.replace('findViewById(R.id.btnAppSettings).setOnClickListener(v -> openAppSettings());','findViewById(R.id.btnAppSettings).setOnClickListener(v -> openAppSettings());\n        findViewById(R.id.btnVivoRepair).setOnClickListener(v -> startVivoRepair());')
s=s.replace('        AppPreferences.setPrivacyShieldEnabled(this, true);\n        internalSwitchChange = true;','        AppPreferences.setPrivacyShieldEnabled(this, true);\n        if (isLegacyVivo()) AppPreferences.setVivoSetupStage(this, 0);\n        internalSwitchChange = true;',1)
old='''        if (!isAccessibilityEnabled()) {\n            openAccessibilitySettings(true);\n            return;\n        }\n        finishEasySetup();'''
new='''        if (isLegacyVivo()) { int stage=AppPreferences.getVivoSetupStage(this); if(stage==0&&!isIgnoringBatteryOptimizations()){AppPreferences.setVivoSetupStage(this,1);openBatteryExemptionRequest();return;} if(stage<=1){AppPreferences.setVivoSetupStage(this,2);openVivoAutoStartSettings();return;} }\n        if (!isAccessibilityEnabled()) { openAccessibilitySettings(true); return; }\n        finishEasySetup();'''
s=s.replace(old,new,1)
old='''            } else if (accessibilitySettingsOpened) {\n                accessibilitySettingsOpened = false;\n                AppPreferences.setAccessibilityRoundTripStarted(this, false);\n                updateStatus();\n            }'''
new='''            } else if (AppPreferences.wasOemRoundTripStarted(this)) { AppPreferences.setOemRoundTripStarted(this,false); continueEasySetup();\n            } else if (accessibilitySettingsOpened) { accessibilitySettingsOpened=false; AppPreferences.setAccessibilityRoundTripStarted(this,false); updateStatus(); }'''
s=s.replace(old,new,1)
marker='    private boolean isAccessibilityEnabled() {'
if 'private void startVivoRepair()' not in s:
    helper=r'''    private boolean isLegacyVivo(){String m=Build.MANUFACTURER==null?"":Build.MANUFACTURER.toLowerCase(Locale.ROOT);String b=Build.BRAND==null?"":Build.BRAND.toLowerCase(Locale.ROOT);return(m.contains("vivo")||b.contains("vivo"))&&Build.VERSION.SDK_INT<=30;}
    private boolean isIgnoringBatteryOptimizations(){if(Build.VERSION.SDK_INT<23)return true;PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);return pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());}
    private void startVivoRepair(){easySetupActive=true;AppPreferences.setEasySetupActive(this,true);AppPreferences.setPrivacyShieldEnabled(this,true);AppPreferences.setVivoSetupStage(this,0);continueEasySetup();}
    private void openBatteryExemptionRequest(){AppPreferences.setOemRoundTripStarted(this,true);AppLockManager.beginTrustedSettingsRoundTrip();try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(RuntimeException x){openAppSettings();}}}
    private void openVivoAutoStartSettings(){AppPreferences.setOemRoundTripStarted(this,true);AppLockManager.beginTrustedSettingsRoundTrip();String[][] c={{"com.vivo.permissionmanager","com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},{"com.iqoo.secure","com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},{"com.iqoo.secure","com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"}};for(String[] a:c){try{Intent i=new Intent();i.setComponent(new ComponentName(a[0],a[1]));List<ResolveInfo> l=getPackageManager().queryIntentActivities(i,PackageManager.MATCH_DEFAULT_ONLY);if(l!=null&&!l.isEmpty()){startActivity(i);return;}}catch(RuntimeException ignored){}}openAppSettings();}

'''
    s=s.replace(marker,helper+marker)
p.write_text(s)

p=res/'layout/activity_main.xml'; s=p.read_text().replace('Makoo 3.3','Makoo 3.3.2 VivoFix',1)
if '@+id/btnVivoRepair' not in s:
    x='<Button android:id="@+id/btnAccessibility"'; b='<Button android:id="@+id/btnVivoRepair" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="56dp" android:layout_marginTop="10dp" android:text="Repair Vivo Accessibility / Not Working" android:textAllCaps="false" android:textStyle="bold" />\n        '; s=s.replace(x,b+x)
p.write_text(s)
(r/'MAKOO_3_3_2_VIVOFIX_NOTES.txt').write_text('Makoo 3.3.2 VivoFix: conservative Vivo Android 11 accessibility mode, lower scan pressure, foreground keep-alive notification, guided battery + Vivo auto-start repair, with ScrollSafe/Anti-Blink/Protected Contact/CopySafe retained.\n')
print('Applied Makoo 3.3.2 VivoFix')
