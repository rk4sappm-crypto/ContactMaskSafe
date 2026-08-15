package org.ucpl.makoodo

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.*

class MainActivity:Activity(),TextToSpeech.OnInitListener{
    private val navy=Color.rgb(8,42,94);private val orange=Color.rgb(255,122,0);private val cream=Color.rgb(255,248,240);private val paleBlue=Color.rgb(235,246,255);private val paleOrange=Color.rgb(255,240,220);private val green=Color.rgb(34,145,91);private val muted=Color.rgb(90,100,115)
    private lateinit var tts:TextToSpeech;private var ttsReady=false;private var speech:SpeechRecognizer?=null
    private lateinit var content:LinearLayout;private lateinit var status:TextView;private lateinit var input:EditText;private lateinit var bgButton:Button;private lateinit var transcript:TextView
    private var currentTab="Home"

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);tts=TextToSpeech(this,this);buildUi();requestEssentialPermissions()}
    override fun onInit(s:Int){ttsReady=s==TextToSpeech.SUCCESS;if(ttsReady){tts.language=Locale.getDefault();tts.setSpeechRate(.92f)}}
    override fun onDestroy(){speech?.destroy();tts.stop();tts.shutdown();super.onDestroy()}

    private fun buildUi(){
        window.statusBarColor=navy;window.navigationBarColor=cream
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(cream);setPadding(dp(12),dp(10),dp(12),dp(18))}
        root.addView(header());root.addView(spacer(10));root.addView(assistantBar());root.addView(spacer(10));root.addView(nav());root.addView(spacer(10))
        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(content)
        root.addView(TextView(this).apply{text="Designed and Developed in India by UCPL Technologies";textSize=12.5f;gravity=Gravity.CENTER;setTextColor(navy);setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(18),0,dp(5))})
        val sv=ScrollView(this).apply{isFillViewport=true;addView(root,ViewGroup.LayoutParams(-1,-2))};setContentView(sv);showHome()
    }

    private fun header():View=card(Color.WHITE,22).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))
        addView(ImageView(this@MainActivity).apply{setImageResource(R.drawable.makoo_logo);scaleType=ImageView.ScaleType.CENTER_CROP},LinearLayout.LayoutParams(dp(72),dp(72)).apply{setMargins(0,0,dp(12),0)})
        addView(LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;addView(text("Makoo Do",25f,navy,true));addView(text("Voice Assistant • Command Center",14f,orange,true));addView(text("Say: Hey Makoo… or Makoo Do…",12.5f,muted,false))},LinearLayout.LayoutParams(0,-2,1f))
    }

    private fun assistantBar():View=card(paleBlue,20).apply{
        orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))
        status=text("Ready",14f,green,true);addView(status)
        transcript=text("Tap the microphone or type a command below.",14f,navy,false).apply{setPadding(0,dp(5),0,dp(8))};addView(transcript)
        val row=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        input=EditText(this@MainActivity).apply{hint="Try: set timer for 10 minutes";setSingleLine(true);setTextColor(navy);setHintTextColor(muted);setPadding(dp(12),0,dp(8),0);background=rounded(Color.WHITE,18)}
        row.addView(input,LinearLayout.LayoutParams(0,dp(54),1f).apply{setMargins(0,0,dp(8),0)})
        row.addView(button("🎙",orange,Color.WHITE){startOneShot()},LinearLayout.LayoutParams(dp(58),dp(54)))
        addView(row)
        addView(button("Ask Makoo",navy,Color.WHITE){val q=input.text.toString().trim();if(q.isNotBlank()){hideKeyboard();runCommand(q);input.setText("")}},LinearLayout.LayoutParams(-1,dp(48)).apply{setMargins(0,dp(8),0,0)})
        bgButton=button("▶ Start background wake listening",Color.WHITE,navy){toggleBackground()};addView(bgButton,LinearLayout.LayoutParams(-1,dp(48)).apply{setMargins(0,dp(7),0,0)})
    }

    private fun nav():View{
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        listOf(listOf("Home","Commands","Routines"),listOf("Notes","Shortcuts","History")).forEach{names->val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};names.forEach{name->r.addView(button(name,if(name==currentTab)navy else Color.WHITE,if(name==currentTab)Color.WHITE else navy){showTab(name)},LinearLayout.LayoutParams(0,dp(46),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))})};box.addView(r)};return box
    }
    private fun showTab(name:String){currentTab=name;when(name){"Home"->showHome();"Commands"->showCommands();"Routines"->showRoutines();"Notes"->showNotes();"Shortcuts"->showShortcuts();"History"->showHistory()}}

    private fun showHome(){content.removeAllViews();content.addView(sectionTitle("Your assistant dashboard"));content.addView(grid(listOf(
        "⏰ Timer 10 min" to "set timer for 10 minutes","🔦 Flashlight" to "turn on flashlight","🔋 Battery" to "battery status","🗓 Calendar" to "calendar","🎵 Play media" to "play music","📍 Navigation" to "navigate to India Gate","🌦 Weather" to "weather","📰 News" to "latest news","📱 Open WhatsApp" to "open whatsapp","🧮 Calculator" to "what is 125 plus 75","📝 Save note" to "note buy milk","🎲 Roll dice" to "roll a dice")))
        content.addView(spacer(10));content.addView(card(paleOrange,18).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(11),dp(12),dp(11));addView(text("Wake phrases",16f,navy,true));addView(text("Hey Makoo • Hello Makoo • Suno Makoo • Hey Maku • Makoo Do",13.5f,muted,false).apply{setPadding(0,dp(4),0,0)});addView(text("Background listening is optional and always shows an Android foreground-service notification.",12.5f,muted,false).apply{setPadding(0,dp(6),0,0)})})
        content.addView(spacer(10));content.addView(permissionCard())
    }

    private fun showCommands(){content.removeAllViews();content.addView(sectionTitle("Things Makoo Do understands"));val groups=listOf(
        "Everyday" to listOf("What time is it?","What's today's date?","Battery status","Tell me a joke","Motivate me","Roll a dice","Flip a coin"),
        "Device & media" to listOf("Turn on flashlight","Volume up","Pause music","Next song","Open Wi-Fi settings","Open Bluetooth","Open camera","Open YouTube"),
        "Productivity" to listOf("Set alarm for 7:30 am","Set timer for 25 minutes","Remind me to drink water in 30 minutes","Note call supplier tomorrow","Show notes","Calendar"),
        "Communication" to listOf("Call Rahul","Send message to Rahul saying I will call you later","Open WhatsApp","Open Gmail"),
        "Search & travel" to listOf("Search for SAP news","Weather in Delhi","Latest news","Navigate to Connaught Place","YouTube relaxing music","Meaning of resilience"))
        groups.forEach{(title,items)->content.addView(card(Color.WHITE,18).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));addView(text(title,16f,navy,true));items.forEach{cmd->val v=text("• $cmd",13.5f,muted,false).apply{setPadding(0,dp(5),0,dp(3));setOnClickListener{runCommand(cmd)}};addView(v)}});content.addView(spacer(8))}
    }

    private fun showRoutines(){content.removeAllViews();content.addView(sectionTitle("One-tap routines"));listOf(
        Triple("☀ Good Morning","Time, date, battery and next-step suggestions","good morning routine"),
        Triple("📚 Study Mode","Set a calm media volume and suggest a focus timer","study mode"),
        Triple("🌙 Good Night","A simple bedtime assistant flow","good night routine"),
        Triple("🗓 My Day","Read upcoming calendar events (permission required)","calendar")
    ).forEach{(t,d,c)->content.addView(card(Color.WHITE,18).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));addView(text(t,17f,navy,true));addView(text(d,13f,muted,false).apply{setPadding(0,dp(4),0,dp(7))});addView(button("Run",navy,Color.WHITE){runCommand(c)},LinearLayout.LayoutParams(-1,dp(44))) });content.addView(spacer(8))}
        content.addView(card(paleBlue,18).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));addView(text("Tip",15f,navy,true));addView(text("Use Custom Shortcuts to make your own phrases. Example: phrase ‘office time’ → command ‘navigate to office’. Makoo Do then executes the mapped command.",13f,muted,false))})
    }

    private fun showNotes(){content.removeAllViews();content.addView(sectionTitle("Saved notes"));val notes=AssistantCore.getNotes(this);if(notes.isEmpty())content.addView(text("No notes yet. Say ‘note buy milk’ or type it above.",14f,muted,false)) else notes.reversed().forEachIndexed{i,n->content.addView(card(Color.WHITE,16).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(11),dp(8),dp(11),dp(8));addView(text("${notes.size-i}. $n",14f,navy,false),LinearLayout.LayoutParams(0,-2,1f))});content.addView(spacer(6))}}

    private fun showShortcuts(){content.removeAllViews();content.addView(sectionTitle("Custom voice shortcuts"));content.addView(button("＋ Add custom shortcut",orange,Color.WHITE){addShortcutDialog()},LinearLayout.LayoutParams(-1,dp(48)));content.addView(spacer(8));val items=AssistantCore.getShortcuts(this);if(items.isEmpty())content.addView(text("Example: phrase ‘office time’ → command ‘navigate to Connaught Place’.",14f,muted,false)) else items.forEach{(p,c)->content.addView(card(Color.WHITE,16).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(11),dp(8),dp(11),dp(8));addView(text("Say: $p",14f,navy,true));addView(text("Runs: $c",13f,muted,false));setOnClickListener{runCommand(p)}});content.addView(spacer(6))}}

    private fun showHistory(){content.removeAllViews();content.addView(sectionTitle("Recent commands"));val h=AssistantCore.getHistory(this);if(h.isEmpty())content.addView(text("No command history yet.",14f,muted,false)) else h.reversed().take(40).forEach{x->content.addView(text("• $x",13.5f,navy,false).apply{setPadding(dp(4),dp(5),0,dp(5))})}}

    private fun addShortcutDialog(){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0)};val p=EditText(this).apply{hint="Voice phrase, e.g. office time"};val c=EditText(this).apply{hint="Command, e.g. navigate to office"};box.addView(p);box.addView(c);AlertDialog.Builder(this).setTitle("New Makoo Do shortcut").setView(box).setPositiveButton("Save"){_,_->if(p.text.toString().isNotBlank()&&c.text.toString().isNotBlank()){AssistantCore.addShortcut(this,p.text.toString(),c.text.toString());showShortcuts();speak("Shortcut saved.")}}.setNegativeButton("Cancel",null).show()}

    private fun runCommand(q:String){transcript.text="You: $q";status.text="Working…";val r=AssistantCore.process(this,q);status.text=if(r.handled)"Done" else "Web answer available";transcript.text="Makoo Do: ${r.text}";if(r.handled)speak(r.text) else AlertDialog.Builder(this).setTitle("Makoo Do").setMessage(r.text+"\n\nSearch the web for: $q ?").setPositiveButton("Search"){_,_->AssistantCore.webSearch(this,q)}.setNegativeButton("Cancel",null).show();if(currentTab=="History")showHistory()}

    private fun startOneShot(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10);return};speech?.destroy();val sr=SpeechRecognizer.createSpeechRecognizer(this);speech=sr;status.text="Listening…";sr.setRecognitionListener(object:RecognitionListener{override fun onReadyForSpeech(p:Bundle?){};override fun onBeginningOfSpeech(){};override fun onRmsChanged(r:Float){};override fun onBufferReceived(b:ByteArray?){};override fun onEndOfSpeech(){status.text="Thinking…"};override fun onError(e:Int){status.text="Could not hear clearly"};override fun onResults(b:Bundle?){val q=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();if(q.isNotBlank())runCommand(q) else status.text="I did not hear a command"};override fun onPartialResults(p:Bundle?){};override fun onEvent(t:Int,p:Bundle?){}});sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)})}

    private fun toggleBackground(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),11);return};if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),12)};val running=getSharedPreferences("makoo_do",MODE_PRIVATE).getBoolean("bg",false);if(running){stopService(Intent(this,VoiceAssistantService::class.java));getSharedPreferences("makoo_do",MODE_PRIVATE).edit().putBoolean("bg",false).apply();bgButton.text="▶ Start background wake listening";status.text="Background listening stopped"}else{val i=Intent(this,VoiceAssistantService::class.java);if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i);getSharedPreferences("makoo_do",MODE_PRIVATE).edit().putBoolean("bg",true).apply();bgButton.text="■ Stop background wake listening";status.text="Background listening started"}}

    private fun requestEssentialPermissions(){val req=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)req+=Manifest.permission.RECORD_AUDIO;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req+=Manifest.permission.POST_NOTIFICATIONS;if(req.isNotEmpty())requestPermissions(req.toTypedArray(),9);refreshBgButton()}
    private fun refreshBgButton(){val running=getSharedPreferences("makoo_do",MODE_PRIVATE).getBoolean("bg",false);bgButton.text=if(running)"■ Stop background wake listening" else "▶ Start background wake listening"}
    private fun permissionCard():View=card(Color.WHITE,18).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));addView(text("Optional permissions",16f,navy,true));addView(text("Contacts lets ‘Call Rahul’ and message commands resolve saved names. Calendar lets Makoo Do summarize upcoming events. Camera supports flashlight/camera features.",12.8f,muted,false).apply{setPadding(0,dp(4),0,dp(7))});addView(button("Allow Contacts + Calendar + Camera",Color.WHITE,navy){val a=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)a+=Manifest.permission.READ_CONTACTS;if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)a+=Manifest.permission.READ_CALENDAR;if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)a+=Manifest.permission.CAMERA;if(a.isNotEmpty())requestPermissions(a.toTypedArray(),14) else speak("Optional permissions are already allowed.")},LinearLayout.LayoutParams(-1,dp(46)))}

    private fun grid(items:List<Pair<String,String>>):View{val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};items.chunked(2).forEach{pair->val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};pair.forEach{(label,cmd)->r.addView(button(label,Color.WHITE,navy){runCommand(cmd)},LinearLayout.LayoutParams(0,dp(58),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))})};if(pair.size==1)r.addView(View(this),LinearLayout.LayoutParams(0,dp(58),1f));box.addView(r)};return box}
    private fun sectionTitle(s:String)=text(s,20f,navy,true).apply{setPadding(dp(3),dp(2),0,dp(8))}
    private fun speak(s:String){if(ttsReady)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"ui_${System.currentTimeMillis()}")}
    private fun hideKeyboard(){(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)}
    private fun text(s:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD)}
    private fun button(s:String,bg:Int,fg:Int,on:()->Unit)=Button(this).apply{text=s;textSize=13f;isAllCaps=false;setTextColor(fg);setTypeface(typeface,Typeface.BOLD);background=rounded(bg,16);setOnClickListener{on()}}
    private fun card(color:Int,r:Int)=LinearLayout(this).apply{background=rounded(color,r);elevation=dp(2).toFloat()}
    private fun rounded(color:Int,r:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat();setStroke(dp(1),Color.argb(28,8,42,94))}
    private fun spacer(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
