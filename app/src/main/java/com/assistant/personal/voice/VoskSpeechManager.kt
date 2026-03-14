package com.assistant.personal.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * VOSK - 100% Offline
 * Model APK ke andar hai - koi download nahi
 * 100% private - koi internet nahi
 */
class VoskSpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onModelDownloadProgress: (Int) -> Unit
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var isModelLoaded = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val modelDir = File(context.filesDir, "vosk-model")

    private val SAMPLE_RATE = 16000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL, FORMAT) * 4

    // ===== Initialize =====
    fun initialize() {
        scope.launch {
            // Pehle check karo model already copy hua hai?
            if (modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
                loadModel()
            } else {
                // APK ke assets se copy karo
                withContext(Dispatchers.Main) {
                    onModelDownloadProgress(0)
                }
                copyModelFromAssets()
            }
        }
    }

    // ===== Assets se Copy =====
    private suspend fun copyModelFromAssets() {
        withContext(Dispatchers.IO) {
            try {
                val assetManager = context.assets
                val assetFiles = assetManager.list("vosk-model") ?: emptyArray()

                if (assetFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onError("MODEL_NOT_FOUND")
                    }
                    return@withContext
                }

                modelDir.mkdirs()
                copyAssetFolder(assetManager, "vosk-model", modelDir.absolutePath)

                withContext(Dispatchers.Main) {
                    onModelDownloadProgress(100)
                }

                loadModel()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Copy error: ${e.message}")
                }
            }
        }
    }

    private fun copyAssetFolder(
        assetManager: android.content.res.AssetManager,
        assetPath: String,
        destPath: String
    ) {
        val files = assetManager.list(assetPath) ?: return
        File(destPath).mkdirs()

        for (file in files) {
            val srcPath = "$assetPath/$file"
            val dstPath = "$destPath/$file"
            val subFiles = assetManager.list(srcPath)

            if (subFiles != null && subFiles.isNotEmpty()) {
                // Folder hai - recursion
                copyAssetFolder(assetManager, srcPath, dstPath)
            } else {
                // File hai - copy karo
                assetManager.open(srcPath).use { input ->
                    FileOutputStream(File(dstPath)).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    // ===== Model Load =====
    private suspend fun loadModel() {
        withContext(Dispatchers.IO) {
            try {
                model = Model(modelDir.absolutePath)
                isModelLoaded = true
                withContext(Dispatchers.Main) {
                    onReady()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Model load nahi hua: ${e.message}")
                }
            }
        }
    }

    // ===== Download - Ab Zarurat Nahi =====
    fun downloadModel() {
        // Model ab APK mein hai - download nahi hoga
        initialize()
    }

    fun isModelReady(): Boolean = modelDir.exists() &&
        modelDir.listFiles()?.isNotEmpty() == true

    // ===== Start Listening =====
    fun startListening() {
        if (!isModelLoaded || model == null) {
            onError("Model tayyar nahi")
            return
        }
        if (isListening) return
        isListening = true

        scope.launch(Dispatchers.IO) {
            try {
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL, FORMAT, BUFFER_SIZE
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        onError("Mic initialize nahi hua")
                    }
                    isListening = false
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(BUFFER_SIZE / 2)

                while (isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) {
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
                        }

                        val accepted = recognizer?.acceptWaveForm(bytes, bytes.size)

                        if (accepted == true) {
                            val json = recognizer?.result ?: continue
                            val text = JSONObject(json).optString("text", "")
                            if (text.isNotBlank()) {
                                withContext(Dispatchers.Main) { onResult(text) }
                                isListening = false
                            }
                        } else {
                            val json = recognizer?.partialResult ?: continue
                            val partial = JSONObject(json).optString("partial", "")
                            if (partial.isNotBlank()) {
                                withContext(Dispatchers.Main) { onPartial(partial) }
                            }
                        }
                    }
                }

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                val finalJson = recognizer?.finalResult ?: return@launch
                val finalText = JSONObject(finalJson).optString("text", "")
                if (finalText.isNotBlank()) {
                    withContext(Dispatchers.Main) { onResult(finalText) }
                }

            } catch (e: Exception) {
                isListening = false
                withContext(Dispatchers.Main) { onError("Mic error: ${e.message}") }
            }
        }
    }

    // ===== Stop =====
    fun stopListening() {
        isListening = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) { }
    }

    fun destroy() {
        stopListening()
        recognizer?.close()
        model?.close()
        scope.cancel()
    }
}
