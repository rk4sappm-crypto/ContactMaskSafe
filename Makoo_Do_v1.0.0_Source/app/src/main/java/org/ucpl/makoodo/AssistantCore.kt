package org.ucpl.makoodo

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

object AssistantCore {
    data class Result(val text:String, val handled:Boolean=true)

    private val wakePrefixes = listOf("hey makoo", "hello makoo", "hi makoo", "suno makoo", "hey maku", "hello maku", "suno maku", "makoo do", "maku do")

    fun stripWake(raw:String):String {
        var s=raw.trim()
        val low=s.lowercase(Locale.getDefault())
        val p=wakePrefixes.firstOrNull{low.startsWith(it)}
        if(p!=null) s=s.substring(p.length).trim().trimStart(',', ':', '-', ' ')
        return s
    }

    fun hasWake(raw:String):Boolean {
        val low=raw.trim().lowercase(Locale.getDefault())
        return wakePrefixes.any{low.startsWith(it) || low.contains(" $it ")}
    }

    fun process(context:Context, raw:String):Result {
        val command=stripWake(raw).trim()
        if(command.isBlank()) return Result("Yes? What would you like me to do?")
        val c=command.lowercase(Locale.getDefault())
        saveHistory(context, command)

        customShortcut(context, c)?.let { mapped ->
            if(mapped.lowercase(Locale.getDefault()) != c) return process(context, mapped)
        }

        if(c in listOf("help","commands","what can you do","what can you do?"))
            return Result("I can open apps, search the web, set alarms and timers, create reminders and notes, call or message contacts, control media and volume, use the flashlight, open navigation and settings, read basic calendar information, calculate, tell time and battery status, and run custom shortcuts.")

        if(c.contains("who are you") || c.contains("your name")) return Result("I am Makoo Do, your Android voice assistant.")
        if(c.contains("who made you") || c.contains("developer")) return Result("I was designed and developed in India by UCPL Technologies.")
        if(c in listOf("hello","hi","namaste","नमस्ते")) return Result("Hello! I am ready.")
        if(c.contains("thank")) return Result("You're welcome.")

        if(c.contains("what time") || c=="time" || c.contains("current time"))
            return Result("It is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}.")
        if(c.contains("what date") || c=="date" || c.contains("today's date") || c.contains("todays date"))
            return Result("Today is ${SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())}.")
        if(c.contains("battery")) {
            val bm=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return Result("Your battery is at $pct percent.")
        }

        calculate(command)?.let{return Result(it)}

        when {
            c=="flashlight on" || c=="torch on" || c.contains("turn on flashlight") || c.contains("turn on torch") -> return flashlight(context,true)
            c=="flashlight off" || c=="torch off" || c.contains("turn off flashlight") || c.contains("turn off torch") -> return flashlight(context,false)
            c in listOf("play","play music","resume","resume music") -> {media(context,KeyEvent.KEYCODE_MEDIA_PLAY);return Result("Playing media.")}
            c in listOf("pause","pause music","stop music") -> {media(context,KeyEvent.KEYCODE_MEDIA_PAUSE);return Result("Media paused.")}
            c.contains("next song") || c=="next" -> {media(context,KeyEvent.KEYCODE_MEDIA_NEXT);return Result("Next track.")}
            c.contains("previous song") || c.contains("previous track") || c=="previous" -> {media(context,KeyEvent.KEYCODE_MEDIA_PREVIOUS);return Result("Previous track.")}
            c.contains("volume up") || c.contains("increase volume") -> return volume(context,1)
            c.contains("volume down") || c.contains("decrease volume") -> return volume(context,-1)
            c=="mute" || c.contains("mute volume") -> return mute(context)
        }

        parseTimer(c)?.let { seconds ->
            val i=Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,seconds).putExtra(AlarmClock.EXTRA_MESSAGE,"Makoo Do timer").putExtra(AlarmClock.EXTRA_SKIP_UI,false)
            start(context,i); return Result("Opening a timer for ${friendlyDuration(seconds)}.")
        }
        parseAlarm(c)?.let { hm ->
            val i=Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,hm.first).putExtra(AlarmClock.EXTRA_MINUTES,hm.second).putExtra(AlarmClock.EXTRA_MESSAGE,"Makoo Do alarm").putExtra(AlarmClock.EXTRA_SKIP_UI,false)
            start(context,i); return Result("Opening the alarm for ${formatTime(hm.first,hm.second)}.")
        }
        parseReminder(c)?.let { r ->
            scheduleReminder(context,r.first,r.second)
            return Result("Okay. I will remind you to ${r.first} in ${friendlyDuration((r.second/1000).toInt())}.")
        }

        if(c.startsWith("note ") || c.startsWith("remember that ") || c.startsWith("remember ")) {
            val note=when { c.startsWith("note ")->command.substring(5); c.startsWith("remember that ")->command.substring(14); else->command.substring(9)}.trim()
            if(note.isNotBlank()){saveNote(context,note);return Result("Saved your note: $note")}
        }
        if(c=="show notes" || c=="my notes" || c=="read my notes") {
            val notes=getNotes(context)
            return Result(if(notes.isEmpty()) "You do not have any saved notes." else "Your latest notes are: "+notes.takeLast(5).joinToString(". "))
        }

        if(c.contains("calendar") || c.contains("schedule today") || c.contains("appointments")) return calendarSummary(context)

        parseCall(command)?.let { who -> return dialContact(context,who) }
        parseMessage(command)?.let { (who,msg) -> return messageContact(context,who,msg) }

        if(c.startsWith("open ")) {
            val name=command.substring(5).trim()
            openSpecial(context,name)?.let{return it}
            if(openApp(context,name)) return Result("Opening $name.")
            return Result("I could not find $name. I can search for it instead.")
        }
        if(c.startsWith("launch ")) {
            val name=command.substring(7).trim()
            if(openApp(context,name)) return Result("Launching $name.")
            return Result("I could not find $name on this phone.")
        }

        if(c.startsWith("navigate to ") || c.startsWith("directions to ") || c.startsWith("go to ")) {
            val q=command.substringAfter("to ").trim()
            start(context, Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(q))))
            return Result("Opening directions to $q.")
        }
        if(c.startsWith("youtube ") || c.startsWith("search youtube for ") || c.startsWith("play on youtube ")) {
            val q=command.substringAfterLast("youtube ").removePrefix("for ").trim()
            start(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(q))))
            return Result("Searching YouTube for $q.")
        }
        if(c.startsWith("weather") || c.contains("weather in ")) {
            val q=if(c.contains(" in ")) "weather "+command.substringAfter(" in ") else "weather near me"
            webSearch(context,q);return Result("Opening the latest weather information.")
        }
        if(c=="news" || c.startsWith("latest news") || c.startsWith("news about ")) {
            webSearch(context, if(c.startsWith("news about ")) command else "latest news")
            return Result("Opening current news.")
        }
        if(c.startsWith("search for ") || c.startsWith("google ") || c.startsWith("look up ") || c.startsWith("find ")) {
            val q=command.substringAfter(" ").removePrefix("for ").trim();webSearch(context,q);return Result("Searching for $q.")
        }
        if(c.startsWith("translate ")) { webSearch(context,command+" translation");return Result("Opening translation results.") }
        if(c.startsWith("define ") || c.startsWith("meaning of ")) { webSearch(context,command);return Result("Opening the definition.") }

        if(c.contains("flip a coin")){return Result(if(Random().nextBoolean()) "Heads." else "Tails.")}
        if(c.contains("roll a dice") || c.contains("roll dice")){return Result("You rolled ${Random().nextInt(6)+1}.")}
        if(c.contains("tell me a joke") || c=="joke") return Result(listOf("Why did the computer go to school? To improve its byte-sized knowledge.","Why was the math book worried? It had too many problems.","What do you call a sleeping computer? A nap-top.").random())
        if(c.contains("motivate me") || c.contains("motivation")) return Result(listOf("Small progress every day becomes a big result.","Focus on the next useful step, not the whole staircase.","Consistency beats intensity when intensity cannot last.").random())

        if(c in listOf("good morning","good morning routine")) return goodMorning(context)
        if(c in listOf("good night","good night routine")) return Result("Good night. I hope you have a peaceful rest. You can say set an alarm or set a timer before sleeping.")
        if(c in listOf("study mode","start study mode")){setVolumePercent(context,30);return Result("Study mode is ready. Media volume is set to thirty percent. Say set timer for 25 minutes to start a focus session.")}

        return Result("I did not find a direct device command for that. I can search the web for it.",false)
    }

    fun webSearch(context:Context,q:String){start(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(q))))}

    private fun openSpecial(context:Context,nameRaw:String):Result?{
        val n=nameRaw.lowercase(Locale.getDefault())
        val intent=when {
            n in listOf("wifi","wi-fi","wifi settings") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            n in listOf("bluetooth","bluetooth settings") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            n in listOf("settings","phone settings") -> Intent(Settings.ACTION_SETTINGS)
            n in listOf("display settings","display") -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            n in listOf("battery settings") -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            n in listOf("sound settings","sound") -> Intent(Settings.ACTION_SOUND_SETTINGS)
            n in listOf("location settings","location") -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            n=="camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            else -> null
        }
        if(intent!=null){start(context,intent);return Result("Opening $nameRaw.")}
        return null
    }

    private fun openApp(context:Context,name:String):Boolean{
        val pm=context.packageManager
        val target=name.lowercase(Locale.getDefault()).replace(" ","")
        val apps=pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        val app=apps.firstOrNull { ai ->
            val label=pm.getApplicationLabel(ai).toString().lowercase(Locale.getDefault())
            val compact=label.replace(" ","")
            compact==target || compact.contains(target) || target.contains(compact)
        } ?: return false
        val i=pm.getLaunchIntentForPackage(app.packageName) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return true
    }

    private fun flashlight(context:Context,on:Boolean):Result{
        return try{
            val cm=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id=cm.cameraIdList.firstOrNull{cid->cm.getCameraCharacteristics(cid).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)==true}
            if(id==null) Result("This phone does not report an available flashlight.") else {cm.setTorchMode(id,on);Result(if(on)"Flashlight is on." else "Flashlight is off.")}
        }catch(e:Exception){Result("I could not change the flashlight on this device.")}
    }

    private fun media(context:Context,key:Int){
        val am=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key))
    }
    private fun volume(context:Context,dir:Int):Result{
        val am=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,if(dir>0)AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI)
        return Result(if(dir>0)"Volume increased." else "Volume decreased.")
    }
    private fun mute(context:Context):Result{
        val am=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,AudioManager.FLAG_SHOW_UI);return Result("Media volume muted.")
    }
    private fun setVolumePercent(context:Context,pct:Int){val am=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager; val max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);am.setStreamVolume(AudioManager.STREAM_MUSIC,(max*pct/100f).roundToInt(),0)}

    private fun parseTimer(c:String):Int?{
        if(!(c.contains("timer")||c.startsWith("countdown"))) return null
        val m=Regex("(\\d+)\\s*(second|seconds|minute|minutes|hour|hours)").find(c)?:return null
        val n=m.groupValues[1].toInt();return when {m.groupValues[2].startsWith("second")->n;m.groupValues[2].startsWith("minute")->n*60;else->n*3600}
    }
    private fun parseAlarm(c:String):Pair<Int,Int>?{
        if(!c.contains("alarm")) return null
        val m=Regex("(?:for|at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(c)?:return null
        var h=m.groupValues[1].toInt(); val min=m.groupValues[2].ifBlank{"0"}.toInt(); val ap=m.groupValues[3]
        if(ap=="pm"&&h<12)h+=12;if(ap=="am"&&h==12)h=0;if(h !in 0..23 || min !in 0..59)return null
        return h to min
    }
    private fun parseReminder(c:String):Pair<String,Long>?{
        if(!c.startsWith("remind me to ")) return null
        val m=Regex("remind me to (.+) in (\\d+)\\s*(second|seconds|minute|minutes|hour|hours)").find(c)?:return null
        val text=m.groupValues[1].trim();val n=m.groupValues[2].toLong();val mult=when{m.groupValues[3].startsWith("second")->1000L;m.groupValues[3].startsWith("minute")->60000L;else->3600000L};return text to n*mult
    }
    private fun scheduleReminder(context:Context,text:String,delay:Long){
        val i=Intent(context,ReminderReceiver::class.java).putExtra("text",text)
        val id=(System.currentTimeMillis()%Int.MAX_VALUE).toInt()
        val pi=PendingIntent.getBroadcast(context,id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+delay,pi)
    }

    private fun parseCall(command:String):String?{val c=command.lowercase();return when{c.startsWith("call ")->command.substring(5).trim();c.startsWith("dial ")->command.substring(5).trim();else->null}}
    private fun parseMessage(command:String):Pair<String,String>?{
        val m=Regex("(?i)(?:send )?(?:a )?(?:text|message|sms) to (.+?)(?: saying | message |: )(.+)").find(command)?:return null
        return m.groupValues[1].trim() to m.groupValues[2].trim()
    }
    private fun dialContact(context:Context,who:String):Result{
        val number=if(who.any{it.isLetter()}) findContactNumber(context,who) else who.filter{it.isDigit()||it=='+'}
        if(number==null) return Result("I need Contacts permission, or I could not find $who.")
        start(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(number))))
        return Result("Opening the dialer for $who.")
    }
    private fun messageContact(context:Context,who:String,msg:String):Result{
        val number=if(who.any{it.isLetter()}) findContactNumber(context,who) else who.filter{it.isDigit()||it=='+'}
        if(number==null)return Result("I need Contacts permission, or I could not find $who.")
        val i=Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number))).putExtra("sms_body",msg);start(context,i);return Result("Opening a message to $who. Please review it and tap send.")
    }
    private fun findContactNumber(context:Context,name:String):String?{
        if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return null
        val cr=context.contentResolver
        val cur=cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" LIKE ?",arrayOf("%$name%"),null)
        cur?.use{if(it.moveToFirst())return it.getString(0)};return null
    }

    private fun calendarSummary(context:Context):Result{
        if(context.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)return Result("Please allow Calendar permission in Makoo Do so I can read your upcoming schedule.")
        val now=System.currentTimeMillis();val end=now+24*60*60*1000L
        val builder=CalendarContract.Instances.CONTENT_URI.buildUpon();ContentUris.appendId(builder,now);ContentUris.appendId(builder,end)
        val cur=context.contentResolver.query(builder.build(),arrayOf(CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN),null,null,CalendarContract.Instances.BEGIN+" ASC")
        val items=mutableListOf<String>();cur?.use{while(it.moveToNext()&&items.size<3){val title=it.getString(0)?:"Event";val t=SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(it.getLong(1)));items+="$title at $t"}}
        return Result(if(items.isEmpty())"I found no calendar events in the next 24 hours." else "Your upcoming events are: "+items.joinToString(". "))
    }

    private fun calculate(raw:String):String?{
        var s=raw.lowercase(Locale.getDefault()).replace("what is"," ").replace("calculate"," ").replace("plus","+").replace("minus","-").replace("times","*").replace("multiplied by","*").replace("divided by","/").replace("x","*").trim()
        val m=Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)").find(s)?:return null
        val a=m.groupValues[1].toDouble();val b=m.groupValues[3].toDouble();val v=when(m.groupValues[2]){"+"->a+b;"-"->a-b;"*"->a*b;"/"->if(b==0.0)return "I cannot divide by zero." else a/b;else->return null};val out=if(v%1.0==0.0)v.toLong().toString() else "%.4f".format(v).trimEnd('0').trimEnd('.');return "The answer is $out."
    }

    private fun goodMorning(context:Context):Result{val bm=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager;val pct=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);val t=SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date());val d=SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(Date());return Result("Good morning. It is $t on $d. Your battery is at $pct percent. Say weather, calendar, news, or start study mode for the next step.")}

    private fun start(context:Context,i:Intent){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{context.startActivity(i)}catch(_:Exception){}}
    private fun formatTime(h:Int,m:Int):String{val cal=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m)};return SimpleDateFormat("h:mm a",Locale.getDefault()).format(cal.time)}
    private fun friendlyDuration(s:Int):String=when{ s>=3600 && s%3600==0 -> "${s/3600} hour${if(s/3600==1)"" else "s"}"; s>=60 && s%60==0 -> "${s/60} minute${if(s/60==1)"" else "s"}"; else -> "$s seconds" }

    fun saveNote(context:Context,note:String){val p=context.getSharedPreferences("makoo_do",Context.MODE_PRIVATE);val list=getNotes(context).toMutableList();list+=note;p.edit().putString("notes",list.takeLast(100).joinToString("\\u001F")).apply()}
    fun getNotes(context:Context):List<String>{val s=context.getSharedPreferences("makoo_do",Context.MODE_PRIVATE).getString("notes","")?:"";return if(s.isBlank())emptyList() else s.split("\\u001F").filter{it.isNotBlank()}}
    fun saveHistory(context:Context,cmd:String){val p=context.getSharedPreferences("makoo_do",Context.MODE_PRIVATE);val old=(p.getString("history","")?:"").split("\\u001F").filter{it.isNotBlank()}.toMutableList();old+=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())+"  "+cmd;p.edit().putString("history",old.takeLast(60).joinToString("\\u001F")).apply()}
    fun getHistory(context:Context):List<String>{val s=context.getSharedPreferences("makoo_do",Context.MODE_PRIVATE).getString("history","")?:"";return if(s.isBlank())emptyList() else s.split("\\u001F").filter{it.isNotBlank()}}
    fun addShortcut(context:Context,phrase:String,command:String){val p=context.getSharedPreferences("makoo_do",Context.MODE_PRIVATE);val old=p.getString("shortcuts","")?:"";val rows=old.split("\\u001E").filter{it.isNotBlank()}.toMutableList();rows.removeAll{it.substringBefore("\\u001F").equals(phrase,true)};rows+=(phrase.trim()+"\\u001F"+command.trim());p.edit().putString("shortcuts",rows.takeLast(50).joinToString("\\u001E")).apply()}
    fun getShortcuts(context:Context):List<Pair<String,String>>{val s=context.getSharedPreferences("makoo_do",Context.MODE_PRIVATE).getString("shortcuts","")?:"";return s.split("\\u001E").mapNotNull{val a=it.split("\\u001F");if(a.size>=2)a[0] to a[1] else null}}
    private fun customShortcut(context:Context,c:String):String?=getShortcuts(context).firstOrNull{it.first.equals(c,true)}?.second
}
