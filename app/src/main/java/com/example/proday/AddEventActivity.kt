package com.example.proday

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.proday.data.Event
import com.example.proday.viewmodel.EventViewModel
import java.util.Calendar

class AddEventActivity : AppCompatActivity() {

    private val viewModel: EventViewModel by viewModels()
    private var startTimeInMillis: Long = 0
    private var endTimeInMillis: Long = 0
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var eventId: Long = 0
    private var isReminderEnabled: Boolean = true
    private var selectedReminderMinutes: Int = 0

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted
            } else {
                Toast.makeText(this, "需要通知权限来发送提醒", Toast.LENGTH_SHORT).show()
                findViewById<Switch>(R.id.switchReminder)?.isChecked = false
                isReminderEnabled = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        selectedYear = intent.getIntExtra("year", 0)
        selectedMonth = intent.getIntExtra("month", 0)
        selectedDay = intent.getIntExtra("day", 0)
        eventId = intent.getLongExtra("event_id", 0)

        val titleInput = findViewById<EditText>(R.id.editTitle)
        val descInput = findViewById<EditText>(R.id.editDescription)
        val locInput = findViewById<EditText>(R.id.editLocation)
        val btnStartTime = findViewById<Button>(R.id.btnStartTime)
        val btnEndTime = findViewById<Button>(R.id.btnEndTime)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnDelete = findViewById<Button>(R.id.btnDelete)
        val switchReminder = findViewById<Switch>(R.id.switchReminder)
        val spinnerReminder = findViewById<Spinner>(R.id.spinnerReminder)
        val reminderOptionsLayout = findViewById<LinearLayout>(R.id.reminderOptionsLayout)

        checkPermissions()
        setupReminderSpinner(spinnerReminder)

        // Initial setup
        if (eventId != 0L) {
            // Edit Mode
            titleInput.setText(intent.getStringExtra("title"))
            descInput.setText(intent.getStringExtra("description"))
            locInput.setText(intent.getStringExtra("location"))
            startTimeInMillis = intent.getLongExtra("start_time", 0)
            endTimeInMillis = intent.getLongExtra("end_time", 0)
            btnDelete.visibility = View.VISIBLE
            
            val reminderMin = intent.getIntExtra("reminder_minutes", 0)
            isReminderEnabled = reminderMin >= 0
            selectedReminderMinutes = if (reminderMin >= 0) reminderMin else 0
            
            switchReminder.isChecked = isReminderEnabled
            reminderOptionsLayout.visibility = if (isReminderEnabled) View.VISIBLE else View.GONE
            
            // Set spinner selection
            setSpinnerSelection(spinnerReminder, selectedReminderMinutes)
            
            val headerTitle = findViewById<TextView>(R.id.headerTitle)
            if (headerTitle != null) {
                headerTitle.text = "编辑日程"
            }
        } else {
            // Add Mode
            val calendar = Calendar.getInstance()
            if (selectedYear != 0) {
                calendar.set(selectedYear, selectedMonth, selectedDay)
            }
            startTimeInMillis = calendar.timeInMillis
            endTimeInMillis = calendar.timeInMillis + 3600000 
            btnDelete.visibility = View.GONE
            
            isReminderEnabled = true
            switchReminder.isChecked = true
            selectedReminderMinutes = 0
            setSpinnerSelection(spinnerReminder, 0)
        }

        updateTimeButtons(btnStartTime, btnEndTime)
        
        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            isReminderEnabled = isChecked
            reminderOptionsLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                checkPermissions()
            }
        }

        btnStartTime.setOnClickListener {
            val c = Calendar.getInstance()
            c.timeInMillis = startTimeInMillis
            showTimePicker(c) { hour, minute ->
                c.set(Calendar.HOUR_OF_DAY, hour)
                c.set(Calendar.MINUTE, minute)
                startTimeInMillis = c.timeInMillis
                updateTimeButtons(btnStartTime, btnEndTime)
            }
        }

        btnEndTime.setOnClickListener {
            val c = Calendar.getInstance()
            c.timeInMillis = endTimeInMillis
            showTimePicker(c) { hour, minute ->
                c.set(Calendar.HOUR_OF_DAY, hour)
                c.set(Calendar.MINUTE, minute)
                endTimeInMillis = c.timeInMillis
                updateTimeButtons(btnStartTime, btnEndTime)
            }
        }

        btnSave.setOnClickListener {
            val title = titleInput.text.toString()
            if (title.isNotBlank()) {
                val calendar = Calendar.getInstance()
                
                val startCal = Calendar.getInstance()
                startCal.timeInMillis = startTimeInMillis
                
                calendar.set(selectedYear, selectedMonth, selectedDay, 
                             startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE))
                val finalStart = calendar.timeInMillis
                
                val endCal = Calendar.getInstance()
                endCal.timeInMillis = endTimeInMillis
                
                calendar.set(selectedYear, selectedMonth, selectedDay, 
                             endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE))
                val finalEnd = calendar.timeInMillis
                
                // Get selected reminder minutes from spinner if enabled
                val finalReminderMinutes = if (isReminderEnabled) {
                    val selectedItem = spinnerReminder.selectedItem as String
                    parseReminderMinutes(selectedItem)
                } else {
                    -1 // Disabled
                }

                val event = Event(
                    id = eventId,
                    title = title,
                    description = descInput.text.toString(),
                    startTime = finalStart,
                    endTime = finalEnd,
                    location = locInput.text.toString(),
                    reminderMinutes = finalReminderMinutes
                )
                
                if (eventId == 0L) {
                    viewModel.insert(event)
                } else {
                    viewModel.update(event)
                }
                finish()
            } else {
                Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("删除日程")
                .setMessage("确定要删除这个日程吗？")
                .setPositiveButton("删除") { _, _ ->
                     val eventToDelete = Event(
                         id = eventId,
                         title = "",
                         description = "",
                         startTime = 0,
                         endTime = 0,
                         location = "",
                         reminderMinutes = 0
                     )
                     viewModel.delete(eventToDelete)
                     finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun setupReminderSpinner(spinner: Spinner) {
        val options = arrayOf("日程开始时", "5分钟前", "10分钟前", "15分钟前", "30分钟前", "1小时前", "1天前")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }
    
    private fun parseReminderMinutes(text: String): Int {
        return when (text) {
            "日程开始时" -> 0
            "5分钟前" -> 5
            "10分钟前" -> 10
            "15分钟前" -> 15
            "30分钟前" -> 30
            "1小时前" -> 60
            "1天前" -> 1440
            else -> 0
        }
    }
    
    private fun setSpinnerSelection(spinner: Spinner, minutes: Int) {
        val options = arrayOf("日程开始时", "5分钟前", "10分钟前", "15分钟前", "30分钟前", "1小时前", "1天前")
        val index = when (minutes) {
            0 -> 0
            5 -> 1
            10 -> 2
            15 -> 3
            30 -> 4
            60 -> 5
            1440 -> 6
            else -> 0
        }
        spinner.setSelection(index)
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private fun showTimePicker(initialTime: Calendar, onTimeSet: (Int, Int) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val hourPicker = dialogView.findViewById<NumberPicker>(R.id.hourPicker)
        val minutePicker = dialogView.findViewById<NumberPicker>(R.id.minutePicker)

        hourPicker.minValue = 0
        hourPicker.maxValue = 23
        hourPicker.value = initialTime.get(Calendar.HOUR_OF_DAY)
        hourPicker.setFormatter { String.format("%02d", it) }

        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.value = initialTime.get(Calendar.MINUTE)
        minutePicker.setFormatter { String.format("%02d", it) }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                onTimeSet(hourPicker.value, minutePicker.value)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateTimeButtons(btnStart: Button, btnEnd: Button) {
        val c = Calendar.getInstance()
        
        c.timeInMillis = startTimeInMillis
        btnStart.text = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        
        c.timeInMillis = endTimeInMillis
        btnEnd.text = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}