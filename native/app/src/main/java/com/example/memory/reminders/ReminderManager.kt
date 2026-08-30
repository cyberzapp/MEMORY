package com.example.memory.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Memory Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for memories you wanted to be reminded about."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminder(memoryId: String, reminderId: String, triggerAtMs: Long, message: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MEMORY_ID, memoryId)
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_MESSAGE, message)
        }

        // We use the reminderId hash code as the unique request code
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Android 14+ requires SCHEDULE_EXACT_ALARM permission.
            // If the permission is revoked, we can't schedule exact alarms.
            // Fallback to inexact alarm if necessary, but we assume we have the permission.
        }
    }

    fun cancelReminder(reminderId: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    companion object {
        const val CHANNEL_ID = "memory_reminders_channel"
        const val EXTRA_MEMORY_ID = "memory_id"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_MESSAGE = "reminder_message"
    }
}
