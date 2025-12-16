package com.example.proday.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.proday.AlarmReceiver
import com.example.proday.data.AppDatabase
import com.example.proday.data.Event
import com.example.proday.data.EventDao
import com.example.proday.data.ICalendarRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val eventDao: EventDao = AppDatabase.getDatabase(application).eventDao()
    private val repository = ICalendarRepository()
    
    val allEvents: LiveData<List<Event>> = eventDao.getAllEvents().asLiveData()

    // Temporary storage for copied events
    private var copiedEvents: List<Event>? = null

    fun insert(event: Event) = viewModelScope.launch {
        val newId = eventDao.insertEvent(event)
        val savedEvent = event.copy(id = newId)
        if (savedEvent.reminderMinutes >= 0) {
            scheduleNotification(savedEvent)
        }
    }

    fun update(event: Event) = viewModelScope.launch {
        eventDao.updateEvent(event)
        // Cancel old alarm and schedule new one to be safe
        cancelNotification(event)
        if (event.reminderMinutes >= 0) {
            scheduleNotification(event)
        }
    }

    fun delete(event: Event) = viewModelScope.launch {
        eventDao.deleteEvent(event)
        cancelNotification(event)
    }
    
    fun getEventsForDate(year: Int, month: Int, dayOfMonth: Int): LiveData<List<Event>> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, dayOfMonth, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(year, month, dayOfMonth, 23, 59, 59)
        val endOfDay = calendar.timeInMillis
        
        return eventDao.getEventsInRange(startOfDay, endOfDay).asLiveData()
    }
    
    fun importEventsFromUrl(url: String) = viewModelScope.launch {
        try {
            val events = repository.fetchEventsFromUrl(url)
            var count = 0
            for (event in events) {
                val newEvent = event.copy(id = 0, reminderMinutes = 0) // Default reminder at event time
                val id = eventDao.insertEvent(newEvent)
                scheduleNotification(newEvent.copy(id = id))
                count++
            }
             Toast.makeText(getApplication(), "已导入 $count 个事件", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
             Toast.makeText(getApplication(), "导入事件失败", Toast.LENGTH_SHORT).show()
        }
    }

    // New methods for Copy/Paste logic
    fun copyEvents(events: List<Event>) {
        if (events.isEmpty()) {
            Toast.makeText(getApplication(), "没有可复制的事件", Toast.LENGTH_SHORT).show()
            return
        }
        copiedEvents = events
        Toast.makeText(getApplication(), "已复制 ${events.size} 个事件", Toast.LENGTH_SHORT).show()
    }
    
    fun pasteEventsToDate(year: Int, month: Int, day: Int) = viewModelScope.launch {
        val eventsToPaste = copiedEvents
        if (eventsToPaste.isNullOrEmpty()) {
            Toast.makeText(getApplication(), "剪贴板为空", Toast.LENGTH_SHORT).show()
            return@launch
        }
        
        var count = 0
        for (event in eventsToPaste) {
            // Calculate new start and end time
            val cal = Calendar.getInstance()
            
            // Get original time of day
            cal.timeInMillis = event.startTime
            val startHour = cal.get(Calendar.HOUR_OF_DAY)
            val startMinute = cal.get(Calendar.MINUTE)
            
            cal.timeInMillis = event.endTime
            val endHour = cal.get(Calendar.HOUR_OF_DAY)
            val endMinute = cal.get(Calendar.MINUTE)
            
            // Set new date with original time
            cal.set(year, month, day, startHour, startMinute)
            val newStartTime = cal.timeInMillis
            
            cal.set(year, month, day, endHour, endMinute)
            val newEndTime = cal.timeInMillis
            
            // Create new event
            val newEvent = event.copy(
                id = 0, // Auto generate
                startTime = newStartTime,
                endTime = newEndTime
            )
            
            insert(newEvent)
            count++
        }
        Toast.makeText(getApplication(), "已粘贴 $count 个事件", Toast.LENGTH_SHORT).show()
    }
    
    private fun scheduleNotification(event: Event) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
        
        val triggerTime = event.startTime - (event.reminderMinutes * 60 * 1000L)
        
        // Schedule if time is in the future or recent past (within 1 minute)
        // This ensures "Now" events trigger immediately instead of being ignored
        if (triggerTime > System.currentTimeMillis() - 60000) {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            
            // Debug Toast
            Toast.makeText(context, "Alarm scheduled for ${formatTime(triggerTime)}", Toast.LENGTH_SHORT).show()
        } else {
             Toast.makeText(context, "Alarm NOT scheduled (Time passed)", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun cancelNotification(event: Event) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.proday.ALARM_${event.id}"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
    
    private fun formatTime(millis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}