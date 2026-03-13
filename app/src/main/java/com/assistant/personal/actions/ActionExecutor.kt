package com.assistant.personal.actions

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
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
        val t = text.lowercase()
        return when {
            t.contains("time") || t.contains("baja") || t.contains("waqt") -> tellTime()
            t.contains("date") || t.contains("tarikh") -> tellDate()
            t.contains("battery") -> tellBattery()
            t.contains("torch") || t.contains("flashlight") -> {
                val on = t.contains("on") || t.contains("chala")
                controlTorch(on)
            }
            t.contains("wifi") -> {
                val on = t.contains("on") || t.contains("chala")
                controlWifi(on)
            }
            t.contains("volume") || t.contains("awaaz") -> {
                when {
                    t.contains("barha") || t.contains("up") -> controlVolume("up")
                    t.contains("ghata") || t.contains("down") -> controlVolume("down")
                    t.contains("mute") -> controlVolume("mute")
                    else -> false
                }
            }
            t.contains("camera") -> openApp("camera")
            t.contains("calculator") -> openApp("calculator")
            t.contains("settings") -> openApp("settings")
            else -> false
        }
    }

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

    fun openApp(appName: String): Boolean {
        return try {
            val packageName = getPackageName(appName)
            val intent = if (packageName != null) {
                context.packageManager.getLaunchIntentForPackage(packageName)
            } else null

            val finalIntent = intent ?: when (appName.lowercase()) {
                "camera" -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                "settings" -> Intent(Settings.ACTION_SETTINGS)
                else -> null
            }

            finalIntent?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(it)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun controlTorch(enable: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            if (enable) speak("Torch on") else speak("Torch off")
            true
        } catch (e: Exception) { false }
    }

    @Suppress("DEPRECATION")
    private fun controlWifi(enable: Boolean): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enable
            if (enable) speak("WiFi on") else speak("WiFi off")
            true
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            true
        }
    }

    private fun controlBluetooth(enable: Boolean): Boolean {
        return try {
            val bt = BluetoothAdapter.getDefaultAdapter()
            if (enable) { bt?.enable(); speak("Bluetooth on") }
            else { bt?.disable(); speak("Bluetooth off") }
            true
        } catch (e: Exception) { false }
    }

    private fun controlVolume(direction: String): Boolean {
        return try {
            when (direction) {
                "up" -> { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); speak("Volume barha diya") }
                "down" -> { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); speak("Volume ghata diya") }
                "mute" -> { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); speak("Mute") }
            }
            true
        } catch (e: Exception) { false }
    }

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

    private fun tellTime(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val amPm = if (hour < 12) "subah" else if (hour < 17) "dopehar" else if (hour < 20) "shaam" else "raat"
        val hour12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        speak("$amPm ke $hour12 baj ke $minute minute")
        return true
    }

    private fun tellDate(): Boolean {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        speak("Aaj ${sdf.format(Date())} hai")
        return true
    }

    private fun tellBattery(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        speak("Battery $level percent hai")
        return true
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant")
    }

    private fun getPackageName(appName: String): String? = when (appName.lowercase()) {
        "camera" -> "com.sec.android.app.camera"
        "gallery" -> "com.sec.android.gallery3d"
        "calculator" -> "com.sec.android.app.popupcalculator"
        "settings" -> "com.android.settings"
        "whatsapp" -> "com.whatsapp"
        "youtube" -> "com.google.android.youtube"
        "clock" -> "com.sec.android.app.clockpackage"
        "contacts" -> "com.samsung.android.contacts"
        "messages" -> "com.samsung.android.messaging"
        else -> null
    }
}
