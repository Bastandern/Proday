package com.example.proday

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.proday.data.Event
import com.example.proday.util.LunarCalendar
import com.example.proday.viewmodel.EventViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.view.WeekDayBinder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import androidx.lifecycle.LiveData

class MainActivity : AppCompatActivity() {

    private val viewModel: EventViewModel by viewModels()
    private lateinit var adapter: EventsAdapter
    private var selectedDate: LocalDate = LocalDate.now()
    
    private lateinit var calendarView: com.kizitonwose.calendar.view.CalendarView
    private lateinit var weekCalendarView: com.kizitonwose.calendar.view.WeekCalendarView
    
    private var currentEventsLiveData: LiveData<List<Event>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        calendarView = findViewById(R.id.calendarView)
        weekCalendarView = findViewById(R.id.weekCalendarView)

        setupRecyclerView()
        setupCalendarView()
        setupFab()
        
        updateLunarDate()
        observeEvents()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.eventsRecyclerView)
        adapter = EventsAdapter(
            onItemClick = { event -> openAddEventActivity(event) },
            onItemLongClick = { event -> showDeleteDialog(event) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private inner class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.calendarDayText)
        val lunarTextView: TextView = view.findViewById(R.id.calendarDayLunarText)
        lateinit var day: CalendarDay
        lateinit var weekDay: WeekDay
        
        init {
            view.setOnClickListener {
                val date = if (::day.isInitialized) day.date else weekDay.date
                val position = if (::day.isInitialized) day.position else DayPosition.MonthDate

                if (position == DayPosition.MonthDate || position == DayPosition.InDate || position == DayPosition.OutDate || ::weekDay.isInitialized) {
                    if (selectedDate != date) {
                        val oldDate = selectedDate
                        selectedDate = date
                        calendarView.notifyDateChanged(oldDate)
                        calendarView.notifyDateChanged(selectedDate)
                        weekCalendarView.notifyDateChanged(oldDate)
                        weekCalendarView.notifyDateChanged(selectedDate)
                        updateLunarDate()
                        observeEvents()
                    }
                }
            }
        }
    }

    private fun setupCalendarView() {
        val viewModeToggle = findViewById<RadioGroup>(R.id.viewModeToggle)

        val monthDayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                bindDayView(container, data.date, data.position == DayPosition.MonthDate)
            }
        }

        val weekDayBinder = object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: WeekDay) {
                container.weekDay = data
                bindDayView(container, data.date, true)
            }
        }
        
        calendarView.dayBinder = monthDayBinder
        weekCalendarView.dayBinder = weekDayBinder

        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(100)
        val endMonth = currentMonth.plusMonths(100)
        val daysOfWeek = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
        
        class MonthViewContainer(view: View) : ViewContainer(view) {
            val titlesContainer = view as android.widget.LinearLayout
        }
        
        calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, data: CalendarMonth) {
                if (container.titlesContainer.tag == null) {
                    container.titlesContainer.tag = data.yearMonth
                    container.titlesContainer.children.map { it as TextView }
                        .forEachIndexed { index, textView ->
                            val dayOfWeek = daysOfWeek.plus(index.toLong())
                            val title = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            textView.text = title
                        }
                }
            }
        }

        calendarView.setup(startMonth, endMonth, daysOfWeek)
        calendarView.scrollToMonth(currentMonth)
        
        weekCalendarView.setup(startMonth.atDay(1), endMonth.atEndOfMonth(), daysOfWeek)
        weekCalendarView.scrollToWeek(LocalDate.now())

        viewModeToggle.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbMonth -> {
                    calendarView.visibility = View.VISIBLE
                    weekCalendarView.visibility = View.GONE
                    calendarView.scrollToMonth(YearMonth.from(selectedDate))
                }
                R.id.rbWeek -> {
                    calendarView.visibility = View.GONE
                    weekCalendarView.visibility = View.VISIBLE
                    weekCalendarView.scrollToWeek(selectedDate)
                }
                R.id.rbDay -> {
                    calendarView.visibility = View.GONE
                    weekCalendarView.visibility = View.GONE
                }
            }
        }
    }
    
    private fun bindDayView(container: DayViewContainer, date: LocalDate, isCurrentMonth: Boolean) {
        container.textView.text = date.dayOfMonth.toString()
        
        val calendar = Calendar.getInstance()
        calendar.set(date.year, date.monthValue - 1, date.dayOfMonth)
        val lunar = LunarCalendar(calendar)
        container.lunarTextView.text = lunar.lunarDayString

        if (date == selectedDate) {
            container.textView.setTextColor(Color.WHITE)
            container.lunarTextView.setTextColor(Color.WHITE)
            container.view.setBackgroundResource(R.drawable.selector_toggle_bg)
             container.view.setBackgroundColor(getColor(R.color.design_default_color_primary))
        } else {
            if (isCurrentMonth) {
                container.textView.setTextColor(Color.BLACK)
                container.lunarTextView.setTextColor(Color.GRAY)
            } else {
                container.textView.setTextColor(Color.LTGRAY)
                container.lunarTextView.setTextColor(Color.LTGRAY)
            }
            container.view.background = null
        }
    }
    
    private fun updateLunarDate() {
        val lunarDateText = findViewById<TextView>(R.id.lunarDateText)
        val calendar = Calendar.getInstance()
        calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
        val lunar = LunarCalendar(calendar)
        
        val solarDateStr = "${selectedDate.year}年 ${selectedDate.monthValue}月${selectedDate.dayOfMonth}日"
        
        lunarDateText.text = "$solarDateStr\n${lunar.lunarDateString}"
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.addEventFab).setOnClickListener {
            openAddEventActivity(null)
        }
        findViewById<FloatingActionButton>(R.id.importEventFab).setOnClickListener {
            // New logic: Show dialog to choose between Import URL or Copy/Paste
            showImportExportDialog()
        }
    }
    
    private fun showImportExportDialog() {
        val options = arrayOf("从链接导入", "复制当天事件", "粘贴事件到当天")
        AlertDialog.Builder(this)
            .setTitle("导入 / 导出")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showImportDialog()
                    1 -> copyCurrentDayEvents()
                    2 -> pasteEventsToCurrentDay()
                }
            }
            .show()
    }
    
    private fun copyCurrentDayEvents() {
        val events = currentEventsLiveData?.value
        if (events != null) {
            viewModel.copyEvents(events)
        } else {
             viewModel.copyEvents(emptyList())
        }
    }
    
    private fun pasteEventsToCurrentDay() {
        viewModel.pasteEventsToDate(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
    }

    private fun observeEvents() {
        currentEventsLiveData?.removeObservers(this)
        currentEventsLiveData = viewModel.getEventsForDate(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
        currentEventsLiveData?.observe(this) { events ->
            adapter.submitList(events)
        }
    }
    
    private fun showImportDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("导入事件")
            .setMessage("请输入 iCalendar 链接")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val url = input.text.toString()
                if (url.isNotEmpty()) {
                    viewModel.importEventsFromUrl(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openAddEventActivity(event: Event?) {
        val intent = Intent(this, AddEventActivity::class.java)
        intent.putExtra("year", selectedDate.year)
        intent.putExtra("month", selectedDate.monthValue - 1)
        intent.putExtra("day", selectedDate.dayOfMonth)
        if (event != null) {
            intent.putExtra("event_id", event.id)
            intent.putExtra("title", event.title)
            intent.putExtra("description", event.description)
            intent.putExtra("start_time", event.startTime)
            intent.putExtra("end_time", event.endTime)
            intent.putExtra("location", event.location)
            intent.putExtra("reminder_minutes", event.reminderMinutes) // Pass reminderMinutes
        }
        startActivity(intent)
    }
    
    private fun showDeleteDialog(event: Event) {
        AlertDialog.Builder(this)
            .setTitle("删除事件")
            .setMessage("确定要删除 '${event.title}' 吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.delete(event)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}