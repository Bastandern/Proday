package com.example.proday.util

import java.util.Calendar
import java.util.TimeZone

class LunarCalendar(calendar: Calendar) {
    private var year: Int
    private val month: Int
    private val day: Int
    private var isLeap: Boolean = false
    private val solarDate: Calendar = calendar

    val lunarDateString: String
        get() = "农历 " + getChinaYearString(year) + "年 " + getChinaMonthString(month) + getChinaDayString(day)
        
    val lunarDayString: String
        get() {
            // Check for festival first
            val festival = getLunarFestival()
            if (festival.isNotEmpty()) return festival
            
            // Check for solar terms
            val term = getSolarTerm()
            if (term.isNotEmpty()) return term
            
            // Check for solar festival
            val solarFest = getSolarFestival()
            if (solarFest.isNotEmpty()) return solarFest
            
            return getChinaDayString(day)
        }

    init {
        val baseDate = Calendar.getInstance()
        baseDate.set(1900, 0, 31, 0, 0, 0)
        var offset = ((calendar.timeInMillis - baseDate.timeInMillis) / 86400000).toInt()

        var iYear = 1900
        var daysOfYear = yearDays(iYear)
        while (offset >= daysOfYear) {
            offset -= daysOfYear
            iYear++
            daysOfYear = yearDays(iYear)
        }
        year = iYear

        val leapMonth = leapMonth(iYear)
        var iMonth = 1
        var daysOfMonth: Int
        var isLeapMonth = false

        // Determine month
        while (true) {
            isLeapMonth = (leapMonth > 0 && iMonth == (leapMonth + 1) && !isLeapMonth)
            daysOfMonth = if (isLeapMonth) {
                leapDays(year)
            } else {
                monthDays(year, iMonth)
            }
            if (offset < daysOfMonth) {
                break
            }
            offset -= daysOfMonth
            if (leapMonth > 0 && iMonth == (leapMonth + 1) && !isLeapMonth) {
                isLeapMonth = true
            } else {
                iMonth++
                isLeapMonth = false
            }
        }
        
        // Simpler leap month adjustment if needed
        if (leapMonth > 0 && iMonth > leapMonth && !isLeapMonth) {
             // Logic placeholder if needed
        }
        
        // Re-calculate with proven logic
        baseDate.set(1900, 0, 31, 0, 0, 0)
        offset = ((calendar.timeInMillis - baseDate.timeInMillis) / 86400000).toInt()
        
        iYear = 1900
        var temp = 0
        while (iYear < 2100 && offset > 0) {
            temp = yearDays(iYear)
            if (offset < temp) break
            offset -= temp
            iYear++
        }
        year = iYear
        
        val leapMonthIndex = leapMonth(year)
        isLeap = false
        iMonth = 1
        while (iMonth < 13 && offset > 0) {
            if (leapMonthIndex > 0 && iMonth == (leapMonthIndex + 1) && !isLeap) {
                --iMonth
                isLeap = true
                temp = leapDays(year)
            } else {
                temp = monthDays(year, iMonth)
            }
            
            if (isLeap && iMonth == (leapMonthIndex + 1)) isLeap = false
            
            if (offset < temp) break
            offset -= temp
            iMonth++
        }
        
        month = iMonth
        day = offset + 1
    }
    
    private fun getLunarFestival(): String {
        for (item in lunarFestival) {
            // item format "MMdd FestivalName"
            val m = item.substring(0, 2).toInt()
            val d = item.substring(2, 4).toInt()
            if (m == month && d == day && !isLeap) {
                return item.substring(5)
            }
        }
        
        // Special case: Chuxi (New Year's Eve) - last day of 12th lunar month
        if (month == 12) {
            val daysInMonth = if (isLeap) leapDays(year) else monthDays(year, month)
            if (day == daysInMonth) {
                return "除夕"
            }
        }
        
        return ""
    }
    
    private fun getSolarFestival(): String {
        val m = solarDate.get(Calendar.MONTH) + 1
        val d = solarDate.get(Calendar.DAY_OF_MONTH)
        for (item in solarFestival) {
             val sm = item.substring(0, 2).toInt()
             val sd = item.substring(2, 4).toInt()
             if (sm == m && sd == d) {
                 return item.substring(5)
             }
        }
        return ""
    }
    
    private fun getSolarTerm(): String {
        val solarMonth = solarDate.get(Calendar.MONTH) + 1
        val solarDay = solarDate.get(Calendar.DAY_OF_MONTH)
        
        if (solarDay == getSolarTermDay(solarDate.get(Calendar.YEAR), (solarMonth - 1) * 2)) {
            return solarTerm[(solarMonth - 1) * 2]
        } else if (solarDay == getSolarTermDay(solarDate.get(Calendar.YEAR), (solarMonth - 1) * 2 + 1)) {
            return solarTerm[(solarMonth - 1) * 2 + 1]
        }
        return ""
    }
    
    // Simplified solar term calculation (approximate)
    private fun getSolarTermDay(y: Int, n: Int): Int {
        val d = 365.2422
        val termInfo = longArrayOf(
            0, 21208, 42467, 63836, 85337, 107014, 128867, 150921, 173149, 195551, 218072, 240693, 
            263343, 285989, 308563, 331033, 353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758
        )
        // Base approximate calculation - in production, use a more precise table
        // This is a simplified placeholder logic for demonstration
        // Using a predefined table for recent years is better, or a complex formula
        // For simplicity, let's just return a placeholder day if we don't implement full astronomical calc
        // Real implementation requires complex math.
        // Let's implement a very basic table lookup for 2024-2026 or generic formula
        
        // Using a simpler formula: D = [Y * D + C] - L
        // C values for 21st century
        val cVal = doubleArrayOf(
            5.4055, 20.12, 3.87, 18.73, 5.63, 20.646, 4.81, 20.1, 5.52, 21.04, 5.678, 21.37,
            7.108, 22.83, 7.5, 23.13, 7.646, 23.042, 8.318, 23.438, 7.438, 22.36, 7.18, 21.94
        )
        
        // Approximate calculation
        if (n >= 0 && n <= 23) {
             val centuryBase = 2000
             if (y < centuryBase) return 0 // Only support 2000+
             val diff = y - centuryBase
             val leapOffset = diff / 4
             val day = ((y * d + cVal[n]) - leapOffset).toInt()
             // This formula returns day of year or accumulated. 
             // Actually standard formula: int ((y%100)*0.2422 + C) - int((y%100-1)/4)
             
             val yy = y % 100
             var date = ((yy * 0.2422 + cVal[n]) - ((yy - 1) / 4)).toInt()
             
             // Adjustments for specific years/terms
             if (y == 2082 && n == 1) date += 1
             return date
        }
        
        return 0
    }

    companion object {
        // Lunar Calendar Data 1900-2100 (Simplified for brevity, usually 200 years of data)
        // Format: [LeapMonth (4bits), MonthDays (12bits 1=30, 0=29)]
        private val lunarInfo = longArrayOf(
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0
        )
        
        private val solarTerm = arrayOf(
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
            "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
            "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
            "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
        )
        
        private val lunarFestival = arrayOf(
            "0101 春节", "0115 元宵", "0505 端午", "0707 七夕",
            "0715 中元", "0815 中秋", "0909 重阳", "1208 腊八", "1223 小年"
        )
        
        private val solarFestival = arrayOf(
            "0101 元旦", "0214 情人节", "0308 妇女节", "0312 植树节", "0401 愚人节",
            "0501 劳动节", "0504 青年节", "0601 儿童节", "0701 建党节", "0801 建军节",
            "0910 教师节", "1001 国庆节", "1225 圣诞节"
        )
        
        private fun yearDays(y: Int): Int {
            var i: Int
            var sum = 348
            i = 0x8000
            while (i > 0x8) {
                if ((lunarInfo[y - 1900] and i.toLong()) != 0L) sum += 1
                i = i shr 1
            }
            return sum + leapDays(y)
        }

        private fun leapDays(y: Int): Int {
            if (leapMonth(y) != 0) {
                return if ((lunarInfo[y - 1900] and 0x10000) != 0L) 30 else 29
            }
            return 0
        }

        private fun leapMonth(y: Int): Int {
            return (lunarInfo[y - 1900] and 0xf).toInt()
        }

        private fun monthDays(y: Int, m: Int): Int {
            return if ((lunarInfo[y - 1900] and (0x10000 shr m).toLong()) == 0L) 29 else 30
        }

        private val chnNumber = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二")
        private val chnMonth = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        
        private fun getChinaYearString(year: Int): String {
             // For simplicity, just return the number or Zodiac could be added
             return year.toString()
        }
        
        private fun getChinaMonthString(month: Int): String {
             return chnMonth[month - 1] + "月"
        }
        
        private fun getChinaDayString(day: Int): String {
            val nStr1 = arrayOf("初", "十", "廿", "三十")
            val nStr2 = arrayOf("日", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
            if (day > 30) return ""
            if (day == 10) return "初十"
            if (day == 20) return "二十"
            if (day == 30) return "三十"
            
            val n1 = (day / 10)
            val n2 = (day % 10)
            
            return if(day < 10) {
                nStr1[0] + nStr2[n2]
            } else {
                 if (day < 20) "十" + nStr2[n2]
                 else nStr1[n1] + if(n2!=0) nStr2[n2] else ""
            }
        }
    }
}