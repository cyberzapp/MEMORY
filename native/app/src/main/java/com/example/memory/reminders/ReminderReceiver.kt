package com.example.memory.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.memory.MainActivity
import com.example.memory.MemoryApplication
import com.example.memory.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val memoryId = intent.getStringExtra(ReminderManager.EXTRA_MEMORY_ID) ?: return
        val reminderId = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_ID) ?: return
        val message = intent.getStringExtra(ReminderManager.EXTRA_MESSAGE) ?: "You have a reminder!"

        // Fire the notification
        showNotification(context, memoryId, message, reminderId.hashCode())

        // Mark as completed in the database
        val app = context.applicationContext as? MemoryApplication
        val repo = app?.container?.memoryRepository
        if (repo != null) {
            CoroutineScope(Dispatchers.IO).launch {
                repo.completeReminder(reminderId)
            }
        }
    }

    private fun showNotification(context: Context, memoryId: String, message: String, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open the app (we can make it navigate to the specific memory later)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // We can pass memoryId here if MainActivity supports deep linking
            putExtra(ReminderManager.EXTRA_MEMORY_ID, memoryId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Memory Reminder")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
