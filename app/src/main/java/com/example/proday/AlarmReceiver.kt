package com.example.proday

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.proday.data.AppDatabase
import com.example.proday.data.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            rescheduleAlarms(context)
            return
        }

        val title = intent.getStringExtra("title") ?: "Event Reminder"
        val message = intent.getStringExtra("message") ?: "You have an event now!"
        val eventId = intent.getLongExtra("eventId", -1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Debug Toast
        Toast.makeText(context, "Alarm received for: $title", Toast.LENGTH_LONG).show()

        val channelId = "event_reminders_alarm"
        
        // Use Alarm sound
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for event alarms"
                enableVibration(true)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setFullScreenIntent(pendingIntent, true) // Show as full screen intent (heads up) if possible
            .build()

        notificationManager.notify(eventId.toInt(), notification)
    }

    private fun rescheduleAlarms(context: Context) {
        val pendingResult = goAsync()
        val eventDao = AppDatabase.getDatabase(context).eventDao()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check exact alarm permission on Android S+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                 // Without permission we can't schedule exact alarms.
                 // We could potentially show a notification asking for permission, or schedule inexact.
                 // For now, simply return to avoid crash.
                 pendingResult.finish()
                 return
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get all events
                val events = eventDao.getAllEvents().first()
                val now = System.currentTimeMillis()

                for (event in events) {
                    if (event.reminderMinutes >= 0) {
                        val triggerTime = event.startTime - (event.reminderMinutes * 60 * 1000L)
                        // Schedule if time is in the future or very recently passed (within 1 min)
                        if (triggerTime > now - 60000) {
                            scheduleNotification(context, alarmManager, event, triggerTime)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleNotification(context: Context, alarmManager: AlarmManager, event: Event, triggerTime: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title", event.title)
            putExtra("message", "Event starts in ${if(event.reminderMinutes>0) "${event.reminderMinutes} mins" else "now"} at ${formatTime(event.startTime)}")
            putExtra("eventId", event.id)
            action = "com.example.proday.ALARM_${event.id}"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    private fun formatTime(millis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}