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

        if (t.startsWith("open ") || t.endsWith("kholo") || t.endsWith("open")) {
            val appName = t.replace("open ", "").replace("kholo", "").trim()
            return openApp(appName)
        }
        if (t.contains("time") || t.contains("what time") || t.contains("baja")) return tellTime()
        if (t.contains("date") || t.contains("today")) return tellDate()
        if (t.contains("battery")) return tellBattery()
        if (t.contains("torch") || t.contains("flashlight")) {
            return controlTorch(t.contains("on"))
        }
        if (t.contains("wifi")) return controlWifi(t.contains("on"))
        if (t.contains("bluetooth")) return controlBluetooth(t.contains("on"))
        if (t.contains("volume")) {
            return when {
                t.contains("up") || t.contains("increase") -> controlVolume("up")
                t.contains("down") || t.contains("decrease") -> controlVolume("down")
                t.contains("mute") -> controlVolume("mute")
                else -> false
            }
        }
        return false
    }

    private fun makeCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            speak("Calling $phoneNumber")
            true
        } catch (e: Exception) {
            speak("Call failed")
            false
        }
    }

    private fun sendSms(number: String, message: String): Boolean {
        return try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            speak("Message sent")
            true
        } catch (e: Exception) {
            speak("Message failed")
            false
        }
    }

    fun openApp(appName: String): Boolean {
        return try {
            val packageName = getPackageName(appName)
            val intent = if (packageName != null)
                context.packageManager.getLaunchIntentForPackage(packageName)
            else null

            val finalIntent = intent ?: when (appName.lowercase().trim()) {
                "camera" -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                "settings" -> Intent(Settings.ACTION_SETTINGS)
                else -> null
            }

            finalIntent?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(it)
                speak("Opening $appName")
                true
            } ?: run {
                speak("$appName not found")
                false
            }
        } catch (e: Exception) {
            speak("Cannot open app")
            false
        }
    }

    fun controlTorch(enable: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], enable)
            speak(if (enable) "Torch on" else "Torch off")
            true
        } catch (e: Exception) { false }
    }

    @Suppress("DEPRECATION")
    fun controlWifi(enable: Boolean): Boolean {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled = enable
            speak(if (enable) "WiFi on" else "WiFi off")
            true
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.startActivity(
                        Intent(Settings.Panel.ACTION_WIFI).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                    speak("WiFi panel opened")
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                    speak("WiFi settings opened")
                }
                true
            } catch (e2: Exception) { false }
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
                "up" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Volume increased")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Volume decreased")
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    speak("Muted")
                }
            }
            true
        } catch (e: Exception) { false }
    }

    private fun setAlarm(text: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, 7)
                putExtra(AlarmClock.EXTRA_MINUTES, 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            speak("Alarm set")
            true
        } catch (e: Exception) { false }
    }

    private fun tellTime(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val period = when {
            hour < 12 -> "AM"
            else -> "PM"
        }
        val hour12 = when {
            hour > 12 -> hour - 12
            hour == 0 -> 12
            else -> hour
        }
        speak("It is $hour12 : $minute $period")
        return true
    }

    private fun tellDate(): Boolean {
        val sdf = SimpleDateFormat("MMMM dd yyyy", Locale.ENGLISH)
        speak("Today is ${sdf.format(Date())}")
        return true
    }

    private fun tellBattery(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        speak("Battery is $level percent. ${if (charging) "Charging" else "Not charging"}")
        return true
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant")
    }

    fun getPackageName(appName: String): String? {
        val name = appName.lowercase().trim()
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)

            // Exact match
            val exact = packages.find {
                pm.getApplicationLabel(it).toString().lowercase() == name
            }
            if (exact != null) return exact.packageName

            // Partial match
            val partial = packages.find {
                val label = pm.getApplicationLabel(it).toString().lowercase()
                label.contains(name) || name.contains(label)
            }
            if (partial != null) return partial.packageName

            // Fallback known apps
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
