package com.neilturner.aerialviews.utils

import android.util.Log
import com.neilturner.aerialviews.models.enums.DateType
import java.lang.Exception
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateHelper {
    fun formatDate(type: DateType, custom: String?): String {
        return when (type) {
            DateType.FULL -> {
                DateFormat.getDateInstance(DateFormat.FULL).format(Date())
            }
            DateType.COMPACT -> {
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date())
            }
            else -> {
                try {
                    val today = Calendar.getInstance().time
                    val formatter = SimpleDateFormat(custom, Locale.getDefault())
                    formatter.format(today)
                } catch (ex: Exception) {
                    Log.i(TAG, "Exception while trying custom date formatting")
                    "Invalid custom date format!"
                }
            }
        }
    }

    private const val TAG = "DateHelper"
}