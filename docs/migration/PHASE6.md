# Phase 6: host/UI seam, opt-in flag, and parity contracts

**Status:** implementation and the standard automated gate are complete. The legacy behavior
checklist still requires human verification, so the Phase 6 gate is not signed off.

## What was added

- `ScreensaverHost` and `ScreensaverUi` in `:core`, plus `ExitReason`. The host owns the
  locale-wrapped `Context`, `Window`, lifecycle/saved-state/ViewModel owners, and the exit result.
- `DreamServiceCompat` in `:app` with no Compose types. It supplies the three owners, drives the
  dream lifecycle to `DESTROYED`, clears the `ViewModelStore` after detaching, and restores saved
  state on create.
- Host adapters for `DreamActivity` and `TestActivity`, a `LegacyScreensaverUi` adapter around the
  existing `ScreenController`, and `ScreensaverUiFactory`.
- `GeneralPrefs.useComposeScreensaver` (default `false`) and the persisted Compose failure count and
  version. `TestActivity` accepts `ui_path=legacy|compose` for testing.
- The factory writes the `ui_path` telemetry key, falls back to legacy if the Compose renderer is
  unavailable or throws, and keeps at most one UI attached. It clears the handoff-dream reference
  when the activity-hosted UI is released.
- `InputHelper.resetKeyState()` clears the process-global key mapper at attach/release boundaries.

Phase 6 still uses the legacy renderer only. `ScreensaverUiFactory.registerComposeUiFactory()` is
the seam Phase 7 will use; until then an explicit `ui_path=compose` records the fallback and starts
the legacy UI.

## Lifecycle override order

Before this change, `DreamActivity` overrode:

1. `onAttachedToWindow`: call super; set fullscreen/interactive; return for `runAsActivity`; hide
   system UI; stop a previous controller; build the locale-wrapped `ScreenController`; set its view;
   install gestures.
2. `onDreamingStarted`: call super; start the activity handoff or send the analytics screen view.
3. `onDreamingStopped`: mark timing; clear the handoff reference; stop the controller; call super.
4. `onDetachedFromWindow`: log; stop the controller; call super.
5. `onDestroy` was not overridden.

After this change, the same order is preserved in `DreamActivity`; the controller work is delegated
to `ScreenController` through `LegacyScreensaverUi`, and `onDestroy` releases the UI before
`DreamServiceCompat` moves the owner to `DESTROYED` and clears its `ViewModelStore`.
`onDetachedFromWindow` clears the store after the UI is released, so a second attach on the same
service instance sees a fresh session.

## Legacy parity checklist

The final column must be completed on a device; automated tests and the smoke launch below do not
count as human verification.

| Scenario | Before | After | Verification |
|---|---|---|---|
| Dream path | `DreamActivity` creates `ScreenController` after attach and stops it on dream stop/detach | Factory creates the same legacy UI; dream lifecycle delegates to it | Not yet run |
| `TestActivity` path | Activity creates `ScreenController` in `onAttachedToWindow`; phone pause and TV stop finish as before | Factory creates the same UI; `activityStarted` drives `onStart`; stop/exit paths unchanged | Explicit legacy smoke passed; full behavior pending |
| Default selection | Always legacy | `useComposeScreensaver=false` selects legacy; no setting is exposed | Explicit legacy smoke passed |
| `ui_path=legacy` | No override | Explicit override selects legacy without touching the pref | Smoke passed |
| `ui_path=compose` | No Compose renderer | Compose request falls back to legacy with a recorded non-fatal while the renderer is unavailable | Smoke passed; actual Compose path is Phase 7 |
| `EXIT` | `InputHelper` calls the host's exit callback with `true`; dream wakes / activity finishes with `exit_app` handling | UI reports `ExitReason.EXIT`; host adapter preserves the old callback | Not yet run |
| `EXIT_TO_SETTINGS` | `InputHelper` calls with `false`; dream does not wake / activity finishes without exit-app | UI reports `ExitReason.EXIT_TO_SETTINGS`; same host behavior | Not yet run |
| `startScreensaverOnLaunch` round trip | `MainActivity` reads `RESULT_OK` and `exit_app` | Result contract is still owned by `TestActivity`; UI only reports the reason | Not yet run |
| Philips handoff | `DreamActivity` starts `TestActivity` and keeps the dream alive underneath | Handoff decision stays in `DreamActivity`; the factory is created by the activity, never by both hosts | Not yet run |
| Locale | Each host passed a locale-wrapped context to `ScreenController` | Both host adapters expose that same locale-wrapped context to the UI | Not yet run |
| Key/touch input | Activity/dream dispatch went through `InputHelper` with the legacy controller | UI interface delegates key and generic-motion events to the same mapper; key state is reset per session | Smoke did not exercise input |
| Single active UI | No guard | Factory assertion rejects a second active UI | Assertion exists; not device-verified |

## Crash-loop guard

- `use_compose_screensaver` defaults to `false` and is not exposed in settings yet.
- A pref-selected Compose session increments `compose_screensaver_failure_count` when it starts.
  The count is reset when the Compose UI reports its first displayed item, and when the app version
  code changes.
- After three consecutive failures, the opt-in is disabled and the session uses legacy. An explicit
  `ui_path=compose` test override bypasses this guard and does not change the persisted count.
- `ScreensaverUiFactory.resetComposeFailureCount()` exists for the Phase 9 user-initiated
  re-enable path.

## Gate evidence

The standard gate passed after the Phase 6 changes:

```sh
./gradlew --no-daemon :core:testDebugUnitTest :app:testBetaDebugUnitTest \
  :core:spotlessCheck :app:spotlessCheck :playback:spotlessCheck \
  :app:assembleBetaDebug
```

`:core`: 497 tests, 0 failures. `:app`: 69 tests, 0 failures.

A device smoke on the attached Chromecast installed `betaDebug` and launched `TestActivity` with
`ui_path=legacy`: the process stayed alive, `screen.built` was logged, and no fatal exception
appeared. A second launch with `ui_path=compose` logged
`Compose screensaver failed to start; falling back to legacy` and also reached `screen.built` with
no fatal exception. A direct DreamService smoke was attempted, but `cmd dreams start-dreaming`
returned `SecurityException: Must be root to call Dream shell commands`; the temporary dream
settings were restored. The dream path, exit paths, round trip, handoff, locale, and input parity
remain human verification items.
