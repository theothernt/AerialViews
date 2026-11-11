# Phase 7: scaffold `:screensaver-compose`

**Status:** the module, renderer, factory wiring, standard gate and full gate are complete. The
Phase 7 gate is **not signed off**: the required dream-host and leak checks are not available to
this agent, and the Phase 6 gate remains open.

## What changed

- Added the flavorless `:screensaver-compose` Android library. It depends on `:core` and
  `:playback`, never on `:app`, and has no Firebase dependency.
- Added Compose tooling through the version catalog and root build: Compose BOM `2026.09.00`, the
  Kotlin Compose compiler plugin (`apply false` at the root), Compose UI/foundation, and
  `lifecycle-viewmodel-compose`. The plugin and Compose UI dependency are applied only in
  `:screensaver-compose`; `:app` contains no Compose source and has no direct Compose dependency.
- Added `:screensaver-compose` as a dependency of `:app` and registered
  `ComposeScreensaverUi` with `ScreensaverUiFactory` from `AerialApp`.
- Implemented `ComposeScreensaverUi`:
  - Builds `ComposeView(host.context)` so the locale-wrapped host context is used.
  - Sets the host's lifecycle, saved-state and ViewModel owners on both the window decor view and
    the `ComposeView`. The decor view is required by `WindowRecomposer`; the `ComposeView` is the
    owner chain used by `setContent`.
  - Uses `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`.
  - Installs itself with `host.window.setContentView`.
  - Mirrors `onStart`/`onStop` into a small `hostStarted` state flow.
  - Disposes and removes the previous composition if `attach` is called again on the same instance,
    then builds a fresh view. `release()` is idempotent and performs the same disposal; it does not
    own the host lifecycle, so `DreamServiceCompat` remains responsible for reaching `DESTROYED`.
  - Returns `false` from key and generic-motion events. Playback, TV input and the real screen are
    Phase 8 work.
- Added `ScreensaverViewModel`, a thin lifetime holder for a `ScreensaverController`. It exposes the
  controller's `StateFlow`, deliberately does not use `viewModelScope`, and calls
  `controller.release()` from `onCleared()`. `viewModel()` obtains it from the host-supplied
  `ViewModelStoreOwner`.
- Added the placeholder composable. It shows the controller phase and host-start state with
  `BasicText`, waits for its first frame, logs
  `Compose screensaver placeholder displayed`, and calls `onFirstItemDisplayed` so the Phase 6
  crash-loop guard can reset.
- Added consumer keep rules for the renderer entry point and the reflectively created ViewModel.
- No legacy entry points, fully qualified names or pref keys changed. No commit was created.

## Ownership and disposal

`ComposeScreensaverUi` owns only the composition and its `ComposeView`. The host owns the
`Window`, the lifecycle, the saved-state registry and the `ViewModelStore`. Phase 6's
`DreamServiceCompat` clears the store when a dream session ends; that reaches
`ScreensaverViewModel.onCleared()` and releases the shared controller. Releasing the renderer
disposes the composition and removes the view, but does not finish the host.

## Automated gate evidence

Final standard gate, after the reattach fix:

```sh
./gradlew --no-daemon :core:testDebugUnitTest :screensaver-compose:testDebugUnitTest \
  :app:testBetaDebugUnitTest :core:spotlessCheck :playback:spotlessCheck \
  :screensaver-compose:spotlessCheck :app:spotlessCheck :app:assembleBetaDebug
```

Result: `BUILD SUCCESSFUL` (59s). The `:core` suite passed 497 tests and `:app` passed 69 tests with
no failures in the migration test runs. `:screensaver-compose` currently has no test sources, so its
unit-test task has nothing to execute. The log is `build/phase7/standard-gate-final.log`.

Final full gate:

```sh
./gradlew --no-daemon :app:assembleGithubDebug :app:assembleBetaDebug \
  :app:assembleGoogleplayDebug :app:assembleGoogleplaybetaDebug \
  :app:assembleAmazonDebug :app:assembleFdroidDebug \
  :app:assembleBetaRelease :app:assembleFdroidRelease \
  :baselineprofile:tasks :microbenchmark:tasks
```

Result: `BUILD SUCCESSFUL in 4m 40s`; 598 actionable tasks. All six debug flavors, `betaRelease`
and `fdroidRelease` assembled, including R8 minification. `:baselineprofile:tasks` and
`:microbenchmark:tasks` configured successfully. The final code added no dependencies, so the
earlier F-Droid dependency/APK check remains valid: no Firebase artifacts. The log is
`build/phase7/full-gate-final.log`.

The release lint output contains 327 pre-existing `ExtraTranslation` findings for unused WebDAV
strings. They did not fail the build and were not changed in this phase.

## Device smoke (agent-run, not human verification)

The final `betaDebug` APK was installed on the attached Chromecast
(`192.168.1.248:5555`). Before installing, the original prefs hash was
`06698a865445c451b367693ecfff1f9c66c9a9344ef044df12144eaa987bd6cc`.

| Check | Result |
|---|---|
| Explicit `ui_path=compose` | `Compose screensaver placeholder attached` and `... displayed`, no fatal exception |
| Persisted `use_compose_screensaver=true` with failure count 2 | Compose path reached `attached` and `displayed` with no `ui_path`; count reset to 0 |
| Restore original prefs | Restored hash matched the original exactly |
| Explicit `ui_path=legacy` | `screen.built` logged; no Compose attach; no fatal exception |
| Pre-hardening 20-cycle batch | 20 attach, 20 displayed, 20 released; no fatal/ANR matches |
| Final-APK cycle batch | 19 attach, 19 displayed, 19 released; one launch did not attach before Back |
| Final verified-loop attempt | Interrupted by the tool timeout at 13 completed cycles (16 attach, 14 displayed, 13 released); no fatal/ANR matches |
| Back release | `Compose screensaver placeholder released` observed after the activity Back press |

The initial Compose smoke failed while the TV display was asleep, with an input-dispatch ANR
(`isSleeping=true`). Waking the display with `KEYCODE_WAKEUP` made the same build reach `displayed`.
The sleep state, not the Compose renderer, caused that failure.

The final APK's clean 20-cycle run has not been reproduced because the adb-driven start/back timing
is unreliable on this device. The per-cycle counts are smoke evidence only. They are not a
LeakCanary or heap-dump leak check.

## Required human checks / open gate items

- Run a LeakCanary review or heap dump across at least 20 start/stop cycles in both hosts. The
  agent's attach/release logs do not prove that compositions, lifecycle owners or players are not
  retained.
- Exercise the real `DreamActivity` path. `cmd dreams start-dreaming` returns
  `SecurityException: Must be root to call Dream shell commands`, so the dream host cannot be
  launched from adb on this device. The pref-selected and explicit Compose paths were exercised only
  through `TestActivity`.
- Complete the Phase 6 legacy parity checklist, including both exit reasons, the
  `startScreensaverOnLaunch` round trip, the Philips handoff, locale and input. The Phase 6 gate is
  still open, so Phase 7 cannot be signed off regardless of these scaffold checks.
- Phase 7 does not add Compose tests or instrumentation tests. The second-attach disposal path is
  compiled and covered by the gates, but not exercised by a dedicated test.

## Deferred to Phase 8

- Real video/image hosting through `:playback`.
- Transitions, overlays, key/touch input and exit behavior.
- TV Material/design and the actual screensaver UI. The Phase 7 renderer is deliberately a
  placeholder built with `BasicText`.
- A stable host/test harness that can run the 20-cycle disposal check without adb window-focus
  timing.

## Recommended adjustments

1. Do the leak and dream-host checks before treating this scaffold as a stable base.
2. Add a host-level test or test harness that attaches, starts, stops and releases the renderer in a
   controlled lifecycle, rather than relying on repeated adb starts and Back presses.
3. Keep the renderer's single-attach-session contract explicit: the current implementation replaces
   the previous composition on a second `attach`, but a released renderer cannot be reused.
