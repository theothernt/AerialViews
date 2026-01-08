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
import com.neilturner.aerialviews.utils.CountdownTimeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class CountdownOverlay : AppCompatTextView {
    var type = OverlayType.EMPTY

    private val prefs = GeneralPrefs
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    private var targetTimeStr: String = ""
    private var targetMessage: String = ""
    private var targetDateTime: LocalDateTime? = null
    private var isCompleted = false

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
        initCountdown()
        startCountdown()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCountdown()
    }

    private fun initCountdown() {
        targetTimeStr = prefs.countdownTargetTime
        targetMessage = prefs.countdownTargetMessage.ifEmpty { "Time's up!" }
        targetDateTime = parseTargetTime(targetTimeStr, LocalDateTime.now())
        isCompleted = false
    }

    private fun startCountdown() {
        stopCountdown()
        if (targetTimeStr.isEmpty() || targetDateTime == null) {
            if (targetTimeStr.isNotEmpty()) {
                text = "Invalid time format"
            }
            return
        }

        updateJob =
            mainScope.launch {
                while (isActive) {
                    updateCountdown()
                    if (isCompleted) break
                    delay(1000)
                }
            }
    }

    private fun stopCountdown() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun updateCountdown() {
        try {
            val target = targetDateTime ?: return
            val now = LocalDateTime.now()

            val totalSeconds = ChronoUnit.SECONDS.between(now, target)

            if (totalSeconds <= 0) {
                text = targetMessage
                isCompleted = true
                return
            }

            text = formatCountdown(totalSeconds)
        } catch (e: Exception) {
            Timber.e("Error updating countdown: $e")
            text = "Error"
        }
    }

    private fun formatCountdown(totalSeconds: Long): String {
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun parseTargetTime(
        timeString: String,
        currentDateTime: LocalDateTime,
    ): LocalDateTime? = CountdownTimeParser.parseTargetTime(timeString, currentDateTime)

    fun applyTextSize(sizeValue: Int) {
        val sizeInSp = sizeValue.toFloat()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp)
    }

    fun applyTextWeight(weightValue: Int) {
        typeface = FontHelper.getTypeface(context, prefs.fontTypeface, weightValue)
    }
}
