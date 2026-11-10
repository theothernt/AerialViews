package com.neilturner.aerialviews.services.sonos

import com.neilturner.aerialviews.services.sonos.SonosClient.Track
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for the Sonos SOAP parsing logic in [SonosClient], using canned SOAP envelopes
 * rather than a real speaker. [SonosClient.parseTransportState] and [SonosClient.parseTrack] are the
 * pure parsers extracted from the blocking network methods ([SonosClient.transportState] and
 * [SonosClient.currentTrack]).
 *
 * Fixtures mirror real Sonos `GetPositionInfo` / `GetTransportInfo` responses: track metadata is a
 * DIDL-Lite document, XML-escaped, inside the SOAP `<TrackMetaData>` element, and the album art URI
 * is resolved against the player's port 1400.
 */
internal class SonosClientTest {
    private val ip = "192.168.1.10"

    @Nested
    @DisplayName("GetTransportInfo parsing")
    inner class TransportState {
        @Test
        @DisplayName("extracts a PLAYING state")
        fun `extracts PLAYING`() {
            val envelope = transportInfoEnvelope("PLAYING")
            assertEquals("PLAYING", SonosClient.parseTransportState(envelope))
        }

        @Test
        @DisplayName("extracts a PAUSED_PLAYBACK state")
        fun `extracts PAUSED_PLAYBACK`() {
            val envelope = transportInfoEnvelope("PAUSED_PLAYBACK")
            assertEquals("PAUSED_PLAYBACK", SonosClient.parseTransportState(envelope))
        }

        @Test
        @DisplayName("extracts STOPPED state")
        fun `extracts STOPPED`() {
            val envelope = transportInfoEnvelope("STOPPED")
            assertEquals("STOPPED", SonosClient.parseTransportState(envelope))
        }

        @Test
        @DisplayName("returns empty string when the state tag is absent")
        fun `returns empty when state absent`() {
            assertEquals("", SonosClient.parseTransportState("<s:Envelope><s:Body></s:Body></s:Envelope>"))
        }
    }

    @Nested
    @DisplayName("GetPositionInfo parsing")
    inner class PositionInfo {
        @Test
        @DisplayName("parses title, artist and resolves a relative album art URI against the player")
        fun `full track with relative art uri`() {
            val didl =
                didl(
                    title = "My Song",
                    artist = "The Artists",
                    albumArtUri = "/getaa?s=1&u=x-sonos-http%3A%2F%2Fmedia.example%2Ftr.mp3",
                )
            val envelope = positionInfoEnvelope(didl)

            val track = SonosClient.parseTrack(envelope, ip)

            assertEquals(
                Track(
                    title = "My Song",
                    artist = "The Artists",
                    albumArtUri = "http://$ip:${SonosClient.PORT}/getaa?s=1&u=x-sonos-http%3A%2F%2Fmedia.example%2Ftr.mp3",
                ),
                track,
            )
        }

        @Test
        @DisplayName("leaves an absolute album art URI untouched")
        fun `absolute art uri is preserved`() {
            val didl =
                didl(
                    title = "My Song",
                    artist = "The Artists",
                    albumArtUri = "http://art.example.com/cover.png",
                )
            val envelope = positionInfoEnvelope(didl)

            val track = SonosClient.parseTrack(envelope, ip)

            assertEquals("http://art.example.com/cover.png", track?.albumArtUri)
        }

        @Test
        @DisplayName("falls back to dc:creator when upnp:artist is absent")
        fun `artist falls back to dc creator when upnp artist is absent`() {
            val didl = didl(title = "My Song", creator = "Creator Name")
            val envelope = positionInfoEnvelope(didl)

            val track = SonosClient.parseTrack(envelope, ip)

            assertEquals("Creator Name", track?.artist)
        }

        @Test
        @DisplayName("unescapes XML entities in the title and artist")
        fun `unescapes xml entities`() {
            val didl =
                didl(
                    title = "AT&T",
                    artist = "\"Weird\" Artist",
                    albumArtUri = "",
                )
            val envelope = positionInfoEnvelope(didl)

            val track = SonosClient.parseTrack(envelope, ip)

            assertEquals("AT&T", track?.title)
            assertEquals("\"Weird\" Artist", track?.artist)
        }

        @Test
        @DisplayName("returns null when both title and artist are empty")
        fun `returns null when title and artist are blank`() {
            val didl = didl(title = "", artist = "")
            val envelope = positionInfoEnvelope(didl)

            assertNull(SonosClient.parseTrack(envelope, ip))
        }

        @Test
        @DisplayName("returns a track from a streaming radio fixture, using the filename as the title")
        fun `streaming radio fixture`() {
            val didl =
                """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <item id="-1" parentID="-1" restricted="true">
                    <res protocolInfo="x-rincon-mp3radio:*:*:*">x-rincon-mp3radio://icecast-qmusic.cdp.triple-it.nl/Qmusic_nl_nonstop_96.mp3</res>
                    <dc:title>Qmusic_nl_nonstop_96.mp3</dc:title>
                    <r:streamContent>Stole The Show - KYGO & PARSON JAMES♣</r:streamContent>
                    <upnp:albumArtURI>/getaa?s=1&u=x-sonos-http%3A%2F%2Ficecast-qmusic.cdp.triple-it.nl%2FQmusic_nl_nonstop_96.mp3</upnp:albumArtURI>
                    <upnp:class>object.item.audioItem.audioBroadcast</upnp:class>
                  </item>
                </DIDL-Lite>
                """.trimIndent()
            val envelope = positionInfoEnvelope(escapeXml(didl))

            val track = SonosClient.parseTrack(envelope, ip)

            assertEquals("Qmusic_nl_nonstop_96.mp3", track?.title)
            assertEquals("", track?.artist)
            assertEquals(
                "http://$ip:${SonosClient.PORT}/getaa?s=1&u=x-sonos-http%3A%2F%2Ficecast-qmusic.cdp.triple-it.nl%2FQmusic_nl_nonstop_96.mp3",
                track?.albumArtUri,
            )
        }
    }

    private fun transportInfoEnvelope(state: String): String =
        """
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <CurrentTransportState>$state</CurrentTransportState>
              <CurrentTransportStatus>OK</CurrentTransportStatus>
              <CurrentSpeed>1</CurrentSpeed>
            </u:GetTransportInfoResponse>
          </s:Body>
        </s:Envelope>
        """.trimIndent()

    private fun positionInfoEnvelope(trackMetaData: String): String =
        """
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <Track>1</Track>
              <TrackDuration>0:03:45</TrackDuration>
              <CurrentTrack>1</CurrentTrack>
              <CurrentTrackDuration>0:03:45</CurrentTrackDuration>
              <TrackMetaData>$trackMetaData</TrackMetaData>
              <TrackURI>x-rincon-playlist:T</TrackURI>
              <RelTime>0:00:10</RelTime>
            </u:GetPositionInfoResponse>
          </s:Body>
        </s:Envelope>
        """.trimIndent()

    private fun didl(
        title: String? = null,
        artist: String? = null,
        creator: String? = null,
        albumArtUri: String? = null,
    ): String =
        """
        <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
          <item id="-1" parentID="-1" restricted="true">
            ${title?.let { "<dc:title>$it</dc:title>" } ?: ""}
            ${artist?.let { "<upnp:artist>$it</upnp:artist>" } ?: ""}
            ${creator?.let { "<dc:creator>$it</dc:creator>" } ?: ""}
            ${albumArtUri?.let { "<upnp:albumArtURI>$it</upnp:albumArtURI>" } ?: ""}
            <upnp:class>object.item.audioItem.musicTrack</upnp:class>
          </item>
        </DIDL-Lite>
        """.trimIndent()

    private fun escapeXml(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
