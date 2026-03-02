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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAccessor
import java.util.TimeZone

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
        val parsedOffset = parseZoneOffset(offset)
        val parsedDate = parseExifDate(date, offset) ?: return null
        return when (type) {
            DateType.FULL -> {
                DateFormat
                    .getDateInstance(DateFormat.FULL)
                    .apply {
                        if (parsedOffset != null) {
                            timeZone = TimeZone.getTimeZone(parsedOffset)
                        }
                    }.format(parsedDate)
            }

            DateType.COMPACT -> {
                DateFormat
                    .getDateInstance(DateFormat.SHORT)
                    .apply {
                        if (parsedOffset != null) {
                            timeZone = TimeZone.getTimeZone(parsedOffset)
                        }
                    }.format(parsedDate)
            }

            DateType.CUSTOM -> {
                try {
                    val pattern = custom?.ifBlank { "yyyy-MM-dd" } ?: "yyyy-MM-dd"
                    SimpleDateFormat(pattern, Locale.getDefault())
                        .apply {
                            if (parsedOffset != null) {
                                timeZone = TimeZone.getTimeZone(parsedOffset)
                            }
                        }.format(parsedDate)
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
        val trimmedDate = date.trim()
        val trimmedOffset = offset?.trim()
        val parsedOffset = parseZoneOffset(trimmedOffset)

        val exifLocalFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
        val exifWithOffsetFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ssXXX", Locale.ROOT)

        if (parsedOffset != null) {
            try {
                val parsed = OffsetDateTime.parse("$trimmedDate${parsedOffset.id}", exifWithOffsetFormatter)
                return Date.from(parsed.toInstant())
            } catch (_: Exception) {
                // Keep trying other formats below.
            }

            try {
                val parsed = LocalDateTime.parse(trimmedDate, exifLocalFormatter)
                return Date.from(parsed.toInstant(parsedOffset))
            } catch (_: Exception) {
                // Keep trying other formats below.
            }
        }

        try {
            val parsed = LocalDateTime.parse(trimmedDate, exifLocalFormatter)
            return Date.from(parsed.atZone(ZoneId.systemDefault()).toInstant())
        } catch (_: Exception) {
            // Keep trying ISO-8601 variants below.
        }

        try {
            val parsed = OffsetDateTime.parse(trimmedDate)
            return Date.from(
                if (parsedOffset != null) {
                    parsed.toLocalDateTime().toInstant(parsedOffset)
                } else {
                    parsed.toInstant()
                },
            )
        } catch (_: Exception) {
            // Keep trying.
        }

        try {
            val parsed = ZonedDateTime.parse(trimmedDate)
            return Date.from(
                if (parsedOffset != null) {
                    parsed.toLocalDateTime().toInstant(parsedOffset)
                } else {
                    parsed.toInstant()
                },
            )
        } catch (_: Exception) {
            // Keep trying.
        }

        try {
            return Date.from(Instant.parse(trimmedDate))
        } catch (_: Exception) {
            // Keep trying.
        }

        try {
            val parsed = LocalDateTime.parse(trimmedDate)
            return Date.from(
                if (parsedOffset != null) {
                    parsed.toInstant(parsedOffset)
                } else {
                    parsed.atZone(ZoneId.systemDefault()).toInstant()
                },
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseZoneOffset(offset: String?): ZoneOffset? {
        if (offset.isNullOrBlank()) {
            return null
        }

        return try {
            ZoneOffset.of(offset.trim())
        } catch (_: Exception) {
            null
        }
    }
}
