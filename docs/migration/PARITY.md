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
| Video finish callback | in progress | `VideoPlayerView.onVideoAlmostFinished` reports to the controller with the existing media-type guard. |
| Image finish callback | in progress | `ImagePlayerView.onImageFinished` reports to the controller. |
| Image loading metadata/EXIF | not started | `ImagePlayerView` still runs its existing extraction; overlay consumption is later. |
| Video metadata/POI | not started | The listener is currently a no-op; metadata overlays are a separate batch. |
| Player error fallback | not started | Controller error retries exist, but the Compose transition/error UI is not implemented. |
| Empty playlist / provider failure | not started | The session starts with an empty playlist on fetch failure; no Compose error UI yet. |

## Remaining parity rows

| Feature | Status | Notes |
|---|---|---|
| Fade-in/fade-out and transition protocol | not started | Batch 2. The current playback layer does not complete a transition. |
| Skip, previous, loop, next | not started | Batch 2; shared controller commands are available. |
| Metadata overlays and POI | not started | Batch 3. |
| Weather now/forecast overlays | not started | Batch 3. |
| Now-playing and music overlays | not started | Batch 3/5; music is not started by the Compose session. |
| Message API overlay | not started | Batch 3. |
| Progress bar | not started | Batch 3. |
| Gradients, brightness, notifications | not started | Batch 3. |
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

## Batch 1 gate

The code path is built and the standard gate is run before this batch stops. The batch is not
marked parity or verified until the human checks the players on a device and `PARITY.md` is updated
with that result. Later batches must keep the rows they touch current.

**Agent smoke (2026-11-11):** the final `betaDebug` APK attached the Compose playback layer and
initialized ExoPlayer, but `MediaService` returned 0 items because the configured custom feed was
unreachable and no other provider produced media. No first-item signal was expected, and no
fatal/ANR appeared. This does not verify video or image playback; the human must test with a
non-empty playlist.
