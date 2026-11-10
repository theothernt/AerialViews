# Phase 0 audit: `:core` extraction readiness

Read-only audit for the Compose screensaver migration (plan v3). No source changes were made; this file is the only addition. Everything below was measured against the current tree on 2026-11-10 and supersedes the generic assumptions in the plan where the two disagree.

## Gate result (untouched tree)

```
./gradlew --no-daemon :app:testBetaDebugUnitTest :app:spotlessCheck :app:assembleBetaDebug
```

- `BUILD SUCCESSFUL in 2m 26s`, exit 0.
- 537 unit tests passed, 0 failed.
- `:app:spotlessCheck` UP-TO-DATE (`spotlessKotlin`/`spotlessMisc` FROM-CACHE).
- `:app:assembleBetaDebug` produced `app/build/outputs/apk/beta/debug/app-beta-debug.apk`.

This is the Standard gate from `AGENTS.md`, and it is the gate Phase 0 is judged on.

---

## 1. Dependency closure and the move order

### 1.1 Package edges (verified by import scan)

"Depends on" means an import of another `com.neilturner.aerialviews.*` package from that directory. Subpackages are listed separately because they differ.

| Package | Depends on |
|---|---|
| `models/enums` | none |
| `models/music` | `models/enums` |
| `models/videos` | `models/enums`, `utils` |
| `models` (top level: `MediaPlaylist`, `MediaFetchResult`, `TimeOfDayFilter`, `OverlayIds`, `SlotPref`) | `models/enums`, `models/music`, `models/videos`, `utils`, `R` |
| `models/prefs` | `models/enums`, `services/weather` (unit enums), `R` |
| `utils` | `models/enums`, `data/network`, `data/storage`, `ui/helpers`, `BuildConfig` |
| `data/network` | `models/prefs`, `BuildConfig` |
| `data/storage` | `utils`, `ui/helpers` |
| `data/preferences` | `models/enums`, `utils`, `ui/helpers`, `BuildConfig` |
| `data` (top level: DB, repository, entities) | `models.*`, `models/prefs`, `utils`, `BuildConfig` |
| `providers` (and subpackages) | `data/network`, `data/storage`, `models.*`, `models/prefs`, `utils`, `R` |
| `services/weather` | `models/prefs`, `data/network`, `utils`, `BuildConfig`, `R` |
| `services` (top level) | `data`, `models.*`, `providers.*`, `services.*`, `ui/core`, `ui/helpers`, `utils`, `R`, `BuildConfig` |
| `services/projectivy` | `models/enums`, `models/prefs`, `providers.*`, `:projectivyapi` |
| `services/sonos` | `services/MusicEvent` |
| `services/philips` | `data/network` |
| `ui/overlays/state` | `models/enums`, service event types (`MessageEvent`, `MusicEvent`, weather events), `ui/controls/ProgressState` |
| `ui/core` | all of the above plus `databinding.*`, `ui/controls`, `ui/helpers`, `ui/overlays` |

### 1.2 Cycles and inversions that block the plan's Phase 2 -> 3 -> 4 order

Four edges break a naive leaf-first order. They are not stylistic: each one means `:core` cannot compile if the plan's phase boundary is taken literally.

1. **`models/prefs` <-> `services/weather`.** `GeneralPrefs` imports `services.weather.TemperatureUnit` and `WindSpeedUnit`; `WeatherService` (same package) imports `models.prefs`. Break: move only `services/weather/TemperatureUnit.kt` and `WindSpeedUnit.kt` in the leaf commit. Both are pure enums with no imports; the package then exists in two modules temporarily, which Kotlin permits, and the move order stays legal.
2. **`utils` <-> `data/storage`.** `utils/MediaTypeResolver` imports `data.storage.FileHelper`; `data/storage/FileHelper` imports `utils.filenameWithoutExtension` and `utils.toStringOrEmpty`. Break: move `utils` and `data/storage` in the **same commit**.
3. **`utils` -> `data/network`.** `utils/LogcatCapture` imports `data.network.NetworkHelper`. So `data/network` must move before `utils`, and `data/network` itself needs `models/prefs` first. This is why Phase 3 cannot move `utils` ahead of the data layer.
4. **`services` -> `ui/core`.** `services/MusicPlayer` imports `ui.core.VideoPlayerHelper` and calls exactly two functions: `buildAudioPlayer(...)` and `createAudioMediaSource(...)`. If `services` moves to `:core` while `VideoPlayerHelper` stays with the views, `:core` would depend on `:playback`. Break: extract those two audio functions into `:core` (for example `AudioPlayerFactory`) **before** Phase 4's services batch. `VideoPlayerHelper` keeps the video-only logic and moves to `:playback` later.

### 1.3 Move order in which `:core` compiles at every commit

Commit-sized steps. `:app` builds after each because it depends on `:core` and its imports do not change (packages are preserved).

| Step | Move to `:core` | Why it compiles |
|---|---|---|
| 0 | Telemetry and `AppConfig` seams (no code moves) | Phase 1 decisions; unblocks `BuildConfig`/`FirebaseHelper` reads |
| 1 | `models/enums`, `models/music`, `services/weather/TemperatureUnit.kt`, `services/weather/WindSpeedUnit.kt` | pure leaves |
| 2 | `ui/helpers/DeviceHelper`, `PackageHelper`, `VolumeHelper`, `PermissionHelper`; `services/Display` | `DeviceHelper` needs `Display`; no internal deps beyond `models/enums` |
| 3 | Resources used by moved code: pref default arrays, provider `raw` JSON, provider/service strings | `R` must exist in `:core` before step 4 |
| 4 | `models/prefs` | needs enums, weather units, arrays |
| 5 | `data/network` | needs `models/prefs` only |
| 6 | `utils` **and** `data/storage` (one commit; split `utils/Extensions.kt`, see 2.1) | mutual edge; needs `NetworkHelper` + `FileHelper` |
| 7 | `models/videos`, then `models` (top level) | need `utils` extensions |
| 8 | `data/preferences` (+ `PackageReplacedReceiver` manifest entry) | needs enums, helpers, utils |
| 9 | `data` (Room DB, DAO, repository, entities) + KSP wiring | needs models, prefs, helpers, utils |
| 10 | `providers` (all subpackages) | needs data/models/prefs/utils; `raw` JSON moved in step 3 |
| 11 | `services/weather` (`WeatherService.kt`; enums already moved) | needs prefs + `data/network` |
| 12 | `services` remaining (philips, sonos, playback, music, Ktor, notification), after step 4 of 1.2 | needs providers; `ui/core` edge removed first |
| 13 | `ui/overlays/state` + `ui/core` pure helpers (Phase 5a) | needs service event types; `ProgressState` split first |
| 14 | `:playback`: `VideoPlayerView`, `ImagePlayerView`, `VideoPlayerHelper`, `ImagePlayerHelper`, `BackgroundBlurHelper`, `PlaybackDiagnosticsListener` | Views + Media3; needs providers/providers' `DataSourceFactory` |
| 15 | `ScreenController`'s view layer stays in `:app` as the legacy renderer | consumes `:core` + `:playback` |

### 1.4 What this does to Phases 2-4

- **Phase 2** can only move steps 1-2. `models/prefs` cannot move until the resources exist, and `models/videos`/`models` cannot move until `utils` does.
- **Phase 3** is "prefs + resources + the four `ui/helpers` files", not "prefs and utils". `utils` moves in Phase 4.
- **Phase 4** absorbs `data/network`, `utils` + `data/storage`, `models/videos` + `models`, then the rest. It is materially larger than written; split it into at least the 5 commits implied by steps 5-12.

---

## 2. Android-framework coupling in the closure

Only a handful of files in the planned `:core` set touch UI types. The rest of `models`/`data`/`providers`/`services` uses only `android.content`/`os`/`net`, which is fine for a library.

| File | Coupling | Action |
|---|---|---|
| `utils/Extensions.kt` | `android.view.View` (`View.delayOnLifecycle`), `androidx.preference.MultiSelectListPreference` | **Split.** Pure extensions move to `:core`; the View and Preference extensions stay in `:app`. This is a file split, like `ProgressState` in Phase 5a. |
| `services/Display.kt` | `android.app.Activity` overload, `WindowManager` | Move to `:core` and drop/relocate the `Activity` overload (only settings fragments pass an `Activity`). |
| `services/projectivy/WallpaperProviderService.kt` | `android.app.Service` | Keep in `:app` (see section 5). |
| `ui/core/ScreenController.kt`, `ImagePlayerView.kt`, `BackgroundBlurHelper.kt` | `View`, `Window`-adjacent, databinding | Stay out of `:core`; views go to `:playback` or remain in `:app`. |
| `ui/helpers/{DeviceHelper,PackageHelper,VolumeHelper,PermissionHelper}` | `android.app.UiModeManager`, `PackageManager`, `AudioManager`, permissions | Safe for `:core`; no View imports. |

No `Window` reference exists outside the screensaver view layer. The plan's rule is achievable once `Extensions.kt` is split and `Display` loses its `Activity` overload.

---

## 3. Manifest-declared components

| FQN | Type | Phase | Notes |
|---|---|---|---|
| `com.neilturner.aerialviews.ui.MainActivity` | Activity | none | Launcher + `.avsettings` handler. Also named in the Projectivy `mainActivity` meta-data; never move. |
| `com.neilturner.aerialviews.ui.screensaver.TestActivity` | Activity | 6 | Exported test entry point; becomes a thin delegate. FQN must not change. |
| `com.neilturner.aerialviews.ui.screensaver.DreamActivity` | DreamService | 6 | `BIND_DREAM_SERVICE`, exported. Becomes `DreamServiceCompat` subclass; FQN unchanged. |
| `com.neilturner.aerialviews.data.preferences.PackageReplacedReceiver` | BroadcastReceiver | 3 | `MY_PACKAGE_REPLACED` migration hook. Moves with the prefs layer and needs a fully-qualified library manifest entry. |
| `com.neilturner.aerialviews.services.NotificationService` | NotificationListenerService | 4 | The user's notification-access grant is stored against this FQN. Fully-qualified manifest entry only. |
| `com.neilturner.aerialviews.services.projectivy.WallpaperProviderService` | Service | 4 (or stays) | Projectivy plugin; `mainActivity` meta-data points at `MainActivity`. Moving it to `:core` pulls in `:projectivyapi` + AIDL. |
| `com.neilturner.aerialviews.testconfig.TestConfigReceiver` | BroadcastReceiver | **stays in `:app`** | Not mentioned in the plan. Test-only (`-PtestConfig=true`); its action string `com.neilturner.aerialviews.testconfig.APPLY` is hardcoded in `docs/testing/README.md` and ADB commands, so both FQN and action must stay. |
| `com.neilturner.aerialviews.ui.AerialApp` | Application | none | Stays in `:app`; it is the composition root and reads prefs at startup. |

---

## 4. Firebase and `BuildConfig` usage in the closure

### 4.1 `FirebaseHelper` call sites

| File | Destination after migration |
|---|---|
| `services/MediaService.kt` | `:core` |
| `services/MediaServiceHelper.kt` | `:core` |
| `services/MusicPlayer.kt` | `:core` |
| `services/weather/WeatherService.kt` | `:core` |
| `ui/core/ImagePlayerHelper.kt` | `:playback` |
| `ui/core/ImagePlayerView.kt` | `:playback` |
| `ui/core/VideoPlayerView.kt` | `:playback` |

Consequence: the telemetry seam must be visible to **both** `:core` and `:playback`. Plan v3 only says `:core`; `:playback` needs the same interface (or must not depend on telemetry, which would mean dropping existing crash reporting from the players).

### 4.2 `BuildConfig` call sites

| File | Fields used | `AppConfig` field needed |
|---|---|---|
| `data/network/HttpLogging.kt` | `DEBUG` | `debug` |
| `data/preferences/MigrationHelper.kt` | `VERSION_CODE` | `versionCode` |
| `services/weather/WeatherService.kt` | `OPEN_WEATHER` (x3) | `openWeatherKey` |
| `utils/DreamTiming.kt` | `DEBUG`, `TEST_CONFIG`, `FLAVOR` | `debug`, `testConfig`, `flavor` |
| `utils/LogcatCapture.kt` | `VERSION_NAME`, `VERSION_CODE`, `FLAVOR`, `BUILD_TYPE` | `versionName`, `versionCode`, `flavor`, `buildType` |
| `ui/core/ImagePlayerHelper.kt` | `DEBUG` | `debug` |

`AppConfig` therefore needs `versionName` and `buildType` as well as the fields listed in the plan. `HttpLogging` reads `DEBUG` in an object initializer; the seam must be set before the first network client is constructed.

---

## 5. `:projectivyapi` and `WallpaperProviderService`

`WallpaperProviderService` depends on:

- `models.enums.AerialMediaType`
- five Projectivy prefs objects (`ProjectivyPrefs`, `ProjectivyApplePrefs`, `ProjectivyAmazonPrefs`, `ProjectivyComm1Prefs`, `ProjectivyComm2Prefs`)
- `providers.{Amazon,Apple,Comm1,Comm2,Local}MediaProvider`, `MediaProvider`, `ProviderFetchResult`
- `tv.projectivy.plugin.wallpaperprovider.api.*` (AIDL: `IWallpaperProviderService`, `Event`, `Wallpaper`, `WallpaperType`, `WallpaperDisplayMode`)

It needs **nothing** from `:core` that `:app` does not already get from `:app -> :core`. Recommendation from Phase 1 decision 3 holds: leave it in `:app` with its manifest entry unchanged. Moving it to `:core` would require `:core` to depend on `:projectivyapi` and enable `buildFeatures.aidl`, for no benefit.

---

## 6. Baselines

All six debug APKs were built on 2026-11-10 (`:app:assemble<Flavor>Debug`, `BUILD SUCCESSFUL in 3m 23s`, exit 0).

| Flavor (debug) | Size |
|---|---|
| `beta` | 27,359,812 bytes (26.09 MiB) |
| `googleplaybeta` | 27,359,812 bytes (26.09 MiB) |
| `github` | 27,359,804 bytes (26.09 MiB) |
| `googleplay` | 27,359,804 bytes (26.09 MiB) |
| `amazon` | 27,359,804 bytes (26.09 MiB) |
| `fdroid` | 24,469,817 bytes (23.34 MiB) |

The five Firebase flavors are within 8 bytes of each other; `fdroid` is 2.75 MiB smaller, which is the Firebase exclusion working as intended. Release APKs already on disk from earlier builds (not produced by this audit) for reference: `beta` 9,267,408; `github` 9,248,524; `googleplay` 9,346,531; `fdroid` 8,219,078 bytes.

Device attached: Google Chromecast, Android 14 (`192.168.1.248:5555`).

| Item | Value |
|---|---|
| Empty-state memory | 89,192 KB total PSS, measured 25 s after `TestActivity` started, with an **empty playlist** (`total_sources=0`, no providers configured on that device). This is a cold-start/empty-state figure, **not** an idle-playback baseline. |
| Idle memory while the screensaver runs | **Not measured.** Needs a device with at least one provider configured; to be measured by the human. |
| Cold start / jank | **Not measured.** Required for Phase 9's comparison. |

The 537 passing tests are the behavioural baseline for Phases 1-6.

---

## 7. Where the plan contradicts or under-specifies the code

1. **Phase 3 cannot move `utils`.** `utils` needs `data/network`, `data/storage`, `ui/helpers` and `BuildConfig`, and has a two-way edge with `data/storage`. Move it with `data/storage` in Phase 4 (steps 5-6 above).
2. **Phase 2 cannot move `models` or `models/videos`.** Both depend on `utils`, which moves later. Only `models/enums`, `models/music` and the two weather enums are true leaves.
3. **`ui/helpers` is missing from the `:core` plan.** Four files must move or nothing in `data`/`providers`/`services` compiles: `DeviceHelper`, `PackageHelper`, `VolumeHelper`, `PermissionHelper`. They are not Views.
4. **`MusicPlayer -> ui/core/VideoPlayerHelper` is a `:core -> :playback` inversion.** Extract the two audio functions before Phase 4's services batch.
5. **`:playback` needs the telemetry seam too** (`VideoPlayerView`, `ImagePlayerView`, `ImagePlayerHelper` all report to Crashlytics).
6. **`AppConfig` needs `VERSION_NAME` and `BUILD_TYPE`**, not just the five fields the plan lists.
7. **Room/KSP moves are unplanned.** `data/AerialDatabase` uses Room 3 + KSP with `room.schemaLocation = $projectDir/schemas` in `:app`. Moving it requires the KSP plugin and Room compiler in `:core`, plus a decision to move `app/schemas` to `core/schemas` (or point `:core` at the existing directory). Commit the schema JSON either way.
8. **Resources must move earlier than Phase 3 implies.** `models/prefs` reads nine-plus `R.array.*` defaults; `providers` reads `R.raw.{fireos8,tvos26,comm1,comm2}[_strings]`; `services` reads `R.string.*`. `R` imports in moved files must point at `:core`'s namespace; the plan's "update imports only if the package changed" wording is wrong for `R`.
9. **No Plex provider exists.** Plan v3 still lists "Plex". The real providers are Amazon, Apple, Comm1, Comm2, Local, custom feed, Immich, Nextcloud Memories, Samba, WebDAV.
10. **`utils/Extensions.kt` cannot move whole.** It contains `View.delayOnLifecycle` and a `MultiSelectListPreference` extension; split it as the plan split `ProgressState`.
11. **`services/Display.kt` imports `Activity`.** Drop or relocate the `Activity` overload before `:core` can satisfy its own rule.
12. **`TestConfigReceiver` is a manifest component the plan never mentions.** Keep it in `:app`; its FQN and action are referenced by ADB commands in `docs/testing/README.md`.
13. **The plan's Phase 4 "move tests alongside moved code" needs a KSP/JUnit caveat.** Unit tests use JUnit 5 (`de.mannodermaus.android-junit5`); each new module that hosts tests must apply the same plugin and `useJUnitPlatform()`, or its tests silently do not run.

---

## 8. Recommended adjustments to the plan

- Replace Phases 2-4 with the 15 steps in 1.3, grouped as: Phase 2 = steps 1-2; Phase 3 = step 3-4; Phase 4 = steps 5-12 (split into at least five commits); Phase 5a = step 13; `:playback` creation = step 14.
- Add the four `ui/helpers` files and `services/Display` to the `:core` inventory.
- Add the audio-function extraction (`buildAudioPlayer`/`createAudioMediaSource`) as the first item of Phase 4's services batch.
- Add `:playback` to the telemetry seam and to the `AppConfig` consumers.
- Add KSP/Room/schema handling to Phase 4 step 9.
- Phase 9's memory comparison needs a configured device session; the empty-state PSS in section 6 is not a substitute for it.
- **Status:** every item above, and every item in section 7, is applied in `docs/MIGRATION_PLAN.md` v4.
