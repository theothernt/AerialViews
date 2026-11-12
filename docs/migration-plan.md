# Aerial Views — Compose Screensaver Migration Plan

> [!IMPORTANT]
> **Scope**: Screensaver UI and overlays only. The Leanback/XML settings menus are **out of scope**. Do not migrate `ui/settings`, `MainActivity`, `MainFragment`, or any preference screens.
>
> The one exception is the toggle itself: it needs a single preference entry in an existing settings screen (Phase 0.2).

## Overview

Migrate the screensaver's overlay, loading and progress UI from XML Views to Jetpack Compose, while keeping both implementations runnable side-by-side and switchable at runtime via a setting. Each overlay is ported and visually verified on-device before moving to the next.

The video/image player host (ExoPlayer, Coil) stays XML throughout — this is not a Media3 or player migration.

---

## Review corrections (v2)

The first draft of this document was checked against the codebase. These are the substantive changes:

| # | First draft said | Actually |
|---|---|---|
| 1 | `composeOptions { kotlinCompilerExtensionVersion = … }` | Obsolete. Kotlin 2.x (this repo: 2.4.21) requires the `org.jetbrains.kotlin.plugin.compose` Gradle plugin, versioned by `kotlin`. `composeOptions` can be removed entirely. |
| 2 | Compose BOM `2024.09.00`, activity-compose `1.9.2` | Latest stable is BOM **2026.09.00**; activity is already **1.13.0** in the catalog. |
| 3 | `androidx.compose.material3.Typography` and `CircularProgressIndicator` with no material3 dependency | Either add `material3` (BOM-managed) or drop both. We add it — it is where `Text` and `CircularProgressIndicator` live. |
| 4 | "Add a `ComposeView` to the root `FrameLayout`" | Wrong z-order. The root is a `FrameLayout` whose children draw in order: video, image, overlay, progress bar, loading, brightness dimmer, notification container. Appending the ComposeView puts overlays **above the brightness dimmer and above toast notifications**. It must live inside `overlay_view` instead. |
| 5 | "`overlayView.visibility = View.GONE`" in Compose mode | `overlay_view` also owns `gradientTop`/`gradientBottom`, which ScreenController fades independently. Hiding it kills the gradients. Hide only the four `Flow` widgets. |
| 6 | Compose container reads "slot preferences" | Vague. Reuse `SlotHelper.slotPrefs(context)` — it is the only place that maps the 8 slots to `OverlayType` and their ordered labels. |
| 7 | Corner slot assignment is static | It is not. `ScreenController.loadItem()` calls `overlayHelper.assignOverlaysAndIds(..., alternate)` every item when `GeneralPrefs.alternateTextPosition` is on, swapping left↔right per corner. Compose needs this as **state**, not a one-time layout decision. |
| 8 | Nothing about auto-hide/reveal | `hideOverlays()`, `showOverlays()`, `getOverlaysToFade()`, `hasTop/BottomOverlaysToFade()`, `hasTop/BottomPersistentOverlays()` are all View-based and drive both overlay alpha **and** gradient alpha per corner. Compose needs an equivalent; the plan had no phase for it. |
| 9 | "Skip `OverlayHelper` entirely" in Compose mode | `ScreenController` uses `overlayHelper.findOverlay<WeatherNowOverlay>()` / `<WeatherForecastOverlay>()` / `<MessageOverlay>()` to decide whether to start the weather service and the Ktor message API. With no inflated overlay Views those all silently stop working. The "is this overlay enabled?" query must move out of `OverlayHelper` into something both paths share. |
| 10 | Move `ui/overlays/utils/` to `ui/overlays/xml/utils/` | Do not. `CountdownFormatter`, `CountdownTimeParser`, `TrackNameShortener` are pure logic shared by both engines and are covered by `app/src/test/.../utils/*Test.kt`. |
| 11 | Move `SvgImageView` to `ui/overlays/xml/` | It is not XML-only — it is the SVG renderer for the weather overlays and stays reachable from the Compose weather port via `AndroidView` interop. Leave it where it is. |
| 12 | "The Compose overlays must read `overlayTextSize` / `overlayTextWeight` / `fontTypeface`" | There is no `overlayTextSize`/`overlayTextWeight` pref. Every overlay has its own size/weight pair (see the mapping in Phase 4). `fontTypeface` **is** global. |
| 13 | Overlay typography is "18sp thin" | Defaults only, and the XML path does three extra things Compose must reproduce: `includeFontPadding = false`, a metric-compensating vertical padding from `FontHelper.getFontVerticalOffset`, and a `maxWidth` clamp of `screenWidth - 2 × horizontalPadding`. |
| 14 | Padding is `screen_border_padding` / `screen_border_padding_bottom` | Those are layout *defaults*. `OverlayPaddingHelper` overrides them from `overlayPaddingHorizontal` / `overlayPaddingVertical` (0–64 dp) in `OverlayHelper.applyScreenPadding()`. |
| 15 | Clock overlay needs "font size/weight" verification | `ClockOverlay` is a `TextClock` — a self-updating View. Compose has to reimplement the time source, honouring `ClockType.DEFAULT/HOUR_12/HOUR_24` **and** inheriting the locale-digit behaviour from `LocaleHelper.alternateLocale`. |
| 16 | Date overlay: "locale-aware format unchanged" | Also a 1-second polling ticker (`DateOverlay.refreshDate`). Compose needs its own ticker around `DateHelper.formatDate`. |
| 17 | `TestActivity` "extends `ComponentActivity`" | It extends `AppCompatActivity` (a `ComponentActivity` subclass), so it is fine — but the note matters because `DreamActivity` is a `DreamService` and needs the owners installed by hand. |
| 18 | `ViewModelStoreOwner` required for Compose | Not required here. Compose needs `ViewTreeLifecycleOwner` + `ViewTreeSavedStateRegistryOwner`. No ViewModels are used in this design (state comes from `OverlayStateStore`), so do not add a ViewModelStore. |
| 19 | Phase 11 deletes the toggle | The requested feature *is* the toggle. It stays as a permanent user setting; Phase 11 is redefined as publishing the Compose path and flipping the default. |
| 20 | Screenshots go to `screenshots/` (gitignored) | A root `screenshots/` folder is **not** gitignored. Use `local/screenshots/` — `local/` already is, and it is where local test configs and harness fixtures live. |
| 21 | `android studio version-lookup` as a version source | It only works with a running Android Studio instance (`android studio check` first). Fallback: the Compose BOM mapping page and the version catalog. |
| 22 | Nothing about the release build | `release` enables R8 (`optimization { enable = true }`). Compose ships its own consumer rules, but run `assembleBetaRelease` (or at least `minifyBetaRelease`) once before calling the migration done. |

---

## Repository context

| Item | Detail |
|---|---|
| Root package | `com.neilturner.aerialviews` |
| Build variant to use | `betaDebug` |
| Build command | `./gradlew --no-daemon :app:assembleBetaDebug` |
| Install command | `./gradlew --no-daemon :app:installBetaDebug` |
| Lint | `./gradlew --no-daemon :app:spotlessCheck` |
| Format | `./gradlew --no-daemon :app:spotlessApply` |
| Unit tests | `./gradlew --no-daemon :app:testBetaDebugUnitTest` |

**Key source files to understand before starting:**

| File | Purpose |
|---|---|
| `ui/screensaver/DreamActivity.kt` | `DreamService` entry point. Also holds the "hand off to `TestActivity`" path (`GeneralPrefs.runAsActivity`). |
| `ui/screensaver/TestActivity.kt` | `AppCompatActivity` — the screensaver preview / launch-from-other-apps entry point. |
| `ui/core/ScreenController.kt` | Inflates `aerial_activity.xml`, owns all overlays, players, fades, blackout, and the `OverlayStateStore`. |
| `ui/helpers/OverlayHelper.kt` | Builds overlay Views from slot prefs, groups them into 4 corner `Flow` widgets, alternates corners. |
| `ui/overlays/state/OverlayStateStore.kt` | `MutableStateFlow<OverlayUiState>` — the single source of truth for overlay content. |
| `ui/overlays/state/OverlayUiState.kt` | Data models for every overlay type. |
| `ui/overlays/state/OverlayEventBridge.kt` | FlowBus → store: music, weather, forecast, message, progress events. |
| `ui/helpers/SlotHelper.kt` | `slotPrefs(context)`: the 8 slots → `OverlayType`, in display order. **The authority on which overlays exist.** |
| `ui/helpers/FontHelper.kt` | Typeface resolution (`google-sans`, `open-sans`, fallback) + the vertical-offset metric hack. |
| `ui/helpers/OverlayPaddingHelper.kt` | User-adjustable corner padding (defaults 32dp / 26dp). |
| `ui/helpers/OverlayOpacityHelper.kt` | Visible alpha from `overlayOpacity` (10–100%), plus the `FADE_THRESHOLD` test. |
| `ui/helpers/LocaleHelper.kt` | Alternate-locale context used by both screensaver activities; also applies `clockForceLatinDigits`. |
| `ui/helpers/DateHelper.kt` | Date formatting used by the date overlay. |
| `res/layout/aerial_activity.xml` | Root `FrameLayout`: video, image, overlay, progress bar, loading, brightness, notifications. |
| `res/layout/overlay_view.xml` | `ConstraintLayout`: 2 gradients + 4 empty spacers + 4 corner `Flow` widgets. |
| `res/layout/loading_view.xml` | `FrameLayout`: full-screen background + bottom-left spinner/text row. |
| `res/values/styles.xml` | `OverlayText` / `LoadingText`. |
| `res/values/dimens.xml` | `screen_border_padding` 32dp, `screen_border_padding_bottom` 26dp, `text_block_padding` 3dp. |

---

## Phase 0 — Prerequisites & Setup

### 0.1 Compose toolchain

Kotlin 2.4 with AGP 9 means: the Compose compiler is a Kotlin plugin, `composeOptions` is gone, and AGP has built-in Kotlin support (this module applies no `kotlin-android` plugin today — keep it that way).

`gradle/libs.versions.toml`:

```toml
[versions]
compose-bom = "2026.09.00"
activity-compose = "1.13.0"     # keep in step with activity-ktx
lifecycle = "2.11.0"            # new entry; the catalog has no lifecycle version yet

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }

[plugins]
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Root `build.gradle.kts` — add one line to the existing `plugins` block:

```kotlin
alias(libs.plugins.compose.compiler) apply false
```

`app/build.gradle.kts` — add to `plugins`:

```kotlin
alias(libs.plugins.compose.compiler)
```

and to `android { buildFeatures { … } }`:

```kotlin
compose = true
```

> [!CAUTION]
> Do **not** add a `composeOptions { kotlinCompilerExtensionVersion = … }` block. That is the pre-Kotlin-2.0 mechanism and is no longer read.

`dependencies`:

```kotlin
val composeBom = platform(libs.compose.bom)
implementation(composeBom)
implementation(libs.compose.ui)
implementation(libs.compose.foundation)
implementation(libs.compose.runtime)
implementation(libs.compose.material3)
implementation(libs.lifecycle.runtime.compose)
implementation(libs.activity.compose)
implementation(libs.compose.ui.tooling.preview)
debugImplementation(libs.compose.ui.tooling)
```

`activity-compose` is only strictly needed if a `ComponentActivity` hosts `setContent`. Neither screensaver activity does (both use `setContentView(ScreenController.view)`), so it can be dropped if the build flags it as unused — but keep it for the future activity migration.

Verify:

```sh
./gradlew --no-daemon :app:assembleBetaDebug
```

### 0.2 Add the user-facing toggle

This is a **real settings entry**, not a hidden developer flag (the first draft had it developer-only; the request is for a switch in Settings).

`models/prefs/GeneralPrefs.kt`:

```kotlin
// Screensaver UI engine
var useComposeScreensaver by booleanPref(false, "use_compose_screensaver")
```

`res/xml/settings_appearance_startup.xml` — new category, and the strings in `res/values/strings.xml`:

```xml
<PreferenceCategory app:title="@string/category_screensaver_ui">
    <CheckBoxPreference
        app:defaultValue="false"
        app:key="use_compose_screensaver"
        app:title="@string/appearance_screensaver_ui_title"
        app:summary="@string/appearance_screensaver_ui_summary" />
</PreferenceCategory>
```

Appearance → Startup is the right home: it already holds the screensaver startup options and "Show loading text".

Behaviour: the toggle is read once when `ScreenController` is constructed. The screensaver is stopped and recreated on every `onDreamingStopped`/`onDestroy`, so switching takes effect the next time the screensaver starts — no live re-composition needed. Say so in the summary string.

### 0.3 Folder structure

```
ui/
├── theme/                        ← NEW: Compose theme + style constants
├── screensaver/
│   ├── xml/                      ← ScreenController moves here (Phase 1)
│   └── compose/                  ← NEW: Compose screensaver surface
├── overlays/
│   ├── state/                    ← UNCHANGED (shared)
│   ├── utils/                    ← UNCHANGED (shared pure logic, unit-tested)
│   ├── SvgImageView.kt           ← UNCHANGED (interop for weather icons)
│   ├── xml/                      ← existing overlay Views move here (Phase 1)
│   └── compose/                  ← NEW
│       └── components/           ← NEW: shared Compose primitives
└── core/                         ← UNCHANGED (players, helpers, metadata)
```

### 0.4 Verify baseline build and device

```sh
mkdir -p local/screenshots
./gradlew --no-daemon :app:assembleBetaDebug
./gradlew --no-daemon :app:installBetaDebug
adb shell am start -n com.neilturner.aerialviews.debug/com.neilturner.aerialviews.ui.screensaver.TestActivity
android screen capture -o local/screenshots/00_baseline_xml.png
```

Visually confirm the XML screensaver runs correctly before making any changes.

> [!NOTE]
> All verification in this plan runs on a **physical device** (TV or Google TV streamer), not an emulator. `adb devices` reports nothing attached at the time of writing — connect and authorise the device before Phase 0.4. Emulator screenshots are not a substitute: `DreamService`, HDR, refresh-rate switching and TV text rendering all differ from real hardware, and the dream path (Phase 2) is the part most likely to break.

---

## Phase 1 — Restructure Existing Code (No Behaviour Changes)

> [!CAUTION]
> Pure rename/move. **No logic changes.** Tests must pass before and after, and Spotless must be applied.

### 1.1 Move XML-only overlay Views

| From | To |
|---|---|
| `ui/overlays/ClockOverlay.kt` | `ui/overlays/xml/ClockOverlay.kt` |
| `ui/overlays/DateOverlay.kt` | `ui/overlays/xml/DateOverlay.kt` |
| `ui/overlays/CountdownOverlay.kt` | `ui/overlays/xml/CountdownOverlay.kt` |
| `ui/overlays/MessageOverlay.kt` | `ui/overlays/xml/MessageOverlay.kt` |
| `ui/overlays/MetadataOverlay.kt` | `ui/overlays/xml/MetadataOverlay.kt` |
| `ui/overlays/NowPlayingOverlay.kt` | `ui/overlays/xml/NowPlayingOverlay.kt` |
| `ui/overlays/WeatherNowOverlay.kt` | `ui/overlays/xml/WeatherNowOverlay.kt` |
| `ui/overlays/WeatherForecastOverlay.kt` | `ui/overlays/xml/WeatherForecastOverlay.kt` |
| `ui/helpers/OverlayHelper.kt` | `ui/overlays/xml/OverlayHelper.kt` |

**Not moved:** `ui/overlays/state/`, `ui/overlays/utils/`, `ui/overlays/SvgImageView.kt`.

### 1.2 Move ScreenController

`ui/core/ScreenController.kt` → `ui/screensaver/xml/ScreenController.kt`, giving `ui/screensaver/xml/` and `ui/screensaver/compose/` as clean counterparts.

Referenced by `DreamActivity`, `TestActivity`, and `InputHelper` (as a parameter type) — update imports. IDE move-refactor, then `spotlessApply`.

> [!NOTE]
> This is the highest-churn step in Phase 1. Keep it a separate commit from the overlay View moves so a bad import is easy to bisect.

### 1.3 Verify no regressions

```sh
./gradlew --no-daemon :app:testBetaDebugUnitTest
./gradlew --no-daemon :app:spotlessCheck
./gradlew --no-daemon :app:assembleBetaDebug
```

Install and confirm the XML screensaver works identically to the Phase 0 baseline.

---

## Phase 2 — DreamService Lifecycle Plumbing

> [!WARNING]
> `DreamActivity` extends `android.service.dreams.DreamService` (a `Service`). Compose needs a `LifecycleOwner` and a `SavedStateRegistryOwner` on an ancestor of the `ComposeView`. `TestActivity` gets both from `AppCompatActivity` — only `DreamActivity` needs work.

Requirements, in order:

1. `SavedStateRegistryController.create(this)` and `LifecycleRegistry(this)` as fields.
2. `savedStateRegistryController.performRestore(null)` **before** `Lifecycle.Event.ON_CREATE`.
3. Owners installed on `window.decorView` **before** `setContentView(...)` / before the `ComposeView` is attached. `AbstractComposeView` resolves `ViewTreeLifecycleOwner` on attach and throws if it is missing.
4. Map `onDreamingStarted()` → `ON_START` then `ON_RESUME`. Without this, `collectAsStateWithLifecycle` never leaves `INITIALIZED` and the overlays render blank.
5. Map `onDreamingStopped()` → `ON_PAUSE`/`ON_STOP`, and `onDetachedFromWindow()` → `ON_DESTROY`.

```kotlin
class DreamActivity :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }

        // ... existing body unchanged; the `runAsActivity` early return now happens
        // after the lifecycle is CREATED, which is harmless ...
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        // ... existing body ...
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        super.onDetachedFromWindow()
    }
}
```

Notes:

- Keep the `GeneralPrefs.runAsActivity` early return where it is (after the owner installation) so the handoff path is untouched.
- `LifecycleRegistry.handleLifecycleEvent` is a no-op for events that do not move the state forward, but the `isAtLeast(CREATED)` guard in `onDetachedFromWindow` is still worth having: `onDetachedFromWindow` can fire on a dream that never fully attached.
- Do **not** add `ViewModelStoreOwner`. Nothing in this design needs a `ViewModel`; overlay content already lives in `OverlayStateStore`, owned by `ScreenController`.
- Add `DisposableEffect`/`Disposable` safety: the `ComposeView` uses `ViewCompositionStrategy.DisposeOnDetachedFromWindow`, so the composition is disposed when the dream's decor view detaches — no extra work in `ScreenController.stop()`.

### 2.1 Proof of concept

Add a temporary `ComposeView` rendering `Text("Compose works!")` inside `overlay_view`, start the dream (via `TestActivity` **and** via the real screensaver), confirm it renders, then remove it.

---

## Phase 3 — Compose Theme

Minimal. The app is a TV screensaver: no Material surfaces, no ripples, no navigation.

**`ui/theme/Color.kt`** — `OverlayWhite`, `OverlayShadow`.

**`ui/theme/Type.kt`** — a `Typography` whose `bodyMedium` is the base overlay style. Do not hard-code 18sp/thin as "the" overlay style; that is a *default* the per-overlay prefs override (Phase 4).

**`ui/theme/Theme.kt`**

```kotlin
@Composable
fun AerialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = AerialTypography,
        content = content,
    )
}
```

Wrapping in `MaterialTheme` (not just returning `content()`) costs nothing and makes `Text`/`CircularProgressIndicator` resolve a sane default scheme instead of the light one.

---

## Phase 4 — Shared Compose Primitives

Create `ui/overlays/compose/components/`.

### 4.1 `OverlayTextStyle` must be a function, not a `val`

The XML path resolves typography **per overlay instance** in `OverlayHelper.applyFontToTextView()`, from four sources:

| Overlay | Size pref | Weight pref |
|---|---|---|
| Clock | `clockSize` | `clockWeight` |
| Date | `dateSize` | `dateWeight` |
| Metadata 1–4 | `overlayMetadata1..4Size` | `overlayMetadata1..4Weight` |
| Weather now | `weatherLine1Size` | `weatherLine1Weight` |
| Weather forecast | `weatherLine2Size` | `weatherLine2Weight` |
| Now playing 1–2 | `nowPlayingSize1/2` | `nowPlayingWeight1/2` |
| Message 1–4 | `messageSize` | `messageWeight` |
| Countdown | `countdownSize` | `countdownWeight` |

Plus globally: `fontTypeface`, `overlayColour`, `overlayOpacity`. (A message pushed over the HTTP API can also override size/weight per message — see `MessageOverlayState`.)

```kotlin
@Composable
fun overlayTextStyle(
    sizeSp: Float,
    weight: String,
    colour: Color = overlayColour(),
): TextStyle
```

which must reproduce the XML pipeline:

| XML / View behaviour | Compose equivalent |
|---|---|
| `typeface = FontHelper.getTypeface(context, fontTypeface, weight)` | `fontFamily = FontFamily(FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight))` |
| `includeFontPadding = false` | `platformStyle = PlatformTextStyle(includeFontPadding = false)` |
| `padding(0, offset, 0, -offset)` from `FontHelper.getFontVerticalOffset` | same values via `Modifier.padding(top = …, bottom = …)`, computed from the equivalent px size |
| `shadowColor #000`, `shadowDx/Dy 1`, `shadowRadius 1` (px) | `shadow = Shadow(Color.Black, Offset(1f, 1f), blurRadius = 1f)` |
| `textColor` from `ColourHelper.overlayColour(overlayColour)` | `color = Color(...)` |
| `textSize` in sp | `fontSize = sizeSp.sp` |
| implicit `letterSpacing` | set `letterSpacing = 0.sp` explicitly for parity |
| `maxWidth = screenWidth - 2 × horizontalPaddingPx` | `Modifier.widthIn(max = …)` or rely on the container's padding |

The vertical-offset hack exists because Open Sans and Google Sans have different ascent metrics. Reproduce it faithfully first — matching pixels matters more than being elegant — and only then consider replacing it with `LineHeightStyle`.

> [!NOTE]
> `FontHelper` has a latent typo: the fallback branch is `Typeface.create("san-serif", …)` (missing a `d`), so it silently returns the default typeface rather than Roboto-Light. The Compose implementation will inherit the same behaviour if it calls `FontHelper`. Worth fixing separately, not as part of this migration.

### 4.2 Other primitives

- `OverlayText(text, style, modifier)` — a thin wrapper over `BasicText`/`Text` that applies the style and the `OverlayOpacityHelper.visibleAlpha` alpha.
- `OverlayIcon(resId, colour, size)` — `AndroidView` wrapping `SvgImageView` + `setSvgResource` + `setTintColour`. Used by the weather overlays now; replaced by a proper SVG/KMP path later.

---

## Phase 5 — Host Wiring in ScreenController

### 5.1 Where the `ComposeView` goes

`aerial_activity.xml` child order (draw order, back to front): `video_view`, `image_view`, `overlay_view`, `progress_bar`, `loading_view`, `brightness_view`, `notification_container`.

The Compose overlay layer must be **inside `overlay_view`'s `ConstraintLayout`**, added as its last child (so it draws above `gradientTop`/`gradientBottom`, exactly as the four `Flow` widgets do now).

Do not add it to the root `FrameLayout`: appended at the end it would draw above the brightness dimmer (overlays would not dim) and above `notification_container` (toasts would be hidden behind the overlay layer).

```kotlin
val composeView = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    setContent { AerialTheme { ScreensaverOverlays(...) } }
}
overlayViewBinding.layout.addView(composeView)

// XML path off, but keep gradients (they are siblings of the ComposeView)
overlayViewBinding.flowTopLeft.visibility = View.GONE
overlayViewBinding.flowTopRight.visibility = View.GONE
overlayViewBinding.flowBottomLeft.visibility = View.GONE
overlayViewBinding.flowBottomRight.visibility = View.GONE
```

Because everything stays under `overlayView`, blackout mode (`overlayView.visibility = INVISIBLE`) and the gradient fades keep working unchanged.

> [!IMPORTANT]
> `addView` without explicit params defaults to `WRAP_CONTENT` → the Compose layer measures to zero. Always pass `MATCH_PARENT`.

### 5.2 Branch points in `ScreenController`

| Site | XML path | Compose path |
|---|---|---|
| `init` overlay build | `OverlayHelper(…).buildOverlaysAndIds(binding)` | `ComposeView` + store |
| `loadItem` → `assignOverlaysAndIds` | swap left/right `Flow` ids | push `alternate` into state |
| `bindOverlayState` → `renderOverlayState` | fan out to overlay Views | no-op for overlays (composables observe the store); keep `progressBarView.render` |
| `fadeInNextItem`/`hideOverlays`/`showOverlays` | animate View alpha | animate a Compose alpha/state |
| `enterBlackOutMode`/`exitBlackOutMode` | toggle View visibility | unchanged (`overlayView` covers it) |
| `hasWeather…`/`hasMessage` service gating | `overlayHelper.findOverlay<…>()` | slot-pref query (5.4) |

### 5.3 Overlay enablement must move out of `OverlayHelper`

`findOverlay<T>()` reflects *instantiated Views*. Replace the service-gating calls with a shared query, e.g.

```kotlin
object EnabledOverlays {
    fun types(context: Context): Set<OverlayType> =
        SlotHelper.slotPrefs(context).map { it.pref }.toSet()
}
```

then gate on `OverlayType.WEATHER1 in types` / `WEATHER2` / `MESSAGE1..4`, in **both** engines. Without this, Compose mode silently disables the weather service and the Ktor message API.

### 5.4 Auto-hide/reveal needs a Compose equivalent

Today, `GeneralPrefs.overlayVisibility` (`ALWAYS_VISIBLE` / `ALWAYS_HIDDEN` / `HIDE_AFTER_DELAY` / `SHOW_AFTER_DELAY`) drives per-View alpha animations for the corners listed in `overlayFadeCornersSelection`, and ScreenController fades the gradients in step. `overlayRevealTimeout`, `overlayFadeInDuration`, `overlayFadeOutDuration` and `overlayOpacity` all feed into it.

Add a phase-5 sub-task: expose an `overlayAlpha: StateFlow<Float>` (0f…visibleAlpha) on the store or a sibling holder, plus per-corner "is this corner fading?" flags derived from the same prefs, and have the Compose layer animate with it. Gradients keep their existing View alpha animation, driven by the same flags.

---

## Compose architecture — state, hoisting, and what we deliberately skip

### Unidirectional data flow, already in place

`OverlayStateStore` is the state holder: a `MutableStateFlow<OverlayUiState>` fed by `OverlayEventBridge` (FlowBus events) and owned by `ScreenController`. Compose consumes it one way only — collect, render, no writes back:

```
GlobalBus ─► OverlayEventBridge ─► OverlayStateStore ─► collectAsStateWithLifecycle ─► composables
                                          ▲
                                          └── ScreenController (setMetadata/resetForNextMedia)
```

Overlay composables never read prefs, players or services. The container resolves the slot and padding prefs **once** (via `SlotHelper.slotPrefs` and `OverlayPaddingHelper`) and passes concrete values down; the leaves take text, style and size as parameters and render. That is the property that matters: it is what makes them previewable and screenshot-comparable.

### State hoisting is explicit, not incidental

`ScreensaverOverlays` takes **state**, not the store:

```kotlin
@Composable
fun ScreensaverOverlays(
    state: OverlayUiState,
    alternate: Boolean,
    modifier: Modifier = Modifier,
)
```

and the caller (`ScreenController`'s `setContent { }`) does the single `collectAsStateWithLifecycle()`. Individual overlays are stateless (`ClockOverlay(text: String, style: TextStyle)`), with the ticking/countdown/animation done in an effect *inside* the leaf so it cancels with the composition.

### Effects, not handlers

Every periodic update becomes a Compose effect bound to the composition: `LaunchedEffect` / `produceState` / `rememberInfiniteTransition`. Clock, date and countdown tickers cancel automatically when the `ComposeView` detaches, instead of relying on `onDetachedFromWindow` bookkeeping the way `DateOverlay` and `CountdownOverlay` do today.

### Immutability and stability

`OverlayUiState` and its members are rebuilt with `copy()` and never mutated in place (the `toMutableMap().apply { }` in the store already produces a new map), so mark `OverlayUiState` and the per-overlay state classes `@Immutable`. Without it, `Map`/`List` fields make every composable that takes a state unstable and unskippable. Eight small texts make the cost irrelevant today, but the annotation is free and stops future readers copying the wrong pattern.

### Previews instead of round trips

`ui-tooling-preview` is already in the dependency list. Add a `SampleOverlayUiState` (one instance per overlay type, plausible English text) so each overlay has a `@Preview`. With no emulator attached and verification requiring a physical device, previews are the only fast layout iteration loop — use them for composition and typography, and treat them as unverified for exact pixel metrics.

### Testing: keep composables thin

The repo has no instrumentation tests (`app/src/androidTest` is empty) and JVM tests cannot touch Android framework classes. So do not introduce Compose UI tests for this migration. Instead keep all formatting and decision logic in pure code that the existing JVM suite covers — `CountdownFormatter`, `CountdownTimeParser`, `TrackNameShortener`, `OverlayPaddingHelper`, `OverlayOpacityHelper`, `ScheduledBlackoutWindow` are the model to follow. If a Composable contains an `if` worth testing, the condition belongs in a pure function.

### What we deliberately do **not** add

| Not used | Why |
|---|---|
| **`ViewModel`** | `OverlayStateStore` already is the observable state holder, and it outlives nothing that a ViewModel would help with. `DreamService` has no `ViewModelStoreOwner`, so it would mean hand-rolling a `ViewModelStore` and clearing it manually — real cost, pass-through benefit. No config-change recreation, no back stack, no navigation, no process-death restore worth having in a screensaver. |
| **Hilt / DI** | The repo uses none; `ScreenController` is constructed directly by two activities. Introducing a DI graph for one screen is pure overhead. |
| **Navigation** | Single screen, no destinations. |
| **LiveData / `observeAsState`** | The store is already a `StateFlow`; `collectAsStateWithLifecycle` is the current recommendation. |
| **A prefs-observable state layer** | Both engines freeze `GeneralPrefs` at `ScreenController` construction. Making Compose live-reactive to prefs would make the two engines behave differently and break the A/B comparison. If live pref updates are wanted later, that is a separate change affecting both paths. |

> [!NOTE]
> If a future phase migrates the settings screens (currently out of scope), *that* is where a `ViewModel` earns its place — real navigation, real config changes, real saved state. Nothing here precludes it.

---

## Phase 6 — Compose Overlay Container

```kotlin
@Composable
fun ScreensaverOverlays(
    state: OverlayUiState,
    alternate: Boolean,
    modifier: Modifier = Modifier,
) {
    // corners from SlotHelper.slotPrefs(context), rendered from `state`
}
```

Layout rules, matching `overlay_view.xml` + `OverlayHelper`:

- Padding comes from `OverlayPaddingHelper.horizontalDp/verticalDp(GeneralPrefs.…)` converted to dp — **not** from `R.dimen.screen_border_padding`, which the helper overrides.
- Four corners: `Box(fillMaxSize().padding(horizontal, vertical))` with `Alignment.TopStart`, `TopEnd`, `BottomStart`, `BottomEnd`.
- Each corner is a `Column` with `verticalArrangement = Arrangement.spacedBy(3.dp)` (i.e. `text_block_padding`).
- Child order within a corner is **upper slot first, then lower slot** — `slotBottomLeft2` above `slotBottomLeft1`, `slotTopLeft2` above `slotTopLeft1`. That is what `buildReferenceIds` encodes with its reversed lists and empty spacers; the spacers and the `Flow` alignment attributes become unnecessary in Compose.
- Alternating position (`alternateTextPosition`): when `alternate` is true, swap the left and right `Column`s per row — same as `assignOverlaysAndIds`.
- Corner contents come from `SlotHelper.slotPrefs(context)`, mapped through `OverlayType` → Composable.
- Width clamp: cap each corner at `screenWidth - 2 × horizontalPadding` so long metadata/POI text wraps instead of running under the opposite edge.

---

## Phase 7 — Port Each Overlay

Order: simplest → most complex. For each: create the Composable, wire it into `ScreensaverOverlays`, build, install, screenshot, compare.

| # | Overlay | File | What actually has to be reimplemented |
|---|---|---|---|
| 7.1 | **Clock** | `compose/ClockOverlay.kt` | A ticker (no `TextClock` in Compose). `ClockType.DEFAULT` follows the system 12/24h setting; `HOUR_12` → `h:mm a`, `HOUR_24` → `HH:mm`. `clockForceLatinDigits` is applied by `LocaleHelper` via the process default locale — verify it survives into the composition. |
| 7.2 | **Date** | `compose/DateOverlay.kt` | 1-second ticker + `DateHelper.formatDate(context, dateFormat, dateCustom)`. |
| 7.3 | **Metadata** | `compose/MetadataOverlay.kt` | Static vs POI text from `state.metadata[type]`; POI rotation needs a **video position source** (`MetadataOverlay` reads `videoPlayer.currentPosition / 1000`) and the 1 s / 3 s poll and cross-fade from the XML version. `isFadingOutMedia` suppresses POI changes while the item is fading out. |
| 7.4 | **Message** | `compose/MessageOverlay.kt` | Per-message size/weight overrides from `MessageOverlayState`; blank text ⇒ hidden; `duration` auto-clear; `messageAnimateChanges` fade path (`ChangeBounds` + `Fade` transitions in the XML version → `AnimatedVisibility`). |
| 7.5 | **Countdown** | `compose/CountdownOverlay.kt` | Reuse `CountdownTimeParser` / `CountdownFormatter` (shared, unit-tested) + the `countdown_target_message` / `countdown_label` strings; 1-second tick until completion. |
| 7.6 | **Now Playing** | `compose/NowPlayingOverlay.kt` | `NowPlayingFormat` string assembly + `TrackNameShortener`; album art is a `Bitmap` on `MusicEvent` → `Image(bitmap.asImageBitmap())`, sized `lineHeight × 2.5` with a 12dp leading margin on the text. |
| 7.7 | **Loading indicator** | `compose/LoadingIndicator.kt` | See Phase 8. |
| 7.8 | **Weather (current)** | `compose/WeatherNowOverlay.kt` | `weatherLine1Layout` is a comma-separated `ForecastType` list (`CITY`, `TEMPERATURE`, `ICON`, `SUMMARY`) rendered as a horizontal row with 16dp gaps (no gap after an icon) and icons sized `1.3 × text height`. Icons are SVG → `AndroidView(SvgImageView)`. Needs the extra 2dp top padding for the degree symbol. |
| 7.9 | **Weather (forecast)** | `compose/WeatherForecastOverlay.kt` | Multi-day row, `weatherLine2Days`, icon alignment, per-day cell layout. Most complex. |

---

## Phase 8 — Loading Indicator

The loading indicator is **not** part of the overlay layer. It is `loading_view.xml`, included directly in `aerial_activity.xml`, and its background doubles as the blackout screen (`enterBlackOutMode` sets it black and makes it visible). Keep the `FrameLayout` + `loading_background`; replace only the `loading_container` contents.

XML baseline to match: horizontal row, `bottom|start`, `screen_border_padding` start / `screen_border_padding_bottom` bottom (user-overridable), 24×24dp indeterminate `ProgressBar` tinted `#FFFFFF`, 12dp gap, `LoadingText` (18sp, thin, white, 1/1/1 shadow).

```kotlin
@Composable
fun LoadingIndicator(
    visible: Boolean,
    text: String,
    spinnerVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Row(
        modifier = modifier.fillMaxSize().padding(start = hPad, bottom = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spinnerVisible) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = overlayTextStyle(loadingTextSize, loadingTextWeight))
    }
}
```

Inputs are driven by `ScreenController`, not by the store:

- `loadingText` — `loading_title` / `loading_building` / `loading_resuming` / `loading_error`.
- `loadingVisible` — mirrors `loadingContainer` fade in/out (`fadeOutLoadingText`, `mediaFadeIn`, `LOADING_DELAY`).
- `spinnerVisible` — `false` after `showLoadingError()`.
- `showLoadingText == false` ⇒ container invisible (spinner and all).

Add these as private `MutableStateFlow`s on `ScreenController` (or a small `LoadingUiState` holder) so the Compose layer observes them without touching the XML members.

M3's `CircularProgressIndicator` defaults to a heavier stroke than the platform `ProgressBar`; verify 2dp visually against the baseline.

---

## Phase 9 — On-Device Visual Verification

### 9.1 Capture the XML baseline

```sh
# useComposeScreensaver = false
./gradlew --no-daemon :app:installBetaDebug
adb shell am start -n com.neilturner.aerialviews.debug/com.neilturner.aerialviews.ui.screensaver.TestActivity
android screen capture -o local/screenshots/xml_<overlay>.png
```

Verify the real `DreamActivity` path too, at least once per phase — `TestActivity` is an `Activity` and gets its lifecycle owners for free, so it cannot prove Phase 2 works.

### 9.2 Capture the Compose version

Toggle `use_compose_screensaver` in Settings → Appearance → Startup, reinstall if needed, relaunch, capture.

### 9.3 Comparison checklist

Verified on the Chromecast (`sabrina`, 1920×1080, density 2.0, `betaDebug`) against the XML engine as baseline, 2026-11-12.

**How to read the measurements.** Each figure is an ImageMagick ink box (`-colorspace gray -threshold 55% -format "%@"`) taken from the same crop of each engine's screenshot, so a shared box means text, icon and padding positions agree to the pixel. Frames were only captured after the platform logged `ScreenController: Loading:` — fixed sleeps were too short and produced loading-screen captures instead of overlays. Two traps worth remembering: `startup_show_loading` is not written until the setting is touched, so a pref edit that only rewrites existing keys silently does nothing; and LeakCanary's heads-up notification can draw over the screensaver on this device and pollute a capture.

- [x] Font weight/size identical, from the per-overlay prefs — clock `80x27` (Compose) vs `79x27` (XML), date `122x27` in both, at default size and weight.
- [x] Vertical text position identical (font-metric offset reproduced) — clock and date baselines both at y=58.
- [x] Corner position correct — clock ink starts at x=66, date at x=733, in both engines.
- [x] Multi-line blocks stack with a 3dp gap — a message block stacked above the metadata block in the same corner; combined ink `1788x130+66+113` in both engines. The gap itself was not measured in pixels.
- [x] Long text wraps at the horizontal padding instead of running under the opposite edge — a 121-character POI wrapped onto a second line and the second line stopped inside the right padding; identical in both engines.
- [x] Alternating position still alternates per item — item 1: clock `80x27+66`, date `122x27+733`; after a skip, item 2: date `122x27+66`, clock `80x27+775`. Same numbers in both engines.
- [x] Auto-hide/reveal fades both the overlays and the gradients — auto-hidden frames carry no overlay ink and a reveal restores `1789x27+66+58` in both; gradients measured separately (bottom strip 0.5177 visible vs reference 0.5278; 0.531373 vs 0.531373 once faded out).
- [x] Overlay opacity setting affects both engines identically — at `overlay_opacity = 50` neither engine has any pixel above 90% brightness in the clock box (470 Compose / 464 XML at 100%), and the text reads grey in both.
- [x] Blackout mode hides everything — sampled pixels are `srgba(0,0,0)` across the frame with no overlay ink, identical in both engines.
- [ ] Text colour matches (`overlayColour`, not hard-coded white) — not exercised; every check above ran at the default white.
- [ ] Shadow present and of matching intensity — not measured.
- [ ] Padding from screen edge matches, including non-default `overlayPaddingHorizontal/Vertical` — default padding matches (x=66 / x=733 / y=58 in both); the non-default padding range is outstanding.

### 9.4 Per-overlay results from this pass

| Overlay | Result |
|---|---|
| Message | ✅ messages 1–2 posted through the Ktor API rendered in both engines; the bottom block measured `1788x130+66+113` in both. |
| Weather (current) | ✅ real data rendered (`7° [icon] Clear sky`): `236x34+65+65` Compose vs `236x36+65+66` XML. |
| Weather (forecast) | ❌ open — see 9.5. |
| Now playing | ⚠️ unverified — see 9.5. |
| Loading indicator with `showLoadingText = false` | ✅ the loading area is empty in both engines. |
| Weather service and Ktor API in Compose mode | ✅ Compose-mode run logged `WeatherService: Processed forecast: 3 days for London`, and API messages reached the Compose layer. |

`magick compare local/screenshots/xml_<overlay>.png local/screenshots/compose_<overlay>.png local/screenshots/diff_<overlay>.png` when ImageMagick is available.

### 9.5 Open items

- **Weather forecast does not match.** Compose `267x113+588+57` vs XML `261x115+592+61`: about 4px further right and higher, and ~6px narrower, with the XML day labels reading slightly heavier in a side-by-side crop (`local/screenshots/cmp2_forecast.png`). The coloured chips seen during this pass were debug-only backgrounds inside `WeatherForecastOverlay` (`BuildConfig.DEBUG`) and have since been removed; they were not the cause of the offset, and the measurements are unchanged after removal. Phase 7.9 stays open.
- **Now playing is unverified.** The overlay renders from `MusicEvent`, which `NowPlayingService` feeds from `MediaSessionManager` via `NotificationService`; nothing on the test TV has an active media session, so both engines show an empty event and there is nothing to compare. Needs a device with music playing.
- **`DreamActivity` is still unverified (9.1).** `cmd dreams start-dreaming` requires root on this TV, and `TestActivity` cannot prove the hand-installed lifecycle owners. Needs Aerial Views selected as the TV's screensaver on an idle device, or root.

---

## Phase 10 — Progress Bar

Independent of the overlay layer, and the state already exists: `ProgressBarEvent` (posted on `GlobalBus`) → `OverlayEventBridge` → `OverlayStateStore.setProgress` → `OverlayUiState.progress: ProgressOverlayState` (`state`, `position`, `duration`), consumed today by `ProgressBar.render()` via `ScreenController.renderOverlayState()`.

- `progressBarLocation` (`DISABLED`/`TOP`/`BOTTOM`) sets visibility and gravity at init and is re-applied on blackout exit.
- `progressBarType` (`BOTH`/`ELAPSED`/`REMAINING`) — check `ProgressBar` and `VideoPlayerView` for where the elapsed/remaining split is applied before porting.
- `progressBarOpacity` → alpha.
- Animation is a `ValueAnimator` growing width from `position/duration` to full over `duration` ms, with `PAUSE`/`RESUME`/`RESET` states. Compose equivalent: an `Animatable` fraction started in a `LaunchedEffect(state, position, duration)`, drawn as a `Box` with `fillMaxWidth(fraction)`, or a custom `LinearProgressIndicator`.

Keep the XML `ProgressBar` view for the XML path; add a Compose one behind the same toggle.

---

## Phase 11 — Publishing the Compose Path

**Decision: both engines ship, permanently.** `use_compose_screensaver` stays as a user-facing setting in Settings → Appearance → Startup. Nothing is deleted — the XML overlays, `OverlayHelper`, `overlay_view.xml` and `loading_view.xml` all remain as the fallback engine.

The toggle defaults to `false`. Flipping the default to `true` is a separate, later change gated on:

- [ ] Every overlay in Phase 7 passes the comparison checklist on a physical device
- [ ] Loading indicator matches, including with `showLoadingText = false`
- [ ] Progress bar matches, including pause/resume/reset and blackout
- [ ] Auto-hide/reveal, alternation, opacity and blackout verified in both engines
- [ ] Weather service and Ktor message API start in Compose mode
- [ ] No measure/layout regression: check `DreamTiming` marks (`screen.built`, `dream.contentView`) against the XML baseline, since Compose adds inflate/first-composition cost on a cold screensaver start
- [ ] A minified release build succeeds

Until the default flips, both engines must keep working: every phase's change has to leave the `use_compose_screensaver = false` path byte-for-byte equivalent in behaviour.

### 11.1 Getting it in front of users

The toggle defaulting to `false` is what makes a wide beta safe, and the `beta` / `googleplaybeta` flavours (already shipping with `versionNameSuffix = "-betaN"`) are the natural channel — the XML engine stays the default for everyone who does not opt in.

Two things are worth doing **before** that beta goes out, because without them the feedback is much weaker:

1. **Tag the engine in telemetry.** `ScreenController` should record which engine it built as a Firebase Analytics user property and a Crashlytics custom key (`use_compose_screensaver`). Both providers are already wired via `FirebaseHelper`, and the F-Droid flavour substitutes a no-op helper, so the call site is safe everywhere. Without this, a crash report or a "the clock is wrong" report cannot be attributed to the Compose path, which undermines the whole point of a beta.
2. **Ask beta users to compare specific things.** A short checklist converts vague feedback into actionable data: clock/date legibility at 10 feet, whether text is still readable with overlays auto-hidden then revealed, whether long POI text wraps instead of running off the edge, and whether the screensaver looks right after `runAsActivity` handoff.

### 11.2 Default flip

`useComposeScreensaver` flips to `true` only when all the criteria in this phase pass, and stays a setting regardless.

Verification:

```sh
./gradlew --no-daemon :app:testBetaDebugUnitTest
./gradlew --no-daemon :app:spotlessApply
./gradlew --no-daemon :app:spotlessCheck
./gradlew --no-daemon :app:assembleBetaDebug
./gradlew --no-daemon :app:installBetaDebug
android screen capture -o local/screenshots/final_compose.png
```

Plus one minified build (`assembleBetaRelease`, or `minifyBetaRelease` if signing material is absent) to confirm R8 does not strip anything Compose needs.

---

## Conventions & Constraints

| Rule | Detail |
|---|---|
| **Never migrate settings menus** | `ui/settings/` screens stay XML/Leanback — the only edit is the Phase 0.2 preference entry |
| **Shared state stays shared** | `ui/overlays/state/` is never duplicated; it may only gain fields |
| **Shared pure logic stays shared** | `ui/overlays/utils/` (unit-tested) is used by both engines |
| **No `!!` on injected/optional values** | Follow existing null-safety idioms |
| **Overlay prefs must match** | Every Composable reads the same `GeneralPrefs` fields as its XML counterpart |
| **Windows Gradle** | Always `./gradlew --no-daemon` |
| **spotlessApply before finishing** | `:app:spotlessCheck` must pass; it covers `app/**/*.kt` and `app/**/*.md` only — `docs/` is not checked |
| **Screenshots** | `local/screenshots/` — `local/` is already gitignored, so nothing new to ignore |
| **No new Room migrations** | The DB uses `fallbackToDestructiveMigration` |
| **One engine at a time** | Never leave the build in a state where both paths are half-wired; each phase must run standalone |

---

## Completion criteria

- [ ] Compose toolchain builds (compiler plugin, BOM 2026.09.00, material3, no `composeOptions`)
- [ ] `use_compose_screensaver` toggle in Settings → Appearance → Startup, defaulting to `false`
- [ ] The `false` path is unchanged — XML screensaver still behaves exactly as the Phase 0 baseline
- [ ] Compose renders inside the real `DreamActivity`, not just `TestActivity`
- [ ] Every overlay in Phase 7 passes the on-device comparison checklist
- [ ] Loading indicator matches Phase 8
- [ ] Progress bar matches the XML behaviour (Phase 10)
- [ ] Auto-hide/reveal, alternation, opacity, blackout all behave identically in both engines
- [ ] Weather service and Ktor message API still start in Compose mode
- [ ] `testBetaDebugUnitTest` passes
- [ ] `spotlessCheck` passes
- [ ] `assembleBetaDebug` succeeds
- [ ] A minified release build succeeds

---

## Decisions

Settled:

1. **Toggle lifetime** — permanent, user-facing, in Settings → Appearance → Startup. XML is not deleted.
2. **Default** — `false` until every overlay is signed off, then flip to `true` (Phase 11 criteria).
3. **Phase 1.2** — do it: `ScreenController` moves to `ui/screensaver/xml/`.
4. **Material3** — yes, BOM-managed, for `Text` and `CircularProgressIndicator`.
5. **Test device** — physical TV/streamer for all visual verification.

Still open (decide when the relevant phase starts):

- **Gradients and brightness dimmer** — out of scope for this migration; they stay XML View layers. Revisit only if the Compose layer needs to own them.
- **`ProgressBarType` (elapsed vs remaining)** — resolve how `VideoPlayerView` applies it before Phase 10, since the Compose bar has to split the same way.
- **POI position source** — the Compose metadata overlay needs video position; decide between a `StateFlow<Long>` on the store (pushed by `ScreenController`'s existing playback callbacks) or passing the player down. The former keeps the Composable testable.

---

## Appendix — Option B: ViewModel-centric architecture

Not the recommended path for the port itself, but worth having written down: if you'd rather rewrite the overlay layer around a `ViewModel` instead of porting it View-for-View, this is the shape it takes.

### B.1 Scope the ViewModel correctly

The mistake to avoid is a `ScreensaverViewModel` that owns the whole session. A ViewModel must not hold `ExoPlayer`, `ImageView` bitmaps or anything else with a View/Activity lifetime — `ScreenController` stays the host and owns every player.

What the ViewModel owns is **overlay presentation state**, replacing `OverlayStateStore` outright (not wrapping it — two holders is worse than either one):

```kotlin
@Immutable
data class ScreensaverUiState(
    val metadata: Map<OverlayType, MetadataOverlayState> = emptyMap(),
    val message: Map<OverlayType, MessageOverlayState> = emptyMap(),
    val nowPlaying: NowPlayingOverlayState = NowPlayingOverlayState(),
    val weather: WeatherOverlayState = WeatherOverlayState(),
    val forecast: ForecastOverlayState = ForecastOverlayState(),
    val clock: String = "",
    val date: String = "",
    val countdown: String = "",
    val progress: ProgressOverlayState = ProgressOverlayState(),
    val alternate: Boolean = false,
    val overlayAlpha: Float = 1f,
)

class ScreensaverViewModel(
    private val formatter: OverlayTextFormatter,   // strings/dates/countdown, no Context
    private val positionSource: PositionSource,    // video position, adapter over VideoPlayerView
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreensaverUiState())
    val uiState: StateFlow<ScreensaverUiState> = _uiState.asStateFlow()

    init {
        // Own the FlowBus subscription, so it dies with onCleared()
        viewModelScope.launch { events.collect(::apply) }
        viewModelScope.launch { tickClock() }
        viewModelScope.launch { tickDate() }
    }

    fun onItemChanged() { … }        // reset metadata/progress, flip alternate
    fun onMessage(type: OverlayType, state: MessageOverlayState) { … }  // starts/stops the auto-clear job
    fun onOverlayVisibility(mode: String, fadeCorners: Set<String>) { … }
    override fun onCleared() { … }   // nothing else to release; players are not ours
}
```

Logic that today lives inside View classes moves here and becomes JVM-testable with `kotlinx-coroutines-test`: message auto-clear after `duration`, POI rotation given a position, countdown completion, `alternate` flipping per item, "weather unchanged ⇒ don't re-emit".

### B.2 Wiring it into a DreamService

This is the part with actual friction. `viewModel()` inside composition needs `ViewTreeViewModelStoreOwner` (and the `lifecycle-viewmodel-compose` artifact). A `DreamService` provides nothing, so the recipe is to own the store yourself and clear it explicitly:

```kotlin
class DreamActivity : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
            it.setViewTreeViewModelStoreOwner(this)   // required for viewModel()
        }
    }

    override fun onDetachedFromWindow() {
        store.clear()                                  // fires onCleared(), cancels viewModelScope
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDetachedFromWindow()
    }
}
```

`TestActivity` needs none of this — `AppCompatActivity` is already a `ViewModelStoreOwner` and installs the view-tree owners.

> [!CAUTION]
> Forgetting `store.clear()` leaks the ViewModel and its coroutines for the process lifetime — and `viewModelScope` never cancels. `ScreenController.stop()` is the other place worth calling it, since `stop()` is idempotent and is reached from both the dream and the activity.

If you'd rather avoid `ViewTreeViewModelStoreOwner` altogether, construct the ViewModel explicitly in `ScreenController` instead:

```kotlin
private val store = ViewModelStore()
private val vm = ViewModelProvider(store, factory)[ScreensaverViewModel::class.java]
// stop(): store.clear()
```

Same lifecycle guarantees, no owner plumbing, no `lifecycle-viewmodel-compose` dependency — you just hand `vm.uiState` to the composition as a parameter. For a single screen this is the smaller change.

### B.3 The constraint that decides everything: no `Context` in the ViewModel

For the ViewModel to be unit-testable on the JVM, it cannot touch `Context`/`Resources`. Today the overlay Views do exactly that:

| View code | Must become |
|---|---|
| `resources.getQuantityString(part.unit.pluralsRes, …)` in `CountdownOverlay` | an injected formatter returning `String` |
| `DateHelper.formatDate(context, type, custom)` in `DateOverlay` | injected formatter, `Context` resolved once outside the VM |
| `LocaleHelper`'s alternate-locale `Context` | kept in `ScreenController`; the VM receives formatted strings or a locale-aware formatter |
| `videoPlayer.currentPosition` in `MetadataOverlay` | `PositionSource` interface |

If you skip this and pass a `Context` into the ViewModel, you get a leak (`Context` outliving the screen) *and* lose the JVM test suite in one go. This is the single most important design rule in Option B.

### B.4 What Option B buys and costs

| Gains | Costs |
|---|---|
| One state holder instead of two (`OverlayStateStore` disappears) | ViewModelStore plumbing on `DreamService`, easy to get wrong |
| Overlay logic becomes JVM-testable: timers, POI rotation, auto-clear, alternation | A formatter/position abstraction layer that does not exist today |
| Both engines can render from the same ViewModel, so XML-vs-Compose comparisons differ only in rendering | More indirection for ~8 small text overlays |
| Natural fit if the settings screens are migrated later | Loses the "prefs frozen at construction" simplicity — prefs become an injected dependency |

### B.5 If you take this path, sequence it

> [!NOTE]
> This appendix assumes the state refactor and the port land together. The recommended order is the opposite: **port the UI first, ship it behind the toggle, gather feedback, then do this.** One consequence — the clock, date, countdown and POI tickers get written once in Compose effects (Phase 7) and then *relocated* into `viewModelScope` here. That is fine provided you follow the Phase 7 rule of pushing decisions into pure functions (`countdownText(state, now)`, `poiTextAt(position, poi)`): the pure functions survive the move untouched, and only the ticking loop relocates. Reimplementing them with logic inline in the composables is what would make this order expensive.

If you do the two in one change, do not combine it with the port. Keep tests green at every step, especially since CI is `workflow_dispatch` only:

1. Extract `OverlayTextFormatter` + `PositionSource`; move the pure text logic there. Existing tests keep passing.
2. Introduce `ScreensaverViewModel` as a **thin facade over `OverlayStateStore`** — same observable output, no behaviour change. XML engine untouched.
3. Move the FlowBus subscription from `OverlayEventBridge` into the ViewModel.
4. Move tickers and POI rotation into the ViewModel; delete the timer code from the Views.
5. Delete `OverlayStateStore`; both engines now render from `vm.uiState`.
6. *Then* start the Compose port (Phases 2-10) against a ViewModel that is already settled and tested.

Steps 1-5 are the "rewrite the overlay code" part and are largely view-layer-neutral: they'd improve the XML engine whether or not Compose ever lands.
