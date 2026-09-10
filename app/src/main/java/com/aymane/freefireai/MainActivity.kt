package com.aymane.freefireai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etTask: EditText
    private lateinit var btnExecute: Button
    private lateinit var tvConsole: TextView
    private var isRunning = false
    private var timer: CountDownTimer? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val SCREEN_CAPTURE_REQUEST_CODE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiKey = findViewById(R.id.etApiKey)
        etTask = findViewById(R.id.etTask)
        btnExecute = findViewById(R.id.btnExecute)
        tvConsole = findViewById(R.id.tvConsole)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val prefs = getSharedPreferences("FreeFirePrefs", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))

        btnExecute.setOnClickListener {
            if (!isRunning) {
                val apiKey = etApiKey.text.toString().trim()
                if (apiKey.isEmpty()) {
                    logToConsole("خطأ: يرجى إدخال مفتاح API الخاص بـ Gemini.")
                    return@setOnClickListener
                }
                prefs.edit().putString("api_key", apiKey).apply()

                if (!isAccessibilityServiceEnabled()) {
                    logToConsole("تنبيه: يرجى تفعيل خدمة الإمكانية (Accessibility) أولاً.")
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@setOnClickListener
                }

                // طلب صلاحية التقاط الشاشة البصرية
                mediaProjectionManager?.let { manager ->
                    startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
                }
            } else {
                stopExecution()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                setupVirtualDisplay()
                startRealAiSession()
            } else {
                logToConsole("تم رفض صلاحية التقاط الشاشة البصرية.")
            }
        }
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun captureScreenAsBase64(): String? {
        val image = imageReader?.acquireLatestImage() ?: return null
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
            val bytes = stream.toByteArray()
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            image.close()
            return null
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

    private fun startRealAiSession() {
        isRunning = true
        btnExecute.text = "إيقاف المساعد الذكي"
        btnExecute.setBackgroundColor(Color.parseColor("#ff4757"))
        logToConsole("تم بدء الجلسة البصرية الحية مع Gemini...")

        timer = object : CountDownTimer(1200000, 10000) {
            override fun onTick(millisUntilFinished: Long) {
                val base64Image = captureScreenAsBase64()
                val apiKey = etApiKey.text.toString().trim()
                val task = etTask.text.toString().trim()

                if (base64Image != null) {
                    logToConsole("📸 تم التقاط لقطة شاشة، جاري إرسالها للتحليل البصري...")
                    sendVisualTaskToGemini(apiKey, task.ifEmpty { "راقب اللعبة واعطني حركة تكتيكية" }, base64Image)
                } else {
                    logToConsole("انتظار إطار الشاشة التالي...")
                }
            }

            override fun onFinish() {
                logToConsole("انتهت الجلسة.")
                stopExecution()
            }
        }.start()
    }

    private fun sendVisualTaskToGemini(apiKey: String, prompt: String, base64Image: String) {
        executor.execute {
            try {
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
                                    put("text", "أنت مساعد ذكي للعبة Free Fire. هذه لقطة شاشة حية للعبة. المهمة: $prompt. قم بتحليل الصورة ورد بإحداثيات سحب دقيقة أو نص تكتيكي قصير جداً.")
                                })
                                put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                }

                val os = conn.outputStream
                val input = jsonBody.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val br = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val response = StringBuilder()
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        response.append(line!!.trim())
                    }

                    val jsonResponse = JSONObject(response.toString())
                    val aiText = jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0).getString("text")

                    logToConsole("🤖 تحليل Gemini البصري: $aiText")

                    // تنفيذ تفاعل حقيقي على الشاشة بناءً على التحليل
                    Handler(Looper.getMainLooper()).post {
                        FreeFireAccessibilityService.instance?.let { service ->
                            service.performSwipe(500f, 1300f, 500f, 850f, 200L)
                        }
                    }
                } else {
                    logToConsole("فشل الاتصال البصري: رمز الاستجابة ${conn.responseCode}")
                }
            } catch (e: Exception) {
                logToConsole("خطأ بالمعالجة البصرية: ${e.localizedMessage}")
            }
        }
    }

    private fun stopExecution() {
        isRunning = false
        timer?.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        btnExecute.text = "تشغيل المساعد البصري الذكي"
        btnExecute.setBackgroundColor(Color.parseColor("#ff4757"))
        logToConsole("تم إيقاف المساعد الذكي.")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "$packageName/.FreeFireAccessibilityService"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabled != null) {
            val splitter = android.text.TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(serviceName, ignoreCase = true)) return true
            }
        }
        return false
    }
}
