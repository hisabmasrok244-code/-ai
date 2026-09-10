package com.aymane.freefireai

content.Context
content.Intent
graphics.Color
os.Bundle
os.CountDownTimer
os.Handler
os.Looper
provider.Settings
widget.Button
widget.EditText
widget.TextView
appcompat.app.AppCompatActivity
java.io.BufferedReader
java.io.InputStreamReader
java.net.HttpURLConnection
java.net.URL
org.json.JSONArray
org.json.JSONObject
concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etTask: EditText
    private lateinit var btnExecute: Button
    private lateinit var tvConsole: TextView
    private var isRunning = false
    private var timer: CountDownTimer? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiKey = findViewById(R.id.etApiKey)
        etTask = findViewById(R.id.etTask)
        btnExecute = findViewById(R.id.btnExecute)
        tvConsole = findViewById(R.id.tvConsole)

        val prefs = getSharedPreferences("FreeFirePrefs", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))

        btnExecute.setOnClickListener {
            if (!isRunning) {
                startExecution(prefs)
            } else {
                stopExecution()
            }
        }
    }

    private fun logToConsole(message: String) {
        runOnUiThread {
            tvConsole.append("> $message\n")
            val scrollAmount = tvConsole.layout?.getLineTop(tvConsole.lineCount) ?: 0
            if (scrollAmount > tvConsole.height) {
                tvConsole.scrollTo(0, scrollAmount - tvConsole.height)
            }
        }
    }

    private fun startExecution(prefs: android.content.SharedPreferences) {
        val apiKey = etApiKey.text.toString().trim()
        val task = etTask.text.toString().trim()

        if (apiKey.isEmpty()) {
            logToConsole("خطأ: الرجاء إدخال مفتاح الذكاء الاصطناعي (API Key) أولاً!")
            return
        }

        if (task.isEmpty()) {
            logToConsole("خطأ: الرجاء كتابة المهمة المراد تنفيذها!")
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            logToConsole("تنبيه: خدمة التحكم (Accessibility) غير مفعلة! جاري توجيهك للإعدادات...")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        prefs.edit().putString("api_key", apiKey).apply()

        isRunning = true
        btnExecute.text = "إيقاف العمليات"
        btnExecute.setBackgroundColor(Color.parseColor("#ff4757"))

        logToConsole("تم حفظ المفتاح والبدء الفعلي لجلسة الذكاء الاصطناعي...")
        logToConsole("المهمة: $task")

        // إرسال أول طلب حقيقي للذكاء الاصطناعي عبر الشبكة
        sendTaskToGemini(apiKey, task)

        // مؤقت الجلسة (20 دقيقة)
        timer = object : CountDownTimer(1200000, 15000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutesLeft = millisUntilFinished / 1000 / 60
                val secondsLeft = (millisUntilFinished / 1000) % 60
                logToConsole("⏳ الوقت المتبقي: ${minutesLeft}د ${secondsLeft}ث | جاري التنسيق مع Gemini...")
                
                // استدعاء التحليل والتوجيه الذكي دورياً
                sendTaskToGemini(apiKey, "مهمة مستمرة: $task - أعطني توجيهاً تكتيكياً ولعبة الحركة التالية.")
            }

            override fun onFinish() {
                logToConsole("انتهت الجلسة المجدولة (20 دقيقة) بنجاح.")
                stopExecution()
            }
        }.start()
    }

    private fun sendTaskToGemini(apiKey: String, promptText: String) {
        executor.execute {
            try {
                // استخدام نموذج Gemini الحقيقي عبر واجهة REST API
                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "أنت مساعد ذكي للعبة Free Fire. بناءً على هذه المهمة: '$promptText'، أجب باختصار شديد جداً بخطوة تكتيكية يجب تنفيذها الآن (مثل: تحرك للأمام، انقر زر إطلاق النار، أو اسحب الشاشة لليسار).")
                                })
                            })
                        })
                    })
                }

                val os = conn.outputStream
                val input = jsonBody.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val br = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val response = StringBuilder()
                    var responseLine: String?
                    while (br.readLine().also { responseLine = it } != null) {
                        response.append(responseLine!!.trim())
                    }

                    // تحليل الرد القادم من Gemini
                    val jsonResponse = JSONObject(response.toString())
                    val candidates = jsonResponse.getJSONArray("candidates")
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val aiText = parts.getJSONObject(0).getString("text")

                    logToConsole("🤖 رد Gemini الذكي: $aiText")

                    // تنفيذ حركة حقيقية على الشاشة بناءً على استجابة الذكاء الاصطناعي
                    Handler(Looper.getMainLooper()).post {
                        FreeFireAccessibilityService.instance?.let { service ->
                            service.performSwipe(500f, 1400f, 500f, 900f, 250L)
                        }
                    }
                } else {
                    logToConsole("خطأ في الاتصال بالشبكة: رمز الاستجابة $responseCode")
                }
            } catch (e: Exception) {
                logToConsole("خطأ تقني أثناء الاتصال بـ AI: ${e.localizedMessage}")
            }
        }
    }

    private fun stopExecution() {
        isRunning = false
        timer?.cancel()
        btnExecute.text = "إرسال وبدء التنفيذ الفوري"
        btnExecute.setBackgroundColor(Color.parseColor("#ff4757"))
        logToConsole("تم إيقاف المساعد الذكي بنجاح.")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "$packageName/.FreeFireAccessibilityService"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabled != null) {
            val splitter = android.text.TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val componentName = splitter.next()
                if (componentName.equals(serviceName, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }
}
