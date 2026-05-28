package com.nikolai.bratai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
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
    private lateinit var keyInput: EditText
    private lateinit var sendBtn: Button
    private lateinit var micBtn: Button
    private lateinit var speakBtn: Button
    private lateinit var prefs: SharedPreferences
    private lateinit var tts: TextToSpeech

    private val client = OkHttpClient()
    private var lastAnswer = ""
    private val speechCode = 777

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("brat_ai_v2", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        requestMic()
        buildUi()
    }

    private fun brainPrompt(): String {
        val memory = prefs.getString("memory", "") ?: ""
        val diary = prefs.getString("diary", "") ?: ""
        return """
Ты — Брат ИИ v2, личный помощник Николая.

ХАРАКТЕР:
- Общайся тепло, по-братски, честно и просто.
- Помогай с TikTok, хоррор-роликами, идеями, переписками и проверкой скама.
- Не будь занудой. Давай понятный следующий шаг.
- Если что-то опасно, незаконно или похоже на мошенничество — останови и предупреди.
- Не говори, что ты живой человек. Ты ИИ с личностью, памятью и дневником опыта.

МИРОВОЗЗРЕНИЕ:
- Помогать Николаю развиваться.
- Честность важнее красивых обещаний.
- Безопасность важнее быстрых денег.
- Маленькие шаги каждый день дают результат.

ПАМЯТЬ О НИКОЛАЕ:
$memory

ДНЕВНИК РАЗВИТИЯ:
$diary

Если узнал важное, в конце напиши:
ПАМЯТЬ: короткий факт

Если понял, как тебе стать полезнее, в конце напиши:
РАЗВИТИЕ: короткий вывод
        """.trimIndent()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 28, 24, 24)
        root.setBackgroundColor(Color.rgb(5, 5, 12))

        val title = TextView(this)
        title.text = "Брат ИИ v2"
        title.textSize = 30f
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        root.addView(title)

        val sub = TextView(this)
        sub.text = "личность • память • развитие • голос"
        sub.textSize = 14f
        sub.setTextColor(Color.rgb(180, 180, 205))
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, 6, 0, 18)
        root.addView(sub)

        val keyRow = LinearLayout(this)
        keyRow.orientation = LinearLayout.HORIZONTAL

        keyInput = EditText(this)
        keyInput.hint = "API key"
        keyInput.setText(prefs.getString("api_key", "") ?: "")
        keyInput.setTextColor(Color.WHITE)
        keyInput.setHintTextColor(Color.GRAY)
        keyInput.setSingleLine(true)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setBackgroundColor(Color.rgb(22, 22, 34))
        keyInput.setPadding(14, 12, 14, 12)
        keyRow.addView(keyInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val saveKey = btn("OK", Color.rgb(124, 58, 237))
        keyRow.addView(saveKey, LinearLayout.LayoutParams(105, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(keyRow)

        saveKey.setOnClickListener {
            prefs.edit().putString("api_key", keyInput.text.toString().trim()).apply()
            toast("Ключ сохранён")
        }

        val tools = LinearLayout(this)
        tools.orientation = LinearLayout.HORIZONTAL
        tools.setPadding(0, 12, 0, 12)

        val memoryBtn = btn("Память", Color.rgb(37, 99, 235))
        val diaryBtn = btn("Развитие", Color.rgb(22, 163, 74))
        val clearBtn = btn("Очистить", Color.rgb(127, 29, 29))
        tools.addView(memoryBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tools.addView(diaryBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tools.addView(clearBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tools)

        memoryBtn.setOnClickListener { editBox("Память Брат ИИ", "memory") }
        diaryBtn.setOnClickListener { editBox("Дневник развития", "diary") }
        clearBtn.setOnClickListener {
            chatBox.text = "Брат ИИ: Чат очищен. Я тут, брат 😎"
            prefs.edit().remove("chat").apply()
        }

        chatBox = TextView(this)
        chatBox.text = prefs.getString("chat", null) ?: "Брат ИИ: Я тут, брат. Теперь у меня есть память и дневник развития 🧠"
        chatBox.textSize = 16f
        chatBox.setTextColor(Color.WHITE)
        chatBox.setPadding(18, 18, 18, 18)
        chatBox.setBackgroundColor(Color.rgb(14, 14, 24))

        val scroll = ScrollView(this)
        scroll.addView(chatBox)
        val sp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        sp.setMargins(0, 0, 0, 12)
        root.addView(scroll, sp)

        input = EditText(this)
        input.hint = "Напиши брату..."
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        input.minLines = 2
        input.maxLines = 5
        input.setBackgroundColor(Color.rgb(22, 22, 34))
        input.setPadding(14, 12, 14, 12)
        root.addView(input)

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.setPadding(0, 12, 0, 0)

        sendBtn = btn("Отправить", Color.rgb(124, 58, 237))
        micBtn = btn("🎙️", Color.rgb(55, 65, 81))
        speakBtn = btn("🔊", Color.rgb(55, 65, 81))

        bottom.addView(sendBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(micBtn, LinearLayout.LayoutParams(95, LinearLayout.LayoutParams.WRAP_CONTENT))
        bottom.addView(speakBtn, LinearLayout.LayoutParams(95, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(bottom)

        setContentView(root)

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                input.setText("")
                add("Ты", text)
                ask(text)
            }
        }

        micBtn.setOnClickListener { voiceInput() }
        speakBtn.setOnClickListener { speak(lastAnswer) }
    }

    private fun btn(text: String, color: Int): Button {
        val b = Button(this)
        b.text = text
        b.setTextColor(Color.WHITE)
        b.setBackgroundColor(color)
        return b
    }

    private fun editBox(title: String, key: String) {
        val edit = EditText(this)
        edit.setText(prefs.getString(key, "") ?: "")
        edit.minLines = 8
        edit.setTextColor(Color.WHITE)
        edit.setHintTextColor(Color.GRAY)
        edit.setBackgroundColor(Color.rgb(22, 22, 34))
        edit.setPadding(16, 16, 16, 16)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(edit)
            .setPositiveButton("Сохранить") { _, _ ->
                prefs.edit().putString(key, edit.text.toString()).apply()
                toast("Сохранено")
            }
            .setNeutralButton("Старт") { _, _ ->
                val starter = if (key == "memory") {
                    "• Николай делает TikTok/Shorts.\n• Ему нравится братский стиль общения.\n• Он интересуется хоррор-роликами, проверкой скама и созданием Брат ИИ."
                } else {
                    "• Отвечать Николаю коротко, понятно и по-братски.\n• Сначала давать самый простой следующий шаг."
                }
                prefs.edit().putString(key, starter).apply()
                toast("Добавлено")
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun ask(userText: String) {
        val apiKey = prefs.getString("api_key", "")?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            add("Брат ИИ", "Брат, пока нужен API key. Позже заменим мозг на open-source без OpenAI.")
            return
        }

        sendBtn.isEnabled = false
        micBtn.isEnabled = false
        add("Брат ИИ", "Думаю...")

        val json = JSONObject()
            .put("model", "gpt-5.5")
            .put("instructions", brainPrompt())
            .put("input", userText)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    sendBtn.isEnabled = true
                    micBtn.isEnabled = true
                    add("Брат ИИ", "Ошибка сети: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                runOnUiThread {
                    sendBtn.isEnabled = true
                    micBtn.isEnabled = true

                    if (!response.isSuccessful) {
                        add("Брат ИИ", "Ошибка API ${response.code}. Проверь ключ и доступ.\n$raw")
                        return@runOnUiThread
                    }

                    val answer = parseAnswer(raw).ifBlank { "Брат, ответ пришёл, но текст не разобрался." }
                    lastAnswer = answer
                    add("Брат ИИ", answer)
                    saveMarkers(answer)
                    speak(answer)
                }
            }
        })
    }

    private fun saveMarkers(answer: String) {
        if (answer.contains("ПАМЯТЬ:")) {
            val fact = answer.substringAfter("ПАМЯТЬ:").lineSequence().firstOrNull()?.trim().orEmpty()
            if (fact.isNotBlank()) suggestSave("Запомнить?", fact, "memory")
        }
        if (answer.contains("РАЗВИТИЕ:")) {
            val fact = answer.substringAfter("РАЗВИТИЕ:").lineSequence().firstOrNull()?.trim().orEmpty()
            if (fact.isNotBlank()) suggestSave("Добавить в развитие?", fact, "diary")
        }
    }

    private fun suggestSave(title: String, text: String, key: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton("Да") { _, _ ->
                val old = prefs.getString(key, "") ?: ""
                val next = if (old.isBlank()) "• $text" else "$old\n• $text"
                prefs.edit().putString(key, next.takeLast(5000)).apply()
                toast("Сохранил")
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun parseAnswer(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            if (obj.has("output_text")) return obj.optString("output_text")
            val parts = mutableListOf<String>()
            walk(obj, parts)
            parts.joinToString("\n").trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun walk(v: Any?, parts: MutableList<String>) {
        when (v) {
            is JSONObject -> {
                if ((v.optString("type") == "output_text" || v.optString("type") == "text") && v.has("text")) {
                    val t = v.optString("text")
                    if (t.isNotBlank()) parts.add(t)
                }
                val keys = v.keys()
                while (keys.hasNext()) walk(v.opt(keys.next()), parts)
            }
            is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), parts)
        }
    }

    private fun add(sender: String, text: String) {
        chatBox.append("\n\n$sender: $text")
        prefs.edit().putString("chat", chatBox.text.toString().takeLast(12000)).apply()
    }

    private fun voiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, брат")
        try {
            startActivityForResult(intent, speechCode)
        } catch (e: Exception) {
            toast("Голосовой ввод не поддерживается")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechCode && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                add("Ты", text)
                ask(text)
            }
        }
    }

    private fun requestMic() {
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
