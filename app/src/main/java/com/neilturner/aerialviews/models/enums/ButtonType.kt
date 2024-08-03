package com.neilturner.aerialviews.models.enums

enum class ButtonType {
    DISABLED, // Trap button press, do nothing
    PASSTHROUGH, // Passthrough but don't exit
    EXIT, // Exit screensaver
    SKIP_NEXT,
    SKIP_PREVIOUS,
    SPEED_UP,
    SPEED_DOWN,
    SHOW_OVERLAY,
}
