package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.DescriptionFilenameType
import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.TemperatureUnit
import com.neilturner.aerialviews.models.enums.WindSpeedUnit
import com.neilturner.aerialviews.models.enums.PhotoScale

object GeneralPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    // Overlays - Tops
    var slotTopLeft1 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_left1")
    var slotTopLeft2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_left2")
    var slotTopRight1 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_right1")
    var slotTopRight2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_right2")

    // Overlays - Bottom
    var slotBottomLeft1 by nullableEnumValuePref(OverlayType.CLOCK, "slot_bottom_left1")
    var slotBottomLeft2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_bottom_left2")
    var slotBottomRight1 by nullableEnumValuePref(OverlayType.LOCATION, "slot_bottom_right1")
    var slotBottomRight2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_bottom_right2")

    // Weather
    var weatherSize by stringPref("18", "weather_size")
    var weatherWeight by stringPref("300", "weather_weight")
    var weatherCityName by stringPref("Dublin, IE", "weather_city_name")
    var weatherCityLatLng by stringPref("Dublin, IE", "weather_city_latlng")
    var weatherUnits by nullableEnumValuePref(TemperatureUnit.METRIC, "weather_units")
    var weatherWindUnits by nullableEnumValuePref(WindSpeedUnit.METRIC, "weather_wind_units")

    // Clock
    var clockFormat by nullableEnumValuePref(ClockType.DEFAULT, "clock_format")
    var clockSize by stringPref("18", "clock_size")
    var clockWeight by stringPref("300", "clock_weight")
    var clockForceLatinDigits by booleanPref(false, "clock_force_latin_digits")

    // Music
    var musicSize by stringPref("18", "music_size")
    var musicWeight by stringPref("300", "music_weight")

    // Date
    var dateFormat by nullableEnumValuePref(DateType.COMPACT, "date_format")
    var dateCustom by stringPref("yyyy-MM-dd", "date_custom")
    var dateWeight by stringPref("300", "date_weight")
    var dateSize by stringPref("18", "date_size")

    // Location
    var descriptionVideoManifestStyle by nullableEnumValuePref(
        DescriptionManifestType.POI,
        "description_video_manifest_style",
    ) // Title or POI
    var descriptionVideoFilenameStyle by nullableEnumValuePref(
        DescriptionFilenameType.DISABLED,
        "description_video_filename_style",
    ) // Filename - Videos
    var descriptionPhotoFilenameStyle by nullableEnumValuePref(
        DescriptionFilenameType.DISABLED,
        "description_photo_filename_style",
    ) // Filename - Photos
    var descriptionSize by stringPref("18", "description_size")
    var descriptionWeight by stringPref("300", "description_weight")

    // Message
    var messageLine1 by stringPref("", "message_line1")
    var messageLine2 by stringPref("", "message_line2")
    var messageSize by stringPref("18", "message_size")
    var messageWeight by stringPref("300", "message_weight")

    // Other
    var alternateTextPosition by booleanPref(false, "alt_text_position")

    // Startup + Shutdown
    var showLoadingText by booleanPref(true, "startup_show_loading")
    var loadingTextSize by stringPref("18", "startup_size")
    var loadingTextWeight by stringPref("300", "startup_weight")

    // Gradients
    var showTopGradient by booleanPref(false, "gradient_top_show")
    var showBottomGradient by booleanPref(false, "gradient_bottom_show")

    // Typeface (for whole app)
    var fontTypeface by stringPref("open-sans", "font_typeface")

    // Locale
    var localeMenu by stringPref("default", "locale_menu")
    var localeScreensaver by stringPref("default", "locale_screensaver")

    // Playlist
    var muteVideos by booleanPref(true, "mute_videos")
    var shuffleVideos by booleanPref(true, "shuffle_videos")
    var removeDuplicates by booleanPref(true, "remove_duplicates")
    var playbackSpeed by stringPref("1", "playback_speed")
    var maxVideoLength by stringPref("0", "playback_max_video_length")
    var ignoreNonManifestVideos by booleanPref(false, "any_videos_ignore_non_manifest_videos")
    var slideshowSpeed by stringPref("30", "slideshow_speed")
    var photoScale by nullableEnumValuePref(PhotoScale.CENTER_CROP, "photo_scale") // Migrate

    // D-pad
    var enableSkipVideos by booleanPref(true, "enable_skip_videos")
    var enablePlaybackSpeedChange by booleanPref(false, "enable_playback_speed_change")
    var enableMediaButtonPassthrough by booleanPref(true, "enable_media_button_passthrough")

    // Advanced
    var enableTunneling by booleanPref(true, "enable_tunneling")
    var refreshRateSwitching by booleanPref(false, "refresh_rate_switching")
    var allowFallbackDecoders by booleanPref(false, "allow_fallback_decoders")
    var enablePlaybackLogging by booleanPref(false, "enable_playback_logging")
    var philipsDolbyVisionFix by booleanPref(false, "philips_dolby_vision_fix")

    // Old devices
    var checkForHevcSupport by booleanPref(false, "check_for_hevc_support")
}
