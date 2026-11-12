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
| 19 | Phase 11 deletes the toggle | The requested feature *is* the toggle. It stays as a permanent user setting; Phase 11 is redefined as publishing the Compose path to beta users; flipping the default is the maintainer's own later decision, taken outside the plan. |
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

**Covered headlessly (2026-11-12).** `ScreenControllerFadeTest` builds the controller in Compose mode
and drives both transitions through the public paths: `onImagePrepared()` in `HIDE_AFTER_DELAY`
mode fades the layer towards 0 and the gradient of the row that fades to 0, leaving the row with
nothing in it at 1; `showOverlays()` returns both to `overlayOpacity`; and with
`overlayVisibilityDelay = 3` the layer is still at full opacity after 2s and at 0 by 3.5s. The
per-corner rule (a fading corner follows the fade, a persistent one keeps the opacity, keyed by
slot rather than position) is now a pure function, `OverlayCorner.alpha`, tested in
`OverlayCornersTest`. Still not covered headlessly: the Compose tween itself
(`animateFloatAsState`) and the drawn pixels, so a fade that ramps at the wrong *speed* would
still only show up on the TV, not in the suite.

**All four visibility modes are pinned (2026-11-12).** `ScreenControllerFadeTest` covers each mode
through the controller's own entry points: `ALWAYS_VISIBLE` keeps the layer at `overlayOpacity`
with its gradient up; `ALWAYS_HIDDEN` leaves layer and gradient at 0 until `showOverlays()`;
`HIDE_AFTER_DELAY` fades both out after the delay; `SHOW_AFTER_DELAY` holds both down and brings
them up when the delay elapses (still 0 at 2s, up by 3.5s with a 3s delay).

Writing the `SHOW_AFTER_DELAY` test found a real bug in Compose mode: the branch seeded the layer
and its gradients to 0 and never restored them, because its delayed block only animated
`overlayHelper.getOverlaysToFade()` — empty when there are no Views — and `canShowOverlays` was
only set by that animation's end action, so a user reveal was refused as well. The overlays stayed
hidden for as long as that mode was selected. Fixed in the same branch: once the delay elapses it
sets the layer to `overlayOpacity`, fades the gradients up and sets `canShowOverlays`.

**Checked on the TV (2026-11-12).** Chromecast `sabrina`, Apple video, Compose engine,
`overlay_visibility = SHOW_AFTER_DELAY`, `overlay_visibility_delay = 3`, clock top left, date top
right, both gradients on. The capture 3s after the first frame has no overlay ink in the top strip
(`0x0`); the capture 2s later carries `1789x27+66+38` and still does at +13s, so the delay is
honoured and the overlays stay up afterwards. Pressing DPAD_DOWN (bound to `showOverlays` for that
run) left them in place and the 4s reveal timeout then removed them (`0x0` again), which is the
reveal path running with `canShowOverlays` true — the thing that never happened before the fix.
Top-strip mean 0.0627 hidden vs 0.0667 shown: the gradients move with the overlays, though the
video was dark enough for that to be a small number. Screenshots: `local/screenshots/show_*.png`.

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

Two more surfaced in the follow-up pass on 2026-11-12. Screencap PNGs keep an alpha channel, so `%[fx:mean]` on a `graya` crop is not the grey level: it averages the grey and the alpha (`(grey + 1) / 2`, so a pure black frame reports 0.5). Count pixels from a `txt:` dump instead. And the bottom gradient darkens the rows the progress bar occupies, so a single threshold cannot cover both edges: the bar was counted above 30% at the bottom (white at 50% over a ~14 grey background reads 134) and above 160 at the top, where the grey fixture is 128.

- [x] Font weight/size taken from the per-overlay prefs — clock `80x27` (Compose) vs `79x27` (XML), date `122x27` in both, at default size and weight. Close, not identical: the clock is 1px wider in Compose, which is glyph rounding rather than a match.
- [x] Vertical text position matches (font-metric offset reproduced) — the clock and date ink boxes both start at y=58.
- [x] Corner position correct — clock ink starts at x=66, date at x=733, in both engines.
- [x] Multi-line blocks stack with a 3dp gap — a message block stacked above the metadata block in the same corner; combined ink `1788x130+66+113` in both engines. The gap itself was not measured in pixels.
- [x] Long text wraps at the horizontal padding instead of running under the opposite edge — a 121-character POI wrapped onto a second line in both engines and the second line stopped inside the right padding. The wrap point was inspected in the captures, not measured.
- [x] Alternating position still alternates per item — item 1: clock `80x27+66`, date `122x27+733`; after a skip, item 2: date `122x27+66`, clock `80x27+775`. Same numbers in both engines.
- [x] Auto-hide/reveal fades both the overlays and the gradients — auto-hidden frames carry no overlay ink and a reveal restores `1789x27+66+58` in both; gradients measured separately (bottom strip 0.5177 visible vs reference 0.5278; 0.531373 vs 0.531373 once faded out).
- [x] Overlay opacity setting affects both engines the same way — at `overlay_opacity = 50` neither engine has any pixel above 90% brightness in the clock box, and the text reads grey in both. At 100% the bright-pixel counts are close but not equal (470 Compose / 464 XML).
- [x] Blackout mode hides everything — every sampled pixel is `srgba(0,0,0)` across the frame with no overlay ink, in both captures.
- [x] Text colour matches (`overlayColour`, not hard-coded white) — at `overlay_colour = #FF0000` the green-channel maximum in the clock crop is 0.501961 in both engines (1.0 would mean white text), and the ink box sits at the same x/y.
- [x] Shadow present and of matching intensity — dark pixels (below 100) in the same 900×220 clock crop: 300 in XML, 326 in Compose. Comparable rather than pixel-identical, and the two captures clocked different minutes (`17:37` vs `17:46`, ink `81x34` vs `85x33`), so glyph coverage differs slightly too.
- [x] Padding from screen edge matches, including non-default `overlayPaddingHorizontal/Vertical` — at `60`/`40` the clock ink starts at x=120, y=83 in both engines (60dp and 40dp at density 2, plus the same 3px font-metric offset), and the date's right edge lands at 1800 in XML and 1803 in Compose.

### 9.4 Per-overlay results from this pass

| Overlay | Result |
|---|---|
| Message | ✅ messages 1–2 posted through the Ktor API rendered in both engines; the bottom block measured `1788x130+66+113` in both. |
| Weather (current) | ✅ real data rendered (`7° [icon] Clear sky`): `236x34+65+65` Compose vs `236x36+65+66` XML. |
| Weather (forecast) | ✅ multi-day row measured on the grey fixture with identical data (`Thu/Fri/Sat`, `5° 4°`, `8° 2°`, `8° 1°`): ink `257x115+66+206` XML vs `258x115+66+206` Compose — same height, same top edge, 1px wider because a centred child rounds differently (9.5). `local/screenshots/geo_xml.png`, `geo_compose.png`. |
| Now playing | ✅ Spotify (`com.spotify.tv.android`) playing through `NowPlayingService`: `Can’t You See · Bonobo, Aanya Martin` in XML and `Atlantic – Ambient Revisit · Jaytech` in Compose (the track changed between runs). Bright-pixel ink `596x34+66+989` and `553x34+65+989` in a `1000x260+0+820` crop, i.e. x 66/65 and the same y=989 and 34px height; widths differ only because the strings do. |
| Loading indicator with `showLoadingText = false` | ✅ the loading area is empty in both engines. |
| Weather service and Ktor API in Compose mode | ✅ Compose-mode run logged `WeatherService: Processed forecast: 3 days for London`, and API messages reached the Compose layer. |

`magick compare local/screenshots/xml_<overlay>.png local/screenshots/compose_<overlay>.png local/screenshots/diff_<overlay>.png` when ImageMagick is available.

### 9.5 Closed items

- **Weather forecast — closed.** Fixed on 2026-11-12 in two passes, both in `ui/overlays/compose/WeatherForecastOverlay.kt`.

  The first pass read the two implementations side by side and found three divergences from the View version:
  1. **Icon size came from the wrong text size.** The View version sizes the icon from the *full* overlay size (`calculateIconSize(size)`); Compose measured it at `size * TEMP_SIZE_RATIO`. Now measured from the paint font metrics at the full size, and both engines log `iconSize=49`.
  2. **The temperature separator was a Material `Text(" ")`.** The View version uses an unstyled `TextView`; Material3's `Text` merges `LocalTextStyle` and took a 24sp line height. It is now a spacer measured from a 14sp space.
  3. **The 2dp "superscript" shift was missing.** The View pads every forecast text `+2dp` top and `-2dp` bottom, which leaves the box height alone but pushes glyphs down 4px at density 2 to clear the degree symbol. Negative padding does not exist in Compose, so the shift is an `offset`.

  That still left the block 7px too tall with its top edge 6px too high, and ink boxes cannot say *which* box is responsible — so both engines were instrumented with temporary geometry logging (the View's column children via `addOnLayoutChangeListener`, the Compose ones via `onGloballyPositioned`) and run against the same grey fixture. Per day column, in pixels:

  | box | View | Compose before | Compose after |
  |---|---|---|---|
  | column | `60x131` | `60x137` | `60x131` |
  | day label | `47x36` top y=0 | `47x44` top y=0 | `47x40` top y=0 |
  | icon | `49x49` top y=40 | `49x57` top y=44 | `49x53` top y=40 |
  | temperatures | `60x38` top y=93 | `60x36` top y=101 | `60x38` top y=93 |

  The Compose label and icon heights include the 4px bottom gap that the View applies as `bottomMargin`, so the meaningful comparison is the tops and the column total. The text line boxes themselves already matched: 36px for the label in both.

  **Cause: the View version applies `elementMargin` as raw pixels.** It declares `private val elementMargin = 4` and puts it straight on `bottomMargin`, unlike `cellSpacing`, which it *does* scale (`(cellSpacing * density).toInt()`). Compose had `4.dp`, i.e. 8px on this 2x display, so both gaps were 4px too wide — +8px. The other half was the separator: the View's plain `TextView` contributes its own 14sp line box (38px) to the temperature row, while the Compose spacer had no height, leaving the row 2px short. +8 − 2 = the 6px the column was too tall.

  **Fix:** `ELEMENT_MARGIN_PX = 4` converted with the current density, so the gap is 4 raw pixels like the View's, and the separator spacer now carries the measured 14sp line height with the temperature row centre-aligned. Logged values after the fix: `iconSize=49 space=7 sepHeight=38 margin=4`, against the View's `49` icon, 38px row and 4px gaps.

  **Result:** matching box geometry (column 131px in both, children at y=0/40/93, from the temporary logging) and ink `257x115+66+206` (View) vs `258x115+66+206` (Compose). Per-row ink bands: label `252x21` at x+71/x+72, icon `238x31` at x+83/x+84, temperatures `234x16` at x+66 in both.

  **Pinned by a test:** `WeatherForecastOverlayTest` renders the column and measures the gap under the
  day name on a 2x and a 3x display (`xhdpi`/`xxhdpi`), 7px in both; it also asserts the stacking
  (day name above the temperatures, high left of low, the 24dp cell gap between days). The guard
  was checked by mutation, not assumed: converting `ELEMENT_MARGIN_PX` to `4.dp` again makes both
  density tests fail (11px at 2x, 15px at 3x) and restoring the px conversion makes them pass.

  **The 1px of width** is the only residual. The label and icon are centred in a 60px column, and a 47px label leaves 6.5px either side: the View does `(width - childWidth) / 2` in Int maths and floors to 6, Compose aligns with floats and rounds to 7. So the label and icon sit 1px right and the ink box is 1px wider; height, top edge and left edge all match. That is the same magnitude as the current-weather line above and not worth a custom `Layout` to floor a half pixel.

### 9.6 Dream path verification

Phase 9.1 asked for the real `DreamActivity`, which `TestActivity` cannot prove. The debug build was registered as the TV's screensaver (`settings put secure screensaver_components com.neilturner.aerialviews.debug/com.neilturner.aerialviews.ui.screensaver.DreamActivity`) and started the way the device does: `KEYCODE_HOME`, then `KEYCODE_BACK` on the launcher. Each engine ran cold (`am force-stop` first).

- [x] `DreamActivity` runs the screensaver in both engines — every `AerialDreamTiming` mark fires: `app.create` → `dream.attach` → `screen.built` → `dream.contentView` → `playlist.resolved` (139 Apple videos, `cached=false`) → `playback.start` → `video.firstFrame` → `video.prepared`, and `dream.stop` on exit. No `FATAL EXCEPTION` in either run.
- [x] Overlays land in the same place inside the dream — the clock ink box is `84x26+66+58` and the date `121x26+513+58` (absolute x=1733) in both engines. The progress bar sits at the bottom edge but its visible height differs by 1px at capture (75 XML vs 76 Compose), so this is an ink-box match, not a pixel match. Frame mean `0.528091` in both, against the TestActivity video baseline of `0.5277`.
- [x] No startup regression from Compose — attach→contentView 2193ms (XML) vs 1892ms (Compose); contentView→`playback.start` 8642 vs 8901 (network-bound on the Apple feed); `playback.start`→`video.firstFrame` 2321 vs 2641. Attach→first frame is 13156 vs 13434ms, about 2% apart.

Method notes. LeakCanary's heads-up notification ("1 retained objects…") draws over the dream and spoiled the first captures — wait for the banner to clear. And the Apple playlist takes ~8.6s to resolve, so a capture 8s after `dream.attach` still shows the loading indicator; wait ~20s, or poll for `video.firstFrame`.

---

## Phase 10 — Progress Bar

Independent of the overlay layer, and the state already exists: `ProgressBarEvent` (posted on `GlobalBus`) → `OverlayEventBridge` → `OverlayStateStore.setProgress` → `OverlayUiState.progress: ProgressOverlayState` (`state`, `position`, `duration`), consumed today by `ProgressBar.render()` via `ScreenController.renderOverlayState()`.

### 10.1 What the XML bar actually does

- `progressBarLocation` (`DISABLED`/`TOP`/`BOTTOM`) sets visibility and gravity at init and is re-applied when blackout ends.
- `progressBarType` (`BOTH`/`VIDEOS`/`PHOTOS`) is **not** an elapsed/remaining split. Each player evaluates it once at construction to decide whether it posts progress events at all: `VideoPlayerView` posts unless the type is `PHOTOS` (`VideoPlayerView.kt:76-77`), `ImagePlayerView` unless it is `VIDEOS` (`ImagePlayerView.kt:88-90`). The split therefore lives at the event source and both engines inherit it, so neither has to re-implement it.
- `progressBarOpacity` → `alpha`.
- `ProgressBar.render(state)` (`ProgressBarView.kt:32`) handles `START` (`animateWidth(position, duration)`: a `ValueAnimator` growing the width from `position/duration` to full over `duration` ms, linear), `PAUSE` (cancels the animator and keeps the width reached), `RESUME` (restarts it for the time left) and `RESET` (width back to zero).

### 10.2 Compose implementation

- **`ui/overlays/compose/ProgressBarMotion.kt`** — pure, and the only part with logic worth testing. `progressFraction(position, duration): Float?` returns null everywhere `animateWidth` would bail (no duration, position outside the item), and `progressBarMotion(state, position, duration)` maps `ProgressState` onto `Animate(from, to, durationMillis)`, `Pause`, `Resume` or `Reset`. Seven Jupiter tests in `ProgressBarMotionTest`.
- **`ui/overlays/compose/ProgressBarOverlay.kt`** — a 1dp white `Box` whose filled fraction is an `Animatable`: `Animate` snaps to `from` and tweens to `to` linearly, `Pause` is the effect being cancelled (the `Animatable` keeps the value it reached, which is what a paused `ValueAnimator` leaves behind), `Resume` continues from the current fraction to full over the time left, `Reset` snaps to 0. Height, alpha and alignment come from the prefs.
- **`ScreenController`** — the bar lives in the existing overlay `ComposeView` but in its own full-size `Box` beside `ScreensaverOverlays` rather than inside it, so the overlay auto-hide fade does not touch it; it hides with blackout because that layer goes `INVISIBLE`. In Compose mode the XML `ProgressBar` is kept `GONE` at init and again after blackout, and `renderOverlayState()` skips `progressBarView.render()`, otherwise both bars would animate.

### 10.3 Comparison checklist

Verified on the Chromecast (`sabrina`, 1920×1080, density 2.0, `betaDebug`) against the XML engine, 2026-11-12. Widths are ImageMagick counts of bright pixels in the bar's rows; the frame is 1920 wide, so a fraction of the clip is `pixels / 1920`.

- [x] Height and edge match — the bar occupies rows 1078–1079 in both engines, i.e. 1dp at density 2.0 flush with the bottom edge for `BOTTOM`.
- [x] Filled width tracks playback — over 7s the filled width grew 20→76px (Compose) and 29→85px (XML) on the same 283s clip (`ProgressBar: Starting progress bar animation: 0s, 283s`), ~7px/s against the 1920/283 ≈ 6.8px/s the clip predicts. The constant ~30–90px offset between the two rows is each run's start instant relative to the first sample, not a rate difference.
- [x] Pause freezes and resume continues — with `button_up_press = TOGGLE_PAUSE`, the bottom-strip mean went 0.5088 (playing) → 0.5176 (paused) → **0.5176 after 4s paused** → 0.5205 (resumed) → 0.5295 (4s later). The identical middle pair is the bar holding still.
- [x] Reset empties the bar between items — in the two-photo run in 10.4 the Compose bar reads 0px at one boundary and both engines fall back towards 0 at every 10s boundary.
- [x] Opacity at a non-default value — at `progress_bar_opacity = 50` the bar samples `srgba(134,134,134)` in both engines against a 14–19 grey background, i.e. 50% white, and the width measurement needed a lower threshold as a result.
- [x] `TOP` location — the bar occupies rows 0–1 and nothing from row 2 down, in both engines, with the same 2px height as at the bottom. Fill rate is offset-independent, so it was compared as a slope: XML `0→24→44px` and Compose `11→31→52px` over ~4s captures, about 6px/s against the 1920/300 = 6.4px/s the clip predicts.
- [x] Hiding the bar in blackout mode — six burst captures in each engine show a uniform frame with no ink box and no bar, and Compose samples `srgba(0,0,0)` at the centre and at the bar's own row. Exiting blackout calls `loadNextItem()`, so the overlays and bar return after the normal load delay rather than instantly.
- [x] `progressBarType` gating in Compose mode — with `VIDEOS` and a photo showing, and with `PHOTOS` and a video playing, rows 1078–1079 stay at 0px in both engines. The gate lives in the players, so the Compose bar simply never receives a `START`.

### 10.4 Identical consecutive items

Two items with the same `position` and `duration` — consecutive photos, which both post `START(0, duration)` — cannot stall, and both engines were measured doing it. `ProgressOverlayState` is a data class behind a `MutableStateFlow`, so an equal state is conflated and never reaches the collector. What keeps the bar restarting is `ScreenController.loadItem()`: it calls `overlayStateStore.resetForNextMedia()` and posts a `RESET` before every item, so a `START` is never adjacent to an identical `START`.

Two 10s photos from a local feed, bar width sampled every 2s:

```
compose:  987 1656 301 1012 1694 420 1083 1746 525 1192 0 570
xml:     1149 1803 409 1095 1752 461 1121 1780 467 1130 1803 515
```

Both sawtooth at every photo boundary. The XML run logged five `Starting progress bar animation` lines; the Compose run logs none, by design, because that `Timber` call sits in the `GONE` XML view. The guarantee is structural rather than local: a path that starts an item without going through `loadItem()`, or re-posts `START` with unchanged values, would be swallowed by the same equality check in both engines.

### 10.5 Closed items

Both gaps recorded here were closed on 2026-11-12.

- **Progress bar timing is covered headlessly.** `ProgressBarOverlayTest` renders the real
  composable and drives Compose's test clock: a start from zero fills 0 → 1 over the item's duration
  (0.25 / 0.5 / 1.0 at 1s / 2s / 4s of a 4s item), a start part-way through snaps to its position and
  then grows from there (0.25 → 0.4375 after 1s), pause holds the fraction across a second of clock,
  resume advances at the remaining-time rate (0.125 per 500ms while 0.75 of the bar and 3s are left)
  and goes on to full width, a start with an impossible position empties the bar, and a reset empties
  a filled one. Reading the fraction needed one production addition: `ProgressBarOverlay` now
  publishes `progressSemantics(fraction.value)`, which is also what a screen reader announces. On
  the TV (2026-11-12, real Apple video, bar at `BOTTOM`, opacity 100, type `BOTH`) the modifier
  changes nothing on screen: the bar is two pure-white rows at y=1078-1079 starting at x=0, in the
  XML engine (29 / 65 / 100px across three captures), in the Compose engine with the line
  (35 / 71 / 107px) and in the same build with the line reverted (42 / 82 / 118px). Growth per
  capture was 35-40px in every run; the absolute widths differ only because each run samples a
  different moment of the playlist. The description is real on the device too: with the video
  paused, `uiautomator dump` of the screensaver window shows the `ComposeView` holding a
  `ProgressBar` node at `[0,1078][1920,1080]` whose child carries
  `content-desc="Playback progress"` — the same two rows the pixels show. TalkBack is not the
  active accessibility service on that device (tvQuickActions is), so the tree was checked but
  no spoken announcement was heard.
- **The emission behaviour in 10.4 is pinned.** `OverlayStateStoreProgressTest` collects the store's
  flow across `setProgress(START, 0, 5000)` → `resetForNextMedia()` → `setProgress(START, 0, 5000)` and
  expects three updates after the initial value. The two starts carry equal state, so a refactor that
  drops the reset leaves `MutableStateFlow` conflating them and only two updates arrive.


---

## Phase 11 — Publishing the Compose Path

**Decision: both engines ship, permanently.** `use_compose_screensaver` stays as a user-facing setting in Settings → Appearance → Startup. Nothing is deleted — the XML overlays, `OverlayHelper`, `overlay_view.xml` and `loading_view.xml` all remain as the fallback engine.

The toggle defaults to `false`, and this plan now leaves it there: flipping the default is the maintainer's own change, made later and outside this document (agreed 2026-11-12). These are the readiness criteria that were recorded for it:

- [x] Every overlay in Phase 7 passes the comparison checklist on a physical device — every Phase 7 overlay is measured in 9.4 and the dream path in 9.6; the forecast closed in 9.5 with only a 1px centring difference left
- [x] Loading indicator matches, including with `showLoadingText = false`
- [x] Progress bar matches, including pause/resume/reset and blackout
- [x] Auto-hide/reveal, alternation, opacity and blackout verified in both engines
- [x] Weather service and Ktor message API start in Compose mode
- [x] No measure/layout regression: check `DreamTiming` marks (`screen.built`, `dream.contentView`) against the XML baseline, since Compose adds inflate/first-composition cost on a cold screensaver start — measured in 9.6: attach→contentView 1892ms (Compose) vs 2193ms (XML), attach→first frame 13434 vs 13156ms. Single samples on a network playlist, so this says "no regression", not "faster".
- [x] A minified release build succeeds — `./gradlew --no-daemon :app:assembleBetaRelease` ran `minifyBetaReleaseWithR8` and finished `BUILD SUCCESSFUL in 4m 8s`, producing a 9.9MB signed APK (`app/build/outputs/apk/beta/release/app-beta-release.apk`, `apksigner verify` reports `CN=Neil Turner`). No R8 missing-class or Compose/reflection warnings; the only Kotlin warning is an unused `Unit` expression at `compose/OverlaySlotContent.kt:24`

For as long as the default is `false`, both engines must keep working: every phase's change has to leave the `use_compose_screensaver = false` path behaviourally equivalent.

### 11.1 Getting it in front of users

The toggle defaulting to `false` is what makes a wide beta safe, and the `beta` / `googleplaybeta` flavours (already shipping with `versionNameSuffix = "-betaN"`) are the natural channel — the XML engine stays the default for everyone who does not opt in.

Both things that were worth doing before that beta goes out are now in place:

1. **Tag the engine in telemetry — done.** `ScreenController.init` sets a Firebase Analytics user property and a Crashlytics custom key, both named `use_compose_screensaver` (`"true"`/`"false"` for the property, a Boolean for the key), so a crash or a "the clock looks wrong" report can be attributed to one engine. This needed one new helper, `FirebaseHelper.analyticsUserProperty(name, value, alwaysLog)`, with the F-Droid variant as a no-op like every other method. Both calls pass `alwaysLog = true`, deliberately: `FirebaseHelper.LOGGING_END_DATE` is 2026-11-01, so the helper currently suppresses everything it sends, and an attribution tag that never fires cannot do its job — flip that argument to `false` if the logging window is meant to stay shut. The arguments are pinned by `ScreenControllerLoadingTest`: it object-mocks the helper (a JVM test has no `FirebaseApp`) and verifies `analyticsUserProperty("use_compose_screensaver", "true"|"false", true)` and `crashlyticsLogKeys("use_compose_screensaver", true|false, true)` exactly once per engine, so a renamed key, a dropped call or a swapped value fails the suite. Delivery is the part no local test can prove: the values only show up in the Firebase console or the Analytics debug view.
2. **Ask beta users to compare specific things — done: the checklist below is ready to ship with the beta notes.**

> **Trying the new screensaver UI (beta)**
>
> Settings → Appearance → Startup → *New screensaver UI (beta)*, then let the screensaver start. It applies from
> the next start, not immediately.
>
> Things worth a look, with the setting on:
>
> - **Clock and date** — still easy to read from across the room? They should sit in the same corner, at the same size.
> - **Auto-hide and reveal** — when the overlays fade out and come back, does the text return to full brightness, and do
>   the top/bottom gradients return with it?
> - **Long place names and now-playing titles** — do they wrap at the screen padding instead of running under the
>   opposite edge?
> - **Weather** — current conditions and the multi-day row: does the row line up with the day labels above it and the
>   temperatures below?
> - **Music** — do artist and track still update when the track changes, and does the per-line dimming look the same?
> - **Messages** — send one to the message API (default port 8081): does it appear in the right corner and clear itself?
> - **Remote** — pause/resume, skip and blackout: still responsive, and is nothing left on screen afterwards?
> - **Coming back from an app** — with `runAsActivity` handoff, does it look right when the screensaver returns?
>
> Then turn the setting back off and compare. If something looks different, the most useful thing to include is **which
> engine you had it on** — that is exactly what the telemetry tag above records.

### 11.2 Default flip — not part of this plan

Removed on 2026-11-12. Flipping `useComposeScreensaver` to `true` is the maintainer's own change, made later, and the plan no longer prescribes it or the verification run that went with it (the standard commands in AGENTS.md cover it). The criteria above are what this document records in support of that decision. The setting stays user-facing whether the default is `true` or `false`.

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

- [x] Compose toolchain builds (compiler plugin, BOM 2026.09.00, material3, no `composeOptions`)
- [x] `use_compose_screensaver` toggle in Settings → Appearance → Startup, defaulting to `false`
- [x] The `false` path is unchanged — verified by source diff plus a device run. No Phase 0 screenshot was kept, so there is no baseline image to diff against. Every XML-engine file was diffed against the pre-migration commit (`7d8b99a7`): `ClockOverlay`, `CountdownOverlay`, `DateOverlay`, `MessageOverlay`, `MetadataOverlay`, `OverlayHelper` and `WeatherNowOverlay` have no changes other than `package`/`import` lines; `NowPlayingOverlay`'s formatting moved into `utils/NowPlayingFormatter` with the same branches, separator and blank handling; `xml/WeatherForecastOverlay` only lost its `BuildConfig.DEBUG` coloured debug backgrounds. In the shared host, `ScreenController` gained `useComposeOverlays` gates but the `else` branch calls the same methods as before (`overlayHelper.buildOverlaysAndIds`, the same progress-bar setup); the only changes on that branch are `progressBarLocation`/`progressBarOpacity` hoisted to fields read at construction instead of inside `setupViews`, and the message-API/weather predicates asked of slot prefs (`EnabledOverlays`) instead of instantiated Views. Device run on the XML engine (custom feed, clock+date, forecast, message slot, bar `BOTTOM` at 50%): no `FATAL EXCEPTION`, `Progress bar: 0.5, BOTTOM`, `Playlist size: 1`, `WeatherService: Processed forecast: 3 days for London`, and the forecast ink box `257x115+66+206`, the same box measured for the XML engine in the same fixture configuration earlier in this migration. That is an ink-box comparison only: the pixels of the two captures were never diffed, and the clock/date ink in this run (`81x28+66+57`) was taken at a different time of day from the 9.4/9.6 captures (`84x26+66+58`), so it is not a like-for-like figure. With `message_api_enabled = true` the Ktor server starts (`Ktor server listening on port 8081`) and `GET /status` returns 200, which is the branch the predicate rewrite touched
- [x] Compose renders inside the real `DreamActivity`, not just `TestActivity`
- [x] Every overlay in Phase 7 passes the on-device comparison checklist — results in 9.4, forecast closure and the 1px centring rounding in 9.5
- [x] Loading indicator matches Phase 8
- [x] Progress bar matches the XML behaviour (Phase 10)
- [x] Auto-hide/reveal, alternation, opacity, blackout all behave the same way in both engines — see the checklist above, where some of those comparisons are exact ink boxes and others are close-but-not-equal counts
- [x] Weather service and Ktor message API still start in Compose mode
- [x] `testBetaDebugUnitTest` passes
- [x] `spotlessCheck` passes
- [x] `assembleBetaDebug` succeeds
- [x] A minified release build succeeds — `./gradlew --no-daemon :app:assembleBetaRelease` ran `minifyBetaReleaseWithR8` and finished `BUILD SUCCESSFUL in 4m 8s`, producing a 9.9MB signed APK (`app/build/outputs/apk/beta/release/app-beta-release.apk`, `apksigner verify` reports `CN=Neil Turner`). No R8 missing-class or Compose/reflection warnings; the only Kotlin warning is an unused `Unit` expression at `compose/OverlaySlotContent.kt:24`

---

## Decisions

Settled:

1. **Toggle lifetime** — permanent, user-facing, in Settings → Appearance → Startup. XML is not deleted.
2. **Default** — stays `false` in this plan. Flipping it is the maintainer's own change, made later and outside this document (2026-11-12).
3. **Phase 1.2** — do it: `ScreenController` moves to `ui/screensaver/xml/`.
4. **Material3** — yes, BOM-managed, for `Text` and `CircularProgressIndicator`.
5. **Test device** — physical TV/streamer for all visual verification.
6. **`ProgressBarType`** — resolved: it is not an elapsed/remaining split. `BOTH`/`VIDEOS`/`PHOTOS`
   decides which players post progress events, evaluated once per player at construction (10.1).

Still open (decide when the relevant phase starts):

- **Gradients and brightness dimmer** — out of scope for this migration; they stay XML View layers. Revisit only if the Compose layer needs to own them.
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
