@file:Suppress("unused")

package com.neilturner.aerialviews.models.enums

enum class ButtonType {
    IGNORE, // Trap button press, do nothing
    EXIT, // Exit screensaver
    SKIP_NEXT,
    SKIP_PREVIOUS,
    SPEED_INCREASE,
    SPEED_DECREASE,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    SHOW_OVERLAYS,
    BLACK_OUT_MODE,
}
