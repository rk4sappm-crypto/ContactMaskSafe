from pathlib import Path
import re
root=Path.cwd(); app=root/'app'; res=app/'src/main/res'
p=app/'build.gradle'; s=p.read_text(); s=re.sub(r"versionCode\s+\d+","versionCode 12",s); s=re.sub(r"versionName\s+'[^']+'","versionName '3.2.1'",s); p.write_text(s)
p=root/'settings.gradle'; s=p.read_text(); s=re.sub(r"rootProject\.name\s*=\s*'[^']+'","rootProject.name = 'Makoo'",s); p.write_text(s)
for p in list((app/'src/main').rglob('*.java'))+list((app/'src/main').rglob('*.xml')):
    s=p.read_text(); s=s.replace('ContactMask Safe','Makoo').replace('"ContactMaskSafe"','"Makoo"'); p.write_text(s)
p=res/'values/strings.xml'; s=p.read_text()
if 'developer_credit' not in s: s=s.replace('</resources>','    <string name="logo_content_description">Makoo logo</string>\n    <string name="developer_credit">Designed and Developed by UCPL Technologies</string>\n</resources>')
p.write_text(s)
def brand_layout(path,w,h):
    p=res/'layout'/path; s=p.read_text()
    if '@drawable/makoo_logo' not in s:
        needle='        <TextView\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:text="Makoo"'
        logo=f'''        <ImageView
            android:layout_width="{w}dp"
            android:layout_height="{h}dp"
            android:layout_gravity="center_horizontal"
            android:adjustViewBounds="true"
            android:contentDescription="@string/logo_content_description"
            android:scaleType="fitCenter"
            android:src="@drawable/makoo_logo" />

'''
        s=s.replace(needle,logo+needle,1)
    if '@string/developer_credit' not in s:
        credit='''        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="18dp"
            android:gravity="center"
            android:paddingTop="10dp"
            android:paddingBottom="24dp"
            android:text="@string/developer_credit"
            android:textColor="#334155"
            android:textSize="14sp"
            android:textStyle="bold" />

'''
        s=s.replace('    </LinearLayout>\n</ScrollView>',credit+'    </LinearLayout>\n</ScrollView>')
    p.write_text(s)
brand_layout('activity_main.xml',96,96); brand_layout('activity_unlock.xml',110,110)
p=res/'layout/activity_main.xml'; s=p.read_text().replace('Privacy Shield • Password Protected','Contact Privacy Shield • Password Protected'); p.write_text(s)
p=app/'src/main/AndroidManifest.xml'; s=p.read_text()
if 'android.hardware.telephony' not in s: s=s.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">','<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-feature android:name="android.hardware.telephony" android:required="false" />')
s=s.replace('android:icon="@drawable/ic_launcher"','android:icon="@drawable/makoo_logo"')
if 'android:roundIcon=' not in s: s=s.replace('android:icon="@drawable/makoo_logo"','android:icon="@drawable/makoo_logo"\n        android:roundIcon="@drawable/makoo_logo"')
if 'android:resizeableActivity=' not in s: s=s.replace('android:label="@string/app_name"','android:label="@string/app_name"\n        android:resizeableActivity="true"')
p.write_text(s)
(root/'MAKOO_BRANDING_NOTES.txt').write_text('Makoo 3.2.1\nDesigned and Developed by UCPL Technologies\nLogo/icon: supplied Makoo U-arrow artwork.\nCore: ScrollSafe + Anti-Blink + password protection + One-Tap Setup.\nAndroid 5.1+ (API 22+). No Internet permission requested.\n')
