package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import kotlinx.coroutines.delay

@Composable
fun MessageOverlayComposable(
    state: MessageOverlayState,
    modifier: Modifier = Modifier,
) {
    var displayText by remember { mutableStateOf(state.text) }
    var isVisible by remember { mutableStateOf(state.text.isNotBlank()) }

    LaunchedEffect(state) {
        displayText = state.text
        isVisible = state.text.isNotBlank()

        val durationSeconds = state.duration
        if (state.text.isNotBlank() && durationSeconds != null && durationSeconds > 0) {
            delay(durationSeconds * 1000L)
            displayText = ""
            isVisible = false
        }
    }

    val textStyle = remember(state.textSize, state.textWeight) {
        val sizeSp = (state.textSize ?: 18).toFloat()
        val weight = state.textWeight ?: 300
        OverlayTextStyle.copy(
            fontSize = sizeSp.sp,
        )
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = displayText,
            style = textStyle,
        )
    }
}
