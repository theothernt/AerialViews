package com.neilturner.aerialviews.ui.overlays.compose

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.ui.helpers.DateHelper
import kotlinx.coroutines.delay

@Composable
fun DateOverlayComposable(
    context: Context,
    type: DateType,
    custom: String,
    modifier: Modifier = Modifier,
) {
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(type, custom) {
        while (true) {
            currentDate = DateHelper.formatDate(context, type, custom)
            delay(1000)
        }
    }

    Text(
        text = currentDate,
        modifier = modifier,
        style = OverlayTextStyle,
    )
}
