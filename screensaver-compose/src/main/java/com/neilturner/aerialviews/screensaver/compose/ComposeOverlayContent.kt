@file:Suppress("ktlint:standard:function-naming")

package com.neilturner.aerialviews.screensaver.compose

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.ForecastDay
import com.neilturner.aerialviews.services.weather.ForecastType
import com.neilturner.aerialviews.ui.core.ScreensaverState
import com.neilturner.aerialviews.ui.overlays.config.OverlayConfig
import com.neilturner.aerialviews.ui.overlays.config.OverlaySlotConfig
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import android.graphics.Color as AndroidColor

/**
 * One populated overlay slot.
 *
 * The slot owns only presentation. Content comes from the shared [OverlayUiState], except for
 * clock/date/countdown/message defaults, which are preference-backed in the legacy renderer too.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun OverlaySlot(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
    state: ScreensaverState,
    overlayState: OverlayUiState,
) {
    val maxWidth = with(LocalDensity.current) { config.layout.maxTextWidthPx.toDp() }

    Box(modifier = Modifier.widthIn(max = maxWidth)) {
        when (slot.overlayType) {
            OverlayType.CLOCK -> ClockOverlayContent(slot, config)

            OverlayType.DATE -> DateOverlayContent(slot, config)

            OverlayType.METADATA1,
            OverlayType.METADATA2,
            OverlayType.METADATA3,
            OverlayType.METADATA4,
            -> MetadataOverlayContent(slot, config, state, overlayState)

            OverlayType.MESSAGE1,
            OverlayType.MESSAGE2,
            OverlayType.MESSAGE3,
            OverlayType.MESSAGE4,
            -> MessageOverlayContent(slot, config, overlayState)

            OverlayType.MUSIC1,
            OverlayType.MUSIC2,
            -> NowPlayingOverlayContent(slot, config, overlayState)

            OverlayType.WEATHER1 -> WeatherNowOverlayContent(slot, config, overlayState)

            OverlayType.WEATHER2 -> WeatherForecastOverlayContent(slot, config, overlayState)

            OverlayType.COUNTDOWN -> CountdownOverlayContent(slot, config)

            OverlayType.EMPTY -> Unit
        }
    }
}

@Composable
private fun ClockOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
) {
    val format = GeneralPrefs.clockFormat ?: ClockType.DEFAULT
    val formatter =
        remember(format) {
            when (format) {
                ClockType.HOUR_12 -> DateTimeFormatter.ofPattern("h:mm a")
                ClockType.HOUR_24 -> DateTimeFormatter.ofPattern("HH:mm")
                ClockType.DEFAULT -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            }
        }
    var now by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(format) {
        while (coroutineContext.isActive) {
            now = LocalTime.now()
            delay(1_000)
        }
    }

    BasicText(
        text = formatter.format(now),
        style = overlayTextStyle(slot, config),
    )
}

@Composable
private fun DateOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
) {
    val type = GeneralPrefs.dateFormat ?: DateType.COMPACT
    val custom = GeneralPrefs.dateCustom
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(type, custom) {
        while (coroutineContext.isActive) {
            now = Date()
            delay(1_000)
        }
    }

    val text =
        when (type) {
            DateType.FULL -> {
                DateFormat.getDateInstance(DateFormat.FULL).format(now)
            }

            DateType.COMPACT -> {
                DateFormat.getDateInstance(DateFormat.SHORT).format(now)
            }

            DateType.CUSTOM -> {
                runCatching {
                    SimpleDateFormat(custom.ifBlank { "yyyy-MM-dd" }, Locale.getDefault()).format(now)
                }.getOrDefault("")
            }
        }

    if (text.isNotBlank()) {
        BasicText(text = text, style = overlayTextStyle(slot, config))
    }
}

@Composable
private fun MetadataOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
    state: ScreensaverState,
    overlayState: OverlayUiState,
) {
    val metadata = overlayState.metadata[slot.overlayType] ?: return
    val poi = metadata.poi
    val isDynamic = metadata.metadataType == MetadataType.DYNAMIC && poi.size > 1
    var elapsedMs by remember(state.itemGeneration) { mutableLongStateOf(0L) }

    if (isDynamic) {
        LaunchedEffect(state.itemGeneration, poi) {
            val startedAt = SystemClock.elapsedRealtime()
            while (coroutineContext.isActive) {
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
                delay(1_000)
            }
        }
    }

    val text =
        if (isDynamic) {
            val elapsedSeconds = elapsedMs / 1_000
            val time = poi.keys.sorted().lastOrNull { it <= elapsedSeconds } ?: poi.keys.minOrNull() ?: 0
            poi[time] ?: metadata.text
        } else {
            metadata.text
        }

    if (text.isNotBlank()) {
        BasicText(
            text = text.replace('\n', ' '),
            style = overlayTextStyle(slot, config),
        )
    }
}

@Composable
private fun MessageOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
    overlayState: OverlayUiState,
) {
    val initial =
        when (slot.overlayType) {
            OverlayType.MESSAGE1 -> GeneralPrefs.messageLine1
            OverlayType.MESSAGE2 -> GeneralPrefs.messageLine2
            OverlayType.MESSAGE3 -> GeneralPrefs.messageLine3
            OverlayType.MESSAGE4 -> GeneralPrefs.messageLine4
            else -> ""
        }
    val message = overlayState.message[slot.overlayType] ?: MessageOverlayState(text = initial)
    var visibleText by remember { mutableStateOf(message.text) }

    LaunchedEffect(message) {
        visibleText = message.text
        val duration = message.duration
        if (duration != null && duration > 0 && message.text.isNotBlank()) {
            delay(duration * 1_000L)
            visibleText = ""
        }
    }

    if (visibleText.isNotBlank()) {
        val style =
            overlayTextStyle(slot, config).let { base ->
                base.copy(
                    fontSize = (message.textSize?.toFloat() ?: slot.typography.sizeSp).sp,
                    fontWeight = FontWeight(message.textWeight ?: slot.typography.weight),
                )
            }
        BasicText(text = visibleText, style = style)
    }
}

@Composable
private fun NowPlayingOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
    overlayState: OverlayUiState,
) {
    val format =
        when (slot.overlayType) {
            OverlayType.MUSIC1 -> GeneralPrefs.nowPlayingLine1
            OverlayType.MUSIC2 -> GeneralPrefs.nowPlayingLine2
            else -> NowPlayingFormat.DISABLED
        } ?: NowPlayingFormat.DISABLED
    if (format == NowPlayingFormat.DISABLED) return

    val event = overlayState.nowPlaying.event
    val text = formatNowPlaying(format, event.artist, event.song)
    if (text.isBlank() && event.albumArt == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        event.albumArt?.let { bitmap ->
            val artSize = max(1f, slot.typography.sizeSp * 2.5f).dp
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(artSize)
                        .alpha(config.layout.visibleAlpha),
            )
        }
        if (text.isNotBlank()) {
            BasicText(text = text, style = overlayTextStyle(slot, config))
        }
    }
}

@Composable
private fun WeatherNowOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
    overlayState: OverlayUiState,
) {
    val event = overlayState.weather.event
    if (event.temperature.isEmpty()) return

    val items =
        remember(GeneralPrefs.weatherLine1Layout) {
            GeneralPrefs.weatherLine1Layout
                .split(',')
                .mapNotNull { value ->
                    runCatching { ForecastType.valueOf(value.trim()) }.getOrNull()
                }.filter { it != ForecastType.EMPTY }
        }
    val style = overlayTextStyle(slot, config)
    val tint = overlayColour(config).copy(alpha = config.layout.visibleAlpha)
    val iconSize = max(1f, slot.typography.sizeSp * 1.3f).dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            val previous = items.getOrNull(index - 1)
            when (item) {
                ForecastType.CITY -> {
                    if (index > 0 && previous != ForecastType.ICON) Spacer(Modifier.width(16.dp))
                    BasicText(text = event.city, style = style)
                }

                ForecastType.TEMPERATURE -> {
                    if (index > 0 && previous != ForecastType.ICON) Spacer(Modifier.width(16.dp))
                    BasicText(text = event.temperature, style = style)
                }

                ForecastType.SUMMARY -> {
                    if (index > 0 && previous != ForecastType.ICON) Spacer(Modifier.width(16.dp))
                    BasicText(text = event.summary, style = style)
                }

                ForecastType.ICON -> {
                    if (event.icon > 0) {
                        Image(
                            painter = painterResource(event.icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(tint),
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }

                ForecastType.EMPTY -> {
                    return@forEachIndexed
                }
            }
        }
    }
}

@Composable
private fun WeatherForecastOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
    overlayState: OverlayUiState,
) {
    val days = overlayState.forecast.event.days
    if (days.isEmpty()) return

    val dayStyle = overlayTextStyle(slot, config, sizeMultiplier = 0.8f, alphaMultiplier = 0.78f)
    val highStyle = overlayTextStyle(slot, config, sizeMultiplier = 0.8f, alphaMultiplier = 0.9f)
    val lowStyle = overlayTextStyle(slot, config, sizeMultiplier = 0.8f, alphaMultiplier = 0.55f)
    val tint = overlayColour(config).copy(alpha = config.layout.visibleAlpha)
    val iconSize = max(1f, slot.typography.sizeSp * 1.1f).dp

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        days.forEach { day ->
            ForecastDayColumn(
                day = day,
                dayStyle = dayStyle,
                highStyle = highStyle,
                lowStyle = lowStyle,
                tint = tint,
                iconSize = iconSize,
            )
        }
    }
}

@Composable
private fun ForecastDayColumn(
    day: ForecastDay,
    dayStyle: TextStyle,
    highStyle: TextStyle,
    lowStyle: TextStyle,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(text = day.dayName, style = dayStyle)
        if (day.icon > 0) {
            Image(
                painter = painterResource(day.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(iconSize),
            )
        }
        Row(horizontalArrangement = Arrangement.Center) {
            BasicText(text = day.tempHigh, style = highStyle)
            Spacer(Modifier.width(4.dp))
            BasicText(text = day.tempLow, style = lowStyle)
        }
    }
}

@Composable
private fun CountdownOverlayContent(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
) {
    val targetRaw = GeneralPrefs.countdownTargetTime
    if (targetRaw.isBlank()) return

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(targetRaw) {
        while (coroutineContext.isActive) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    val target = remember(targetRaw, now.toLocalDate()) { parseCountdownTarget(targetRaw, now) }
    if (target == null) {
        BasicText(text = "Invalid time format", style = overlayTextStyle(slot, config))
        return
    }

    val remaining = ChronoUnit.SECONDS.between(now, target)
    val text =
        if (remaining <= 0) {
            GeneralPrefs.countdownTargetMessage.ifBlank { "Time is up" }
        } else {
            val label =
                GeneralPrefs.countdownLabel
                    .takeIf { it.isNotBlank() }
                    ?.plus(" ")
                    .orEmpty()
            label + formatCountdown(remaining)
        }

    BasicText(text = text, style = overlayTextStyle(slot, config))
}

private fun overlayTextStyle(
    slot: OverlaySlotConfig,
    config: OverlayConfig,
    sizeMultiplier: Float = 1f,
    alphaMultiplier: Float = 1f,
): TextStyle =
    TextStyle(
        // Custom bundled fonts live in :app; the shared config carries the choice, but the
        // Compose renderer uses the platform default until that resource is moved to a library.
        fontFamily = FontFamily.Default,
        fontSize = (slot.typography.sizeSp * sizeMultiplier).sp,
        fontWeight = FontWeight(slot.typography.weight),
        color =
            overlayColour(config).copy(
                alpha = config.layout.visibleAlpha * alphaMultiplier,
            ),
    )

private fun overlayColour(config: OverlayConfig): Color =
    runCatching { Color(AndroidColor.parseColor(config.colour)) }.getOrDefault(Color.White)

private fun formatNowPlaying(
    format: NowPlayingFormat,
    artist: String,
    song: String,
): String =
    when (format) {
        NowPlayingFormat.SONG_ARTIST -> joinNonBlank(song, artist)
        NowPlayingFormat.ARTIST_SONG -> joinNonBlank(artist, song)
        NowPlayingFormat.ARTIST -> artist
        NowPlayingFormat.SONG -> song
        NowPlayingFormat.DISABLED -> ""
    }

private fun joinNonBlank(
    first: String,
    second: String,
): String =
    when {
        first.isNotBlank() && second.isNotBlank() -> "$first · $second"
        first.isNotBlank() -> first
        else -> second
    }

private fun parseCountdownTarget(
    value: String,
    now: LocalDateTime,
): LocalDateTime? {
    val timeMatch = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(value.trim())
    if (timeMatch != null) {
        val hour = timeMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = timeMatch.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        return if (target.isBefore(now)) target.plusDays(1) else target
    }

    val dateTimeMatch = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2}) (\d{1,2}):(\d{2})$""").matchEntire(value.trim())
    if (dateTimeMatch != null) {
        return runCatching {
            LocalDateTime.of(
                dateTimeMatch.groupValues[1].toInt(),
                dateTimeMatch.groupValues[2].toInt(),
                dateTimeMatch.groupValues[3].toInt(),
                dateTimeMatch.groupValues[4].toInt(),
                dateTimeMatch.groupValues[5].toInt(),
            )
        }.getOrNull()
    }

    return null
}

private fun formatCountdown(totalSeconds: Long): String {
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
