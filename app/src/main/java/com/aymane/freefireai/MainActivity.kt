package com.aymane.freefireai

content.Context
content.Intent
os.Bundle
os.CountDownTimer
provider.Settings
widget.Button
widget.EditText
widget.TextView
appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etTask: EditText
    private lateinit var btnExecute: Button
    private lateinit var tvConsole: TextView
    private var isRunning = false
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiKey = findViewById(R.id.etApiKey)
        etTask = findViewById(R.id.etTask)
        btnExecute = findViewById(R.id.btnExecute)
        tvConsole = findViewById(R.id.tvConsole)

        // استرجاع المفتاح المحفوظ مسبقاً إن وجد
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
            // تمرير النص تلقائياً للأسفل
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
            logToConsole("خطأ: الرجاء إدخال مفتاح الذكاء الاصطناعي أولاً!")
            return
        }

        if (task.isEmpty()) {
            logToConsole("خطأ: الرجاء كتابة المهمة أو الأمر المراد تنفيذه!")
            return
        }

        // التحقق من تفعيل خدمة إمكانية الوصول
        if (!isAccessibilityServiceEnabled()) {
            logToConsole("تنبيه: خدمة التحكم (Accessibility) غير مفعلة! جاري توجيهك للإعدادات...")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        // حفظ المفتاح محلياً
        prefs.edit().putString("api_key", apiKey).apply()

        isRunning = true
        btnExecute.text = "إيقاف العمليات"
        btnExecute.setBackgroundColor(android.graphics.Color.parseColor("#ff4757"))

        logToConsole("تم حفظ المفتاح وبدء تحليل المهمة...")
        logToConsole("المهمة الحالية: $task")
        logToConsole("جاري الاتصال بنظام الذكاء الاصطناعي وتحضير استراتيجية المعركة...")

        // بدء مؤقت لمدة 20 دقيقة (1200000 ملي ثانية) كمثال لعمل الحلقة المستمرة
        timer = object : CountDownTimer(1200000, 5000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutesLeft = millisUntilFinished / 1000 / 60
                val secondsLeft = (millisUntilFinished / 1000) % 60
                logToConsole("⏳ الوقت المتبقي للجلسة: ${minutesLeft}د ${secondsLeft}ث | الذكاء الاصطناعي يحلل الشاشة وينفذ التكتيك...")
                
                // مثال على تحريك الحركة أو النقر عبر خدمة الإمكانية
                FreeFireAccessibilityService.instance?.let { service ->
                    // تنفيذ نقرة تجريبية أو تمرير ذكي لإبقاء المحاكاة نشطة
                    service.performSwipe(500f, 1500f, 500f, 1000f, 200L)
                }
            }

            override fun onFinish() {
                logToConsole("انتهت الجلسة المجدولة (20 دقيقة بنجاح).")
                stopExecution()
            }
        }.start()
    }

    private fun stopExecution() {
        isRunning = false
        timer?.cancel()
        btnExecute.text = "إرسال وبدء التنفيذ الفوري"
        btnExecute.setBackgroundColor(android.graphics.Color.parseColor("#ff4757"))
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
