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

- `OverlayText(text, sizeSp, weight, modifier, colour, textAlign, maxLines)` — a thin wrapper over `Text` that applies the style, the font-metric offset and the width clamp. It does not apply opacity: the corner's `visibleAlpha`/auto-hide alpha is folded in by `ScreensaverOverlays` through `OverlayCorner.alpha`.
- `OverlayFade(fadeMillis)` — the alpha `Animatable` behind the "fade out, swap the content, fade back" sequence both engines use on a change: `fadeOut()`, `fadeIn()`, plus `snapHidden()` for an arrival from nothing and `reset()`. POI text uses 1000ms and now playing and messages 300ms, matching their View counterparts, and the `CubicBezierEasing(0.4f, 0f, 0.6f, 1f)` curve reproduces `AccelerateDecelerateInterpolator`. Tests hold the same instance the composable draws with, because `captureToImage()` cannot see a node's own `Modifier.alpha` under Robolectric (9.7).
- `OverlayIcon(resId, colour, size)` — `AndroidView` wrapping `SvgImageView` + `setSvgResource` + `setTintColour`. Used by the weather overlays now; replaced by a proper SVG/KMP path later.

**Alpha is applied with `Modifier.alpha`, after a device A/B decided against `graphicsLayer`.** The
overlay fades were briefly switched to `Modifier.graphicsLayer { alpha = ... }` so the animation read
happened in the layer block instead of in composition, including a `() -> Float` per corner in
`ScreensaverOverlays`. The pre-change code was then rebuilt from `0e07f29d^` as a signed `betaRelease` and
both builds were measured on the TV with one protocol: Compose overlays on, Overlay visibility "Hide after
delay" (4s), 600ms fades, `TestActivity` restarted per capture, `dumpsys gfxinfo framestats` polled through
startup so the fade burst could be picked out afterwards - 36-37 frames at 16.7ms spacing over 0.60s, the
whole fade, on a static photo where nothing else drew. Eight captures per build in two interleaved blocks
(before, after, before, after, swapping the two APKs):

| fade, 8 captures each | frames | frame duration p50 | draw-record p50 | spacing p50 |
|---|---|---|---|---|
| `Modifier.alpha` | 36-37 | 20.78ms | 0.295ms (0.281-0.314) | 16.68ms |
| `graphicsLayer` | 36-37 | 20.79ms | 0.327ms (0.298-0.357) | 16.68ms |

Pacing and frame duration are identical: the recomposition the deferred read avoids was never on the
critical path, because a 20.8ms frame against a 16.7ms cadence leaves slack and an overlay is a few dozen
composables. The one axis that moved is the time spent recording draw commands, up 0.03ms per frame (about
1.2ms across the fade), which is the layer being present at alpha 1 on the overlays that are not themselves
fading. That is paid on every frame the overlay layer draws, while the recomposition it avoids happens only
during a 300-1000ms fade - and `Modifier.alpha` allocates nothing at alpha 1 (`Modifier.alpha(1f)` returns
`Modifier` itself, checked in a throwaway unit test where `alpha(0.5f)` returned a `GraphicsLayerElement`)
and a layer only while fading. The trade was therefore a small loss, so all four sites, and the corner's
lambda and State plumbing with them, went back to `Modifier.alpha(...)` and `by animateFloatAsState`.

Compose's guidance to use `graphicsLayer` for animated alpha still holds as a principle - it keeps the
animation out of composition - and nothing here contradicted it; this overlay simply never had the
recomposition cost the guidance targets. Revisit if a profile shows overlay recomposition inside a fade
window mattering: the deferred-read form is the fix, at the price of one graphics layer per overlay at rest.
One difference to remember if that happens is that `alpha`'s layer sets `clip = true` while `graphicsLayer`
leaves it false.

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

**Refactored to one target per mode (2026-11-12).** The seeding block and the four-branch `when` are
gone. `overlayVisibilityPlan(mode, overlayOpacity)` (`ui/overlays/OverlayVisibilityPlan.kt`) returns
one `OverlayVisibilityTarget` - overlay alpha plus gradient alpha - per mode, the target to move to
after the delay, and whether a reveal may run while waiting. `ScreenController` applies a target
through `applyOverlayTarget` (settle immediately) and `animateOverlayTarget` (fade), which set the
View alphas, the gradients and the Compose fade from the same two numbers; `hideOverlays` and
`showOverlays` are thin wrappers over the same pair, so the mode and the reveal path can no longer
disagree either.

Two things the refactor turned up. The delay now lives in one coroutine at the call site: the first
attempt passed it down as an animator `startDelay`, and a zero-length gradient animator ignores that,
so the gradient jumped up while the overlays were still hidden (caught by
`ScreenControllerFadeTest`). And the XML gradient rule is now the same in both directions - a row
moves only when it has fading overlays and nothing persistent - where the old show branch also set
rows with a persistent overlay to 1; those rows were already at 1, so nothing moved on screen.

`OverlayVisibilityPlanTest` pins the mapping: each mode's start and end target, that the two delayed
modes are each other's opposite, that an unknown mode settles hidden, and that a gradient is up
exactly when its overlays are. Re-checked on the TV in both engines after the refactor: Compose
`SHOW_AFTER_DELAY` has no overlay ink at +2s and `1789x27+66+38` at +6s; XML `HIDE_AFTER_DELAY` has
`1789x27+66+38` at +2s and none at +6s (`local/screenshots/mode_*_early.png` / `_late.png`).

XML reveal after the delay, same run settings plus `button_down_press = SHOW_OVERLAYS`: the press logs
`InputHelper: Action: SHOW_OVERLAYS, ButtonPressType: PRESS`, one second later the top strip carries
`1789x27+66+38` and the strip mean rises 0.0627 -> 0.0666 (overlays and gradient up). Four seconds
after the press the mean is 0.0648 with no ink above threshold - the fade-out in progress - and at
press+10s it is back to 0.0627 with no ink: the reveal timeout took them down again. The activity was
still the resumed window at the end, so the last frame is the screensaver, not another screen
(`local/screenshots/x2_*.png`). An earlier attempt was discarded because the activity finished at the
press (`Duplicate finish request` in logcat) and the capture that should have shown the timeout
showed the launcher instead.

The fade-out itself was caught as a frame rather than a mean: with `overlay_fade_out_duration` raised
to 2000ms (the default 600ms gives too narrow a window for `screencap`) and a capture 5.2s after
the press, the clock peaks at 104/255 - about 37% of its full brightness, top-strip mean 0.0642
against 0.0666 up and 0.0627 down - so the overlays and the gradient are visibly mid-fade; the
frame 3s later is back to background (peak 16/255, mean 0.0627). `local/screenshots/xmid2_half.png`,
with full / mid / gone stacked in `xmid2_compare.png`. The earlier attempt at the default 600ms
landed only ~20% into the fade (peak 212/255), which is why the longer fade was used.

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

The repo has no instrumentation tests (`app/src/androidTest` is empty), but Robolectric runs the framework on
the JVM, and seven composables are covered that way: `ScreensaverOverlaysTest`, `WeatherForecastOverlayTest`,
`ProgressBarOverlayTest`, `LoadingIndicatorTest` and the three fade tests - `MetadataOverlayFadeTest`,
`NowPlayingOverlayFadeTest`, `MessageOverlayFadeTest`. Keep formatting and decision logic in pure code, where the
existing JVM suite already covers it - `CountdownFormatter`, `CountdownTimeParser`, `TrackNameShortener`,
`OverlayPaddingHelper`, `OverlayOpacityHelper`, `ScheduledBlackoutWindow` are the model to follow. Reach for a
Robolectric compose test only for what a pure function cannot express: layout geometry, and animation timing.

Three things worth knowing before writing another one:

- **`captureToImage()` does not see a node's own `Modifier.alpha`** under Robolectric - it reads 1.0
  throughout. Alphas are therefore read from an `OverlayFade` the test constructs and passes in, which is why
  the fade durations are parameters of the composables and the constants are `internal`.
- **A state change needs `waitForIdle()` before the clock moves, then one frame.** The first attempt advanced a
  frame straight after the state write and read alpha 1.0 at +150ms, i.e. the effect had not started; the
  mirror-image mistake, `waitForIdle()` with no frame, reads the fade one frame short (0.62 instead of 0.5).
- **`delay` is not driven by the Compose test clock**, and it cannot be shortened either - a zero
  wait simply spins the poll loop. What is injectable is the wait itself: `MetadataOverlay` takes a
  `waitForNextCheck` hook in place of `delay`, so a test releases one check at a time instead of
  waiting a real interval out.

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
| 7.4 | **Message** | `compose/MessageOverlay.kt` | Per-message size/weight overrides from `MessageOverlayState`; blank text ⇒ hidden; `duration` auto-clear; `messageAnimateChanges` has the two modes the View has - off shows and hides the line outright (the View's `applyVisibilityImmediate`), on fades out 300ms, swaps the message while invisible and fades back in via `OverlayFade(300)`, with `snapHidden()` for an arrival from nothing. No `ChangeBounds` equivalent, so neighbours reflow instantly where the View animates the layout. Pinned by `MessageOverlayFadeTest`, both modes, and measured on the device in 9.7. |
| 7.5 | **Countdown** | `compose/CountdownOverlay.kt` | Reuse `CountdownTimeParser` / `CountdownFormatter` (shared, unit-tested) + the `countdown_target_message` / `countdown_label` strings; 1-second tick until completion. |
| 7.6 | **Now Playing** | `compose/NowPlayingOverlay.kt` | `NowPlayingFormat` string assembly + `TrackNameShortener`; album art is a `Bitmap` on `MusicEvent` → `Image(bitmap.asImageBitmap())`, sized `lineHeight × 2.5` with a 12dp leading margin on the text. A track change runs the View's fade - `OverlayFade(300)` out, swap track and art while invisible, in - and `snapHidden()` when nothing was on screen, pinned by `NowPlayingOverlayFadeTest` (9.7). Album art is the one part no capture has exercised: the device's Spotify sessions report `albumArt=null`. |
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
| Message | ✅ messages 1–2 posted through the Ktor API rendered in both engines; the bottom block measured `1788x130+66+113` in both. Layout only - the show/hide animation is in 9.7. |
| Weather (current) | ✅ real data rendered (`7° [icon] Clear sky`): `236x34+65+65` Compose vs `236x36+65+66` XML. |
| Weather (forecast) | ✅ multi-day row measured on the grey fixture with identical data (`Thu/Fri/Sat`, `5° 4°`, `8° 2°`, `8° 1°`): ink `257x115+66+206` XML vs `258x115+66+206` Compose — same height, same top edge, 1px wider because a centred child rounds differently (9.5). `local/screenshots/geo_xml.png`, `geo_compose.png`. |
| Now playing | ✅ Spotify (`com.spotify.tv.android`) playing through `NowPlayingService`: `Can’t You See · Bonobo, Aanya Martin` in XML and `Atlantic – Ambient Revisit · Jaytech` in Compose (the track changed between runs). Bright-pixel ink `596x34+66+989` and `553x34+65+989` in a `1000x260+0+820` crop, i.e. x 66/65 and the same y=989 and 34px height; widths differ only because the strings do. The track-change fade is in 9.7. |
| Loading indicator with `showLoadingText = false` | ✅ the loading area is empty in both engines. |
| Weather service and Ktor API in Compose mode | ✅ Compose-mode run logged `WeatherService: Processed forecast: 3 days for London`, and API messages reached the Compose layer. |
| Metadata (POI rotation) | ✅ a local feed with POIs at 0/8/16/24s rotated through the same strings in both engines (ink `257x34+67+58`, `259x34+65+58`), and after the 2026-11-12 fade change the transition between POIs matches too: ~1.8s both ways, text blank in the middle (9.7). |
| Countdown | ✅ same layout in both engines (`284x26+67+58` XML vs `283x26+67+58` Compose), ticking, and the completion message at the target time (`92x26+67+58` in both); see 9.7. |

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

### 9.7 POI rotation, overlay fades and countdown — what 9.4 did not measure

The POI and countdown captures were made on 2026-11-12 on the Chromecast (`sabrina`, 1920x1080, `betaDebug`),
engines run cold (`am force-stop` first), against fixtures served over `adb reverse tcp:8765` from a local
HTTP server (`local/poi-feed/`), so the same media and the same metadata ran in both engines. The fade work
was done on 2026-11-13.

**Metadata / POI rotation.** The fixture is the 30s clip with `pointsOfInterest` in the feed JSON at
0/8/16/24s ("Piccadilly Circus", "Trafalgar Square", "Tower Bridge", "The Shard"), `METADATA1` in
`slot_top_left1`, every other slot empty, alternation off, overlays always visible, both gradients off.
The strings match: at +2s the ink box is `257x34+67+58` in both engines, at +16s `259x34+65+58` in both.
(The +25s captures read `210x34+65+58` in XML and `156x26+65+58` in Compose because they straddled the 24s
boundary at different poll phases — "Tower Bridge" against "The Shard", visible in `poi_texts.png` — not a
layout mismatch.)

The **transition between POIs** was first measured as a mismatch and then changed to match.
`screenrecord` of the boundary, pulled apart with `ffmpeg`, measuring the peak brightness of a
`1000x200+0+20` crop (255 is white text, ~17 the video behind it), 5fps:

| engine | frame peaks across the change |
|---|---|
| XML | 255 ... 249 202 132 72 20 19 (min) 62 133 199 250 255 |
| Compose, before the change (`Crossfade(tween(300))`) | 255 ... 231 255 — one frame, never blank |
| Compose, after the change | 255 ... 228 176 94 37 17 (min) 34 103 189 225 255 |

At 30fps over the same crop, frame index of the first value below 250 to the first back at/above 250:

| | span | trough (peak <= 25) | shape |
|---|---|---|---|
| XML | 189-243, 55 frames (~1.8s) | 212-222, ~0.33s | slow start, minimum 17 |
| Compose, after the change | 181-234, 54 frames (~1.8s) | 204-211, ~0.23s | slow start, minimum 17 |

The change: `compose/MetadataOverlay.kt` lost the `Crossfade` and now drives an `Animatable` alpha - fade to
0 over `POI_FADE_MILLIS = 1000`, swap the text at zero, fade back to 1 over another 1000ms - which is what the
View does with `animate().alpha(0f)` then `alpha(visibleAlpha)` in the end action. A first build with the
default `tween` easing came out front-loaded (peaks 235 135 69 25 17 on the way down against the View's 249 202
132 72 20) because Compose's default is fast-out-slow-in while `ViewPropertyAnimator` defaults to
`AccelerateDecelerateInterpolator`; the fade now uses `CubicBezierEasing(0.4f, 0f, 0.6f, 1f)` to match, which
is the run in the tables above. The remaining difference is the ~0.1s narrower trough, consistent with the
View chaining its second animation from the first one's end action rather than running them back to back.

**Pinned by a test.** `MetadataOverlayFadeTest` drives the real composable on Compose's test clock and asserts
alpha 1.0 at the change, 0.5 at +500ms, 0 at +1s, the swap landing on the next frame at alpha 0 (a blank gap,
not a crossfade of the two strings), 0.5 at +1.5s and 1.0 at +2s. Two structural notes came out of writing it.
The curve lives in `OverlayFade`, so the test can hold the same `Animatable` the overlay draws with -
`captureToImage()` was tried first and reads alpha as 1.0 throughout, because a node's own `Modifier.alpha`
layer is not part of its captured bitmap under Robolectric. And the poll's `delay` is not driven by the
Compose test clock, so `MetadataOverlay` takes a `waitForNextCheck` hook in place of `delay`; this test
releases the first check and parks the loop (a no-op wait would spin it). The intervals themselves are pinned
by the hook's recorded arguments in `MetadataOverlayHoldTest` - 1s before the first check, 3s after a change,
and 3s again for a pending change that is being held - rather than only by the constants. Mutation-checked: with `POI_FADE_MILLIS` set to 300 the test fails at 0.642 instead of 0.5.

**Now playing.** The View version has a second fade the Compose one did not: on a track change it fades the
line out over 300ms, swaps track and art while it is invisible, then fades it back in - `AnimatedVisibility`
in Compose only covered the line appearing and disappearing, so a track change swapped its text instantly at
full brightness. `compose/NowPlayingOverlay.kt` now holds the displayed track (text and art together) and runs
`OverlayFade(300)` - the same helper the POI line uses, with its own duration - out, swap, in.

Checked on the TV with Spotify playing (`com.spotify.tv.android`, `STATE_PLAYING`) and `KEYCODE_MEDIA_NEXT`
every 5s, `screenrecord` for 24s, peak brightness of a `1200x120+0+0` crop at 10 and 30fps:

| engine | 30fps across one skip: ramp out | blank | ramp in |
|---|---|---|---|
| XML | 449-456 (~230ms) | 457-488 (~1.1s) | 489-498 (~330ms) |
| Compose | 463-469 (~200ms) | 470-517 (~1.6s) | 518-523 (~200ms) |

The **blank is not the fade**: every skip sends Spotify to `STATE_BUFFERING`, and `NowPlayingService` posts an
empty `MusicEvent()` while `active` is false (`Posting music event (updateMetadata active=false ...):
MusicEvent(artist=, song=, albumArt=null)` in logcat), which the line renders as no text. The blanks measured
across four skips were 1.1-2.2s and vary with buffering, in both engines, and both engines put the same
~200-350ms ramps either side of them. So the recording proves the two engines behave alike on the path users
actually see, but it cannot isolate the track-to-track fade - Spotify's buffering blank sits between the two
events.

That path is pinned headlessly by `NowPlayingOverlayFadeTest`: the real composable on Compose's test clock, a
state change from one track to the next, alpha 1.0 -> 0.5 at +150ms -> 0 at +300ms, the swap on the next frame
at alpha 0, 0.5 at +450ms, 1.0 at +600ms. The fade constants are `internal` so the tests build the same `OverlayFade` the overlay defaults to; mutation-checked, `NOW_PLAYING_FADE_MILLIS = 1000` fails it at 0.955
and `POI_FADE_MILLIS = 300` fails the POI test at 0.642.

**Appearing from blank spent 300ms fading nothing.** `NowPlayingOverlay` ran `fade.fadeOut()` on every
change, where the View only fades out a line that is still visible (`if (isVisible(alpha, visibleAlpha))`) and
leaves a gone one at alpha 0. Spotify sends an empty event before the new track, so every track change began
with a 300ms fade-out of an empty line and the new one appeared 300ms later than the View's. It now snaps to
zero when nothing is displayed and fades in directly, as `MessageOverlay` already did, and
`NowPlayingOverlayFadeTest` pins it: one frame for the effect and one to draw, the new text is on screen at
under 0.1 alpha, reaching 1 within 400ms. Mutation-checked by restoring the unconditional `fadeOut()`, which
fails with the text simply not there after those two frames.

Two other suspected differences are sound, recorded so they need not be re-investigated. A fade running while a
corner auto-hides multiplies two independent alphas - the corner's (`OverlayCornerColumn(alpha = ...)`) and the
line's own (`Modifier.alpha(fade.alpha.value)`) - exactly as the View multiplies its per-View alpha by the
content alpha, so there is nothing to fix and little to test beyond Compose's own composition. And Sonos cannot
re-fade the line on every poll: `SonosService` compares `SonosClient.Track` (three strings) before fetching art,
so a `MusicEvent` with a new `Bitmap` only appears when the track actually changed. The remaining weakness is
latent - `MusicEvent` equality is Bitmap identity, so anything that re-posts the same track with re-decoded art
would read as a new event.

The **poll cadence** also differed and was aligned on 2026-11-12: Compose looked every 1s flat, where the
View waits 1s before its first check and then backs off to 3s after a change. `MetadataOverlay` now does the
same - first check after `POI_POLL_MILLIS`, then `POI_SETTLE_MILLIS = 3000` after a change and 1s otherwise.
The 3s runs alongside the 2s fade rather than after it (a `coroutineScope` with the fade in a `launch`, as the
View's non-blocking `animate()` does), so the next check lands 3s after the change, not 5s.
That is a code-level alignment, not a device measurement: both cadences pick up a POI within a second of its
start time when POIs are 8s apart, so the recordings cannot tell them apart. (One difference remains inside the
comparison itself: the View tracks the last POI *index* and Compose compares the string, so a POI change to
identical text fades in the View and not in Compose. Nothing is visible either way.)

The View's `isFadingOutMedia` guard was ported on 2026-11-12, closing the last known POI difference. The
flag lives on `OverlayUiState.fadingOutMedia`; `ScreenController.fadeOutCurrentItem` sets it through
`overlayStateStore.setFadingOutMedia(true)` beside the loop that flags the View overlays, and it is cleared
when the next item's metadata lands (`setMetadata`, which is the View's `updateLocationData` reset) or on
`resetForNextMedia`. `OverlaySlotContent` passes it into every `MetadataOverlay`, whose poll now uses the View's
condition - `shouldUpdate && !fadingOutMedia` - so a POI that falls due mid fade-out is held until the next
item, and a pending change keeps the 3s interval the View uses. The store call is gated on `useComposeOverlays`:
the emission re-renders the View overlays, and their `updateLocationData` would clear the flag it had just been
given, so writing it unconditionally would have broken the XML engine's own guard.

This one is code-level parity rather than a device measurement. The window is the 800ms media fade-out, which
starts as the item ends or is skipped, and the metadata line sits underneath the loading view fading to black
over it - a frame captured then cannot say whether the text held or animated. The mechanism is exercised only
when a skip lands just before a POI boundary.

**Pinned by tests - and one bug found doing it.** As first ported, the guard read `fadingOutMedia` directly
inside the poll's `LaunchedEffect`, which captures its parameters when the effect launches. The flag is set
later, on the item's fade-out, so the running poll kept reading the `false` it started with and never held
anything: the port looked right and could not have worked. It is read through `rememberUpdatedState` now, and
the test that caught it fails if that is undone.

`MetadataOverlayHoldTest` drives the poll one check at a time through a `waitForNextCheck` hook, because the
poll's `delay` is not on the Compose test clock and cannot be waited out from a test. That hook is the only
test seam on the composable: the `pollMillis` and `settleMillis` overrides it replaced were dead surface once
the wait itself was injectable, since neither changed any timing a test could observe. A check released with
the flag set must leave the text alone and never take the alpha below 1 over 120 frames; the same check
released after the flag clears must fade out, swap and land on the next POI. `ScreenControllerFadingOutMediaTest`
covers the engine gate: `onImageFinished()` sets the store flag in Compose mode, and in XML mode flags the View
`MetadataOverlay` it finds in the hierarchy (the positive control, so the assertion cannot pass by the path
never running) while leaving the store untouched. Mutation-checked: capturing the parameter again fails the
first hold test, removing `!isFadingOutMedia` fails both, and dropping the `useComposeOverlays` gate fails the
XML case.

A third thing the tests had to get right: a frame's continuation is dispatched to the main looper, so each
`advanceTimeByFrame()` needs a `waitForIdle()`, and a state change needs two frames before
`rememberUpdatedState` sees it - a single frame reads the previous value and makes the guard look broken when
it is not.

**Countdown.** Static photo fixture, `COUNTDOWN` in `slot_top_left1`, label "Launch in", target 13 minutes
out, and each capture aligned to clock second 30 so the two strings differ only in the minute digit
(`Launch in 11m 30s` against `Launch in 10m 31s`). Ink box `284x26+67+58` (XML) vs `283x26+67+58` (Compose):
same left edge, same top, same 26px height, 1px narrower — the same glyph-rounding magnitude as the clock in
9.3, and the differing-digit crop diff of 2443 pixels is consistent with that. Ticking shows in consecutive
captures a second apart (`271x26` -> `270x26` XML, `252x26` -> `251x26` Compose, the width changing with the
seconds value). Completion was checked by starting with a target two minutes out and capturing 9s past it:
both engines show the custom completion message and nothing else, `92x26+67+58` in both, still unchanged 4s
later — the Compose `produceState` does stop ticking at zero as the View's job does.

**Message.** The one overlay whose fade is a user setting. `message_animate_changes` (default off) picks
between the View's `applyVisibilityImmediate` - the line appears and disappears outright - and its 300ms out,
swap, in. Compose had neither: it always used `AnimatedVisibility(fadeIn/fadeOut(300))`, so with the default
pref it faded where the View popped, and with the pref on a message-to-message change swapped instantly where
the View fades. `compose/MessageOverlay.kt` now reads the pref: off draws the text directly, on holds the
displayed message and runs `OverlayFade(300)` out, swap, in, with `snapHidden()` for an arrival from nothing
(a fresh `OverlayFade` starts at alpha 1, where the View leaves a hidden overlay at 0).

**Pinned by `MessageOverlayFadeTest`**, both modes. Animated: alpha below 0.1 on arrival rather than popped in,
past 0.3 at +150ms, 1.0 by +350ms; a change is 0.5 at +150ms, 0 at +300ms, the swap on the next frame at alpha
0, 0.5 at +450ms, 1.0 at +600ms. Not animated: the new message is on screen at the first settled frame and the
curve is still 1.0 at +150ms, i.e. never touched, and clearing hides the line at once. Mutation-checked:
`MESSAGE_FADE_MILLIS = 1000` fails both animated cases, forcing the animated path fails the pref-off case, and
forcing the instant path fails both animated ones.

**Measured on the device (2026-11-13)**, the same way as the POI and now playing fades: static photo fixture
(a uniform `#202020` 1920x1080 image served from `local/poi-feed/`), `MESSAGE1` alone in `slot_top_left1`,
`ALWAYS_VISIBLE`, both gradients off, the message posted two seconds into a 14s `screenrecord` through the
Ktor API (`POST /message/1`, `duration` 3, `adb forward tcp:8081`), peak brightness of an `800x160+0+20` crop
at 30fps - background 16-17, text 255:

| engine | `message_animate_changes` | appears | clears |
|---|---|---|---|
| XML | off | 16 -> 255 in one frame | 255 -> 17 in one frame |
| Compose | off | 16 -> 255 in one frame | 255 -> 17 in one frame |
| XML | on | 11 frames: 33 32 46 76 98 119 178 199 243 243 255 | 8 frames: 245 234 217 161 161 78 81 25 |
| Compose | on | 6 frames: 124 166 160 220 220 255 | 7 frames: 234 220 204 181 140 81 23 |

With the pref off both engines pop, which is the behaviour the fix restored; with it on both fade through
intermediate levels and neither pops. The View's own log agrees with the intent - `Fading in...` then
`Transition... VISIBLE` 314ms later, and `Fading out...` then `Clearing message` 314ms later.

The ramp extents are not a like-for-like measurement. A 300ms fade is at most nine frames at 30fps, and both
series contain repeated and non-monotonic samples (243 243, 161 161, 166 160) - the screenrecord encoder
duplicates frames, so a monotonically increasing fade reads back quantised. Compose's single fade reading six
frames against the View's eleven for the same 300ms is therefore sampling as much as anything; where they do
differ by construction is that the View runs its parent `TransitionSet` `Fade` on top of the alpha animation
when it becomes `VISIBLE`, i.e. two overlapping fades for one appearance. The 300ms itself is pinned by the
test, not by these frames.

One difference remains, and is not fixable without a custom `Layout`: the View's `ChangeBounds` animates the
parent layout as the line appears and disappears, so neighbours reflow smoothly there and instantly in
Compose.

Screenshots: `local/screenshots/poi_{xml,compose}_{0,1,2,3}.png`, `poi_texts.png`, `cda_*.png`,
`cdone_*.png`; recordings `local/poi_{xml,compose}_a.mp4`, `local/np_np_{xml,compose}.mp4` (now playing,
`local/np_xml_f/` and `local/np_comp_f/` frames), `local/message_{xml,comp}_{off,on}.mp4` (message,
`local/msgf_msg_*` frames).

---

### 9.8 Parity review — what the Compose path was missing

A review pass over the two engines on 2026-11-13, file by file and preference by preference, looking for
anything the Compose layer never received. Two gaps were found and fixed.

**The configured message text never reached the Compose layer.** `message_line1..4` are the fixed message a
slot shows, and `OverlayHelper` hands them to the View overlays when it builds them
(`MessageOverlay.message(prefs.messageLine1)`). Nothing seeded the Compose store: `state.message[type]` is
written only by `OverlayEventBridge` on a Ktor `MessageEvent`, so a slot assigned to a message showed
nothing in Compose mode until an API message arrived. `OverlaySlotContent` now passes the slot's configured
string and `MessageOverlay` starts from it - `state?.text ?: fallbackText`, with a null state meaning "no
API message yet" - and an API message replaces it, while an auto-clear leaves the line blank instead of
restoring the configured text, which is what the View ends up doing too. Pinned by two cases in
`ScreensaverOverlaysTest`; dropping `fallbackText` back to `""` fails the first of them. Device-checked the
same day on `betaRelease` (1.8.6-beta4): `message_line1` set to "Configured line one", `MESSAGE1` in the
top-left upper slot, Compose overlays on - the line renders with no API message posted and no server
running (`local/msg_configured_topleft.png`), and `dumpsys activity top` shows the 1920x1080 `ComposeView`
inside `overlay_view` with a second one for the loading indicator, so the Compose layer is what drew it.
Settings restored afterwards: message text cleared, the slot back to Now Playing #1, engine off.

**A reveal ran at the hide speed.** `ScreensaverOverlays` took one `fadeDurationMillis`, wired to
`overlay_fade_out_duration`, for a single `animateFloatAsState` covering both directions - while the View
engine uses `overlay_fade_in_duration` when the alpha heads for visible and `overlay_fade_out_duration` when
it heads for 0, and both are user-facing settings (Settings → Appearance → Animations). The layer now takes
both and picks by target: `if (fadeAlpha > 0f) fadeInDurationMillis else fadeOutDurationMillis`, since 0 is
`hiddenOverlayTarget` and anything above it is a reveal towards `overlayVisibleAlpha`. No test: the corner
alpha is applied with `Modifier.alpha`, which `captureToImage()` cannot see under Robolectric (4.2, 9.7), so
this is a code-path fix rather than a measured one.

**Checked and found equal.** Every `OverlayType` has a Compose branch carrying the same size, weight and
format preferences: clock (minute-boundary ticker through `ClockFormatter`, 12/24 from prefs or the system),
date, countdown (shared `CountdownTimeParser`/`CountdownFormatter`, stops at zero), metadata 1-4 (same
`findLast { it <= seconds }` POI choice through `PoiText`, newlines flattened, `isFadingOutMedia` hold),
message 1-4 (per-message size/weight overrides, `duration` auto-clear, both `message_animate_changes`
modes), music 1-2 (`NowPlayingFormatter` with `nowplaying_shorten_track_name`), weather 1-2 (icons through
`OverlayIcon`, tinted with `overlayColour`). The progress bar's video/photo gating (`progress_bar_type`) and
`progress_bar_location` are applied in the player Views, before either overlay layer; gradients are still
the View images in both modes, only the row decision differs (`setComposeGradientAlpha`); the loading
indicator reads `showLoadingText` and `loading_text_*` through `ScreenController`. Locale switching
(`locale_screensaver`, `clockForceLatinDigits`) is applied by `LocaleHelper.alternateLocale` to the context
both engines render with, and `ClockFormatter.format` uses the default locale that helper sets.

**Where the engines deliberately differ.** No `ChangeBounds`: neighbours reflow instantly where the View
animates layout. Album art is Compose-only in practice, because the device's Spotify sessions report
`albumArt=null` and only the Sonos path fills it. POI rotation polls on the Compose clock (1s, then 3s after
a change) where the View has a ticker - the durations match, the mechanism does not.

**Key and gesture handling never sees the engine flag.** `InputHelper.handleKeyEvent` runs from
`dispatchKeyEvent` on `DreamActivity` and `TestActivity` before the view tree, and every bindable action
(skip next/previous, music next/previous, speed, seek, brightness, show overlays, blackout, mute, pause,
looping, restart, exit to settings, exit) calls the same `ScreenController` method either way. Hover-to-exit
is handled in `dispatchGenericMotionEvent` on the activity. Gestures are a touch listener on
`controller.view` (`setupGestureListener`) in both modes, and the Compose overlay tree declares no pointer
input, clickable or focusable modifiers, so it has nothing to consume a swipe or take focus with. What has
not been tested is a gesture on a phone with Compose overlays on: the paths are shared, but no run has
exercised a swipe or tap in Compose mode.

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
- [x] Every overlay in Phase 7 passes the on-device comparison checklist — results in 9.4, forecast closure and the 1px centring rounding in 9.5; POI rotation and countdown, which 9.4 did not cover, were measured in 9.7 (countdown matches; the POI transition was brought to parity by the fade change recorded there). The now playing and message fades went further than an ink box: the message was matched in code, pinned headlessly and recorded on the device in both pref modes in 9.7, and the now playing fade was matched in code and pinned headlessly there, but its device recording shows only Spotify's buffering path, so the track-to-track fade itself rests on the test
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
- **POI position source** — settled: neither a store `StateFlow` nor the player. `MetadataOverlay` takes a `videoPosition: () -> Long` lambda, read inside its own 1s poll (`MetadataOverlay(videoPosition = ...)`), so the ticker exists only while a POI overlay is on screen and the lookup stays in the pure `PoiText.at`. Measured in 9.7.

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
