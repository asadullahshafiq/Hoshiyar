package com.assistant.personal.actions

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import com.assistant.personal.storage.CommandStorage
import java.text.SimpleDateFormat
import java.util.*

class ActionExecutor(
    private val context: Context,
    private val tts: TextToSpeech,
    private val storage: CommandStorage
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun execute(command: CommandStorage.CustomCommand, spokenText: String): Boolean {
        return when (command.action) {
            "ACTION_CALL" -> makeCall(command.parameter)
            "ACTION_SMS" -> sendSms(command.parameter, spokenText)
            "ACTION_APP" -> openApp(command.parameter)
            "ACTION_TORCH" -> controlTorch(command.parameter == "on")
            "ACTION_WIFI" -> controlWifi(command.parameter == "on")
            "ACTION_BLUETOOTH" -> controlBluetooth(command.parameter == "on")
            "ACTION_VOLUME" -> controlVolume(command.parameter)
            "ACTION_ALARM" -> setAlarm(spokenText)
            "ACTION_TIME" -> tellTime()
            "ACTION_BATTERY" -> tellBattery()
            "ACTION_SPEAK" -> { speak(command.parameter); true }
            else -> false
        }
    }

    fun executeBuiltIn(text: String): Boolean {
        val t = text.lowercase().trim()

        // ===== OPEN APP =====
        if (t.startsWith("open ") || t.contains("kholo")) {
            val appName = t
                .replace("open ", "")
                .replace("kholo", "")
                .trim()
            return openApp(appName)
        }

        // ===== TIME =====
        if (t.contains("time") || t.contains("baja") || t.contains("waqt")) return tellTime()

        // ===== DATE =====
        if (t.contains("date") || t.contains("tarikh")) return tellDate()

        // ===== BATTERY =====
        if (t.contains("battery")) return tellBattery()

        // ===== TORCH =====
        if (t.contains("torch") || t.contains("flashlight")) {
            val on = t.contains("on") || t.contains("chala")
            return controlTorch(on)
        }

        // ===== WIFI =====
        if (t.contains("wifi")) {
            val on = t.contains("on") || t.contains("chala")
            return controlWifi(on)
        }

        // ===== BLUETOOTH =====
        if (t.contains("bluetooth")) {
            val on = t.contains("on") || t.contains("chala")
            return controlBluetooth(on)
        }

        // ===== VOLUME =====
        if (t.contains("volume") || t.contains("awaaz")) {
            return when {
                t.contains("barha") || t.contains("up") -> controlVolume("up")
                t.contains("ghata") || t.contains("down") -> controlVolume("down")
                t.contains("mute") -> controlVolume("mute")
                else -> false
            }
        }

        // ===== CAMERA =====
        if (t.contains("camera")) return openApp("camera")

        // ===== CALCULATOR =====
        if (t.contains("calculator")) return openApp("calculator")

        // ===== SETTINGS =====
        if (t.contains("settings")) return openApp("settings")

        return false
    }

    // ===== MAKE CALL =====
    private fun makeCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            speak("Call nahi ho saka")
            false
        }
    }

    // ===== SEND SMS =====
    private fun sendSms(number: String, message: String): Boolean {
        return try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            speak("Message bhej diya")
            true
        } catch (e: Exception) {
            speak("Message nahi gaya")
            false
        }
    }

    // ===== OPEN APP =====
    fun openApp(appName: String): Boolean {
        return try {
            val packageName = getPackageName(appName)
            val intent = if (packageName != null) {
                context.packageManager.getLaunchIntentForPackage(packageName)
            } else null

            val finalIntent = intent ?: when (appName.lowercase().trim()) {
                "camera" -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                "settings" -> Intent(Settings.ACTION_SETTINGS)
                else -> null
            }

            finalIntent?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(it)
                speak("$appName khol diya")
                true
            } ?: run {
                speak("$appName nahi mila")
                false
            }
        } catch (e: Exception) {
            speak("App nahi khula")
            false
        }
    }

    // ===== TORCH =====
    fun controlTorch(enable: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            if (enable) speak("Torch on") else speak("Torch off")
            true
        } catch (e: Exception) { false }
    }

    // ===== WIFI =====
    @Suppress("DEPRECATION")
    private fun controlWifi(enable: Boolean): Boolean {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enable
            if (enable) speak("WiFi on kar diya") else speak("WiFi off kar diya")
            true
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panelIntent = Intent(
                        Settings.Panel.ACTION_WIFI
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(panelIntent)
                    speak("WiFi panel khul gaya")
                } else {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    speak("WiFi settings khul gayi")
                }
                true
            } catch (e2: Exception) { false }
        }
    }

    // ===== BLUETOOTH =====
    private fun controlBluetooth(enable: Boolean): Boolean {
        return try {
            val bt = BluetoothAdapter.getDefaultAdapter()
            if (enable) { bt?.enable(); speak("Bluetooth on") }
            else { bt?.disable(); speak("Bluetooth off") }
            true
        } catch (e: Exception) { false }
    }

    // ===== VOLUME =====
    private fun controlVolume(direction: String): Boolean {
        return try {
            when (direction) {
                "up" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Volume barha diya")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Volume ghata diya")
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Mute kar diya")
                }
            }
            true
        } catch (e: Exception) { false }
    }

    // ===== ALARM =====
    private fun setAlarm(spokenText: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, 7)
                putExtra(AlarmClock.EXTRA_MINUTES, 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            speak("Alarm set kar diya")
            true
        } catch (e: Exception) { false }
    }

    // ===== TIME =====
    private fun tellTime(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val amPm = when {
            hour < 12 -> "subah"
            hour < 17 -> "dopehar"
            hour < 20 -> "shaam"
            else -> "raat"
        }
        val hour12 = when {
            hour > 12 -> hour - 12
            hour == 0 -> 12
            else -> hour
        }
        speak("$amPm ke $hour12 baj ke $minute minute")
        return true
    }

    // ===== DATE =====
    private fun tellDate(): Boolean {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        speak("Aaj ${sdf.format(Date())} hai")
        return true
    }

    // ===== BATTERY =====
    private fun tellBattery(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val status = if (charging) "charge ho rahi hai" else "charge nahi ho rahi"
        speak("Battery $level percent hai aur $status")
        return true
    }

    // ===== SPEAK =====
    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant")
    }

    // ===== GET PACKAGE NAME =====
    private fun getPackageName(appName: String): String? {
        val name = appName.lowercase().trim()

        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)

            // Exact match pehle
            val exact = packages.find { pkg ->
                pm.getApplicationLabel(pkg).toString().lowercase() == name
            }
            if (exact != null) return exact.packageName

            // Partial match
            val partial = packages.find { pkg ->
                val label = pm.getApplicationLabel(pkg).toString().lowercase()
                label.contains(name) || name.contains(label)
            }
            if (partial != null) return partial.packageName

            // Known apps fallback
            when (name) {
                "youtube" -> "com.google.android.youtube"
                "whatsapp" -> "com.whatsapp"
                "instagram" -> "com.instagram.android"
                "facebook" -> "com.facebook.katana"
                "chrome" -> "com.android.chrome"
                "telegram" -> "org.telegram.messenger"
                "snapchat" -> "com.snapchat.android"
                "tiktok" -> "com.zhiliaoapp.musically"
                "spotify" -> "com.spotify.music"
                "netflix" -> "com.netflix.mediaclient"
                "zoom" -> "us.zoom.videomeetings"
                "twitter" -> "com.twitter.android"
                "linkedin" -> "com.linkedin.android"
                "gmail" -> "com.google.android.gm"
                "maps" -> "com.google.android.apps.maps"
                "play store" -> "com.android.vending"
                "pubg" -> "com.tencent.ig"
                "daraz" -> "com.daraz.android"
                "jazzcash" -> "com.techlogix.mobilinkcustomer"
                "easypaisa" -> "pk.com.telenor.phoenix"
                "camera" -> "com.sec.android.app.camera"
                "gallery" -> "com.sec.android.gallery3d"
                "calculator" -> "com.sec.android.app.popupcalculator"
                "settings" -> "com.android.settings"
                "clock" -> "com.sec.android.app.clockpackage"
                "contacts" -> "com.samsung.android.contacts"
                "messages" -> "com.samsung.android.messaging"
                "phone" -> "com.samsung.android.dialer"
                "files" -> "com.sec.android.app.myfiles"
                "notes" -> "com.samsung.android.app.notes"
                "music" -> "com.sec.android.app.music"
                "browser" -> "com.sec.android.app.sbrowser"
                "email" -> "com.samsung.android.email.provider"
                else -> null
            }
        } catch (e: Exception) { null }
    }
}
