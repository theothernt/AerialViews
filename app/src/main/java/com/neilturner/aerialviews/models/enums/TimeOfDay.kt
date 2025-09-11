package com.neilturner.aerialviews.models.enums

enum class TimeOfDay {
    UNKNOWN,
    DAY,
    NIGHT,
    SUNSET,
    SUNRISE,
    ;

    companion object {
        fun fromString(value: String?): TimeOfDay =
            when (value?.lowercase()) {
                "day" -> DAY
                "night" -> NIGHT
                "sunset" -> SUNSET
                "sunrise" -> SUNRISE
                else -> UNKNOWN
            }
    }
}
