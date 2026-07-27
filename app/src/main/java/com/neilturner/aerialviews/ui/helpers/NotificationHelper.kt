package com.neilturner.aerialviews.ui.helpers

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

object NotificationHelper {
    private const val DEFAULT_DURATION_MS = 2000L
    private const val FADE_DURATION_MS = 200L
    private const val BOTTOM_MARGIN_DP = 16

    private var currentTextView: TextView? = null
    private var currentContainer: ViewGroup? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun show(
        view: ViewGroup,
        message: String,
        duration: Long = DEFAULT_DURATION_MS,
    ) {
        // Remove existing notification immediately
        removeCurrentNotification()

        val context = view.context
        val textView =
            TextView(context).apply {
                text = message
                setTextColor(Color.BLACK)
                typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, 400)
                setBackgroundResource(R.drawable.bg_notification)
                gravity = Gravity.CENTER
            }

        val params =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (BOTTOM_MARGIN_DP * context.resources.displayMetrics.density).toInt()
            }

        view.addView(textView, view.childCount, params)
        currentTextView = textView
        currentContainer = view

        // Fade in
        textView.alpha = 0f
        textView.animate()
            .alpha(1f)
            .setDuration(FADE_DURATION_MS)
            .start()

        // Auto-dismiss after duration
        dismissRunnable =
            Runnable {
                textView.animate()
                    .alpha(0f)
                    .setDuration(FADE_DURATION_MS)
                    .withEndAction {
                        if (currentTextView == textView) {
                            view.removeView(textView)
                            currentTextView = null
                            currentContainer = null
                        }
                    }
                    .start()
            }
        handler.postDelayed(dismissRunnable!!, duration)
    }

    private fun removeCurrentNotification() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        currentTextView?.let { textView ->
            textView.animate().cancel()
            currentContainer?.removeView(textView)
        }
        currentTextView = null
        currentContainer = null
        dismissRunnable = null
    }
}
