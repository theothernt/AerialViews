package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.utils.CountdownFormatter
import com.neilturner.aerialviews.ui.overlays.utils.CountdownTimeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds

class CountdownOverlay : AppCompatTextView {
    var type = OverlayType.EMPTY

    private val prefs = GeneralPrefs
    private var scopeJob = SupervisorJob()
    private var mainScope = CoroutineScope(Dispatchers.Main + scopeJob)
    private var updateJob: Job? = null

    private var targetTimeStr: String = ""
    private var targetMessage: String = ""
    private var label: String = ""
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
        if (scopeJob.isCancelled) {
            scopeJob = SupervisorJob()
            mainScope = CoroutineScope(Dispatchers.Main + scopeJob)
        }
        initCountdown()
        startCountdown()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCountdown()
        scopeJob.cancel()
    }

    private fun initCountdown() {
        targetTimeStr = prefs.countdownTargetTime
        targetMessage = prefs.countdownTargetMessage.ifEmpty { resources.getString(R.string.countdown_time_is_up) }
        label = prefs.countdownLabel
        targetDateTime = parseTargetTime(targetTimeStr, LocalDateTime.now())
        isCompleted = false
    }

    private fun startCountdown() {
        stopCountdown()
        if (targetTimeStr.isEmpty() || targetDateTime == null) {
            if (targetTimeStr.isNotEmpty()) {
                text = resources.getString(R.string.countdown_invalid_time_format)
            }
            return
        }

        updateJob =
            mainScope.launch {
                while (isActive) {
                    updateCountdown()
                    if (isCompleted) break
                    delay(1000.milliseconds)
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
            text = resources.getString(R.string.countdown_error)
        }
    }

    private fun formatCountdown(totalSeconds: Long): String {
        val separator = resources.getString(R.string.countdown_unit_separator)
        val countdown =
            CountdownFormatter.parts(totalSeconds).joinToString(separator) { part ->
                resources.getQuantityString(part.unit.pluralsRes, part.value.toInt(), part.value)
            }

        return listOfNotNull(
            label.takeIf { it.isNotBlank() },
            countdown.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

    private fun parseTargetTime(
        timeString: String,
        currentDateTime: LocalDateTime,
    ): LocalDateTime? = CountdownTimeParser.parseTargetTime(timeString, currentDateTime)
}
