package com.assistant.personal.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.assistant.personal.R
import com.assistant.personal.actions.ActionExecutor
import com.assistant.personal.storage.CommandStorage
import com.assistant.personal.voice.VoskSpeechManager
import java.util.Locale

class AssistActivity : AppCompatActivity() {

    private lateinit var voskManager: VoskSpeechManager
    private var onlineSpeech: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var commandStorage: CommandStorage
    private lateinit var actionExecutor: ActionExecutor

    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var ivWave: View
    private lateinit var bgOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var etCommand: EditText
    private lateinit var btnSend: Button

    private var ttsReady = false
    private var voskReady = false
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContentView(R.layout.activity_assist)
        initViews()
        initStorage()
        initTTS()
        checkPermissionAndInitVosk()
    }

    private fun initViews() {
        tvStatus   = findViewById(R.id.tv_status)
        tvResult   = findViewById(R.id.tv_result)
        ivWave     = findViewById(R.id.iv_wave)
        bgOverlay  = findViewById(R.id.bg_overlay)
        progressBar = findViewById(R.id.progress_download)
        tvProgress  = findViewById(R.id.tv_progress)
        etCommand   = findViewById(R.id.et_command)
        btnSend     = findViewById(R.id.btn_send)

        // Background tap se band
        bgOverlay.setOnClickListener { finishWithAnimation() }

        // Mic button
        ivWave.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        // Send button
        btnSend.setOnClickListener { sendTextCommand() }

        // Keyboard Enter
        etCommand.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                sendTextCommand()
                true
            } else false
        }
    }

    private fun sendTextCommand() {
        val text = etCommand.text.toString().trim()
        if (text.isEmpty()) return
        etCommand.setText("")
        processCommand(text)
    }

    private fun initStorage() {
        commandStorage = CommandStorage(this)
        commandStorage.loadDefaultCommandsIfEmpty()
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langs = listOf(Locale("ur", "PK"), Locale("hi", "IN"), Locale.ENGLISH)
                for (lang in langs) {
                    val r = tts.setLanguage(lang)
                    if (r != TextToSpeech.LANG_NOT_SUPPORTED &&
                        r != TextToSpeech.LANG_MISSING_DATA) break
                }
                tts.setSpeechRate(1.0f)
                ttsReady = true
                actionExecutor = ActionExecutor(this, tts, commandStorage)
                tvStatus.text = "Tayyar hun! Bolein ya likhein 🎤"
            }
        }
    }

    private fun checkPermissionAndInitVosk() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else {
            initVosk()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            initVosk()
        }
    }

    private fun initVosk() {
        voskManager = VoskSpeechManager(
            context = this,
            onResult   = { text    -> handler.post { processCommand(text) } },
            onPartial  = { partial -> handler.post { tvResult.text = partial } },
            onError    = { error   -> handler.post { handleVoskError(error) } },
            onReady    = {
                handler.post {
                    voskReady = true
                    hideDownloadUI()
                    tvStatus.text = "Tayyar! Bolein ya likhein 🎤"
                }
            },
            onModelDownloadProgress = { progress ->
                handler.post {
                    progressBar.progress = progress
                    tvProgress.text = "Model download: $progress%"
                }
            }
        )
        voskManager.initialize()
    }

    private fun handleVoskError(error: String) {
        when (error) {
            "MODEL_NOT_FOUND" -> {
                // Vosk nahi hai - online use karo
                tvStatus.text = "Tayyar! Bolein ya likhein 🎤"
            }
            else -> {
                tvStatus.text = "Tayyar! Bolein ya likhein 🎤"
            }
        }
    }

    private fun startListening() {
        isListening = true

        // Pehle Vosk try karo
        if (voskReady) {
            tvStatus.text = "Sun raha hun... (Offline) 🎤"
            tvResult.text = ""
            startWaveAnimation()
            voskManager.startListening()
            return
        }

        // Online fallback
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "Mic kaam nahi kar raha - Type karein"
            isListening = false
            return
        }

        tvStatus.text = "Sun raha hun... 🎤"
        tvResult.text = ""
        startWaveAnimation()

        onlineSpeech?.destroy()
        onlineSpeech = SpeechRecognizer.createSpeechRecognizer(this)
        onlineSpeech?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                isListening = false
                stopWaveAnimation()
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                processCommand(t)
            }
            override fun onError(e: Int) {
                isListening = false
                stopWaveAnimation()
                tvStatus.text = "Sunai nahi diya - dobara try karein"
            }
            override fun onPartialResults(p: Bundle?) {
                tvResult.text = p?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            }
            override fun onReadyForSpeech(p: Bundle?) {
                tvStatus.text = "Bolein... 🎤"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {
                val s = 1f + (r / 20f).coerceIn(0f, 1f)
                ivWave.scaleX = s; ivWave.scaleY = s
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { stopWaveAnimation() }
            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            onlineSpeech?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            stopWaveAnimation()
            tvStatus.text = "Mic error - Type karein"
        }
    }

    private fun stopListening() {
        isListening = false
        if (::voskManager.isInitialized) voskManager.stopListening()
        onlineSpeech?.stopListening()
        stopWaveAnimation()
        tvStatus.text = "Tayyar! Bolein ya likhein 🎤"
    }

    private fun processCommand(spokenText: String) {
        stopListening()
        tvResult.text = "\"$spokenText\""
        tvStatus.text = "Samajh aa gaya ✅"

        val custom = commandStorage.findCommand(spokenText)
        if (custom != null) {
            actionExecutor.execute(custom, spokenText)
            handler.postDelayed({ finishWithAnimation() }, 1800)
            return
        }

        if (actionExecutor.executeBuiltIn(spokenText)) {
            handler.postDelayed({ finishWithAnimation() }, 1800)
            return
        }

        tvStatus.text = "Command nahi mili ❓"
        if (ttsReady) actionExecutor.speak("Ye command maloom nahi")
        handler.postDelayed({
            tvStatus.text = "Tayyar! Bolein ya likhein 🎤"
        }, 2000)
    }

    private fun showModelDownloadUI() {
        progressBar.visibility = View.VISIBLE
        tvProgress.visibility = View.VISIBLE
    }

    private fun hideDownloadUI() {
        progressBar.visibility = View.GONE
        tvProgress.visibility = View.GONE
    }

    private fun startWaveAnimation() {
        val anim = ObjectAnimator.ofFloat(ivWave, "alpha", 0.4f, 1f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        anim.start(); ivWave.tag = anim
    }

    private fun stopWaveAnimation() {
        (ivWave.tag as? ObjectAnimator)?.cancel()
        ivWave.alpha = 1f; ivWave.scaleX = 1f; ivWave.scaleY = 1f
    }

    private fun finishWithAnimation() {
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voskManager.isInitialized) voskManager.destroy()
        onlineSpeech?.destroy()
        if (::tts.isInitialized) tts.shutdown()
    }

    override fun onBackPressed() = finishWithAnimation()
}
