# Compose screensaver parity

Working matrix for Phase 8. **Not started** means no implementation in `:screensaver-compose`;
**in progress** means implemented but not yet accepted as parity; **verified** is reserved for a
feature checked on a real device by a human. This agent has not marked any row verified.

## Playback layer (batch 1)

| Feature | Status | Notes |
|---|---|---|
| Video playback via `VideoPlayerView` | in progress | Hosted in `AndroidView`; first-item path is wired to the shared controller. Not yet verified against the legacy player on a device. |
| Image playback via `ImagePlayerView` | in progress | Hosted in `AndroidView`; reuses `ImagePlayerHelper` and Coil loading. Not yet verified. |
| Warm video player between items | in progress | Both views stay in the hierarchy; the video is suspended while an image is visible. |
| Video finish callback | in progress | `VideoPlayerView.onVideoAlmostFinished` starts a controller transition; the overlay fade completes it. Not device-verified. |
| Image finish callback | in progress | `ImagePlayerView.onImageFinished` starts the same transition path. Not device-verified. |
| Image loading metadata/EXIF | in progress | `ImagePlayerView` still runs its extraction. The session publishes media short description/POI; EXIF, geocoding and per-slot metadata rules are not wired. |
| Video metadata/POI | in progress | The Compose listener is still a no-op. Session metadata uses the media's existing POI/short description, and POI cycling uses item elapsed time. |
| Player error fallback | not started | Controller error retries exist, but the Compose transition/error UI is not implemented. |
| Empty playlist / provider failure | not started | The session starts with an empty playlist on fetch failure; no Compose error UI yet. |

## Remaining parity rows

| Feature | Status | Notes |
|---|---|---|
| Fade-in/fade-out and transition protocol | in progress | Batch 2: overlay fade-in after the player is prepared, fade-out on `state.transition`, then `onTransitionCompleted`. Not device-verified. |
| Skip, previous, loop, next | in progress | Controller transition path handles finish/skip/loop; no Compose input or control surface yet. |
| Metadata overlays and POI | in progress | Batch 3 renderer and state are wired. Subset feed: media short description/POI only; per-slot selection, geocoding, EXIF and exact video-position POIs remain. |
| Weather now/forecast overlays | in progress | Batch 3 composables render the shared weather/forecast state; `WeatherService` starts for configured slots. Not device-verified. |
| Now-playing and music overlays | in progress | Batch 3 renderer and notification-listener feed start when a music slot is configured; actual background-music playback remains batch 5. |
| Message API overlay | in progress | Batch 3 renderer and optional Ktor server are wired to the shared store; not device-verified. |
| Overlay visibility modes | in progress | Batch 3 implements always visible/hidden and delayed show/hide with per-corner fade. Input-driven reveal is batch 4. |
| Progress bar | not started | Deferred past batch 3; the shared progress state and event bridge exist. |
| Gradients, brightness, notifications | in progress | Top/bottom gradients are rendered; brightness and notification overlays remain. |
| Blackout, sleep timer, scheduled blackout | not started | Batch 5; controller support exists. |
| Key-event input | not started | Batch 4; `onKeyEvent` still returns false. |
| Touch/mouse input | not started | Batch 4. |
| Exit reasons and `TestActivity` result | not started | Batch 4; host seam exists. |
| Philips handoff path | not started | Batch 4/5; host/factory placement exists. |
| `TestActivity` phone-only pause stop | not started | Batch 4; the activity's existing `onPause` is untouched. |
| Screensaver locale | in progress | The host still supplies the locale-wrapped context; the Compose playback path has not been checked. |
| Video workarounds (portrait, tunneling, Dolby Vision, refresh rate, HDR) | in progress | The views are reused unchanged; device verification is pending. |
| Music start/stop | not started | Batch 5. |
| Network loss | not started | Batch 5. |
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
