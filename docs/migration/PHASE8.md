# Phase 8: Compose UI parity

**Status:** batch 1 (playback layer) is implemented and its standard gate passes. Phase 8 is not
complete; transitions, overlays, input and edge cases remain. No row in `PARITY.md` is marked
verified until a human checks it on a device.

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

- **Transitions are not implemented.** When a video or image finishes, the controller requests a
  transition, but this batch does not animate the current item out or call
  `onTransitionCompleted`. The first item remains on screen. Batch 2 adds the transition.
- The legacy loading view, overlays, progress bar, blackout UI and error messages are absent. The
  screen is black while the playlist is being fetched and when the playlist is empty.
- Compose input still returns false; key/touch handling and both exit reasons are batch 4.
- Music, scheduled blackout, sleep timer, empty/provider-error UI and network-loss handling are
  batch 5.
- The Compose path currently uses the controller's default `ScreensaverConfig`; preference mapping
  for loop, blackout and music will be added with the batches that implement those behaviors.

### Recommended next step

Implement batch 2 (item transitions) on top of this host: observe `state.transition`, animate the
loading overlay rather than the SurfaceView, call `onTransitionCompleted(id)`, then verify with a
non-empty video/image playlist.
