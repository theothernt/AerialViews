# Phase 5b before/after: legacy view on the shared controller

**Status:** implementation and the standard automated gate are complete. The per-feature
**human-verified** before/after table is still pending, so the Phase 5b gate is not yet signed off.

## Verification environment

- Variant: `betaDebug`, installed on the attached Chromecast (`192.168.1.248:5555`).
- A smoke launch of `TestActivity` started successfully, the process stayed alive, `screen.built`
  was logged, and logcat showed no fatal exception. This did not exercise a configured media
  playlist or the interactive scenarios below.
- No human has compared the legacy and controller-driven renderer side by side yet.
- Standard gate passed:

```sh
./gradlew --no-daemon :core:testDebugUnitTest :app:testBetaDebugUnitTest \
  :core:spotlessCheck :app:spotlessCheck :playback:spotlessCheck \
  :app:assembleBetaDebug
```

`:core`: 497 tests, 0 failures. `:app`: 67 tests, 0 failures. `:app:assembleBetaDebug` passed.

## Before/after expectations

The "After" column describes the intended result of moving `ScreenController` onto
`ScreensaverController`; it is not a human-observed result until the last column says so.

| Area | Scenario | Before (legacy view) | After (controller-driven renderer) | Verification |
|---|---|---|---|---|
| Startup | Playlist resolves with at least one item | Build playlist, load first item, fade out loading view, enable skip | Controller starts the playlist; renderer loads `state.media`, runs the same fade, then calls `onMediaVisible()` | Device smoke reached `screen.built`; playlist scenario pending |
| Playlist advance | Video reaches its finish point | `VideoPlayerView` finish runnable calls `fadeOutCurrentItem()`, then advances or replays | Player reports `onItemFinished()`; controller requests ITEM_FINISHED; renderer runs the same fade and reports completion | Controller tests cover advance; device/human pending |
| Photo timer | Photo reaches its finish time | `ImagePlayerView` delayed runnable fades out and advances | Same player timer calls `onItemFinished()`; controller handles advance/replay | Controller tests cover advance; device/human pending |
| Skip | Next/previous button while media is visible | Set explicit-skip flags and fade immediately | `skip()` requests USER_SKIP; `previous` stays in the request; renderer fades and completes | Controller tests cover both directions; device/human pending |
| Loop | Loop-until-skipped is toggled | Local flag, persisted pref, refreshed video finish timer; finish replays current item | Controller owns the flag; toggle returns it, pref and video timer are refreshed; ITEM_FINISHED replays | Controller tests cover replay; device/human pending |
| Loading error | Video/image error before the item is visible | Recreate player, wait 1s, load next item | Player recreation stays in the renderer; controller owns the 1s error policy and next-item decision | Controller tests cover loading error; device/human pending |
| Visible error | Video/image error after the item is visible | Recreate player, wait 1s, set explicit skip and fade out | Renderer recreates player and calls `onPlaybackError()`; controller requests a PLAYBACK_ERROR fade after 1s | Controller tests cover visible error; device/human pending |
| Blackout | User/sleep/scheduled blackout | Local flags, timers, music pause, view hiding, and exit reload | Controller owns source/state/timers/music; renderer observes the state and applies the same view hiding/restoration | Controller tests cover all three sources; device/human pending |
| Music | Background music starts, changes track, or fails | `setupMusicPlayer` owned the player; local track callbacks saved cache state | `LegacyMusicPlayback` adapts `MusicPlayer`; controller starts, pauses, resumes, changes and releases it; cache callbacks remain | Controller tests cover policy; device/human pending |
| Overlays | Auto-hide/auto-show/reveal and gradients | Local `canShowOverlays` gates reveal timing | Controller's `canShowOverlays` is updated through `onOverlaysBusy()`/`onOverlaysReady()`; same animations remain in the renderer | Unit-tested controller state; device/human pending |
| Cache | Playlist restored from cache | Wait 1s before playback; skip playback if blackout was entered | Controller owns the 1s cache delay and blackout deferral; renderer consumes the resulting media state | Controller tests cover delay; device/human pending |
| Empty playlist | No media available | Show loading error, still start configured background music | Controller reports EMPTY_PLAYLIST and starts configured music; renderer shows the loading error | Controller test covers empty music; device/human pending |
| Input | Press, long press, hold-repeat, release suppression | `InputHelper` private `previousEvent`/`longPressEvent` state machine | `KeyEventMapper` in `:core`; `InputHelper` adapts Android `KeyEvent` and executes its mapping | 6 mapper tests pass; device/human pending |

Fill in the final column after each scenario is compared on a device and mark it **pass** or
**fail**. Do not mark the phase complete from the automated results alone.

## Finish-timer mapping

The controller does not take ownership of the players' timers.

- `VideoPlayerView.setupAlmostFinishedRunnable()` still calculates `PlaybackTiming.finishDelayMs`
  and posts `almostFinishedRunnable`. That callback reaches
  `ScreenController.onVideoAlmostFinished()` → `ScreensaverController.onItemFinished()` →
  a `TransitionRequest` → `ScreenController.fadeOutCurrentItem()` →
  `onTransitionCompleted(request.id)`.
- `ImagePlayerView.runSetupFinishedRunnable()` still posts its `finishedRunnable` for
  `slideshowSpeed - mediaFadeOut`. It reaches `onImageFinished()` and the same protocol.
- `loopUntilSkipped` still suppresses the video finish timer in `VideoPlayerView`; toggling the
  controller flag still calls `refreshFinishTimer()`.
- `suspendPlayback()` still cancels the video finish timer when a photo replaces a video.
- `onVideoAlmostFinished()`/`onImageFinished()` remain guarded by the controller's `canSkip` state,
  which is enabled only after the loading fade completes.

## Device-specific workarounds: where they remain

- `ScreenController.installVideoView()` / `recreateVideoPlayer()`: Realtek codec teardown avoidance
  and player replacement remain in `:app`.
- `VideoPlayerView`: Philips Dolby Vision, refresh-rate switching, portrait rotation, separate-audio
  retry, tunneling and finish-timer behavior remain in `:playback`.
- `ImagePlayerView`: SMB/stream handling, EXIF extraction, pre-S background-blur constraints and
  image timers remain in `:playback`.
- `DreamActivity`: Philips OLED activity handoff, dream wake-up, touch/key security-exception
  handling remain in `:app`.
- `TestActivity`: handoff grace, screen wake, exit-result contract remain in `:app`.
- `MusicPlayer`: per-track recovery and give-up policy remain in `:core`.
- `MediaService` and provider-specific filtering remain in `:core`.
