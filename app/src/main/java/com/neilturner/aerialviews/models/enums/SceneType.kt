package com.neilturner.aerialviews.models.enums

enum class SceneType {
    UNKNOWN,
    NATURE,
    COUNTRYSIDE,
    WATERFALL,
    BEACH,
    CITY,
    SEA,
    SPACE,
    ;

    companion object {
        fun fromString(value: String?): SceneType =
            when (value?.lowercase()) {
                "nature" -> NATURE
                "beach" -> BEACH
                "countryside" -> COUNTRYSIDE
                "waterfall" -> WATERFALL
                "city" -> CITY
                "sea" -> SEA
                "space" -> SPACE
                else -> UNKNOWN
            }
    }
}
