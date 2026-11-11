# Phase 8: Compose UI parity

**Status:** batches 1 (playback layer), 2 (transitions) and 3 (overlays) are implemented and their
standard gates pass. Phase 8 is not complete; input and edge cases remain. No row in `PARITY.md` is
marked verified until a human checks it on a device.

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

### Remaining gaps after batch 3

- Custom bundled fonts remain in `:app`; Compose currently uses `FontFamily.Default`. The colour,
  size and weight are resolved from prefs.
- Metadata production is a subset: the session publishes the current media's short description and
  POI map to all metadata slots, but does not yet apply the per-slot selection, geocoding, photo
  EXIF/date rules or the video metadata listener. POI changes cycle from item elapsed time rather
  than the video's exact current position.
- Message and weather content update without the legacy cross-fade/ChangeBounds animation;
  countdown unit labels are not localized, and no `@Preview` harness was added.
- The progress bar, loading text/spinner, error/empty-state UI, brightness, notifications and
  blackout visuals are not implemented.
- Compose key/touch input and both exit reasons are still unimplemented; skip/previous are only
  reachable through the controller API today.
- Music playback start/stop and network/error handling remain batch 5.

### Recommended next step

Implement batch 4 (input handling) with the shared key mapper, touch/motion handling, overlay
reveal commands, and both host exit paths.
