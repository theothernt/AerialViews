package com.neilturner.aerialviews.models.enums

enum class ButtonType {
    IGNORE, // Trap button press, do nothing
    SKIP_NEXT,
    SKIP_PREVIOUS,
    MUSIC_NEXT,
    MUSIC_PREVIOUS,
    SPEED_INCREASE,
    SPEED_DECREASE,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    BRIGHTNESS_INCREASE,
    BRIGHTNESS_DECREASE,
    EXIT, // Exit screensaver
    EXIT_TO_SETTINGS, // Exit to settings screen if "Start screensaver on launch" is enabled
    SHOW_OVERLAYS, // Show overlays if auto-hide is enabled
    BLACK_OUT_MODE, // Hide overlays + video, black screen
    TOGGLE_MUTE, // Toggle audio mute/unmute
    TOGGLE_PAUSE, // Pause/unpause video or photo timer
    TOGGLE_LOOPING, // Toggle looping for current video
}
