# Phase 8: Compose UI parity

**Status:** batches 1 (playback layer), 2 (transitions), 3 (overlays), 4 (input) and 5 (edge cases)
are implemented and their standard gates pass. Phase 8 is not complete: the remaining rows still
need human device verification. No row in `PARITY.md` is marked verified until a human checks it on a
device.

## Batch 1: playback layer

### What changed

- Added `ScreensaverSession`, the Compose path's bootstrap. It fetches the same playlist through
  `MediaService` and starts the shared `ScreensaverController`. The playlist fetch runs in a
  session-owned `SupervisorJob` scope, not `viewModelScope`; `release()` cancels it and releases the
  controller.
- Changed `ScreensaverViewModel` to an `Application`-backed holder for that session. It still has no
  playback state, no player and no UI logic; it exposes the controller's `StateFlow` and the
  controller used by the playback layer.
- Added `ComposePlaybackLayer`:
  - Hosts the existing `VideoPlayerView` and `ImagePlayerView` through `AndroidView`.
  - Keeps both views in the hierarchy and changes only their visibility, preserving the warm video
    codec behavior of the legacy renderer.
  - Reuses the existing players and image loader unchanged; no player or Coil behavior was
    rewritten.
  - Sends video/image prepared, finish and error events through the controller's existing
    transition protocol.
  - Applies the existing `PlaybackCallbacks.videoEventAppliesTo` guard so a late video callback
    cannot advance a photo that replaced it.
  - Pauses the video and image timer while the host is stopped, and resumes them when it starts.
  - Releases both views through `AndroidView`'s release callback.
- Replaced the Phase 7 placeholder with the playback layer. The crash-loop guard now resets only
  after the controller reports a prepared item as visible (`canSkip`), not on the first composition
  frame.
- Added the Media3 UI/common compile dependencies to `:screensaver-compose`; `VideoPlayerView`'s
  Media3 supertypes are part of the public type surface Compose references.
- Added `docs/migration/PARITY.md` with the batch rows and remaining feature matrix.

### Standard gate

```sh
./gradlew --no-daemon :core:testDebugUnitTest :screensaver-compose:testDebugUnitTest \
  :app:testBetaDebugUnitTest :core:spotlessCheck :playback:spotlessCheck \
  :screensaver-compose:spotlessCheck :app:spotlessCheck :app:assembleBetaDebug
```

Result: `BUILD SUCCESSFUL` (54s). The existing `:core` and `:app` suites passed with no failures.
`:screensaver-compose` still has no test sources, so its unit-test task has nothing to execute. The
log is `build/phase8-batch1-standard-gate.log`.

No manifest components moved, so there is no merged-manifest diff for this batch.

### Device smoke (agent-run, not parity verification)

The final `betaDebug` APK was installed on the attached Chromecast and started with
`TestActivity` and explicit `ui_path=compose`.

Observed:

- `Compose screensaver playback attached`
- ExoPlayer initialized (`AndroidXMedia3/1.11.1`)
- `MediaService` completed and reported **0 media items** (the configured custom feed on
  `127.0.0.1:8765` was unavailable and no other provider produced items)
- No `Compose screensaver first item displayed`, correctly, because there was no item to display
- No fatal exception or app ANR in the captured log

Log: `build/phase8-batch1-smoke.log`. This smoke proves the Compose host, session start and player
construction path, but it does **not** verify image or video playback: the device had no configured
media. The human should repeat with a non-empty playlist.

### Intentional gaps in this batch

- The legacy loading view, overlays, progress bar, blackout UI and error messages are absent. The
  screen is black while the playlist is being fetched and when the playlist is empty.
- Compose input still returns false; key/touch handling and both exit reasons are batch 4.
- Music, scheduled blackout, sleep timer, empty/provider-error UI and network-loss handling are
  batch 5.
- The Compose path maps `loopUntilSkipped` and `alternateTextPosition` into `ScreensaverConfig`;
  blackout, music and the remaining preferences are added with the batches that implement those
  behaviors.

## Batch 2: item transitions

### What changed

- `ComposePlaybackLayer` now owns a black loading overlay separate from the player views. The
  overlay is animated with `graphicsLayer` alpha; the SurfaceView itself is never alpha-animated.
- New media resets the overlay to fully black, loads through the existing player/image view and
  waits for the player's prepared callback. The overlay then fades to transparent over the
  configured media fade-in duration and calls `controller.onMediaVisible()` only when the item is
  actually visible.
- A controller transition request (finish, skip or playback error) fades the overlay to black over
  the configured fade-out duration, fades video audio, stops the image timer/write surface where
  applicable, and calls `controller.onTransitionCompleted(id)`.
- The controller's next/replay/loop decision is therefore driven from the animation completion, not
  from a timer in the renderer. Video finishes still come from `VideoPlayerView`; image finishes
  still come from `ImagePlayerView`.
- `ScreensaverSession` now maps `GeneralPrefs.loopUntilSkipped` and
  `GeneralPrefs.alternateTextPosition` into the controller config so image replay and overlay
  alternation match the legacy settings. Skip/previous calls remain controller APIs; input is still
  batch 4.
- The loading overlay is a Compose sibling above the player views, matching the legacy loading-view
  layer. No player, image-loader or device workaround was changed.

### Standard gate

```sh
./gradlew --no-daemon :core:testDebugUnitTest :screensaver-compose:testDebugUnitTest \
  :app:testBetaDebugUnitTest :core:spotlessCheck :playback:spotlessCheck \
  :screensaver-compose:spotlessCheck :app:spotlessCheck :app:assembleBetaDebug
```

Result: `BUILD SUCCESSFUL` (57s). The existing `:core` and `:app` suites passed with no failures.
`:screensaver-compose` still has no test sources. The log is
`build/phase8-batch2-standard-gate.log`.

### Device smoke (agent-run, not parity verification)

The final `betaDebug` APK was installed again. It attached the Compose path and completed the
`MediaService` request, but the test library again contained **0 media items**; no prepared item or
transition animation could run. No fatal exception or ANR appeared. Log:
`build/phase8-batch2-smoke.log`. The fade and finish/loop paths need a human test with a non-empty
video/image playlist.

## Batch 3: overlays

### What changed

- Added `ComposeOverlayLayer`, which renders the resolved overlay layout in the same four corner
  columns as `overlay_view.xml`. It applies screen-edge padding, max text width, per-corner fading,
  opacity, and gradients, and sits between the player and the black loading layer so a loading fade
  still covers the overlays.
- Added `ComposeOverlayContent` for clock, date, metadata, message, now-playing, weather-now,
  weather-forecast and countdown slots. All content is derived from `OverlayUiState`, with the
  preference-backed initial message text and clock/date/countdown settings matching the legacy
  defaults.
- Added `ComposeOverlayConfig`, which maps `GeneralPrefs` into the existing pure
  `OverlayConfigResolver` model. Slot assignment, typography, padding, opacity, fade corners,
  alternation and gradient flags therefore come from the shared configuration rather than a second
  Compose-only layout model.
- Implemented the `ALWAYS_VISIBLE`, `ALWAYS_HIDDEN`, `HIDE_AFTER_DELAY` and `SHOW_AFTER_DELAY`
  overlay visibility modes. Fading is applied with `graphicsLayer` to corner groups and gradients;
  persistent corners stay visible. Input-driven reveal remains batch 4.
- Wired overlay producers into `ScreensaverSession`: the existing `OverlayEventBridge`, metadata
  state from the current media, `WeatherService`, `NowPlayingService` when a music slot is
  configured, and the optional Ktor message API. `ScreensaverController` now exposes its
  controller-owned `OverlayStateStore` so the renderer and producers share one state model.
- `ComposeScreensaverScreen` collects the controller's overlay flow; the playback layer accepts an
  overlay composable slot so the layering stays correct without a second renderer.

### Standard gate

```sh
./gradlew --no-daemon :core:testDebugUnitTest :screensaver-compose:testDebugUnitTest \
  :app:testBetaDebugUnitTest :core:spotlessCheck :playback:spotlessCheck \
  :screensaver-compose:spotlessCheck :app:spotlessCheck :app:assembleBetaDebug
```

Result: `BUILD SUCCESSFUL` (1m 8s). Existing `:core` and `:app` suites passed; the Compose module
still has no test sources. Log: `build/phase8-batch3-standard-gate.log`.

### Device smoke (agent-run, not parity verification)

The final `betaDebug` APK was installed and launched with `TestActivity` and explicit
`ui_path=compose`. It attached the Compose path and completed the playlist request, but the device
still had **0 media items**, so no item, overlay or fade was visible. No fatal exception or ANR
appeared. Log: `build/phase8-batch3-smoke.log`. Visible overlay parity needs a human test with a
non-empty playlist.

## Batch 4: input

### What changed

- Moved the existing `SwipeGestureListener` and `GestureType` into `:core` under their existing
  package, so the legacy and Compose paths share one gesture implementation. `ScreensaverUi` now
  exposes `onTouchEvent`; `TestActivity` and `DreamActivity` dispatch touch into the active UI.
- Added `ComposeScreensaverInput`, which reuses the shared `KeyEventMapper`, preference bindings and
  blackout wake rule. It handles presses, long presses, hold-repeat, media passthrough, both exit
  reasons and gesture actions.
- The screen installs one controller-aware input handler for the life of the composition and resets
  it on disposal. Playback actions are delegated to `ComposePlaybackLayer`: pause/resume, mute,
  speed, seek, restart, and refreshing the video finish timer after a loop toggle.
- Added overlay reveal from input. `ComposeOverlayLayer` accepts a reveal key, shows the configured
  corner group, and re-runs the legacy reveal timeout before auto-hiding again. The reveal resets
  for each item.
- Preserved mouse-to-exit through `onGenericMotionEvent`, and routed both exit reasons through the
  existing host contract rather than finishing the activity from the renderer.
- `TestActivity`'s phone-only `onPause` stop remains intact; the Compose UI uses the same host
  lifecycle and result contract as the legacy UI.

### Standard gate (batches 4 and 5)

These two batches were implemented in one request, so they share the final gate:

```sh
./gradlew --no-daemon :core:testDebugUnitTest :screensaver-compose:testDebugUnitTest \
  :app:testBetaDebugUnitTest :core:spotlessCheck :playback:spotlessCheck \
  :screensaver-compose:spotlessCheck :app:spotlessCheck :app:assembleBetaDebug
```

Result: `BUILD SUCCESSFUL` (1m 54s). Existing `:core` and `:app` suites passed; the Compose module
still has no test sources. Log: `build/phase8-batch45-standard-gate.log`.

### Device smoke (agent-run, not parity verification)

The first run used the device's existing empty playlist: the final `betaDebug` APK attached the
Compose path, accepted `KEYCODE_BACK`, logged `Compose screensaver playback released`, and left no
resumed `TestActivity`. No fatal exception or ANR appeared. Log:
`build/phase8-batch45-smoke.log`.

A second run temporarily enabled the built-in Apple, Amazon, Comm1 and Comm2 feeds (the original
preferences were backed up and restored afterward). `MediaService` fetched **262 videos** (Comm1
20, Comm2 18, Amazon 85, Apple 139). Compose logged `first item displayed`; `VideoPlayerView`
buffered, prepared and played a 1920x1080 HEVC video (duration 1m45s). A `KEYCODE_DPAD_RIGHT` press
triggered a second playlist transition and prepared/played another video (duration 1m29s). Back
released the session cleanly, with no fatal exception or ANR. Logs:
`build/phase8-default-provider-smoke.log` and
`build/phase8-default-provider-input-smoke.log`. This verifies built-in provider fetch, first-video
startup, default right-skip input, transition and release; it does not verify visual overlay parity,
touch gestures, blackout, music, or the full binding matrix.

### Remaining gaps after batch 4

- Brightness increase/decrease currently update no visible state; the legacy notification and
  brightness overlay are not reproduced. Toggle-looping also has no notification.
- Key, gesture and mouse behavior has only been smoke-tested for Back/exit and the default
  right-skip binding; touch gestures and the full binding matrix still need a human check.
- Overlay reveal works through the controller gate, but its exact timing against the legacy view
  path is unverified.

## Batch 5: edge cases

### What changed

- Added a session loading-status flow from `MediaService` and a Compose status layer inside the
  black loading fade. It shows the legacy loading labels, an empty/provider-failure message, or a
  playback-error message, with a Compose-drawn spinner and no new UI dependency.
- Added background music to the Compose session using the shared `MusicPlayer` and a local
  `MusicPlayback` adapter. The fetched music playlist and resume index are passed to the controller,
  track changes are persisted through `PlaylistCacheRepository`, and the controller owns pause,
  resume, failure and release. Video is forced-muted while background music is active.
- Mapped the sleep timer and scheduled-blackout preferences into `ScreensaverConfig`. The Compose
  screen renders a full-screen blackout, suppresses overlays and stops the hosted players while
  blackout is active; the controller resumes with a new item and music after wake.
- Provider failure and an empty fetched playlist now reach the same `ScreensaverError` UI. Playback
  errors continue through the controller's retry/advance policy without leaving a blank screen.
- No dedicated connectivity monitor was added: network loss is handled through provider/playlist
  failure and media playback errors, as in the existing service layer.

### Remaining gaps after batch 5

- Custom bundled fonts remain in `:app`; Compose uses `FontFamily.Default` with the resolved colour,
  size and weight.
- Metadata is a subset: current media short description/POI is published to every metadata slot;
  per-slot selection, geocoding, EXIF/date rules, the video metadata listener and exact video-position
  POI cycling remain.
- Message/weather transitions, countdown localization and a `@Preview` harness remain.
- The progress bar, brightness visuals, notification toasts, Sonos integration and blackout
  notifications are not implemented. No dedicated no-network UI exists.
- All visual, input, blackout, music and error behavior still needs human device verification;
  no row is marked verified.

### Recommended next step

Stop at the combined batch 4/5 gate. The human should verify batches 3-5 on a non-empty playlist and
close the still-open Phase 6/7 gates. Phase 9's opt-in beta is recorded separately in `PHASE9.md`;
its performance review and the Phase 8 human checks remain open. No commit was created.
