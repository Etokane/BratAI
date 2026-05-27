package com.nikolai.bratai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var chatBox: TextView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var micBtn: Button
    private lateinit var speakBtn: Button
    private lateinit var keyInput: EditText
    private lateinit var saveKeyBtn: Button
    private lateinit var prefs: SharedPreferences
    private lateinit var tts: TextToSpeech

    private val client = OkHttpClient()
    private var lastAnswer: String = ""
    private val speechRequestCode = 777

    private val systemPersonality = """
Ты — «Брат ИИ», личный ИИ-друг Николая.
Общайся тепло, по-братски, просто и честно.
Помогай с TikTok, хоррор-роликами, проверкой скама, переписками, идеями и поддержкой.
Не будь занудой. Пиши коротко, ясно, с дружеской энергией.
Если задача опасная, незаконная или может навредить — спокойно останови и предложи безопасный вариант.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("brat_ai_settings", MODE_PRIVATE)
        tts = TextToSpeech(this, this)

        requestMicPermissionIfNeeded()
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(28, 34, 28, 28)
        root.setBackgroundColor(Color.rgb(5, 5, 9))

        val title = TextView(this)
        title.text = "Брат ИИ"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        title.setPadding(0, 0, 0, 20)
        root.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Твой личный ИИ-друг для идей, роликов, переписок и защиты от скама."
        subtitle.textSize = 14f
        subtitle.setTextColor(Color.rgb(190, 190, 205))
        subtitle.gravity = Gravity.CENTER
        subtitle.setPadding(0, 0, 0, 22)
        root.addView(subtitle)

        val keyRow = LinearLayout(this)
        keyRow.orientation = LinearLayout.HORIZONTAL
        keyRow.gravity = Gravity.CENTER_VERTICAL

        keyInput = EditText(this)
        keyInput.hint = "Вставь OpenAI API key"
        keyInput.setHintTextColor(Color.rgb(120, 120, 135))
        keyInput.setTextColor(Color.WHITE)
        keyInput.setSingleLine(true)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setText(prefs.getString("api_key", "") ?: "")
        keyInput.setBackgroundColor(Color.rgb(20, 20, 30))
        keyInput.setPadding(16, 12, 16, 12)
        keyRow.addView(keyInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        saveKeyBtn = Button(this)
        saveKeyBtn.text = "OK"
        keyRow.addView(saveKeyBtn)

        root.addView(keyRow)

        saveKeyBtn.setOnClickListener {
            prefs.edit().putString("api_key", keyInput.text.toString().trim()).apply()
            toast("Ключ сохранён, брат")
        }

        chatBox = TextView(this)
        chatBox.text = "Брат ИИ: Я тут, брат. Напиши или нажми микрофон 🎙️"
        chatBox.textSize = 16f
        chatBox.setTextColor(Color.WHITE)
        chatBox.setPadding(20, 20, 20, 20)
        chatBox.setBackgroundColor(Color.rgb(14, 14, 22))
        chatBox.gravity = Gravity.START

        val scroll = ScrollView(this)
        scroll.addView(chatBox)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        scrollParams.setMargins(0, 22, 0, 18)
        root.addView(scroll, scrollParams)

        input = EditText(this)
        input.hint = "Напиши брату..."
        input.setHintTextColor(Color.rgb(120, 120, 135))
        input.setTextColor(Color.WHITE)
        input.minLines = 2
        input.maxLines = 5
        input.setBackgroundColor(Color.rgb(20, 20, 30))
        input.setPadding(16, 14, 16, 14)
        root.addView(input)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.CENTER
        buttons.setPadding(0, 16, 0, 0)

        sendBtn = Button(this)
        sendBtn.text = "Отправить"
        micBtn = Button(this)
        micBtn.text = "🎙️"
        speakBtn = Button(this)
        speakBtn.text = "🔊"

        buttons.addView(sendBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(micBtn)
        buttons.addView(speakBtn)
        root.addView(buttons)

        setContentView(root)

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                input.setText("")
                addMessage("Ты", text)
                askOpenAI(text)
            }
        }

        micBtn.setOnClickListener {
            startVoiceInput()
        }

        speakBtn.setOnClickListener {
            speak(lastAnswer)
        }
    }

    private fun askOpenAI(userText: String) {
        val apiKey = prefs.getString("api_key", "")?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            addMessage("Брат ИИ", "Брат, сначала вставь OpenAI API key сверху и нажми OK.")
            return
        }

        setLoading(true)

        val bodyJson = JSONObject()
            .put("model", "gpt-5.5")
            .put("instructions", systemPersonality)
            .put("input", userText)

        val body = bodyJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoading(false)
                    addMessage("Брат ИИ", "Брат, ошибка сети: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string().orEmpty()
                runOnUiThread {
                    setLoading(false)
                    if (!response.isSuccessful) {
                        addMessage("Брат ИИ", "Брат, API вернул ошибку ${response.code}. Проверь ключ, интернет и доступ к модели.\n\n$responseText")
                        return@runOnUiThread
                    }

                    val answer = extractTextFromResponse(responseText).ifBlank {
                        "Брат, я получил ответ, но не смог разобрать текст. Вот сырой ответ:\n$responseText"
                    }

                    lastAnswer = answer
                    addMessage("Брат ИИ", answer)
                    speak(answer)
                }
            }
        })
    }

    private fun extractTextFromResponse(raw: String): String {
        return try {
            val json = JSONObject(raw)

            if (json.has("output_text")) {
                return json.optString("output_text")
            }

            val parts = mutableListOf<String>()
            collectText(json, parts)
            parts.joinToString("\n").trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun collectText(value: Any?, parts: MutableList<String>) {
        when (value) {
            is JSONObject -> {
                val type = value.optString("type")
                if ((type == "output_text" || type == "text") && value.has("text")) {
                    val text = value.optString("text")
                    if (text.isNotBlank()) parts.add(text)
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    collectText(value.opt(keys.next()), parts)
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) collectText(value.opt(i), parts)
            }
        }
    }

    private fun addMessage(sender: String, text: String) {
        chatBox.append("\n\n$sender: $text")
    }

    private fun setLoading(isLoading: Boolean) {
        sendBtn.isEnabled = !isLoading
        micBtn.isEnabled = !isLoading
        if (isLoading) addMessage("Брат ИИ", "Думаю, брат...")
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, брат")
        try {
            startActivityForResult(intent, speechRequestCode)
        } catch (e: Exception) {
            toast("Голосовой ввод не поддерживается на этом телефоне")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechRequestCode && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = result?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                addMessage("Ты", text)
                askOpenAI(text)
            }
        }
    }

    private fun requestMicPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
            }
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        tts.speak(text.take(900), TextToSpeech.QUEUE_FLUSH, null, "brat_ai_voice")
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}
