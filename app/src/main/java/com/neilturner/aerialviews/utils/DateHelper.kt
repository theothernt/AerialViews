package com.neilturner.aerialviews.utils

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAccessor

object DateHelper {
    fun formatDate(
        context: Context,
        type: DateType,
        custom: String?,
    ): String =
        when (type) {
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
                } catch (e: Exception) {
                    Timber.e(e)
                    context.resources.getString(R.string.appearance_date_custom_error)
                }
            }
        }

    fun formatExifDateTime(
        date: String,
        offset: String? = null,
    ): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

        val parsed: TemporalAccessor? =
            try {
                if (!offset.isNullOrBlank()) {
                    OffsetDateTime.parse("$date$offset", DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ssXXX", Locale.ROOT))
                } else {
                    LocalDateTime.parse(date, formatter)
                }
            } catch (_: Exception) {
                null
            }

        return try {
            when (parsed) {
                is OffsetDateTime -> {
                    DateTimeFormatter
                        .ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(Locale.getDefault())
                        .format(parsed)
                }

                is LocalDateTime -> {
                    DateTimeFormatter
                        .ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(Locale.getDefault())
                        .format(parsed)
                }

                else -> {
                    date
                }
            }
        } catch (_: Exception) {
            date
        }
    }

    fun formatExifDate(
        date: String,
        offset: String?,
        type: DateType,
        custom: String?,
    ): String? {
        val parsedDate = parseExifDate(date, offset) ?: return null
        return when (type) {
            DateType.FULL -> {
                DateFormat.getDateInstance(DateFormat.FULL).format(parsedDate)
            }

            DateType.COMPACT -> {
                DateFormat.getDateInstance(DateFormat.SHORT).format(parsedDate)
            }

            DateType.CUSTOM -> {
                try {
                    val pattern = custom?.ifBlank { "yyyy-MM-dd" } ?: "yyyy-MM-dd"
                    SimpleDateFormat(pattern, Locale.getDefault()).format(parsedDate)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun parseExifDate(
        date: String,
        offset: String?,
    ): Date? {
        val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
        return try {
            if (!offset.isNullOrBlank()) {
                val parsed = OffsetDateTime.parse("$date$offset", DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ssXXX", Locale.ROOT))
                Date.from(parsed.toInstant())
            } else {
                val parsed = LocalDateTime.parse(date, formatter)
                Date.from(parsed.atZone(ZoneId.systemDefault()).toInstant())
            }
        } catch (_: Exception) {
            null
        }
    }
}
