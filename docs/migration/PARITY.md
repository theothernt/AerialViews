# Compose screensaver parity

Working matrix for Phase 8. **Not started** means no implementation in `:screensaver-compose`;
**in progress** means implemented but not yet accepted as parity; **verified** is reserved for a
feature checked on a real device by a human. This agent has not marked any row verified.

## Playback layer (batch 1)

| Feature | Status | Notes |
|---|---|---|
| Video playback via `VideoPlayerView` | in progress | Hosted in `AndroidView`; default-provider smoke prepared and played a 1920x1080 HEVC item. Not yet compared visually against the legacy player. |
| Image playback via `ImagePlayerView` | in progress | Hosted in `AndroidView`; reuses `ImagePlayerHelper` and Coil loading. Not yet verified. |
| Warm video player between items | in progress | Both views stay in the hierarchy; the video is suspended while an image is visible. |
| Video finish callback | in progress | `VideoPlayerView.onVideoAlmostFinished` starts a controller transition; the overlay fade completes it. Not device-verified. |
| Image finish callback | in progress | `ImagePlayerView.onImageFinished` starts the same transition path. Not device-verified. |
| Image loading metadata/EXIF | in progress | `ImagePlayerView` still runs its extraction. The session publishes media short description/POI; EXIF, geocoding and per-slot metadata rules are not wired. |
| Video metadata/POI | in progress | The Compose listener is still a no-op. Session metadata uses the media's existing POI/short description, and POI cycling uses item elapsed time. |
| Player error fallback | in progress | Batch 5 status layer shows the playback error while the controller retries/advances. Not device-verified. |
| Empty playlist / provider failure | in progress | Batch 5 shows the loading/error layer for an empty or failed fetch; no dedicated connectivity monitor. |

## Remaining parity rows

| Feature | Status | Notes |
|---|---|---|
| Fade-in/fade-out and transition protocol | in progress | Batch 2: overlay fade-in after the player is prepared, fade-out on `state.transition`, then `onTransitionCompleted`. Not device-verified. |
| Skip, previous, loop, next | in progress | Batch 4 input maps skip/previous and loop; default-provider smoke used D-pad right to prepare a second video. Not fully device-verified. |
| Metadata overlays and POI | in progress | Batch 3 renderer and state are wired. Subset feed: media short description/POI only; per-slot selection, geocoding, EXIF and exact video-position POIs remain. |
| Weather now/forecast overlays | in progress | Batch 3 composables render the shared weather/forecast state; `WeatherService` starts for configured slots. Not device-verified. |
| Now-playing and music overlays | in progress | Batch 3 renderer and notification-listener feed start when a music slot is configured; batch 5 adds background-music playback and track persistence. |
| Message API overlay | in progress | Batch 3 renderer and optional Ktor server are wired to the shared store; not device-verified. |
| Overlay visibility modes | in progress | Batch 3 implements always visible/hidden and delayed show/hide; batch 4 adds input reveal with the configured timeout. Not device-verified. |
| Bundled Google Sans / Open Sans font | in progress | Fonts now live in `:core` and both renderers use the configured family. Compose loading glyphs matched legacy exactly in an aligned crop. Not human-verified. |
| Loading screen layout/text | in progress | Bottom-left 24dp spinner/gap, 32dp/26dp padding, configured size/weight/font/visibility and loading background. Not human-verified. |
| Progress bar | not started | Deferred past batch 3; the shared progress state and event bridge exist. |
| Gradients, brightness, notifications | in progress | Top/bottom gradients are rendered; brightness and notification overlays remain. |
| Blackout, sleep timer, scheduled blackout | in progress | Batch 5 maps the prefs, renders a black overlay, stops the hosted players and lets the controller pause/resume music. Not device-verified. |
| Key-event input | in progress | Batch 4 installs `ComposeScreensaverInput` over the shared mapper; Back/exit and default D-pad right-skip passed smoke tests. Full binding matrix remains. |
| Touch/mouse input | in progress | Batch 4 shares `SwipeGestureListener` through `:core` and preserves mouse exit; gestures are not device-verified. |
| Exit reasons and `TestActivity` result | in progress | Batch 4 routes both reasons through `ScreensaverHost`; Back exited and released in smoke tests. Exit-to-settings and the round trip remain human checks. |
| Philips handoff path | not started | The host/factory placement exists; no Compose-specific handoff verification. |
| `TestActivity` phone-only pause stop | in progress | The activity's existing `!DeviceHelper.isTV` pause/finish path is retained; not device-verified. |
| Screensaver locale | in progress | The host still supplies the locale-wrapped context; the Compose playback path has not been checked. |
| Video workarounds (portrait, tunneling, Dolby Vision, refresh rate, HDR) | in progress | The views are reused unchanged; device verification is pending. |
| Music start/stop | in progress | Batch 5 starts background music from the fetched playlist, persists track changes, forces video mute and releases through the controller. Not device-verified. |
| Network loss | in progress | No dedicated connectivity monitor; provider/playlist failure and media errors route through the edge-case UI/controller policy. |
| Second attach and release while playing | in progress | Phase 7 disposal remains; player release now runs through `AndroidView` release. Leak check is still human-only. |

## Batch gates

The code path is built and the standard gate is run before each batch stops. A batch is not marked
parity or verified until the human checks the players on a device and `PARITY.md` is updated with
that result. Later batches must keep the rows they touch current.

**Batch 1 agent smoke (2026-11-11):** the final `betaDebug` APK attached the Compose playback layer
and initialized ExoPlayer, but `MediaService` returned 0 items because the configured custom feed
was unreachable and no other provider produced media. No first-item signal was expected, and no
fatal/ANR appeared. This does not verify video or image playback; the human must test with a
non-empty playlist.

**Batch 2 agent smoke (2026-11-11):** the final APK attached and completed the playlist request,
but the library was still empty (0 items). No fade or finish/loop transition could run. No
fatal/ANR appeared. Log: `build/phase8-batch2-smoke.log`.

**Batch 3 agent smoke (2026-11-11):** the final APK attached and completed the playlist request,
but the library remained empty (0 items), so no visible overlay was rendered. Compose constructed
the overlay layer without a fatal/ANR. Log: `build/phase8-batch3-smoke.log`.

**Batches 4/5 agent smoke (2026-11-11):** the final APK attached, completed the empty playlist
request, accepted `KEYCODE_BACK`, released the Compose UI and left no resumed `TestActivity`; no
fatal/ANR appeared. Log: `build/phase8-batch45-smoke.log`.

**Default-provider agent smoke (2026-11-11):** after backing up prefs, the built-in Apple, Amazon,
Comm1 and Comm2 feeds were enabled. `MediaService` fetched 262 videos (20/18/85/139). Compose
reported the first item displayed; a 1920x1080 HEVC video buffered, prepared and played. D-pad right
triggered a second transition and a 1m29s video. Back released cleanly and the original prefs were
restored. Logs: `build/phase8-default-provider-smoke.log` and
`build/phase8-default-provider-input-smoke.log`. Visual overlay parity, touch, blackout, music and
the full binding matrix remain human checks.

**Phase 9 beta exposure (2026-11-11):** the experimental toggle was exercised on the attached
Chromecast. The pref-selected Compose path reached its first item, the failure-limit guard disabled
Compose and fell back to legacy, the settings toggle reset the failure count, and the
`MainActivity` start-on-launch/Back round trip produced `exit_app=true`. These are agent smoke
checks; no row above changes status and no row is marked verified. See `PHASE9.md` and `PERF.md`.

**Phase 9a overlay screenshots (2026-11-11):** all 15 visual `OverlayType` values were captured on
legacy and Compose with a single still, live weather and a live Spotify track. The report is
`build/phase9/overlays/report/index.html`; the matrix and limitations are in `OVERLAYS.md`. This
confirms the metadata, message, music, clock/date, weather and countdown paths render, but it does
not verify visual parity: the long legacy now-playing line overflowed into the metadata column,
forecast icon backgrounds differ, and the progress bar and visibility modes are not captured. At
capture time Compose still used the default font; a subsequent font/loading follow-up is recorded
below. No row above is marked verified.

**Font/loading follow-up (2026-11-11):** Google Sans and Open Sans moved to `:core`; Compose now
uses the configured family and weight, and its loading row follows the legacy spinner, spacing,
visibility and font prefs. A forced-loading capture measured identical 151x36px text bounds and a
pixel-identical aligned glyph crop (`AE 0`), with a 4px baseline offset remaining. The side-by-side
is `build/phase10/loading/loading-side-by-side.png`. This is agent evidence; the rows remain in
progress and no row is marked verified.
