package com.example.proday.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val reminderMinutes: Int = 0 // 0 means no reminder, or immediate? Let's use 0 as "At time of event", -1 as None? 
    // Usually reminder is "minutes before". 
    // Let's say: -1 = Off, 0 = At start time, 5 = 5 mins before, etc.
)