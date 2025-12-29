package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FontHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class CountdownOverlay : AppCompatTextView {
    var type = OverlayType.EMPTY
    
    private val prefs = GeneralPrefs
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var updateJob: Job? = null
    
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startCountdown()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCountdown()
    }

    private fun startCountdown() {
        stopCountdown()
        updateJob = mainScope.launch {
            while (isActive) {
                updateCountdown()
                delay(1000) // Update every second
            }
        }
    }

    private fun stopCountdown() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun updateCountdown() {
        try {
            val targetTime = prefs.countdownTargetTime
            val targetMessage = prefs.countdownTargetMessage
            
            if (targetTime.isEmpty()) {
                text = null
                return
            }

            val now = LocalDateTime.now()
            val target = parseTargetTime(targetTime, now)
            
            if (target == null) {
                text = "Invalid time format"
                return
            }

            val totalSeconds = ChronoUnit.SECONDS.between(now, target)
            
            if (totalSeconds <= 0) {
                // Countdown finished, show target message
                text = targetMessage.ifEmpty { "Time's up!" }
                return
            }

            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            val countdownText = when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }

            text = countdownText

        } catch (e: Exception) {
            Timber.e("Error updating countdown: $e")
            text = "Error"
        }
    }

    private fun parseTargetTime(timeString: String, currentDateTime: LocalDateTime): LocalDateTime? {
        return try {
            when {
                // HH:MM format - same day
                timeString.matches(Regex("^\\d{1,2}:\\d{2}$")) -> {
                    val parts = timeString.split(":")
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    val targetTime = LocalTime.of(hour, minute)
                    val targetDate = currentDateTime.toLocalDate()
                    
                    var targetDateTime = LocalDateTime.of(targetDate, targetTime)
                    
                    // If time has passed today, schedule for tomorrow
                    if (targetDateTime.isBefore(currentDateTime)) {
                        targetDateTime = targetDateTime.plusDays(1)
                    }
                    
                    targetDateTime
                }
                
                // YYYY-MM-DD HH:MM format
                timeString.matches(Regex("^\\d{4}-\\d{2}-\\d{2} \\d{1,2}:\\d{2}$")) -> {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm")
                    LocalDateTime.parse(timeString, formatter)
                }
                
                else -> null
            }
        } catch (e: Exception) {
            Timber.e("Error parsing target time: $e")
            null
        }
    }

    fun applyTextSize(sizeValue: Int) {
        val sizeInSp = sizeValue.toFloat()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp)
    }

    fun applyTextWeight(weightValue: Int) {
        typeface = FontHelper.getTypeface(context, prefs.fontTypeface, weightValue)
    }
}
