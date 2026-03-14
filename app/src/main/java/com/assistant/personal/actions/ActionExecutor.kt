package com.assistant.personal.actions

import android.app.AlarmManager
import android.app.PendingIntent
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
import com.assistant.personal.core.CalculatorHelper
import com.assistant.personal.core.DictionaryHelper
import com.assistant.personal.core.QuickNotesManager
import com.assistant.personal.core.ReminderManager
import com.assistant.personal.storage.CommandStorage
import java.text.SimpleDateFormat
import java.util.*

class ActionExecutor(
    private val context: Context,
    private val tts: TextToSpeech,
    private val storage: CommandStorage
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notesManager = QuickNotesManager(context)
    private val reminderManager = ReminderManager(context)

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

        // ===== CALCULATOR =====
        if (CalculatorHelper.isCalculation(t)) {
            val result = CalculatorHelper.calculate(t)
            return if (result != null) {
                speak("The answer is $result")
                true
            } else false
        }

        // ===== DICTIONARY =====
        if (DictionaryHelper.isLookupRequest(t)) {
            val word = DictionaryHelper.extractWord(t)
            val meaning = DictionaryHelper.lookup(word)
            return if (meaning != null) {
                speak("$word means: $meaning")
                true
            } else {
                speak("Sorry, $word not found in offline dictionary")
                true
            }
        }

        // ===== QUICK NOTES =====
        if (t.startsWith("note ") || t.startsWith("save ") ||
            t.startsWith("remember ") || t.contains("note karo")) {
            val noteText = t
                .replace("note karo", "")
                .replace("note ", "")
                .replace("save ", "")
                .replace("remember ", "")
                .trim()
            if (noteText.isNotEmpty()) {
                notesManager.saveNote(noteText)
                speak("Note saved: $noteText")
                return true
            }
        }

        // ===== READ LAST NOTE =====
        if (t.contains("last note") || t.contains("my note") ||
            t.contains("note parho") || t.contains("note batao")) {
            val note = notesManager.getLastNote()
            return if (note != null) {
                speak("Your note: ${note.text}")
                true
            } else {
                speak("No notes found")
                true
            }
        }

        // ===== REMINDER =====
        if (t.contains("remind") || t.contains("reminder") ||
            t.contains("bad batao") || t.contains("yaad dilao")) {
            val parsed = reminderManager.parseReminder(t)
            return if (parsed != null) {
                val (minutes, message) = parsed
                reminderManager.setReminder(message, minutes)
                val timeStr = if (minutes >= 60)
                    "${minutes/60} hour${if(minutes/60>1)"s" else ""}"
                else "$minutes minutes"
                speak("Reminder set for $timeStr. I will remind you: $message")
                true
            } else {
                speak("Please say how many minutes. For example: remind me in 30 minutes")
                true
            }
        }

        // ===== ALARM =====
        if (t.contains("alarm") || t.contains("wake me") ||
            t.contains("alarm lagao") || t.contains("alarm set")) {
            return setAlarmFromText(t)
        }

        // ===== OPEN APP =====
        if (t.startsWith("open ") || t.endsWith("open") ||
            t.endsWith("kholo") || t.startsWith("launch ")) {
            val appName = t
                .replace("open ", "").replace("launch ", "")
                .replace("kholo", "").trim()
            return openApp(appName)
        }

        // ===== SHORTCUT COMMANDS =====
        when (t) {
            "yt" -> return openApp("youtube")
            "wa" -> return openApp("whatsapp")
            "ig" -> return openApp("instagram")
            "fb" -> return openApp("facebook")
            "tg" -> return openApp("telegram")
            "sc" -> return openApp("snapchat")
            "sp" -> return openApp("spotify")
            "gm" -> return openApp("gmail")
        }

        // ===== TIME =====
        if (t == "time" || t.contains("what time") ||
            t.contains("kitna baja") || t.contains("time batao")) return tellTime()

        // ===== DATE =====
        if (t == "date" || t.contains("today") ||
            t.contains("tarikh")) return tellDate()

        // ===== BATTERY =====
        if (t == "battery" || t.contains("battery")) return tellBattery()

        // ===== TORCH =====
        if (t.contains("torch") || t.contains("flashlight")) {
            return controlTorch(t.contains("on") || t.contains("chala"))
        }

        // ===== WIFI =====
        if (t.contains("wifi")) {
            return controlWifi(t.contains("on") || t.contains("chala"))
        }

        // ===== BLUETOOTH =====
        if (t.contains("bluetooth")) {
            return controlBluetooth(t.contains("on") || t.contains("chala"))
        }

        // ===== VOLUME =====
        if (t.contains("volume")) {
            return when {
                t.contains("up") || t.contains("increase") ||
                t.contains("barha") -> controlVolume("up")
                t.contains("down") || t.contains("decrease") ||
                t.contains("ghata") -> controlVolume("down")
                t.contains("mute") -> controlVolume("mute")
                else -> false
            }
        }

        return false
    }

    // ===== ALARM FROM TEXT =====
    private fun setAlarmFromText(text: String): Boolean {
        return try {
            // Extract time — "7:30", "7 30", "7 baj"
            val timeRegex = Regex("(\\d{1,2})[:. ](\\d{2})")
            val hourOnly = Regex("(\\d{1,2})\\s*(am|pm|baj|o'clock|baje)")

            var hour = 7; var minute = 0

            val match = timeRegex.find(text)
            if (match != null) {
                hour = match.groupValues[1].toInt()
                minute = match.groupValues[2].toInt()
            } else {
                val hMatch = hourOnly.find(text)
                if (hMatch != null) {
                    hour = hMatch.groupValues[1].toInt()
                    val period = hMatch.groupValues[2].lowercase()
                    if (period == "pm" && hour < 12) hour += 12
                    if (period == "am" && hour == 12) hour = 0
                }
            }

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Hoshiyar Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            speak("Alarm set for $hour:${if(minute<10)"0$minute" else "$minute"}")
            true
        } catch (e: Exception) { false }
    }

    // ===== CALL =====
    private fun makeCall(phoneNumber: String): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            speak("Calling $phoneNumber")
            true
        } catch (e: Exception) { speak("Call failed"); false }
    }

    // ===== SMS =====
    private fun sendSms(number: String, message: String): Boolean {
        return try {
            android.telephony.SmsManager.getDefault()
                .sendTextMessage(number, null, message, null, null)
            speak("Message sent")
            true
        } catch (e: Exception) { speak("Message failed"); false }
    }

    // ===== OPEN APP =====
    fun openApp(appName: String): Boolean {
        return try {
            val pkg = getPackageName(appName)
            val intent = pkg?.let {
                context.packageManager.getLaunchIntentForPackage(it)
            } ?: when (appName.lowercase().trim()) {
                "camera" -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                "settings" -> Intent(Settings.ACTION_SETTINGS)
                else -> null
            }

            intent?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(it)
                speak("Opening $appName")
                true
            } ?: run { speak("$appName not found"); false }
        } catch (e: Exception) { speak("Cannot open app"); false }
    }

    // ===== TORCH =====
    fun controlTorch(enable: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], enable)
            speak(if (enable) "Torch on" else "Torch off")
            true
        } catch (e: Exception) { false }
    }

    // ===== WIFI =====
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
                        })
                    speak("WiFi panel opened")
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
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
    private fun controlVolume(dir: String): Boolean {
        return try {
            when (dir) {
                "up" -> { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    speak("Volume up") }
                "down" -> { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    speak("Volume down") }
                "mute" -> { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    speak("Muted") }
            }
            true
        } catch (e: Exception) { false }
    }

    private fun setAlarm(text: String): Boolean = setAlarmFromText(text)

    // ===== TIME =====
    private fun tellTime(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val period = if (hour < 12) "AM" else "PM"
        val h12 = when { hour > 12 -> hour-12; hour == 0 -> 12; else -> hour }
        speak("It is $h12:${if(min<10)"0$min" else "$min"} $period")
        return true
    }

    // ===== DATE =====
    private fun tellDate(): Boolean {
        speak("Today is ${SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH).format(Date())}")
        return true
    }

    // ===== BATTERY =====
    private fun tellBattery(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        speak("Battery is $level percent. ${if(bm.isCharging) "Charging." else "Not charging."}")
        return true
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant")
    }

    fun getPackageName(appName: String): String? {
        val name = appName.lowercase().trim()
        return try {
            val pm = context.packageManager
            val pkgs = pm.getInstalledApplications(0)
            pkgs.find { pm.getApplicationLabel(it).toString().lowercase() == name }?.packageName
                ?: pkgs.find {
                    val l = pm.getApplicationLabel(it).toString().lowercase()
                    l.contains(name) || name.contains(l)
                }?.packageName
                ?: knownApps[name]
        } catch (e: Exception) { knownApps[name] }
    }

    private val knownApps = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "chrome" to "com.android.chrome",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "zoom" to "us.zoom.videomeetings",
        "twitter" to "com.twitter.android",
        "linkedin" to "com.linkedin.android",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "play store" to "com.android.vending",
        "pubg" to "com.tencent.ig",
        "daraz" to "com.daraz.android",
        "jazzcash" to "com.techlogix.mobilinkcustomer",
        "easypaisa" to "pk.com.telenor.phoenix",
        "camera" to "com.sec.android.app.camera",
        "gallery" to "com.sec.android.gallery3d",
        "calculator" to "com.sec.android.app.popupcalculator",
        "settings" to "com.android.settings",
        "clock" to "com.sec.android.app.clockpackage",
        "contacts" to "com.samsung.android.contacts",
        "messages" to "com.samsung.android.messaging",
        "phone" to "com.samsung.android.dialer",
        "files" to "com.sec.android.app.myfiles",
        "notes" to "com.samsung.android.app.notes",
        "music" to "com.sec.android.app.music",
        "browser" to "com.sec.android.app.sbrowser",
        "email" to "com.samsung.android.email.provider"
    )
}
