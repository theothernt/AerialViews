package com.neilturner.aerialviews.models.enums

enum class AerialMediaSource {
    UNKNOWN,
    APPLE,
    COMM1,
    COMM2,
    RTSP,
    AMAZON,
    CUSTOM,
    LOCAL,
    SAMBA,
    WEBDAV,
    IMMICH,
    NCMEMORIES,
    HLS,
    ;

    /**
     * True for sources served over the network. These are excluded when WiFi-only playback is
     * active and the device is not on WiFi/Ethernet. `UNKNOWN` is treated as local so an
     * unrecognised source is never dropped.
     */
    val requiresNetwork: Boolean get() = this in NETWORK_SOURCES

    private companion object {
        val NETWORK_SOURCES =
            setOf(
                APPLE,
                COMM1,
                COMM2,
                RTSP,
                AMAZON,
                CUSTOM,
                HLS,
                IMMICH,
                NCMEMORIES,
            )
    }
}
