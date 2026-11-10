package com.neilturner.aerialviews.ui.controls

data class ProgressBarEvent(
    val state: ProgressState = ProgressState.RESET,
    val position: Long = 0,
    val duration: Long = 0,
)

enum class ProgressState {
    START,
    PAUSE,
    RESUME,
    RESET,
}
