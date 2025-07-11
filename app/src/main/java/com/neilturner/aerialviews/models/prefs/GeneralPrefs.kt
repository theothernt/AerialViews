package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.enumpref.nullableEnumValuePref
import com.neilturner.aerialviews.models.enums.ButtonType
import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.DescriptionFilenameType
import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.models.enums.LimitLongerVideos
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.enums.ProgressBarType
import com.neilturner.aerialviews.models.enums.VideoScale
import com.neilturner.aerialviews.services.weather.TemperatureUnit
import com.neilturner.aerialviews.services.weather.WindSpeedUnit

object GeneralPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    // Overlays - Top
    var slotTopLeft1 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_left1")
    var slotTopLeft2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_left2")
    var slotTopRight1 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_right1")
    var slotTopRight2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_top_right2")

    // Overlays - Bottom
    var slotBottomLeft1 by nullableEnumValuePref(OverlayType.CLOCK, "slot_bottom_left1")
    var slotBottomLeft2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_bottom_left2")
    var slotBottomRight1 by nullableEnumValuePref(OverlayType.LOCATION, "slot_bottom_right1")
    var slotBottomRight2 by nullableEnumValuePref(OverlayType.EMPTY, "slot_bottom_right2")

    // Clock
    var clockFormat by nullableEnumValuePref(ClockType.DEFAULT, "clock_format")
    var clockSize by stringPref("18", "clock_size")
    var clockWeight by stringPref("300", "clock_weight")
    var clockForceLatinDigits by booleanPref(false, "clock_force_latin_digits")

    // Music
    var nowPlayingLine1 by nullableEnumValuePref(NowPlayingFormat.SONG_ARTIST, "nowplaying_line1")
    var nowPlayingLine2 by nullableEnumValuePref(NowPlayingFormat.DISABLED, "nowplaying_line2")
    var nowPlayingSize1 by stringPref("18", "nowplaying_size1")
    var nowPlayingWeight1 by stringPref("300", "nowplaying_weight1")
    var nowPlayingSize2 by stringPref("18", "nowplaying_size2")
    var nowPlayingWeight2 by stringPref("300", "nowplaying_weight2")
    var nowPlayingShortenTrackName by booleanPref(false, "nowplaying_shorten_track_name")

    // Date
    var dateFormat by nullableEnumValuePref(DateType.COMPACT, "date_format")
    var dateCustom by stringPref("yyyy-MM-dd", "date_custom")
    var dateWeight by stringPref("300", "date_weight")
    var dateSize by stringPref("18", "date_size")

    // Weather
    var weatherLocationName by stringPref("", "weather_location_name")
    var weatherLocationLat by stringPref("", "weather_location_lat")
    var weatherLocationLon by stringPref("", "weather_location_lon")

    var weatherLine1 by stringPref("FORECAST", "weather_line1")
    var weatherLine1Size by stringPref("18", "weather_line1_size")
    var weatherLine1Weight by stringPref("300", "weather_line1_weight")

    var weatherLine2 by stringPref("WIND", "weather_line2")
    var weatherLine2Size by stringPref("18", "weather_line2_size")
    var weatherLine2Weight by stringPref("300", "weather_line2_weight")

    var weatherForecast by stringPref("TEMPERATURE,ICON,SUMMARY", "weather_forecast")
    var weatherTemperatureUnits by nullableEnumValuePref(TemperatureUnit.METRIC, "weather_temperature_units")

    var weatherWind by stringPref("", "weather_wind")
    var weatherWindUnits by nullableEnumValuePref(WindSpeedUnit.METERS, "weather_temperature_units")

    var weatherHumidity by stringPref("", "weather_humidity")
    var weatherSunrise by stringPref("", "weather_sunrise")

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

    // Filename + Folder depth
    var descriptionVideoFolderLevel by stringPref("1", "description_video_folder_levels")
    var descriptionPhotoFolderLevel by stringPref("1", "description_photo_folder_levels")

    var descriptionSize by stringPref("18", "description_size")
    var descriptionWeight by stringPref("300", "description_weight") // Message

    var messageLine1 by stringPref("", "message_line1")
    var messageLine2 by stringPref("", "message_line2")
    var messageLine3 by stringPref("", "message_line3")
    var messageLine4 by stringPref("", "message_line4")

    var messageSize by stringPref("18", "message_size")
    var messageWeight by stringPref("300", "message_weight")

    var messageApiEnabled by booleanPref(false, "message_api_enabled")
    var messageApiPort by stringPref("8081", "message_api_port")

    // Other
    var alternateTextPosition by booleanPref(true, "alt_text_position")

    // Startup + Shutdown
    var showLoadingText by booleanPref(true, "startup_show_loading")
    var startScreensaverOnLaunch by booleanPref(false, "startup_screensaver_on_launch")
    var loadingTextSize by stringPref("18", "startup_size")
    var loadingTextWeight by stringPref("300", "startup_weight")

    // Animation
    var overlayFadeInDuration by stringPref("600", "overlay_fade_in_duration")
    var overlayFadeOutDuration by stringPref("600", "overlay_fade_out_duration")
    var mediaFadeInDuration by stringPref("600", "media_fade_in_duration")
    var mediaFadeOutDuration by stringPref("800", "media_fade_out_duration")

    // Overlay Auto hide/reveal
    var overlayAutoHide by stringPref("-1", "overlay_auto_hide")
    var overlayRevealTimeout by stringPref("4", "overlay_reveal_timeout")

    // Background Colour
    var backgroundLoading by stringPref("BLACK", "background_colour_loading")
    var backgroundVideos by stringPref("BLACK", "background_colour_videos")
    var backgroundPhotos by stringPref("BLACK", "background_colour_photos")

    // Brightness / Dimness
    var videoBrightness by stringPref("100", "video_brightness")

    // Gradients
    var showTopGradient by booleanPref(false, "gradient_top_show")
    var showBottomGradient by booleanPref(true, "gradient_bottom_show")

    // Typeface (for whole app)
    var fontTypeface by stringPref("open-sans", "font_typeface")

    // Progress Bar
    var progressBarLocation by nullableEnumValuePref(ProgressBarLocation.DISABLED, "progress_bar_location")
    var progressBarType by nullableEnumValuePref(ProgressBarType.BOTH, "progress_bar_type")
    var progressBarOpacity by stringPref("100", "progress_bar_opacity")

    // Ignore system animation override
    var ignoreAnimationScale by booleanPref(true, "ignore_animation_scale")

    // Locale
    var localeMenu by stringPref("default", "locale_menu")
    var localeScreensaver by stringPref("default", "locale_screensaver")

    // Playlist
    var removeDuplicates by booleanPref(true, "remove_duplicates") // photos & videos?
    var shuffleVideos by booleanPref(true, "shuffle_videos") // rename to media

    // Playlist - Videos
    var muteVideos by booleanPref(true, "mute_videos")
    var videoVolume by stringPref("100", "video_volume")
    var videoScale by nullableEnumValuePref(VideoScale.SCALE_TO_FIT_WITH_CROPPING, "video_scale")
    var playbackSpeed by stringPref("1", "playback_speed")
    var ignoreNonManifestVideos by booleanPref(false, "any_videos_ignore_non_manifest_videos")

    // Playlist - Videos Advanced
    var maxVideoLength by stringPref("0", "playback_max_video_length")
    var loopUntilSkipped by booleanPref(false, "loop_until_skipped")
    var loopShortVideos by booleanPref(false, "loop_short_videos")
    var limitLongerVideos by nullableEnumValuePref(LimitLongerVideos.LIMIT, "limit_longer_videos")
    var randomStartPosition by booleanPref(false, "random_start_position")
    var randomStartPositionRange by stringPref("50", "random_start_position_range")

    // Playlist - Photos
    var slideshowSpeed by stringPref("30", "slideshow_speed")
    var photoScale by nullableEnumValuePref(PhotoScale.CENTER_CROP, "photo_scale")

    // D-pad
    var buttonLeftPress by nullableEnumValuePref(ButtonType.SKIP_PREVIOUS, "button_left_press")
    var buttonRightPress by nullableEnumValuePref(ButtonType.SKIP_NEXT, "button_right_press")
    var buttonUpPress by nullableEnumValuePref(ButtonType.IGNORE, "button_up_press")
    var buttonDownPress by nullableEnumValuePref(ButtonType.IGNORE, "button_down_press")
    var buttonOkPress by nullableEnumValuePref(ButtonType.IGNORE, "button_ok_press")

    var buttonLeftHold by nullableEnumValuePref(ButtonType.IGNORE, "button_left_hold")
    var buttonRightHold by nullableEnumValuePref(ButtonType.IGNORE, "button_right_hold")
    var buttonUpHold by nullableEnumValuePref(ButtonType.IGNORE, "button_up_hold")
    var buttonDownHold by nullableEnumValuePref(ButtonType.IGNORE, "button_down_hold")
    var buttonOkHold by nullableEnumValuePref(ButtonType.IGNORE, "button_ok_hold")

    var gestureLeft by nullableEnumValuePref(ButtonType.IGNORE, "gesture_swipe_left")
    var gestureRight by nullableEnumValuePref(ButtonType.IGNORE, "gesture_swipe_right")
    var gestureUp by nullableEnumValuePref(ButtonType.IGNORE, "gesture_swipe_up")
    var gestureDown by nullableEnumValuePref(ButtonType.IGNORE, "gesture_swipe_down")
    var gestureTap by nullableEnumValuePref(ButtonType.IGNORE, "gesture_tap")
    var gestureDoubleTap by nullableEnumValuePref(ButtonType.IGNORE, "gesture_double_tap")
    var gestureTapHold by nullableEnumValuePref(ButtonType.IGNORE, "gesture_tap_hold")

    var enableMediaButtonPassthrough by booleanPref(true, "enable_media_button_passthrough")
    var wakeOnAnyButtonPress by booleanPref(true, "wake_on_any_button_press")
    var seekInterval by stringPref("10", "seek_interval") // Advanced
    var enableTunneling by booleanPref(true, "enable_tunneling")
    var refreshRateSwitching by booleanPref(false, "refresh_rate_switching")
    var allowFallbackDecoders by booleanPref(false, "allow_fallback_decoders")
    var enablePlaybackLogging by booleanPref(false, "enable_playback_logging")
    var showMediaErrorToasts by booleanPref(false, "show_media_error_toasts")
    var philipsDolbyVisionFix by booleanPref(false, "philips_dolby_vision_fix")

    // Old devices
    var checkForHevcSupport by booleanPref(false, "check_for_hevc_support")
}
