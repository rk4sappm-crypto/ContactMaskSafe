package org.ucpl.makoodo

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class VoiceAssistantService:Service(),TextToSpeech.OnInitListener{
    private var recognizer:SpeechRecognizer?=null
    private lateinit var tts:TextToSpeech
    private val handler=Handler(Looper.getMainLooper())
    private var speaking=false
    private var armedUntil=0L
    private var destroyed=false

    companion object { const val ACTION_STOP="org.ucpl.makoodo.STOP"; const val CHANNEL="makoo_voice" }

    override fun onCreate(){super.onCreate();tts=TextToSpeech(this,this);createChannel()}
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==ACTION_STOP){stopSelf();return START_NOT_STICKY}
        startForeground(7001,notification("Listening for Hey Makoo • Makoo Do"))
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)handler.postDelayed({listen()},500)
        return START_STICKY
    }
    override fun onInit(status:Int){if(status==TextToSpeech.SUCCESS){tts.language=Locale.getDefault();tts.setSpeechRate(0.92f);tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){speaking=true};override fun onDone(id:String?){speaking=false;handler.postDelayed({listen()},700)};@Deprecated("Deprecated") override fun onError(id:String?){speaking=false;handler.postDelayed({listen()},900)}})}}

    private fun listen(){
        if(destroyed||speaking||checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return
        try{
            recognizer?.destroy()
            val sr=SpeechRecognizer.createSpeechRecognizer(this);recognizer=sr
            sr.setRecognitionListener(object:RecognitionListener{
                override fun onReadyForSpeech(p:Bundle?){};override fun onBeginningOfSpeech(){};override fun onRmsChanged(v:Float){};override fun onBufferReceived(b:ByteArray?){};override fun onEndOfSpeech(){}
                override fun onError(error:Int){if(!destroyed)handler.postDelayed({listen()},when(error){SpeechRecognizer.ERROR_RECOGNIZER_BUSY->1400;else->700})}
                override fun onResults(results:Bundle?){val heard=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();handleHeard(heard)}
                override fun onPartialResults(p:Bundle?){};override fun onEvent(t:Int,p:Bundle?){}
            })
            val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)}
            sr.startListening(i)
        }catch(_:Exception){handler.postDelayed({listen()},1500)}
    }

    private fun handleHeard(raw:String){
        val now=System.currentTimeMillis();val hasWake=AssistantCore.hasWake(raw)
        if(hasWake){
            val cmd=AssistantCore.stripWake(raw)
            if(cmd.isBlank()){armedUntil=now+10000;speak("Yes?")} else {armedUntil=0;runCommand(cmd)}
        } else if(now<armedUntil){armedUntil=0;runCommand(raw)} else handler.postDelayed({listen()},600)
    }
    private fun runCommand(cmd:String){val r=AssistantCore.process(this,cmd);if(r.handled)speak(r.text) else {speak("I can search the web for that when you open Makoo Do.")}}
    private fun speak(text:String){speaking=true;recognizer?.cancel();tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"makoo_${System.currentTimeMillis()}")}

    private fun createChannel(){if(Build.VERSION.SDK_INT>=26){val nm=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;nm.createNotificationChannel(NotificationChannel(CHANNEL,"Makoo Do Voice Assistant",NotificationManager.IMPORTANCE_LOW).apply{description="Keeps optional wake-phrase listening active"})}}
    private fun notification(text:String):Notification{
        val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,2,Intent(this,VoiceAssistantService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,if(Build.VERSION.SDK_INT>=26)CHANNEL else "").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Makoo Do is listening").setContentText(text).setOngoing(true).setContentIntent(open).addAction(android.R.drawable.ic_media_pause,"Stop",stop).build()
    }
    override fun onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);recognizer?.destroy();tts.stop();tts.shutdown();super.onDestroy()}
}
