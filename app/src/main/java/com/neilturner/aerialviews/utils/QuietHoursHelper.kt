package com.neilturner.aerialviews.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.receivers.QuietHoursWakeReceiver
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object QuietHoursHelper {
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    /**
     * Check if we are currently in quiet hours
     */
    fun isInQuietHours(): Boolean {
        if (!GeneralPrefs.quietHoursEnabled) {
            Timber.d("Quiet hours disabled")
            return false
        }
        
        val currentTime = Calendar.getInstance()
        val currentTimeString = timeFormat.format(currentTime.time)
        
        val startTime = GeneralPrefs.quietHoursStart
        val endTime = GeneralPrefs.quietHoursEnd
        
        Timber.d("Quiet hours check - Enabled: ${GeneralPrefs.quietHoursEnabled}")
        Timber.d("Quiet hours check - Current: $currentTimeString, Start: $startTime, End: $endTime")
        
        val result = isTimeInRange(currentTimeString, startTime, endTime)
        Timber.d("Quiet hours check - Result: $result")
        
        return result
    }
    
    /**
     * Check if a time falls within a range, handling overnight ranges
     * @param currentTime Current time in HH:mm format
     * @param startTime Start time in HH:mm format
     * @param endTime End time in HH:mm format
     */
    private fun isTimeInRange(currentTime: String, startTime: String, endTime: String): Boolean {
        try {
            val current = timeFormat.parse(currentTime)!!
            val start = timeFormat.parse(startTime)!!
            val end = timeFormat.parse(endTime)!!
            
            Timber.d("Time comparison - Current: $currentTime (${current.time}), Start: $startTime (${start.time}), End: $endTime (${end.time})")
            
            // Handle overnight ranges (e.g., 22:00 to 07:00)
            if (start.after(end)) {
                // Overnight range: current time should be >= start OR <= end
                val result = current >= start || current <= end
                Timber.d("Overnight range - Current >= Start: ${current >= start}, Current <= End: ${current <= end}, Result: $result")
                return result
            } else {
                // Same day range: current time should be >= start AND <= end
                val result = current >= start && current <= end
                Timber.d("Same day range - Current >= Start: ${current >= start}, Current <= End: ${current <= end}, Result: $result")
                return result
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing time for quiet hours check")
            return false
        }
    }
    
    /**
     * Get the next wake-up time (when quiet hours end)
     */
    fun getNextWakeUpTime(): Calendar {
        val calendar = Calendar.getInstance()
        val endTime = GeneralPrefs.quietHoursEnd
        
        try {
            val end = timeFormat.parse(endTime)!!
            val endCalendar = Calendar.getInstance()
            endCalendar.time = end
            calendar.set(Calendar.HOUR_OF_DAY, endCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, endCalendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // If the end time has already passed today, set it for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing end time for wake up calculation")
            // Default to 7:00 AM tomorrow
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 7)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        
        return calendar
    }
    
    /**
     * Get the next quiet hours start time
     */
    fun getNextQuietHoursStartTime(): Calendar {
        val calendar = Calendar.getInstance()
        val startTime = GeneralPrefs.quietHoursStart
        
        try {
            val start = timeFormat.parse(startTime)!!
            val startCalendar = Calendar.getInstance()
            startCalendar.time = start
            calendar.set(Calendar.HOUR_OF_DAY, startCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, startCalendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // If the start time has already passed today, set it for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating next quiet hours start time")
            // Fallback to 1 minute from now if parsing fails
            calendar.add(Calendar.MINUTE, 1)
        }
        
        return calendar
    }
    
    /**
     * Get a human-readable description of quiet hours
     */
    fun getQuietHoursDescription(): String {
        if (!GeneralPrefs.quietHoursEnabled) {
            return "Quiet hours disabled"
        }
        
        val startTime = GeneralPrefs.quietHoursStart
        val endTime = GeneralPrefs.quietHoursEnd
        
        return "Quiet hours: $startTime - $endTime"
    }
    
    /**
     * Schedule an alarm to wake up the screensaver when quiet hours end
     */
    fun scheduleWakeUpAlarm(context: Context) {
        if (!GeneralPrefs.quietHoursEnabled) {
            Timber.d("Quiet hours not enabled, skipping alarm scheduling")
            return
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check if we can schedule exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Timber.e("Cannot schedule exact alarms - permission not granted")
                return
            }
        }
        
        val wakeUpTime = getNextWakeUpTime()
        
        val intent = Intent(context, QuietHoursWakeReceiver::class.java).apply {
            action = "WAKE_UP_FROM_QUIET_HOURS"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // Schedule the alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                wakeUpTime.timeInMillis,
                pendingIntent
            )
            
            Timber.d("Successfully scheduled wake-up alarm for: ${timeFormat.format(wakeUpTime.time)} (${wakeUpTime.timeInMillis})")
            Timber.d("Current time: ${timeFormat.format(Date())} (${System.currentTimeMillis()})")
            Timber.d("Time until wake-up: ${(wakeUpTime.timeInMillis - System.currentTimeMillis()) / 1000} seconds")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule wake-up alarm")
        }
    }
    
    /**
     * Cancel the wake-up alarm
     */
    fun cancelWakeUpAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, QuietHoursWakeReceiver::class.java).apply {
            action = "WAKE_UP_FROM_QUIET_HOURS"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Timber.d("Cancelled wake-up alarm")
    }
    
    /**
     * Schedule a test alarm for debugging (wakes up in 10 seconds)
     */
    fun scheduleTestWakeUpAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val testTime = System.currentTimeMillis() + 10000 // 10 seconds from now
        
        val intent = Intent(context, QuietHoursWakeReceiver::class.java).apply {
            action = "WAKE_UP_FROM_QUIET_HOURS"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999, // Different request code for test alarm
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule the test alarm
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            testTime,
            pendingIntent
        )
        
        Timber.d("Scheduled TEST wake-up alarm for: ${timeFormat.format(Date(testTime))} (in 10 seconds)")
    }
    
    /**
     * Schedule an alarm to turn off the screensaver when quiet hours start
     */
    fun scheduleQuietHoursStartAlarm(context: Context) {
        if (!GeneralPrefs.quietHoursEnabled) {
            Timber.d("Quiet hours not enabled, skipping quiet hours start alarm scheduling")
            return
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check if we can schedule exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Timber.e("Cannot schedule exact alarms - permission not granted")
                return
            }
        }
        
        val quietHoursStartTime = getNextQuietHoursStartTime()
        
        val intent = Intent(context, QuietHoursWakeReceiver::class.java).apply {
            action = "QUIET_HOURS_START"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1, // Different request code for quiet hours start
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // Schedule the alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                quietHoursStartTime.timeInMillis,
                pendingIntent
            )
            
            Timber.d("Successfully scheduled quiet hours start alarm for: ${timeFormat.format(quietHoursStartTime.time)} (${quietHoursStartTime.timeInMillis})")
            Timber.d("Current time: ${timeFormat.format(Date())} (${System.currentTimeMillis()})")
            Timber.d("Time until quiet hours start: ${(quietHoursStartTime.timeInMillis - System.currentTimeMillis()) / 1000} seconds")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule quiet hours start alarm")
        }
    }
    
    /**
     * Cancel the quiet hours start alarm
     */
    fun cancelQuietHoursStartAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, QuietHoursWakeReceiver::class.java).apply {
            action = "QUIET_HOURS_START"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1, // Same request code as scheduling
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Timber.d("Cancelled quiet hours start alarm")
    }
    
    /**
     * Test method to verify quiet hours settings and logic
     */
    fun testQuietHoursSettings(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== QUIET HOURS TEST ===")
        sb.appendLine("Enabled: ${GeneralPrefs.quietHoursEnabled}")
        sb.appendLine("Start Time: ${GeneralPrefs.quietHoursStart}")
        sb.appendLine("End Time: ${GeneralPrefs.quietHoursEnd}")
        
        val currentTime = Calendar.getInstance()
        val currentTimeString = timeFormat.format(currentTime.time)
        sb.appendLine("Current Time: $currentTimeString")
        
        val isInQuietHours = isInQuietHours()
        sb.appendLine("Is in Quiet Hours: $isInQuietHours")
        
        if (GeneralPrefs.quietHoursEnabled) {
            val wakeUpTime = getNextWakeUpTime()
            val quietHoursStartTime = getNextQuietHoursStartTime()
            sb.appendLine("Next Wake Up: ${timeFormat.format(wakeUpTime.time)}")
            sb.appendLine("Time Until Wake Up: ${(wakeUpTime.timeInMillis - System.currentTimeMillis()) / 1000} seconds")
            sb.appendLine("Next Quiet Hours Start: ${timeFormat.format(quietHoursStartTime.time)}")
            sb.appendLine("Time Until Quiet Hours Start: ${(quietHoursStartTime.timeInMillis - System.currentTimeMillis()) / 1000} seconds")
        }
        
        sb.appendLine("========================")
        
        val result = sb.toString()
        Timber.d(result)
        return result
    }
}
