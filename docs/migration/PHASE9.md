# Phase 9: opt-in beta

**Status:** implementation and the automated full gate are complete. The settings toggle, guard,
reset rules and `MainActivity` round trip were exercised on the attached Chromecast. The Phase 9
gate is **not signed off**: the performance comparison needs human review, and the Phase 6, 7 and 8
human checks remain open. No commit was created.

## What changed

- Added **Appearance > Startup > Experimental > Try the new screensaver (beta)** in
  `settings_appearance_startup.xml`, bound to `use_compose_screensaver`.
- The summary says this is a preview, that some features are still in development, that the
  standard screensaver returns automatically on failure, and that a restart is needed to apply it.
- `AppearanceStartupFragment` installs a preference-change listener. Explicitly switching the
  toggle on calls `ScreensaverUiFactory.resetComposeFailureCount()`, clearing both the failure
  count and the failure version before the new attempt.
- Updated the stale migration comments in `GeneralPrefs` and `ScreensaverUiFactory`. No launch-path
  code change was needed: `MainActivity`/`TestActivity` and `DreamActivity` already select through
  `ScreensaverUiFactory.create(host)` with no override. `ui_path=legacy|compose` remains the
  explicit test override.

The legacy renderer is still the default; the beta is opt-in.

## Guard and reset evidence

All checks used `com.neilturner.aerialviews.debug` on the Chromecast. The original prefs were
backed up before the first check and restored at the end; both hashes are
`06698a865445c451b367693ecfff1f9c66c9a9344ef044df12144eaa987bd6cc`.

| Check | Setup | Observed result |
|---|---|---|
| Crash-loop guard | `use_compose_screensaver=true`, failure count 3, current version | Logged `Compose screensaver disabled after repeated startup failures`; pref persisted `false`; legacy `screen.built`; count stayed 3 |
| Version reset | `use_compose_screensaver=true`, failure count 3, failure version 1, current version 153 | Compose attached, fetched 262 videos, logged `first item displayed`; count persisted 0 and version 153 |
| User re-enable reset | `use_compose_screensaver=false`, failure count 3; toggled on in Settings | Pref persisted `true`; failure count persisted 0; version 153 |
| `MainActivity` round trip | Pref true, start-on-launch true, OK mapped to `EXIT_TO_SETTINGS` | `MainActivity` launched pref-selected Compose; first item displayed; Back logged `exitApp: true`; result handler logged `Exit app now? true`; task finished to launcher |

The guard path above is the real persisted-limit path, not a test-only bypass: the preference was
enabled, the request went through `preferredPath()`, and the factory fell back to the legacy UI.
Explicit `ui_path` overrides still bypass the guard and do not change the persisted count.

## Telemetry

`ScreensaverUiFactory` writes the `ui_path` Crashlytics custom key with `alwaysLog = true` before
attempting to create the selected UI, and again after a UI attaches. A Compose construction or
attach failure therefore has the requested value set before `recordException`; a fallback then
records the legacy value. The failure-limit message is also a non-fatal Crashlytics log message.

This was verified by source inspection. No Crashlytics report was generated or uploaded from the
debug build during this phase, so dashboard receipt of the custom key is still a human check.

## Full gate

```sh
./gradlew --no-daemon \
  :core:testDebugUnitTest :screensaver-compose:testDebugUnitTest :app:testBetaDebugUnitTest \
  :core:spotlessCheck :playback:spotlessCheck :screensaver-compose:spotlessCheck :app:spotlessCheck \
  :app:assembleGithubDebug :app:assembleBetaDebug :app:assembleGoogleplayDebug \
  :app:assembleGoogleplaybetaDebug :app:assembleAmazonDebug :app:assembleFdroidDebug \
  :app:assembleBetaRelease :app:assembleFdroidRelease \
  :baselineprofile:tasks :microbenchmark:tasks
```

Result: `BUILD SUCCESSFUL in 6m 30s`, 629 actionable tasks. The first wrapper was stopped by the
local command timeout after Gradle had already written the success line, so the same task set was
run again incrementally; that run ended `BUILD SUCCESSFUL in 4m 30s` with `GRADLE_EXIT=0`. Logs:
`build/phase9/full-gate.log` and `build/phase9/full-gate-clean.log`.

- `:core` tests: 497, 0 failures/errors/skips.
- `:app` tests: 69, 0 failures/errors/skips.
- `:screensaver-compose` has no test sources.
- All six debug flavors assembled, including `fdroid`.
- `betaRelease` and `fdroidRelease` assembled with R8/resource optimization.
- `:baselineprofile:tasks` and `:microbenchmark:tasks` configured successfully.
- Both `:app:dependencies --configuration fdroidDebugRuntimeClasspath` and
  `fdroidReleaseRuntimeClasspath` contain no Firebase entry.
- The release lint emitted the same 327 pre-existing `ExtraTranslation` findings recorded in
  Phase 7; they did not fail the build.

CI (`.github/workflows/ci.yml`) is `workflow_dispatch`-only and was not triggered: this session
created no commit and performed no push. The settings change did not add a dependency, so the
F-Droid/Firebase boundary is unchanged.

## Device checks not performed

- The system dream path cannot be launched with adb on this device
  (`SecurityException: Must be root to call Dream shell commands`).
- The settings screen was exercised through `MainActivity` on the TV; the phone layout, phone
  `onPause` stop, locale switching and full remote/gesture matrix still need human checks.
- Phase 8 visual parity, touch, overlay reveal, blackout, music and the full key matrix remain
  human checks. No `PARITY.md` row is marked verified.

## Performance

See `docs/migration/PERF.md`. The short version on the 2 GB Chromecast is now split by build type.

Debug build:

- Compose cold start was 0.56 s slower at the median (+8.0%).
- Configured-session PSS was about 1% lower for Compose in the steady runs.
- UI-thread jank was higher for Compose in this sample, but `gfxinfo` excludes video presentation.
- Empty-state whole-build PSS was 63.8% above the Phase 0 legacy baseline, even on the legacy
  path. This needs a memory investigation and human review before beta sign-off.

Release build (`betaRelease`, R8):

- Compose cold start was 15.5 ms slower at the median (+2.1%), within the run-to-run spread.
- Configured-session PSS was about 3.9% lower for Compose (84.9 MB vs 88.4 MB median).
- UI-thread jank was 4.36% for Compose vs 5.34% for legacy in this small sample.
- The release package's existing Apple-only playlist (139 videos) was used; its prefs were not
  changed. A release empty-state run is still missing.

## Draft release notes

> **Try the new screensaver (beta)**
>
> Aerial Views now includes an optional new screensaver built with Jetpack Compose. It is off by
> default. To try it, open **Settings > Appearance > Startup > Experimental** and enable
> **Try the new screensaver (beta)**, then restart the screensaver.
>
> The existing screensaver is still the default. If the new one fails to start repeatedly, the app
> returns to the existing screensaver automatically and switches the beta off. Re-enable it in
> Settings when you want to try again.
>
> This is an early beta: the progress bar, some metadata and brightness/notification behavior are
> still being completed, and some device-specific playback paths have not been verified on every
> TV. Please report your device model, Android version, media source and what happened if you see
> a problem.

## Draft feedback prompt

> **Trying the new screensaver beta?**
>
> Tell us how it went: your TV/device model, Android version, which media source you used, and
> whether video, overlays, remote controls, blackout and music behaved as expected. If it fell back
> to the standard screensaver, please include that too. Open an issue at
> <https://github.com/theothernt/AerialViews/issues> with logs if possible.

## Gate status

| Item | Result |
|---|---|
| Experimental toggle and preview wording | Implemented and checked on device |
| Both pref-selected preview paths | `MainActivity` round trip checked; `TestActivity` pref path checked |
| `ui_path` telemetry | Source path verified; no Crashlytics dashboard report generated |
| Fallback / crash-loop guard | Device demonstration passed |
| Reset rules | User re-enable and version reset passed |
| Full gate | Passed |
| Performance comparison | Debug and release agent-collected; **human review pending** |
| Human parity/device checks | **Open from Phases 6, 7 and 8** |

**Stop here.** Phase 10 must not start until the human reviews the performance data and the beta
feedback/crash data supports flipping the default.
