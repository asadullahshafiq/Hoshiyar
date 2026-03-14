package com.assistant.personal.core

import android.app.*
import android.content.*
import android.os.Build
import java.util.*

class ReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "reminders"
        const val ACTION_REMINDER = "com.assistant.personal.REMINDER"
    }

    fun setReminder(text: String, delayMinutes: Int): Boolean {
        return try {
            createChannel()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra("message", text)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            true
        } catch (e: Exception) { false }
    }

    // Parse time from text
    // "remind me in 30 minutes to take medicine"
    // "10 minutes bad remind karo"
    fun parseReminder(text: String): Pair<Int, String>? {
        val t = text.lowercase()
        val minutePatterns = listOf(
            Regex("(\\d+)\\s*min"),
            Regex("(\\d+)\\s*minute"),
            Regex("(\\d+)\\s*ghante\\s*bad"),
            Regex("in\\s*(\\d+)")
        )
        val hourPatterns = listOf(
            Regex("(\\d+)\\s*hour"),
            Regex("(\\d+)\\s*ghante")
        )

        var minutes = 0
        for (pattern in minutePatterns) {
            val match = pattern.find(t)
            if (match != null) {
                minutes = match.groupValues[1].toIntOrNull() ?: 0
                break
            }
        }
        for (pattern in hourPatterns) {
            val match = pattern.find(t)
            if (match != null) {
                val hours = match.groupValues[1].toIntOrNull() ?: 0
                minutes += hours * 60
                break
            }
        }

        if (minutes == 0) return null

        // Extract reminder message
        val message = t
            .replace(Regex("remind(er)?\\s*(me)?"), "")
            .replace(Regex("\\d+\\s*(min|minute|hour|ghante)(s)?\\s*(bad|mein|after|in)?"), "")
            .replace("to ", "")
            .replace("karo", "")
            .replace("in", "")
            .trim()
            .replaceFirstChar { it.uppercase() }

        return Pair(minutes, message.ifEmpty { "Reminder!" })
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#6C63FF")
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Reminder!"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderManager.CHANNEL_ID, "Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val notification = Notification.Builder(context, ReminderManager.CHANNEL_ID)
            .setContentTitle("🤖 Hoshiyar Reminder")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
