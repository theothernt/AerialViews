package com.neilturner.aerialviews.ui.overlays.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.DateHelper
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import kotlinx.coroutines.delay

/** The date overlay. Polled per second so a date change lands at midnight. */
@Composable
fun DateOverlay(
    sizeSp: Float,
    weight: String,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(dateText(context)) }

    LaunchedEffect(context) {
        while (true) {
            delay(DATE_POLL_MILLIS)
            text = dateText(context)
        }
    }

    OverlayText(
        text = text,
        sizeSp = sizeSp,
        weight = weight,
    )
}

private const val DATE_POLL_MILLIS = 1000L

private fun dateText(context: Context): String =
    DateHelper.formatDate(
        context,
        GeneralPrefs.dateFormat ?: DateType.COMPACT,
        GeneralPrefs.dateCustom,
    )
