# AerialViews: Gradual Compose Screensaver Migration (v4)

Plan for an LLM coding agent. Read this whole file before starting. Work on **one phase at a time** and **stop at the end of each phase** for human review.

v2 incorporated a validation pass against the actual codebase; v3 added `:playback`, the `DreamServiceCompat` contract and the overlay-config extraction. v4 applies the Phase 0 audit (`docs/migration/AUDIT.md`) to the plan: Phases 2-4 now follow the audit's 15-step move order, the four missing `ui/helpers` files are in the `:core` inventory, the `MusicPlayer -> VideoPlayerHelper` inversion is called out, and the resource, Room/KSP and telemetry gaps are closed. Where this plan and the code disagree, the code wins: stop and report.

## Goal

Add a new Jetpack Compose screensaver implementation that users can opt into, running alongside the existing implementation. When it is stable, make it the default, then remove the legacy path.

## Scope and non-goals

**In scope**
- A shared, UI-agnostic controller and state in `:core` (finishing the extraction already under way).
- A Compose shell in `:screensaver-compose`: the screen root, item transitions (fades, skip/loop/next timing, gating), and all overlays. This replaces `ScreenController`'s **view layer**, not just the overlay views.
- Thin ViewModels in `:screensaver-compose` that expose the shared controller to Compose (see "ViewModels and owners" below).

**Non-goals (do not touch)**
- **The video player and the image player.** The existing `VideoPlayerView` and the existing image loading/view (the `ImagePlayerHelper` path), including all device-specific workarounds, are **reused as they are** from `:playback` and hosted in the Compose screen via `AndroidView`. No reimplementation, no player or image-loader swap (a Compose image loader could be a separate, optional future project).
- The settings UI, providers, and prefs behavior.
- Replacing Media3/ExoPlayer configuration, tunneling, HDR, Dolby Vision, or refresh-rate handling.

**ViewModels and owners**
- The shared controller in `:core` stays plain Kotlin (constructor-injected dependencies, its own `CoroutineScope`/`StateFlow`s, idempotent `release()`), so the legacy view can use it without any Android ViewModel machinery.
- The Compose module wraps it in thin `androidx.lifecycle.ViewModel`s. The ViewModel is a lifetime holder, not a second state owner: it creates or receives the controller, exposes its `StateFlow`s, and calls `controller.release()` from `onCleared()`. It holds no logic, no prefs reads, and no player. **Do not run the controller on `viewModelScope`**: `onDreamingStopped` takes the lifecycle to `CREATED`, not `DESTROYED`, so `viewModelScope` would keep timers and playback alive after the dream stops. The controller's own `CoroutineScope` plus an explicit, idempotent `release()` is the contract.
- A `DreamService` is not a `LifecycleOwner`, `SavedStateRegistryOwner`, or `ViewModelStoreOwner`, so the dream host uses a **`DreamServiceCompat` base class** in `:app` that implements those three interfaces on top of `DreamService`. Reference implementation by Niels van Velzen: https://gist.githubusercontent.com/nielsvanvelzen/e7d7d288e0e6e152bec9295579dfc32d/raw/70550214ac0be55f823070b3164cce48459802c1/DreamServiceCompat.kt. `TestActivity` already provides them as an `AppCompatActivity`. If the agent cannot fetch the reference, it must **ask the human to paste it** rather than reinventing it from memory.
- **The reference is a starting point, not a copy-paste.** It has four defects for this app, all of which the implementation must fix:
  1. It never drives the lifecycle to `DESTROYED`, so `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` never fires and the composition is never disposed. Override `onDestroy()` and move to `DESTROYED` there.
  2. It never clears its `ViewModelStore`, so `onCleared()` is never called and the controller (with its ExoPlayer) outlives the dream. `DreamActivity` documents that `onAttachedToWindow` can fire again on the same service instance, so the store must be per dream session and cleared on stop/detach.
  3. Its `ComposeView(this)` bypasses the locale-wrapped `Context` this app runs the screensaver on (see Verified facts). The Compose view must be built from `ScreensaverHost.context`.
  4. Its `setContent(@Composable)` helper would drag Compose UI and the compose compiler plugin into `:app`, which Phase 7 forbids. `DreamServiceCompat` exposes owners only and contains **no Compose types**; `:screensaver-compose` builds its own `ComposeView`.
- The host exposes these owners to the UI through `ScreensaverHost` (Phase 6); `ComposeScreensaverUi` consumes them (Phase 7).

## Target architecture

```
:core                  models, enums, prefs, providers, services, data, utils,
                       four non-UI ui/helpers, shared screensaver controller / state
                       (UI-agnostic)
:playback              the existing video and image playback layer: VideoPlayerView,
                       ImagePlayerView, VideoPlayerHelper, ImagePlayerHelper and their
                       device-specific workarounds. Depends on :core ONLY.
:app                   legacy screensaver UI, settings, activities (DreamActivity, TestActivity)
                       composition root: depends on :core, :playback and :screensaver-compose
:screensaver-compose   new Compose screensaver UI (depends on :core and :playback, never on :app)
```

Dependency rules (enforce in every phase):
- `:core` depends on neither `:app`, `:playback` nor `:screensaver-compose`.
- `:core` contains no `Activity`, `DreamService`, `View` implementation, or Compose code, and (see Phase 1) no direct Firebase calls. It **may declare host/UI contracts** (framework types such as `Context`, `Window`, `KeyEvent`, and the lifecycle owners) as long as it implements no UI. That is why `:core` takes `androidx.lifecycle` and `androidx.savedstate` dependencies in Phase 1.
- `:playback` depends on `:core` only. It holds the player/image Views and their workarounds, and no screensaver orchestration. It consumes the telemetry seam declared in `:core`, never Firebase directly.
- `:screensaver-compose` never imports from `:app` and never depends on Firebase.

## Verified facts about the project (reviewed against the codebase; re-verify cheaply, don't redo)

- Modules: `:app`, `:projectivyapi` (AIDL library), `:baselineprofile`, `:microbenchmark`. One `version` flavor dimension: `github`, `beta`, `googleplay`, `googleplaybeta`, `amazon`, `fdroid`. minSdk 24, targetSdk 37, AGP 9.5.1, Kotlin 2.4.21.
- **Phase 0 result:** `docs/migration/AUDIT.md` is authoritative for the dependency closure, the 15-step move order, the manifest inventory and the baselines. Its section 7 lists every place this plan originally disagreed with the code; those corrections are already applied below.
- Version catalog already exists (`gradle/libs.versions.toml`). `android.nonTransitiveRClass=true` and `nonFinalResIds=true` are already set. Do not "enable" them.
- `AGENTS.md` defines the build/test/lint commands (see Standard gate below).
- Entry points: `DreamActivity : DreamService` and `TestActivity : AppCompatActivity`, both manifest-declared; `DreamActivity` is exported with `BIND_DREAM_SERVICE`. `NotificationService` (notification listener) grant is stored by component name.
- Prefs use Kotpref (its initializer captures the Context; the app has no static Context capture of its own).
- `ScreenController` (~1,400 lines) **is the view**: its constructor inflates `AerialActivityBinding`, builds every overlay, installs the video view, reads ~20 prefs into fields. Playlist advance is implemented in animation end callbacks (`fadeOutCurrentItem`'s `withEndAction` decides skip/loop/next); gating uses `canSkip`/`canShowOverlays` flags mutated by `ViewPropertyAnimator` callbacks; video finish timing is a `View.postDelayed` inside `VideoPlayerView`. The fade/gate timing is effectively the state machine.
- Existing, already-extracted and tested pure logic: `PlaybackTiming`, `PlaybackCallbacks`, `ScheduledBlackoutWindow`, `PortraitVideoOrientation`, `MetadataResolver`, `TrackMetadataExtractor`, `VideoMetadataHandler` (51 test files in total). Overlays are already driven from a `StateFlow` via `ui/overlays/state/` (`OverlayStateStore`, `OverlayUiState`, `OverlayEventBridge`). Note `OverlayUiState` imports `ui.controls.ProgressState`, so it is not yet UI-agnostic.
- Source-set swap: `app/build.gradle.kts` hand-wires `src/common/java` for five flavors and `src/fdroid/java` for F-Droid. `FirebaseHelper` exists twice; the F-Droid build deliberately excludes Firebase (firebase flavors loop, Perf plugin disabled). `MediaService`, `VideoPlayerView`, `VideoPlayerHelper`, `MediaServiceHelper` call `FirebaseHelper` directly.
- `BuildConfig` is read by code headed for `:core`: `OPEN_WEATHER` (`WeatherService`), `VERSION_CODE` (`MigrationHelper`), `DEBUG`/`TEST_CONFIG`/`FLAVOR` (`DreamTiming`, `ImagePlayerHelper`).
- `GeneralPrefs` imports `services.weather.TemperatureUnit` and `WindSpeedUnit` (in `models/prefs/GeneralPrefs.kt`), so prefs depend on types that live under `services/`.
- `:screensaver-compose` cannot reach the players as things stand: `VideoPlayerView`, `ImagePlayerView` and their helpers live in `:app`'s `ui/core`, and `:screensaver-compose` may not depend on `:app`. This is why `:playback` exists. `MetadataOverlay.render(...)` also takes a `VideoPlayerView`, so overlays are coupled to the player today.
- Overlay *content* is in `OverlayUiState`; overlay *configuration* (slot assignment, sizes, weights, fonts, colours, opacity, gradient flags and top/bottom alternation) lives in `OverlayHelper`, `SlotHelper`, `FontHelper` and `OverlayOpacityHelper`, all of which build `View`s. There is no UI-agnostic overlay-config model yet.
- `ProgressState` and `ProgressBarEvent` are declared inside `ui/controls/ProgressBarView.kt` (not in their own file), so making `OverlayUiState` UI-agnostic requires splitting that file.
- `:baselineprofile` is a `com.android.test` module with `targetProjectPath = ":app"` that declares all six `version` flavors itself; `:microbenchmark` is a plain library with no flavor dimension and no `:app` dependency. Neither needs `missingDimensionStrategy`, and a flavorless `:core` consumed by a flavored `:app` does not either.
- The catalog has no `androidx.lifecycle`/`androidx.savedstate` entries beyond `activity-ktx`; `lifecycle-runtime-ktx`, `lifecycle-viewmodel`, `lifecycle-viewmodel-compose`, `savedstate-ktx` and the Compose artifacts are all new entries.
- `InputHelper` is a key-event state machine (`isLongPress`, `repeatCount.rem(10)` hold-repeat, `ACTION_UP` suppression after a repeat) with two process-global fields, and it imports the concrete `ScreenController`.
- Manifest-declared components exist outside the activities, including `PackageReplacedReceiver` (in `data/preferences`, a package headed for `:core`) and `WallpaperProviderService` (`services/projectivy`, depends on `:projectivyapi`).
- No `proguard-rules.pro`; keep rules live in `app/src/main/keepRules/*.keep`.
- Spotless (ktlint) is declared in `:app` only and runs as part of `check`.
- CI runs only on `workflow_dispatch`.
- Locale: both hosts construct `ScreenController(LocaleHelper.alternateLocale(this, GeneralPrefs.localeScreensaver))`, so all string lookups use a locale-wrapped Context.
- Exit semantics: `InputHelper` distinguishes `EXIT_TO_SETTINGS` from `EXIT` via `exit(shouldExit: Boolean)`. `TestActivity.finishWithResult()` computes `shouldExitApp` from `startScreensaverOnLaunch` + `isExitToSettingSet()` and returns `RESULT_OK` with an `exit_app` extra that `MainActivity` reads.
- Process-global mutable state: `DreamActivity.activeHandoffDream`, `InputHelper.previousEvent`, `InputHelper.longPressEvent`.
- `runAsActivity`: the Philips OLED handoff starts `TestActivity` from `DreamActivity.onDreamingStarted` and keeps the dream alive underneath.
- `VideoPlayerView` extends Media3 `PlayerView` (SurfaceView by default). The portrait-rotation fix (`VideoPlayerView.kt` ~line 445) rotates/scales that surface and is explicitly scoped to SurfaceView with fit-without-cropping. Tunneling, a Philips Dolby Vision renderer, refresh-rate switching, and HDR handling are all SurfaceView-sensitive. Fades are on loading/overlay layers, not over the video.
- `FirebaseHelper`'s `LOGGING_END_DATE` (2026-11-01) has lapsed, so non-fatals now log unconditionally. Note it; don't fix it in this migration.

## Ground rules

1. **One phase per session.** Finish the phase, run the gate, write the stop report, then stop.
2. **One branch per phase**, named `compose-migration/phase-N-short-name`, with small, reviewable commits.
3. **Phases 1 to 6 are behavior-preserving.** No functional change, no bug fixes, no unrelated cleanup. Note bugs in the stop report; leave them alone.
4. **Do not rename packages or classes when moving files.** Move first, refactor never (in the same commit).
5. **Component name stability.** Anything referenced from the manifest or matched/stored by name (DreamService, NotificationListenerService, receivers, activities, shortcuts, intent actions, class-name strings in prefs or serialization) must keep its fully qualified class name. Module boundaries do not change FQNs; packages do. The real hazard is **relative `android:name` values in a library manifest**: `.services.NotificationService` in `:core`'s manifest resolves against `:core`'s namespace, not the applicationId, and silently kills existing grants. Rules:
   - Keep package names unchanged.
   - If a manifest-declared class moves to `:core`, move its manifest entry with it **and** use a fully-qualified `android:name`. (Alternatively leave the entry in `:app`'s manifest, also fully qualified.) Never use relative names in a library manifest.
   - Verify by diffing the **merged manifest** component list (names, exported flags, intent filters, permissions) before and after, per flavor, in every phase that moves a component.
6. **Gates:** see below. A phase is not finished unless its gate passes.
7. **Ask, don't guess.** If a requirement is ambiguous or the code contradicts the plan, stop and ask.
8. **Do not touch the settings UI** except where a phase explicitly says so.
9. **Single active UI instance.** At most one screensaver UI (legacy or Compose) may exist at a time, including the process-global state listed above. Any release path must also clear or correctly own that state.

## Gates

**Standard gate (every phase):** the commands in `AGENTS.md`: `testBetaDebugUnitTest`, `spotlessCheck`, `assembleBetaDebug`. New modules must be covered (Spotless/ktlint configured for them, tests included), or their files go unchecked.

**Full gate (Phases 1, 4, 7, 9, 10 and before any release):** assemble **all** flavors including `fdroid`, build an R8-minified release for at least one Firebase flavor and for `fdroid`, run `:baselineprofile`/`:microbenchmark` configuration, and verify the F-Droid variant pulls in no Firebase artifacts. CI is `workflow_dispatch` only, so run or trigger it explicitly and say so in the stop report.

**Device checks** are always labeled **human-verified**; an agent cannot claim them unless it actually ran on a device/emulator.

## Stop report template (end of every phase)

- What changed (files/modules, summarized, not a diff dump)
- Gate results (commands run and outcomes)
- Merged-manifest diff result (if components moved)
- Anything surprising or deferred (bugs found, risks, deviations)
- Recommended adjustments to later phases
- Confirmation that no behavior changed (phases 1 to 6)

---

## Phase 0: Confirm and fill gaps (read-only)

**Status: complete.** `docs/migration/AUDIT.md` was produced on 2026-11-10; it records the verified facts, the dependency closure, the 15-step move order, the manifest inventory, the Firebase/`BuildConfig` call sites and the baselines. The numbered items below are kept as the record of what it had to answer:

1. **Dependency-closure map** for the planned `:core` contents: for every package slated to move, list what it imports from packages that are not yet moved (e.g. prefs → weather unit enums), and derive a **move order in which `:core` compiles at every commit**.
2. Android-framework coupling in that closure (`Activity`, `Window`, `View`, `Context`-capturing singletons) that blocks extraction.
3. Every manifest-declared component, its package, and which phase moves it.
4. Every use of `FirebaseHelper` and `BuildConfig.*` in the closure.
5. What `:projectivyapi` / `WallpaperProviderService` require of `:core`.
6. Baselines: APK size per flavor, and (if a device/emulator is available) idle memory while the screensaver runs; otherwise mark "to be measured by the human".
7. Any place where the plan contradicts the code.

**Gate (passed):** AUDIT.md exists; the Standard gate ran on the untouched tree (`BUILD SUCCESSFUL`, 537 tests passed, 0 failed) and all six debug APK sizes are recorded. **STOP.**

## Phase 1: `:core` foundation and the three structural decisions

Create the module, then resolve the decisions that define `:core`'s whole dependency surface. **Stop for human confirmation on decisions 1 to 3 before moving any code.** Recommended defaults are given.

1. **Firebase / source-set swap (decision).** Recommended: define a small telemetry interface in `:core` (crash/non-fatal logging, performance traces, analytics calls used today), with the Firebase implementation and the no-op implementation supplied from `:app` per flavor via the existing `common`/`fdroid` source sets, injected at startup. `:core` has no Firebase dependency. The alternative (replicating the source-set swap inside `:core`) requires giving `:core` the `version` dimension and the same `sourceSets` wiring; only choose it if the interface approach proves unworkable. **The same seam serves `:playback`:** `VideoPlayerView`, `ImagePlayerView` and `ImagePlayerHelper` all report to Crashlytics (AUDIT.md section 4.1), so the interface must live in `:core`, and neither library may depend on Firebase directly.
2. **`BuildConfig` values (decision).** Recommended: `:core` reads them through an `AppConfig` abstraction initialized by `:app` (`OPEN_WEATHER`, `VERSION_CODE`, `VERSION_NAME`, `DEBUG`, `TEST_CONFIG`, `FLAVOR`, `BUILD_TYPE`), so secrets/`secrets.properties` plumbing and the `testConfig` property are **not** duplicated into the library. `VERSION_NAME`/`BUILD_TYPE` are needed by `LogcatCapture` and are easy to miss; `HttpLogging` reads `DEBUG` before the first HTTP client exists, so the seam is set at startup. Don't enable `buildConfig` in `:core` unless it truly needs its own constants.
3. **`:projectivyapi` (decision).** Recommended: `WallpaperProviderService` stays in `:app` (manifest entry unchanged) unless the Phase 0 closure shows `:core` code needs it; if `:core` must expose it, `:core` takes the `:projectivyapi` dependency and `buildFeatures.aidl` accordingly.

Then implement:
4. Create the `:core` Android library (distinct namespace, assumed `com.neilturner.aerialviews.core`; flag it if you want another), register it in `settings.gradle.kts`, add it to `:app`.
5. Keep `:core` **flavorless** (no `version` dimension). A flavorless library needs no `missingDimensionStrategy` when consumed by a flavored `:app`, and it needs only the default `debug`/`release` build types. Do not add flavor machinery to `:core`, `:baselineprofile` (which declares the six flavors itself) or `:microbenchmark` (which has no flavor dimension).
6. Add the catalog entries `:core` needs for the `ScreensaverHost` owner types (`androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.lifecycle:lifecycle-viewmodel`, `androidx.savedstate:savedstate-ktx`) now, so the contracts in Phase 6 are pure addition. Do not enable `buildConfig` in `:core`.
7. Add `consumerProguardFiles` for `:core` (the project has no `proguard-rules.pro`; existing keep rules live in `app/src/main/keepRules/*.keep`, so check which of those must be moved or duplicated as code moves).
8. Add a Spotless/ktlint block (matching `:app`'s configuration) for `:core`.
9. Implement the telemetry and `AppConfig` seams in `:app` and `:core` **without moving other code yet**, and have existing `:app` code go through them where trivial; otherwise defer to the phase that moves that code.
10. Do **not** touch `nonTransitiveRClass` or the version catalog structure beyond adding the module/dependency entries.

**Gate:** Full gate. F-Droid has no Firebase. **STOP.**

## Phase 2: Move the leaf closure (enums, music, weather units, four helpers)

`docs/migration/AUDIT.md` section 1.3 is authoritative; only true leaves move here.

1. Move `models/enums` and `models/music`.
2. Move `services/weather/TemperatureUnit.kt` and `WindSpeedUnit.kt` (pure enums) so `models/prefs` can follow in Phase 3. This temporarily splits one package across two modules; that is allowed and intended.
3. Move the four non-UI helpers that `data`, `providers`, `services` and `utils` depend on: `ui/helpers/DeviceHelper`, `PackageHelper`, `VolumeHelper`, `PermissionHelper`.
4. Move `services/Display` with `DeviceHelper`, dropping or relocating its `Activity` overload (only settings fragments pass an `Activity`).
5. Keep package names. Anything that does not move cleanly stays in `:app` and is listed in the stop report.
6. Do **not** move `models/videos` or `models` (they need `utils`), `models/prefs` (needs resources), or `utils` (needs the data layer). Those belong to Phases 3-4.

**Gate:** Standard gate. **STOP.**

## Phase 3: Resources, prefs, and the preference receiver

`utils` does **not** move here: it depends on `data/network` and `data/storage` (AUDIT.md section 1.2) and moves with the data layer in Phase 4.

1. Move the resources moved code needs into `:core` **before** the code: the `R.array.*` pref defaults, the provider `raw` JSON (`fireos8[_strings]`, `tvos26[_strings]`, `comm1[_strings]`, `comm2[_strings]`) and the provider/service strings used by Phase 4 code. Settings-only resources stay in `:app`; never duplicate a resource across both modules.
2. Change every moved file's `R` import to `:core`'s R. The earlier "imports only change if packages change" rule does not apply to `R`.
3. Move the Kotpref prefs layer. Preserve the initialization mechanism (the `kotpref-initializer` ContentProvider) and document it.
4. Replace `BuildConfig` reads in moved code with `AppConfig` (Phase 1 decision 2 and AUDIT.md section 4.2), including `VERSION_NAME`/`BUILD_TYPE`; keep behavior identical.
5. Move `PackageReplacedReceiver` and its migration code with a fully-qualified manifest entry and a clean merged-manifest diff (ground rule 5). If that proves invasive, leave the receiver and its three files in `:app` and say so.
6. Add or move consumer keep rules for moved code.

**Gate:** Standard gate, merged-manifest diff, plus an R8 release build of one Firebase flavor. Smoke test of screensaver start/stop (human-verified). **STOP.**

## Phase 4: Data, providers, services, and `:playback`

Follow the audit's commit order (AUDIT.md section 1.3, steps 5-14); every commit must compile.

1. **`data/network`** (needs prefs). Route `BuildConfig.DEBUG` in `HttpLogging` through `AppConfig`, which must be set before the first HTTP client is constructed.
2. **`utils` + `data/storage` in one commit.** They form a cycle (`MediaTypeResolver` -> `FileHelper`; `FileHelper` -> `utils` extensions). Split `utils/Extensions.kt` first: pure extensions go to `:core`, `View.delayOnLifecycle` and the `MultiSelectListPreference` extension stay in `:app`. `utils` also needs `NetworkHelper` (step 1) and `DeviceHelper` (Phase 2).
3. **`models/videos`, then `models`** (both need `utils`).
4. **`data/preferences`** (migration helpers; `PackageHelper` is already in `:core`).
5. **`data`** (Room DB, DAO, repository, entities). Apply the KSP plugin and Room compiler in `:core`, and decide the schema location: move `app/schemas` to `core/schemas` (commit the JSON) or point `:core`'s `room.schemaLocation` at the existing directory. Keep `fallbackToDestructiveMigration` behavior identical.
6. **`providers`** (all subpackages), now that data/models/prefs/utils are in `:core` and the `raw` JSON moved in Phase 3. Move their unit tests too, and apply the JUnit 5 plugin (`de.mannodermaus.android-junit5`) plus `useJUnitPlatform()` in `:core`, or the tests silently do not run.
7. **`services/weather`** (`WeatherService`; the unit enums already moved in Phase 2).
8. **Extract the audio functions first, then move `services`.** `services/MusicPlayer` imports `ui/core/VideoPlayerHelper` and uses exactly `buildAudioPlayer` and `createAudioMediaSource`; move those two into `:core` (for example `AudioPlayerFactory`) before moving `services`, or `:core` would depend on `:playback`. Then move the rest of `services` (philips, sonos, playback, music, Ktor, notification) through the telemetry interface.
9. **`:playback`**: create the module (flavorless, depends on `:core` only, Spotless block, consumer keep rules) and move `VideoPlayerView`, `ImagePlayerView`, `VideoPlayerHelper`, `ImagePlayerHelper`, `BackgroundBlurHelper`, `PlaybackDiagnosticsListener`. `:playback` consumes the telemetry seam too (AUDIT.md section 4.1). Package names are unchanged, so `MetadataOverlay`'s `VideoPlayerView` parameter still compiles and `:app` depends on `:playback`.
10. **Manifest components**: `NotificationService` and `WallpaperProviderService` follow ground rule 5. Keep `WallpaperProviderService` in `:app` per AUDIT.md section 5; moving it would pull `:projectivyapi` and AIDL into `:core` for no benefit. `TestConfigReceiver` also stays in `:app`: its action (`com.neilturner.aerialviews.testconfig.APPLY`) is referenced by the ADB commands in `docs/testing/README.md`, and it depends on `BuildConfig.TEST_CONFIG`.
11. Move the pure playback helpers (`PlaybackCallbacks`, `PlaybackTiming`, `PortraitVideoOrientation`, `ScheduledBlackoutWindow`, `MetadataResolver`, `TrackMetadataExtractor`, `VideoMetadataHandler`) into `:core` here or in Phase 5a: pick one and record it, do not move them twice.

**Phase 4 implementation notes (2026-11-10):** `PortraitVideoOrientation`, `TrackMetadataExtractor`, `VideoMetadataHandler`, `BitmapHelper` and `FastBlurCompat` moved to `:core` because the playback views need them; `ProgressState`/`ProgressBarEvent` were split out of `ProgressBarView.kt` into `:core` (and the view file was renamed `ProgressBar.kt`). `AudioPlayerFactory` in `:core` now supplies music playback. `services/projectivy` (`WallpaperProviderService`) and `TestConfigReceiver` stayed in `:app`; `PackageReplacedReceiver` moved with a fully-qualified `:core` manifest entry, and `NotificationService` stays declared in `:app` under its unchanged FQN. Room schemas moved to `core/schemas`.

**Gate:** Full gate, merged-manifest diff for every flavor, moved providers' unit tests pass (and actually execute under JUnit 5), `:app` assembles against `:playback`, and a smoke run of playback in the legacy screensaver (human-verified). **STOP.**

## Phase 5a: Extract the shared controller (no view changes)

This phase **finishes an extraction that is roughly 60% done**; it is not a greenfield state machine.

1. Seed from what exists: promote/extend `OverlayStateStore`, `OverlayUiState`, `OverlayEventBridge`, and the already-extracted pure logic (`PlaybackTiming`, `PlaybackCallbacks`, `ScheduledBlackoutWindow`, `PortraitVideoOrientation`, `MetadataResolver`, `TrackMetadataExtractor`, `VideoMetadataHandler`). **Do not build a parallel `ScreenState` that duplicates the overlay state model.** To make the state model UI-agnostic, split `ProgressState`/`ProgressBarEvent` out of `ui/controls/ProgressBarView.kt` into their own file in `:core`; this file split is an explicitly sanctioned exception to ground rule 4.
2. Extract the **overlay-configuration** model that does not exist yet: slot assignment, sizes, weights, fonts, colours, opacity, gradient flags and the top/bottom alternation currently built inside `OverlayHelper`, `SlotHelper`, `FontHelper` and `OverlayOpacityHelper`. Put it in `:core` as pure data plus resolution functions (prefs in, config out) so the legacy `OverlayHelper` and the future Compose overlays render the same layout from the same input. `OverlayUiState` is content only and is not a substitute.
3. Create a UI-agnostic controller in `:core` owning: playlist advance, item timers, music/now-playing, error policy, and blackout. It does **not** own fades or any animation.
4. Define an explicit **transition protocol** so renderers own animation timing but the controller owns decisions, for example: the renderer reports `onItemFinished()`/`onTransitionCompleted()`, and the controller answers with skip/loop/next and gating state (`canSkip`, `canShowOverlays` as controller state rather than animation-callback flags). Document the protocol; it is what the Compose renderer will implement later.
5. Dependencies come through constructors/interfaces; it must be unit-testable with fakes and a test dispatcher.
6. Idempotent construction; `release()` safe to call multiple times. The controller is **plain Kotlin, not an Android `ViewModel`** (see "ViewModels and owners"); the Compose module wraps it later.
7. If the code shows playlist/skip logic cannot be separated from animation callbacks without changing observable behavior, **stop and report exactly where**.

**Phase 5a implementation notes (2026-11-10):** The overlay state (`OverlayUiState`, `OverlayStateStore`, `OverlayEventBridge`), playback helpers (`PlaybackCallbacks`, `ScheduledBlackoutWindow`) and pure overlay helpers (`OverlayPaddingHelper`, `OverlayOpacityHelper`) moved to `:core` with their package names and FQNs unchanged; their tests moved with them. `BlackOutSource` moved to its own `:core` file so its FQN is still `com.neilturner.aerialviews.ui.core.BlackOutSource`, and `ScheduledBlackoutWindow` is now public because `:app` still uses it. `OverlayConfig.kt` adds a pure slot/typography/padding/opacity/gradient/alternation model and `OverlayConfigResolver` (raw preference values in, resolved layout out); it is not wired into the legacy `OverlayHelper` yet. `ScreensaverController` owns playlist navigation, cache-resume/sleep/scheduled-blackout timers, error policy, blackout and music state; players keep their existing finish timers and report completion through the documented transition protocol. New tests cover skip/loop/next, timers, both error paths, scheduled/user/sleep blackout, music, release and overlay alternation. The legacy `ScreenController` only lost the extracted enum; no controller adoption or fade changes were made. Gate passed: `./gradlew --no-daemon :core:testDebugUnitTest :app:testBetaDebugUnitTest :core:spotlessCheck :app:spotlessCheck :playback:spotlessCheck :app:assembleBetaDebug`. No device or human verification was performed for this phase.

**Gate:** Standard gate plus new unit tests covering playlist advance (skip/loop/next), timers, error policy, blackout, music state transitions. **Unit tests alone gate 5a; the legacy view is not changed in this phase.** **STOP.**

## Phase 5b: Move the legacy view onto the controller

This is the riskiest step: the fade/gate timing is today's state machine, so "no functional change" must be demonstrated, not assumed.

1. Make `ScreenController`'s view code a renderer of the shared controller's state, implementing the Phase 5a transition protocol with the existing fade animations. Do this **one feature at a time** (e.g. playlist advance, then skip handling, then music overlay, then blackout), each in its own commit.
2. Keep all device-specific workarounds in the legacy playback/rendering layer untouched; list where each ended up.
3. Keep `VideoPlayerView` finish timing semantics identical (document how the `postDelayed` behavior maps to the protocol).
4. Maintain `docs/migration/BEFORE_AFTER.md`: for each feature, the scenario, expected behavior, and **human-verified** before/after result on a device.
5. **Input:** extract `InputHelper`'s key-event state machine into a pure, unit-tested `KeyEventMapper` in `:core` (key event to action + press type, preserving `isLongPress`, `repeatCount.rem(10)` hold-repeat and `ACTION_UP` suppression) and have the legacy path consume it. This retires `InputHelper.previousEvent`/`longPressEvent` and leaves the Compose path one implementation to reuse rather than a rewrite by eye.

**Phase 5b implementation notes (2026-11-10):** `ScreenController` now observes `ScreensaverController.state` as its renderer state. The controller starts the playlist, advances/replays items, owns cache/sleep/scheduled-blackout/error timers, music policy and blackout state; `ScreenController` keeps the existing view construction, fades, metadata, weather, overlay animation and device workarounds. `LegacyMusicPlayback` adapts the existing `MusicPlayer` to the controller's `MusicPlayback` contract. Overlay reveal gating moved to controller state through `onOverlaysBusy()`/`onOverlaysReady()`. `InputHelper`'s key state machine moved to `KeyEventMapper` and `ButtonPressType` in `:core` (same package/FQN), with six unit tests. `docs/migration/BEFORE_AFTER.md` records the scenarios, finish-timer mapping and workarounds. The standard gate passed (497 `:core` tests, 67 `:app` tests). A `betaDebug` smoke launch on the attached Chromecast stayed alive with no fatal exception, but the required human before/after comparison has not been performed, so the Phase 5b gate remains open. No commit was created.

**Gate:** Standard gate plus the per-feature before/after table complete (human-verified). **STOP after each feature batch if the human asked for that; otherwise at the end.**

## Phase 6: Host/UI seam, opt-in flag, and parity contracts

1. In `:core`, define the host/UI interfaces. They must carry what the legacy path actually relies on, not just window/context:

   ```kotlin
   enum class ExitReason { EXIT, EXIT_TO_SETTINGS }

   interface ScreensaverHost {
       val context: Context        // already locale-wrapped (see below)
       val window: Window
       val lifecycleOwner: LifecycleOwner
       val savedStateRegistryOwner: SavedStateRegistryOwner
       val viewModelStoreOwner: ViewModelStoreOwner
       fun exit(reason: ExitReason)    // host adapter owns result contract / finish()
   }

   interface ScreensaverUi {
       fun attach(host: ScreensaverHost)
       fun onStart()
       fun onStop()
       fun onKeyEvent(event: KeyEvent): Boolean
       fun release()
   }
   ```

   (Adjust to what the code needs, but keep these contracts.)
2. **Locale:** the factory/host provides the Context wrapped by `LocaleHelper.alternateLocale(..., GeneralPrefs.localeScreensaver)` for **both** paths, so a Compose path cannot silently lose the screensaver-language pref.
3. **Exit semantics:** the UI only reports an `ExitReason`. The host adapter keeps the existing behavior: for `TestActivity`, `finishWithResult()` logic (`shouldExitApp` from `startScreensaverOnLaunch` + `isExitToSettingSet()`, `RESULT_OK` with the `exit_app` extra read by `MainActivity`); for `DreamActivity`, existing finish behavior. `InputHelper`'s exit handling stays equivalent.
4. **Static state:** the factory and `release()` must own/clear `DreamActivity.activeHandoffDream` per ground rule 9. If Phase 5b's `KeyEventMapper` has already retired `InputHelper.previousEvent`/`longPressEvent`, only the handoff reference remains; otherwise clear those too. Add a test or assertion that two UIs are never active at once.
5. **`runAsActivity` handoff:** the UI-path switch must sit **below** the Philips OLED handoff. `DreamActivity` still decides whether to hand off to `TestActivity`; the factory is invoked at the point that actually renders (the activity in the handoff case, the dream otherwise), so the dream and activity never both create a UI.
6. In `:app`: host adapters for `DreamActivity` (DreamService) and `TestActivity`, `LegacyScreensaverUi` wrapping existing behavior, and a `ScreensaverUiFactory` choosing by a new pref `useComposeScreensaver` (default **false**). `DreamActivity`/`TestActivity` stay in `:app` with unchanged FQNs and become thin delegates.
7. `TestActivity` accepts an intent extra (e.g. `ui_path=compose|legacy`) overriding the pref, for testing without touching the user-facing setting.
7a. **Owners for the dream host.** Introduce a `DreamServiceCompat` base class in `:app` (per "ViewModels and owners") and make `DreamActivity` extend it. `DreamActivity`'s FQN is unchanged; only its superclass chain changes. The class implements `LifecycleOwner`, `SavedStateRegistryOwner` and `ViewModelStoreOwner`, and nothing else:
    - `onCreate`: `performAttach()` (if not already done in an initializer), `savedStateRegistryController.performRestore(null)`, lifecycle to `CREATED`.
    - `onDreamingStarted`: lifecycle to `STARTED`. `onDreamingStopped`: lifecycle back to `CREATED`.
    - `onDestroy`: lifecycle to `DESTROYED` **and** `viewModelStore.clear()`, so `onCleared()` releases the controller and `DisposeOnViewTreeLifecycleDestroyed` can dispose the composition.
    - Fresh `ViewModelStore` per attach: `DreamActivity` documents that `onAttachedToWindow` can run again on the same instance with a live controller, so `onDetachedFromWindow` must clear the old store (or replace it) before a new UI is created.
    - **No Compose types in this class** (no `ComposeView`, no `@Composable`). It exposes owners; Phase 7 builds the view.
    - Behavior-preserving only if verified: document `DreamActivity`'s existing override order (`onAttachedToWindow`, `onDreamingStarted`, `onDreamingStopped`, `onDetachedFromWindow`) before the change and confirm the compat class neither reorders nor swallows them. `TestActivity` supplies the owners itself. Both host adapters implement the three owner members of `ScreensaverHost`.
8. Crashlytics custom key `ui_path` via the telemetry interface (no-op on F-Droid).
9. Fallback guard in the factory: if compose-path init throws, fall back to legacy and record a non-fatal. **Crash-loop guard:** a counter with a defined storage key (a Kotpref pref), incremented when a Compose session starts and **reset on a successful start** (first item displayed or N seconds stable) **and on app upgrade** (`VERSION_CODE` change). After N consecutive failures (default 3) the opt-in is auto-disabled. The auto-disable must not be permanent: re-enabling is user-initiated, and the reset rules above prevent one transient failure from pinning the user to legacy forever.
10. Legacy is the only implementation at this stage. Nothing user-visible changes.

**Gate:** Standard gate; legacy path behaves identically via both entry points (human-verified), including exit-to-settings, `startScreensaverOnLaunch` round trip, and the handoff path; intent override exercised. **STOP.**

## Phase 7: Scaffold `:screensaver-compose`

1. Create the module (depends on `:core` and `:playback`, never on `:app`; flavorless, so no flavor dimension and no matching fallbacks; no Firebase). Add a Spotless block and consumer keep rules.
2. Compose tooling is **not** module-local: add the Compose BOM and the `org.jetbrains.kotlin.plugin.compose` plugin to the version catalog and the root `build.gradle.kts` (which lists every plugin with `apply false`), then apply it in this module only. Also add the ViewModel interop the Compose UI needs: `androidx.lifecycle:lifecycle-viewmodel-compose` (for `viewModel()`/`LocalViewModelStoreOwner`), plus `lifecycle-runtime-compose` only if lifecycle-aware collectors are used. `:app` gains **no** Compose dependency in this phase.
3. Implement `ComposeScreensaverUi`:
   - Build the `ComposeView` here, not in the host: `ComposeView(host.context)` so the locale-wrapped `Context` from Phase 6 is used, then `setContentView` on the host's window. `DreamServiceCompat` deliberately has no `setContent` helper.
   - Set `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, and `ViewTreeViewModelStoreOwner` on the `ComposeView` from the owners the **host** provides (`DreamServiceCompat` for the dream, the activity for `TestActivity`); do not hand-roll owners here. Use `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, which only works because Phase 6 drives the dream lifecycle to `DESTROYED`. Saved state is nearly pointless in a `DreamService` (no `onSaveInstanceState`), so don't build features that depend on it.
   - Expose the shared controller through a thin `ViewModel` obtained from the host's `ViewModelStoreOwner` (see "ViewModels and owners"): no logic, no `viewModelScope`, `onCleared()` calls `controller.release()`.
   - Drive the lifecycle from host callbacks; dispose the composition and move to `DESTROYED` in `release()`; make `release()` idempotent. A second `attach` on the same host must dispose the previous composition before creating a new one.
4. Render a minimal placeholder driven by the shared controller to prove lifecycle, state flow, and disposal.
5. Wire into the factory (`:app` depends on `:screensaver-compose`).
6. Leak check: start/stop at least 20 cycles in both hosts; confirm no leaked compositions, lifecycle owners, or players (heap dump or LeakCanary in debug).

**Gate:** Full gate; the pref and the intent override show the placeholder; leak check (human-verified) passes; legacy path unaffected. **STOP.**

## Phase 8: Build out the Compose UI (parity)

Maintain `docs/migration/PARITY.md`, one row per feature (not started / in progress / parity / verified on device), seeded from the overlay and workaround lists in AUDIT.md. Include locale, exit semantics (both exit reasons and the `TestActivity` result contract), key/touch input, the handoff path, and `TestActivity`'s phone-only `onPause` stop (`!DeviceHelper.isTV(...)` → stop + `finishWithResult()`), which is easy to lose behind a factory. These are parity rows, not just visuals.

Work in this order, committing per item:

1. **Playback layer (video and images):** wrap `:playback`'s existing `VideoPlayerView` (a Media3 `PlayerView`, SurfaceView by default) and the existing image view/loading path (`ImagePlayerHelper`) in `AndroidView` and **keep them as the default**. Neither player is migrated or rewritten in this plan; only their hosting changes. Do not reimplement playback or its workarounds (portrait rotation/scale fix, tunneling, Philips Dolby Vision renderer, refresh-rate switching, HDR handling). Do **not** switch to TextureView: those workarounds are SurfaceView-sensitive, and nothing animated is composited over the video (fades are on loading/overlay layers). If a Compose requirement appears to need animated alpha/transforms over the video surface, stop and ask. The legacy `MetadataOverlay`'s `VideoPlayerView` parameter is legacy-only: Compose overlays take POI data from the shared state, so the player does not leak into the new overlay composables.
2. **Item transitions**, implementing the Phase 5a transition protocol on the Compose side.
3. **Overlays**, one at a time, as pure state-to-UI composables with `@Preview`s, fed by the shared overlay state.
4. **Input handling:** key and touch events and exit behavior, with parity for both hosts.
5. **Edge cases:** errors, empty playlists, provider failures, no network, music start/stop, scheduled blackout.

Performance rules: avoid per-frame recomposition, use `graphicsLayer` for animated properties, read state as late as possible, keep the tree small.

Screenshot tests would be **new infrastructure**, not parity work: ask the human first, and keep them out of the parity gate.

**Gate (per batch):** Standard gate; PARITY.md updated; "verified" only if checked on a real device (human-verified). **STOP after each logical batch** (playback layer; transitions; overlays; input; edge cases).

## Phase 9: Opt-in beta

1. Add an "experimental" toggle in settings (in `:app`) bound to `useComposeScreensaver`, with clear preview wording.
2. Ensure **both** preview/test paths use the chosen implementation, including `MainActivity`'s `startScreensaverOnLaunch` / `exit_app` round trip with `TestActivity`.
3. Verify the `ui_path` key is on crash reports and non-fatals, and demonstrate the fallback and crash-loop guard (simulate failures, including the reset rules).
4. Measure on the lowest-RAM supported devices (memory, cold start, dropped frames/jank) versus the Phase 0 baselines; record in `docs/migration/PERF.md`. Phase 0 captured APK sizes and an empty-state PSS only (no providers configured on the device), so a **configured-session idle-playback baseline** must be captured before this comparison; the empty-state figure is not a substitute.
5. Draft release notes and a feedback prompt.

**Gate:** Full gate; guard behavior demonstrated; perf comparison reviewed by the human. **STOP.**

## Phase 10: Stabilize, flip default, retire legacy

Do not start without explicit human approval based on crash rates and beta feedback.

1. Flip the default of `useComposeScreensaver` to true. Keep the legacy path and the toggle for one or more release cycles.
2. Only on explicit approval after a stable period: delete the legacy UI and `ScreenController`'s rendering layer, the factory and fallback, the pref and toggle, and unused resources. **Keep `:playback` and the shared controller**: they are the surviving implementation, not legacy. `DreamActivity`/`TestActivity` use the Compose UI directly (FQNs unchanged).
3. Re-run the Full gate, including an R8 release build and a leak check.

---

## Known risks to watch throughout

| Risk | Mitigation |
|---|---|
| `:core` pulls in Firebase and defeats the F-Droid build | Telemetry interface; F-Droid dependency check in the Full gate (Phases 1, 4, 7, 9, 10) |
| `:core` won't compile mid-move (prefs → services enums) | Phase 0 closure map; leaf-first ordering; every commit compiles |
| Move order breaks on package cycles (prefs ↔ weather, utils ↔ storage, services → ui/core) | AUDIT.md section 1.3 order; one-commit pairs; audio extraction before `services` |
| Library manifest silently changes component names | FQN rule, fully-qualified `android:name`, merged-manifest diff per flavor |
| Phase 5 is a rewrite disguised as a refactor | Split 5a (unit-test-gated controller) / 5b (device-verified view adoption); transition protocol; per-feature before/after table |
| Duplicated state model next to `OverlayStateStore` | Seed from and extend the existing overlay state |
| Compose path loses locale, exit result, or handoff behavior | Locale-wrapped Context, `ExitReason` + host-owned result contract, factory below the `runAsActivity` handoff (Phase 6); parity rows (Phase 8) |
| Process-global state (`activeHandoffDream`, `InputHelper` events) with two UI paths | Ground rule 9; single-instance assertion; release owns/clears it |
| Compose leaks or duplicate players in DreamService | Idempotent init/release, host-provided owners via `DreamServiceCompat`, `ViewModelStore` cleared on stop, 20-cycle leak check (Phases 5a, 6, 7) |
| `DreamServiceCompat` changes `DreamActivity` lifecycle behavior | Document existing override order first; verify before/after (Phase 6, step 7a); FQN unchanged |
| Device-specific video workarounds drift or break | Keep `PlayerView`-in-`AndroidView` on SurfaceView; no TextureView switch; no playback reimplementation |
| Low-RAM devices regress | Measure early against Phase 0 baselines (Phases 7, 9) |
| Crash-loop guard pins users to legacy forever | Reset on success and on app upgrade; user-initiated re-enable |
| New modules' files unchecked or mis-built | Spotless block per module; keep `:core`/`:playback`/`:screensaver-compose` flavorless; consumer keep rules |
| CI doesn't catch regressions (workflow_dispatch only) | Run/trigger the Full gate explicitly at the listed phases |
| `:screensaver-compose` cannot reach the players (they live in `:app`) | Shared `:playback` module; Compose overlays take POI data from state, not a `VideoPlayerView` |
| `DreamServiceCompat` copied verbatim: composition never disposed, ViewModels never cleared | `onDestroy` → `DESTROYED`; store per dream session and cleared; 20-cycle leak check (Phases 6, 7) |
| Compose UI types leak into `:app` via the compat class | `DreamServiceCompat` exposes owners only; `ComposeView` is built in `:screensaver-compose` |
| Overlay layout reimplemented in Compose | Overlay-config model extracted in Phase 5a; both renderers consume it |