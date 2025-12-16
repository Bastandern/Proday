package com.example.proday.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ICalendarRepository {

    suspend fun fetchEventsFromUrl(urlAddress: String): List<Event> = withContext(Dispatchers.IO) {
        val events = mutableListOf<Event>()
        try {
            val url = URL(urlAddress)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String? = reader.readLine()
                var inEvent = false
                var summary = ""
                var description = ""
                var location = ""
                var dtStart = 0L
                var dtEnd = 0L
                
                // Simplified iCalendar parser
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed == "BEGIN:VEVENT") {
                        inEvent = true
                        summary = ""
                        description = ""
                        location = ""
                        dtStart = 0L
                        dtEnd = 0L
                    } else if (trimmed == "END:VEVENT") {
                        inEvent = false
                        if (summary.isNotEmpty() && dtStart > 0) {
                            if (dtEnd == 0L) dtEnd = dtStart + 3600000 // Default 1 hour if no end time
                             events.add(
                                Event(
                                    title = summary,
                                    description = description,
                                    startTime = dtStart,
                                    endTime = dtEnd,
                                    location = location
                                )
                            )
                        }
                    } else if (inEvent) {
                        if (trimmed.startsWith("SUMMARY:")) {
                            summary = trimmed.substringAfter("SUMMARY:")
                        } else if (trimmed.startsWith("DESCRIPTION:")) {
                            description = trimmed.substringAfter("DESCRIPTION:")
                        } else if (trimmed.startsWith("LOCATION:")) {
                            location = trimmed.substringAfter("LOCATION:")
                        } else if (trimmed.startsWith("DTSTART")) {
                            // Handle DTSTART;VALUE=DATE:20230101 or DTSTART:20230101T120000Z
                            val value = trimmed.substringAfter(":")
                            dtStart = parseICalDate(value)
                        } else if (trimmed.startsWith("DTEND")) {
                             val value = trimmed.substringAfter(":")
                            dtEnd = parseICalDate(value)
                        }
                    }
                    line = reader.readLine()
                }
                reader.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext events
    }
    
    private fun parseICalDate(dateStr: String): Long {
        try {
             // Basic parsing, cover common formats
             // 20230520T143000Z
             if (dateStr.contains("T")) {
                 val format = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                 format.timeZone = TimeZone.getTimeZone("UTC")
                 return format.parse(dateStr)?.time ?: 0L
             } else {
                 // 20230520
                 val format = SimpleDateFormat("yyyyMMdd", Locale.US)
                 return format.parse(dateStr)?.time ?: 0L
             }
        } catch (e: Exception) {
            // Try without Z
             try {
                 if (dateStr.contains("T")) {
                     val format = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                     return format.parse(dateStr)?.time ?: 0L
                 }
            } catch (ignored: Exception) {}
            return 0L
        }
    }
}