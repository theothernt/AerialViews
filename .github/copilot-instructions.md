# Aerial Views - AI Coding Agent Instructions

## Project Overview
Aerial Views is an Android screensaver app for TV devices (Android TV, Google TV, Fire TV, Nvidia Shield) that displays beautiful videos and photos. It's inspired by Apple TV's screensavers and supports 4K HDR content from multiple sources.

**Key characteristics:**
- **Target platform:** Android TV (API 22+, targeting API 36) with Leanback UI
- **Language:** Kotlin 2.2.21 with JVM target 21
- **Build system:** Gradle with Kotlin DSL
- **Architecture:** Provider-based media fetching, coroutine-driven async operations, FlowBus for event communication

## Architecture Overview

### Multi-Flavor Build System
The project uses **product flavors** to target different app stores with conditional dependencies:
- **Flavors:** `github`, `beta`, `googleplay`, `googleplaybeta`, `amazon`, `fdroid`
- **Build types:** `debug`, `release`
- **Flavor dimension:** `version`

**Critical pattern:** Source sets share common code via `src/common/java` (contains Firebase implementations for all non-F-Droid flavors), while `src/fdroid/java` provides no-op stubs for Firebase APIs. This is configured in `app/build.gradle.kts`:

```kotlin
sourceSets {
    getByName("github").java.srcDir("src/common/java")
    getByName("fdroid").java.srcDir("src/fdroid/java")
}
```

When adding Firebase/proprietary code, implement in `src/common/java` with matching no-op interface in `src/fdroid/java`.

### Core Components

#### 1. Provider Pattern (`app/src/main/java/com/neilturner/aerialviews/providers/`)
All media sources implement the `MediaProvider` abstract class:
- **Local providers:** `LocalMediaProvider` (USB/internal storage), `SambaMediaProvider` (SMB shares), `WebDavMediaProvider`
- **Remote providers:** `AppleMediaProvider`, `AmazonMediaProvider`, `Comm1MediaProvider`, `Comm2MediaProvider`, `ImmichMediaProvider`, `CustomFeedProvider`
- Each provider implements `fetchMedia()`, `fetchTest()`, and `fetchMetadata()`
- Providers are sorted by type (LOCAL first, then REMOTE) in `MediaService.init()`

#### 2. Preferences System (Kotpref)
**All app settings use Kotpref library**, NOT standard Android SharedPreferences:
- **Main settings:** `GeneralPrefs` object in `models/prefs/GeneralPrefs.kt`
- **Provider settings:** Individual objects like `AppleVideoPrefs`, `LocalMediaPrefs`, etc.
- **Convention:** All Kotpref objects use `kotprefName = "${context.packageName}_preferences"`
- **Property delegates:** Use `by booleanPref()`, `by stringPref()`, `by nullableEnumValuePref()`, etc.

**Example pattern:**
```kotlin
object GeneralPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"
    var clockFormat by nullableEnumValuePref(ClockType.DEFAULT, "clock_format")
    var removeDuplicates by booleanPref(false, "remove_duplicates")
}
```

To add new settings: Create property in appropriate Prefs object, add UI in corresponding XML under `app/src/main/res/xml/settings_*.xml`.

#### 3. Screensaver Entry Points
- **DreamService:** `ui/screensaver/DreamActivity.kt` - System-launched screensaver
- **Test Activity:** `ui/screensaver/TestActivity.kt` - User-testable screensaver from settings
- Both delegate to `ScreenController` for playback logic

#### 4. Event Communication (FlowBus)
**Global event bus pattern** for cross-component communication:
- Services post events: `GlobalBus.post(WeatherUpdateEvent(...))`
- Overlays subscribe: `private val eventsReceiver = subscribe<WeatherUpdateEvent> { ... }`
- **Common events:** `MessageEvent`, `WeatherUpdateEvent`, `ProgressBarEvent`, `MusicEvent`
- Preferred over direct coupling between services and UI components

#### 5. Media Playback (`ui/core/`)
- **ScreenController:** Orchestrates media playback, overlay management, and user input
- **VideoPlayerView:** ExoPlayer wrapper for video playback
- **ImagePlayerView:** Coil-based photo display with Ken Burns effect
- Uses coroutines with `CoroutineScope(Dispatchers.Main)` for lifecycle management

## Development Workflows

### Building the App
```powershell
# Build debug APK (beta flavor is default)
.\gradlew assembleBetaDebug

# Build all flavors
.\gradlew assemble

# Install debug build to connected device
.\gradlew installBetaDebug

# Run tests
.\gradlew testBetaDebug
```

**Note:** Uses `libs.versions.toml` for centralized dependency management. Version bumps happen there, not in build.gradle files.

### Testing Strategy
- **Unit tests:** JUnit Jupiter (JUnit 5) in `app/src/test/` - see `TrackNameShortenerTest.kt` for examples
- **Test configuration:** Uses `useJUnitPlatform()` in Gradle (configured in `app/build.gradle.kts`)
- **Benchmarking:** Macrobenchmark module in `microbenchmark/` using AndroidX Benchmark library
- **Baseline profiles:** `baselineprofile/` module generates startup optimization profiles

### Debugging on Android TV
The app targets TV devices, which affects development:
- **Leanback library:** Use `PreferenceFragmentCompat` + `LeanbackPreferenceFragmentCompat` for settings UI
- **D-Pad navigation:** All UI must be navigable via remote control
- **No touch input assumption:** Phone/tablet support is secondary (though gestures are implemented)
- **Testing on TV:** Use ADB commands to set as screensaver (see README.md for full instructions)

## Project-Specific Conventions

### Code Style
- **Linting:** Kotlinter Gradle plugin enforces ktlint style
- **Logging:** Use Timber, not `println` or `Log.d()`: `Timber.i("Message")`, `Timber.e(exception, "Error")`
- **Suppression:** Common suppressions in file headers: `@file:Suppress("PackageDirectoryMismatch")` for flavor-specific files

### Naming Patterns
- **Preferences:** End with `Prefs` (e.g., `GeneralPrefs`, `AppleVideoPrefs`)
- **Providers:** End with `Provider` or `MediaProvider`
- **Events:** End with `Event` (e.g., `ProgressBarEvent`, `MessageEvent`)
- **Helpers:** End with `Helper` (e.g., `DialogHelper`, `OverlayHelper`)
- **Services:** End with `Service` (e.g., `MediaService`, `WeatherService`)

### Resource Conventions
- **Preferences XML:** Located in `res/xml/`, prefixed by category (`settings_*.xml`, `sources_*.xml`)
- **String resources:** Organized by feature in `res/values/strings.xml`
- **Arrays:** Video quality, scene types, time-of-day filters in `res/values/arrays.xml`

### Async Operations
- **Preference:** Use coroutines with `suspend` functions
- **IO operations:** Always use `withContext(Dispatchers.IO)` - see `MediaService.fetchMedia()`
- **Lifecycle scoping:** Use `lifecycleScope.launch` in Fragments, `CoroutineScope(Dispatchers.Main)` in ScreenController
- **No RxJava:** Project uses Kotlin coroutines and Flow exclusively

### Firebase Integration
- **Conditional compilation:** Firebase only in non-F-Droid builds
- **Analytics:** `FirebaseHelper.analyticsScreenView()` in Fragment's `onResume()`
- **Crashlytics:** `FirebaseHelper.crashlyticsException()` for non-fatal errors
- **Time-limited logging:** Check `LOGGING_END_DATE` in `FirebaseHelper` - some events are temporarily logged

## External Dependencies & Integration

### Key Libraries
- **Media:** ExoPlayer (media3) for video, Coil 3 for images
- **Networking:** Ktor (server for remote control API), Retrofit (REST APIs), OkHttp
- **Storage protocols:** SMBJ (Samba), Sardine (WebDAV)
- **Preferences:** Kotpref (type-safe SharedPreferences)
- **Event bus:** FlowBus (Kotlin Flow-based)
- **Weather:** OpenWeather API (key in `secrets.properties`)

### Signing Configuration
- **Release builds:** Use keystores defined in `signing/release.properties` and `signing/legacy.properties`
- **Property loading:** `loadProperties()` function in `app/build.gradle.kts` handles missing files gracefully
- **Secrets:** API keys in `secrets.properties` (not committed to git)

## Common Pitfalls & Gotchas

1. **Don't use Android SharedPreferences directly** - Always use Kotpref objects
2. **Flavor-specific code must have both implementations** - `src/common/` and `src/fdroid/` need matching signatures
3. **TV-first design** - Features requiring touch/sensors may not work on primary target devices
4. **Overlay positioning** - Uses "slot" system (top-left/right, bottom-left/right) with alternating positions for OLED burn-in prevention
5. **Duplicate media handling** - `GeneralPrefs.removeDuplicates` deduplicates by filename (case-insensitive) EXCEPT for Immich (uses URI)
6. **HEVC codec detection** - First run checks `DeviceHelper.hasHevcSupport()` and downgrades video quality if needed
7. **Locale switching** - Screensaver can use different locale than system via `LocaleHelper.alternateLocale()`

## File Organization Reference
- **Models:** `models/` (data classes, enums, Kotpref objects)
- **Providers:** `providers/` (media source implementations)
- **Services:** `services/` (background services, API integrations)
- **UI:** `ui/` (activities, fragments, custom views)
  - `ui/core/` - Playback controllers
  - `ui/overlays/` - On-screen display elements
  - `ui/screensaver/` - Entry point activities
  - `ui/settings/` - Settings fragments
  - `ui/sources/` - Media source configuration fragments
- **Utils:** `utils/` (helpers, extensions, standalone utilities)

## Questions to Iterate On
If you need more context about this codebase, ask about:
- How video metadata from Apple/Community manifests is matched to media
- The refresh rate switching mechanism for matching content frame rates
- How the Project Ivy launcher integration works (`projectivyapi/` module)
- The Ktor server implementation for remote control API
- Photo slideshow timing and Ken Burns effect implementation
