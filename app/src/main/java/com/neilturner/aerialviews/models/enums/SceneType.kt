package com.neilturner.aerialviews.models.enums

enum class SceneType {
    UNKNOWN,
    NATURE,
    BEACH,
    CITY,
    SEA,
    SPACE;

    companion object {
        fun fromString(value: String?): SceneType {
            return when (value?.lowercase()) {
                "nature" -> NATURE
                "beach" -> BEACH
                "city" -> CITY
                "sea" -> SEA
                "space" -> SPACE
                else -> UNKNOWN
            }
        }
    }
}
