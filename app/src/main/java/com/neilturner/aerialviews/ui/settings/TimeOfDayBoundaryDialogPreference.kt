package com.neilturner.aerialviews.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.preference.DialogPreference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

class TimeOfDayBoundaryDialogPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    ) : DialogPreference(context, attrs, defStyleAttr) {
    override fun onClick() {
        showDialog()
    }

    private fun showDialog() {
        val dayIncludes = GeneralPrefs.playlistTimeOfDayDayIncludes
        val nightIncludes = GeneralPrefs.playlistTimeOfDayNightIncludes

        val rows =
            mutableListOf(
                TimeOfDayRow.Header(context.getString(R.string.playlist_time_of_day_heading_day)),
                TimeOfDayRow.Option(
                    group = TimeOfDayGroup.DAY,
                    value = VALUE_SUNRISE,
                    label = context.getString(R.string.playlist_time_of_day_option_sunrise),
                    isSelected = dayIncludes.contains(VALUE_SUNRISE),
                ),
                TimeOfDayRow.Option(
                    group = TimeOfDayGroup.DAY,
                    value = VALUE_SUNSET,
                    label = context.getString(R.string.playlist_time_of_day_option_sunset),
                    isSelected = dayIncludes.contains(VALUE_SUNSET),
                ),
                TimeOfDayRow.Header(context.getString(R.string.playlist_time_of_day_heading_night)),
                TimeOfDayRow.Option(
                    group = TimeOfDayGroup.NIGHT,
                    value = VALUE_SUNRISE,
                    label = context.getString(R.string.playlist_time_of_day_option_sunrise),
                    isSelected = nightIncludes.contains(VALUE_SUNRISE),
                ),
                TimeOfDayRow.Option(
                    group = TimeOfDayGroup.NIGHT,
                    value = VALUE_SUNSET,
                    label = context.getString(R.string.playlist_time_of_day_option_sunset),
                    isSelected = nightIncludes.contains(VALUE_SUNSET),
                ),
            )

        val adapter = TimeOfDayBoundaryAdapter(rows)
        val dialogView =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                clipToPadding = false
                setPadding(12, 12, 0, 12)
            }

        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(dialogView)
            }

        AlertDialog
            .Builder(context)
            .setTitle(dialogTitle ?: title ?: "")
            .setView(container)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                GeneralPrefs.playlistTimeOfDayDayIncludes.clear()
                GeneralPrefs.playlistTimeOfDayDayIncludes.addAll(adapter.getSelectedFor(TimeOfDayGroup.DAY))
                GeneralPrefs.playlistTimeOfDayNightIncludes.clear()
                GeneralPrefs.playlistTimeOfDayNightIncludes.addAll(adapter.getSelectedFor(TimeOfDayGroup.NIGHT))
                notifyChanged()
            }.setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    companion object {
        private const val VALUE_SUNRISE = "SUNRISE"
        private const val VALUE_SUNSET = "SUNSET"
    }
}
