package org.ucpl.makookidslanguages

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    data class LanguagePack(
        val name: String, val nativeName: String, val locale: Locale,
        val hello: String, val thanks: String, val yes: String, val no: String,
        val child: String, val water: String, val book: String, val school: String,
        val phrase: String, val reply: String
    )

    private lateinit var tts: TextToSpeech
    private var speech: SpeechRecognizer? = null
    private lateinit var targetText: TextView
    private lateinit var resultText: TextView
    private lateinit var scoreText: TextView
    private lateinit var languageSpinner: Spinner
    private lateinit var lessonSpinner: Spinner
    private var currentTarget = "Hello"
    private var stars = 0

    private val packs = listOf(
        LanguagePack("English","English",Locale.US,"Hello","Thank you","Yes","No","Child","Water","Book","School","How are you?","I am fine."),
        LanguagePack("Hindi","हिन्दी",Locale("hi","IN"),"नमस्ते","धन्यवाद","हाँ","नहीं","बच्चा","पानी","किताब","विद्यालय","आप कैसे हैं?","मैं ठीक हूँ।"),
        LanguagePack("Sanskrit","संस्कृतम्",Locale("sa","IN"),"नमस्ते","धन्यवादः","आम्","न","बालकः","जलम्","पुस्तकम्","विद्यालयः","भवान् कथम् अस्ति?","अहं कुशली अस्मि।"),
        LanguagePack("Spanish","Español",Locale("es","ES"),"Hola","Gracias","Sí","No","Niño","Agua","Libro","Escuela","¿Cómo estás?","Estoy bien."),
        LanguagePack("French","Français",Locale.FRANCE,"Bonjour","Merci","Oui","Non","Enfant","Eau","Livre","École","Comment ça va ?","Ça va bien."),
        LanguagePack("German","Deutsch",Locale.GERMANY,"Hallo","Danke","Ja","Nein","Kind","Wasser","Buch","Schule","Wie geht es dir?","Mir geht es gut."),
        LanguagePack("Japanese","日本語",Locale.JAPAN,"こんにちは","ありがとう","はい","いいえ","こども","みず","ほん","がっこう","お元気ですか？","元気です。"),
        LanguagePack("Chinese","中文",Locale.SIMPLIFIED_CHINESE,"你好","谢谢","是","不是","孩子","水","书","学校","你好吗？","我很好。"),
        LanguagePack("Korean","한국어",Locale.KOREA,"안녕하세요","감사합니다","네","아니요","아이","물","책","학교","어떻게 지내요?","잘 지내요."),
        LanguagePack("Italian","Italiano",Locale.ITALY,"Ciao","Grazie","Sì","No","Bambino","Acqua","Libro","Scuola","Come stai?","Sto bene."),
        LanguagePack("Portuguese","Português",Locale("pt","BR"),"Olá","Obrigado","Sim","Não","Criança","Água","Livro","Escola","Como você está?","Estou bem."),
        LanguagePack("Russian","Русский",Locale("ru","RU"),"Привет","Спасибо","Да","Нет","Ребёнок","Вода","Книга","Школа","Как дела?","Хорошо."),
        LanguagePack("Arabic","العربية",Locale("ar","SA"),"مرحبا","شكرا","نعم","لا","طفل","ماء","كتاب","مدرسة","كيف حالك؟","أنا بخير."),
        LanguagePack("Bengali","বাংলা",Locale("bn","IN"),"নমস্কার","ধন্যবাদ","হ্যাঁ","না","শিশু","জল","বই","স্কুল","তুমি কেমন আছ?","আমি ভালো আছি।"),
        LanguagePack("Punjabi","ਪੰਜਾਬੀ",Locale("pa","IN"),"ਸਤ ਸ੍ਰੀ ਅਕਾਲ","ਧੰਨਵਾਦ","ਹਾਂ","ਨਹੀਂ","ਬੱਚਾ","ਪਾਣੀ","ਕਿਤਾਬ","ਸਕੂਲ","ਤੁਸੀਂ ਕਿਵੇਂ ਹੋ?","ਮੈਂ ਠੀਕ ਹਾਂ।"),
        LanguagePack("Gujarati","ગુજરાતી",Locale("gu","IN"),"નમસ્તે","આભાર","હા","ના","બાળક","પાણી","પુસ્તક","શાળા","તમે કેમ છો?","હું મજામાં છું."),
        LanguagePack("Marathi","मराठी",Locale("mr","IN"),"नमस्कार","धन्यवाद","हो","नाही","मूल","पाणी","पुस्तक","शाळा","तुम्ही कसे आहात?","मी ठीक आहे."),
        LanguagePack("Tamil","தமிழ்",Locale("ta","IN"),"வணக்கம்","நன்றி","ஆம்","இல்லை","குழந்தை","தண்ணீர்","புத்தகம்","பள்ளி","நீங்கள் எப்படி இருக்கிறீர்கள்?","நான் நன்றாக இருக்கிறேன்."),
        LanguagePack("Telugu","తెలుగు",Locale("te","IN"),"నమస్తే","ధన్యవాదాలు","అవును","కాదు","పిల్ల","నీరు","పుస్తకం","పాఠశాల","మీరు ఎలా ఉన్నారు?","నేను బాగున్నాను."),
        LanguagePack("Malayalam","മലയാളം",Locale("ml","IN"),"നമസ്കാരം","നന്ദി","അതെ","അല്ല","കുട്ടി","വെള്ളം","പുസ്തകം","സ്കൂൾ","സുഖമാണോ?","എനിക്ക് സുഖമാണ്."),
        LanguagePack("Kannada","ಕನ್ನಡ",Locale("kn","IN"),"ನಮಸ್ಕಾರ","ಧನ್ಯವಾದಗಳು","ಹೌದು","ಇಲ್ಲ","ಮಗು","ನೀರು","ಪುಸ್ತಕ","ಶಾಲೆ","ನೀವು ಹೇಗಿದ್ದೀರಿ?","ನಾನು ಚೆನ್ನಾಗಿದ್ದೇನೆ."),
        LanguagePack("Urdu","اردو",Locale("ur","IN"),"السلام علیکم","شکریہ","ہاں","نہیں","بچہ","پانی","کتاب","اسکول","آپ کیسے ہیں؟","میں ٹھیک ہوں۔"),
        LanguagePack("Thai","ไทย",Locale("th","TH"),"สวัสดี","ขอบคุณ","ใช่","ไม่","เด็ก","น้ำ","หนังสือ","โรงเรียน","สบายดีไหม?","สบายดี"),
        LanguagePack("Indonesian","Bahasa Indonesia",Locale("id","ID"),"Halo","Terima kasih","Ya","Tidak","Anak","Air","Buku","Sekolah","Apa kabar?","Saya baik."),
        LanguagePack("Turkish","Türkçe",Locale("tr","TR"),"Merhaba","Teşekkürler","Evet","Hayır","Çocuk","Su","Kitap","Okul","Nasılsın?","İyiyim.")
    )

    private val lessons = listOf("Greetings", "Magic Words", "Yes & No", "Family & Kids", "Food & Water", "Books & School", "Mini Conversation", "Speak & Score")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
        updateLesson()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(255,248,235))
        }
        val title = TextView(this).apply {
            text = "🌈 Makoo Kids Languages"
            textSize = 27f; setTextColor(Color.rgb(95,55,15)); gravity = Gravity.CENTER
        }
        val brand = TextView(this).apply {
            text = "Listen • Learn • Speak • Play\nDesigned and Developed in India by UCPL Technologies"
            textSize = 13f; gravity = Gravity.CENTER; setPadding(0,8,0,18)
        }
        languageSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, packs.map { "${it.name} — ${it.nativeName}" })
            onItemSelectedListener = simpleListener { updateLesson() }
        }
        lessonSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, lessons)
            onItemSelectedListener = simpleListener { updateLesson() }
        }
        targetText = TextView(this).apply {
            textSize = 31f; gravity = Gravity.CENTER; setTextColor(Color.rgb(30,95,150)); setPadding(12,35,12,35)
        }
        resultText = TextView(this).apply { textSize = 17f; gravity = Gravity.CENTER; setPadding(8,10,8,10) }
        scoreText = TextView(this).apply { text = "⭐ Stars: 0"; textSize = 18f; gravity = Gravity.CENTER; setPadding(0,8,0,12) }
        val listen = Button(this).apply { text = "🔊 Listen"; setOnClickListener { speakTarget() } }
        val speakBtn = Button(this).apply { text = "🎤 Speak"; setOnClickListener { startListening() } }
        val next = Button(this).apply { text = "➡ Next Practice"; setOnClickListener { cycleTarget() } }
        val tip = TextView(this).apply {
            text = "Tip for parents: Start with 5–10 minutes daily. Let the child listen first, then repeat naturally."
            textSize = 13f; gravity = Gravity.CENTER; setPadding(8,18,8,4)
        }
        listOf(title,brand,label("Choose Language"),languageSpinner,label("Choose Lesson"),lessonSpinner,targetText,listen,speakBtn,next,resultText,scoreText,tip).forEach { root.addView(it, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,6,0,6) }) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(s: String) = TextView(this).apply { text = s; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(2,8,2,0) }

    private fun simpleListener(block: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = block()
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun currentPack() = packs[languageSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun lessonTargets(p: LanguagePack): List<String> = when(lessonSpinner.selectedItemPosition) {
        0 -> listOf(p.hello, p.phrase, p.reply)
        1 -> listOf(p.thanks, p.hello)
        2 -> listOf(p.yes, p.no)
        3 -> listOf(p.child, p.hello)
        4 -> listOf(p.water, p.thanks)
        5 -> listOf(p.book, p.school)
        6 -> listOf(p.phrase, p.reply, p.hello)
        else -> listOf(p.hello, p.thanks, p.water, p.book, p.school, p.phrase, p.reply)
    }

    private var targetIndex = 0
    private fun updateLesson() {
        targetIndex = 0
        val p = currentPack()
        currentTarget = lessonTargets(p)[0]
        if (::targetText.isInitialized) {
            targetText.text = currentTarget
            resultText.text = "Tap Listen, then Speak and repeat."
        }
    }

    private fun cycleTarget() {
        val list = lessonTargets(currentPack())
        targetIndex = (targetIndex + 1) % list.size
        currentTarget = list[targetIndex]
        targetText.text = currentTarget
        resultText.text = "New practice phrase"
    }

    private fun speakTarget() {
        tts.language = currentPack().locale
        tts.setSpeechRate(0.78f)
        tts.speak(currentTarget, TextToSpeech.QUEUE_FLUSH, null, "target")
    }

    override fun onInit(status: Int) {}

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 44); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            resultText.text = "Speech recognition is not available on this device."; return
        }
        speech?.destroy(); speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { resultText.text = "Listening…" }
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val score = similarity(normalize(heard), normalize(currentTarget))
                if (score >= 0.70) { stars++; scoreText.text = "⭐ Stars: $stars" }
                resultText.text = "You said: $heard\nSpeaking score: ${(score*100).toInt()}%" + if(score>=0.70) " 🎉 Great job!" else " — Try once more."
            }
            override fun onError(error: Int) { resultText.text = "I couldn't hear that clearly. Please try again." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentPack().locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: $currentTarget")
        })
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "").trim()

    private fun similarity(a: String, b: String): Double {
        if (a == b && a.isNotEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val prev = IntArray(b.length+1) { it }
        val cur = IntArray(b.length+1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(cur[j-1]+1, prev[j]+1, prev[j-1] + if(a[i-1]==b[j-1]) 0 else 1)
            for (j in prev.indices) prev[j] = cur[j]
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length,b.length)
    }

    override fun onDestroy() {
        speech?.destroy(); tts.stop(); tts.shutdown(); super.onDestroy()
    }
}
