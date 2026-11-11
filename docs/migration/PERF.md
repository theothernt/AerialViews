# Phase 9: performance comparison

**Status:** agent-collected measurements only. The Phase 9 gate requires this comparison to be
reviewed by the human; it is not signed off here.

## Device and build

| Item | Value |
|---|---|
| Device | Chromecast (`sabrina`), Android 14 / API 34, 1920x1080 |
| RAM | `MemTotal: 1,982,996 kB` (about 1.89 GiB) |
| Debug app | `com.neilturner.aerialviews.debug`, `1.8.6-beta4` (versionCode 153) |
| Debug build | `betaDebug` from the Phase 9 tree, `35,919,659` bytes |
| Release app | `com.neilturner.aerialviews`, `1.8.6-beta4` (versionCode 153) |
| Release build | `betaRelease` with R8, `9,910,520` bytes |
| Display state | awake, `TestActivity` foreground |

This is the only attached device. It is the low-RAM sample used for this comparison; no low-end
phone or other TV was available.

The **debug** configured-session runs enabled the four built-in feeds with no custom feed:

| Provider | Videos |
|---|---:|
| Apple | 139 |
| Amazon | 85 |
| Jetson Creative (Comm1) | 20 |
| Robin Fourcade (Comm2) | 18 |
| **Total** | **262** |

The **release** runs used the existing release preferences, which had Apple enabled and the other
feeds disabled: **139 Apple videos**. No release preferences were changed.

The debug empty-state runs disabled every provider, including the custom feed, and logged
`using full in-memory playlist (0 items)`.

## Method

- Cold start: `adb shell am force-stop`, then `adb shell am start -W` for
  `TestActivity` with an explicit `ui_path` and `wake_screen=true`. `TotalTime` is the value
  reported by `am start -W`. Four runs per renderer; every run was a cold process start.
- Configured memory and jank: after the activity started, reset `dumpsys gfxinfo`, wait 30 s, then
  capture `dumpsys meminfo` and `dumpsys gfxinfo`. The 30 s window includes playlist fetching and
  the start of playback.
- Empty memory: wait 25 s after the activity started, then capture `dumpsys meminfo`.
- Media: the debug runs used Apple, Amazon, Comm1 and Comm2 only; the release runs used the
  existing Apple-only prefs. Video audio was muted and the playlist cache was disabled.
- PSS/RSS are taken from the `App Summary` line in `dumpsys meminfo`; baseline 30 s values are
  reported after the configured session was already playing.

Raw logs and captures are in `build/phase9/perf-legacy/`, `build/phase9/perf-compose/`,
`build/phase9/perf-empty-clean/` and `build/phase9/perf-release/`.

## Cold start

| Renderer | Runs (ms) | Median (ms) | Mean (ms) |
|---|---|---:|---:|
| Legacy | 7034, 6997, 6944, 7080 | 7015.5 | 7013.8 |
| Compose | 7632, 7559, 7588, 7568 | 7578.0 | 7586.8 |

Compose is **562.5 ms slower at the median (+8.0%)** on this debug build. Phase 0 did not capture
a cold-start baseline, so this is a new measurement rather than a comparison against Phase 0.

## Configured-session memory

All values are KB of PSS at 30 s after activity start.

| Renderer | Run 1 | Run 2 | Run 3 | Run 4 | Steady median (runs 2-4) |
|---|---:|---:|---:|---:|---:|
| Legacy | 175,825 | 176,022 | 176,468 | 176,544 | 176,468 |
| Compose | 158,383 | 174,835 | 174,716 | 174,494 | 174,716 |

Compose's first run was 16 MB below the other three and is treated as a warm-up outlier. In the
steady runs, Compose is **1,752 KB lower (about 1.0%)** than legacy. There is no Phase 0
configured-session baseline; these values become the first one.

RSS for the same steady runs was 247,204-247,844 KB for legacy and 245,500-246,144 KB for Compose.

## Empty-state comparison with Phase 0

| Build / renderer | Total PSS at 25 s | Change from Phase 0 |
|---|---:|---:|
| Phase 0 legacy (recorded in `AUDIT.md`) | 89,192 KB | - |
| Phase 9 legacy, all providers disabled | 146,122 KB | **+56,930 KB (+63.8%)** |
| Phase 9 Compose, all providers disabled | 138,892 KB | **+49,700 KB (+55.7%)** |

This is a whole-build comparison, not a Compose-only comparison: the Phase 9 APK contains the
Compose module and all migration changes even when `ui_path=legacy`. The Phase 0 figure is the only
historical empty-state baseline available. The increase is material and must be reviewed before
the beta is treated as performance-neutral.

The largest component in both Phase 9 captures is mapped code (`Code` PSS 88,784 KB for legacy,
88,648 KB for Compose). This needs a profile/heap review; it is not explained by the Compose UI
alone because the legacy path shows it too.

## UI-thread jank

`dumpsys gfxinfo` measures the app's HWUI/UI-thread frame work. It does **not** measure decoder or
SurfaceView video presentation, so these are not claims about dropped video frames.

| Renderer | Frames | Janky frames | Jank rate |
|---|---:|---:|---:|
| Legacy | 2,687 | 22 | 0.82% |
| Compose | 2,376 | 105 | 4.42% |

Per-run jank was 0.85%, 0.47%, 0.58% and 1.40% for legacy, versus 5.36%, 4.18%, 4.53% and 3.72%
for Compose. Compose's median frame time was 19 ms (p90 31-36 ms, p99 65-81 ms); legacy's was
8-16 ms (p90 16-17 ms, p99 42-46 ms).

The Compose run had fewer total frames over the same wall-clock window because it was still in its
loading/first-item phase for longer. The jank comparison therefore needs a second device before it
is treated as a stable regression number.

## Release build results

The release APK was installed over the existing `com.neilturner.aerialviews` package to preserve
its data; the original APK was backed up first and restored after the measurements. The release
package is not debuggable, so its preferences were left untouched. The existing Apple-only
playlist produced 139 videos for every measured run.

| Metric | Legacy | Compose |
|---|---:|---:|
| Cold-start runs (ms) | 743, 777, 750, 733 | 932, 753, 743, 771 |
| Cold-start median | 746.5 ms | 762.0 ms |
| Cold-start mean | 750.8 ms | 799.8 ms |
| PSS runs (KB) | 93,267, 88,169, 88,548, 82,547 | 82,961, 85,112, 93,908, 84,637 |
| PSS median | 88,358.5 KB | 84,874.5 KB |
| PSS mean | 88,132.8 KB | 86,654.5 KB |
| UI frames / janky | 543 / 29 | 528 / 23 |
| UI jank rate | 5.34% | 4.36% |

Compose's release cold-start median was **15.5 ms slower (+2.1%)**; its first run (932 ms) was an
outlier and the other three were 743-771 ms. Compose's release PSS median was **3,484 KB lower
(-3.9%)** than legacy. Compose had a lower aggregate UI jank rate in this small sample, but the
frames-per-run count is only 107-158, so this is not a strong statistical result.

These release figures are much better than the debug figures: release cold start is under a
second rather than about seven seconds, and release PSS is roughly half the debug PSS. The
release playlist had 139 videos versus 262 on debug, so the cross-build PSS comparison is not
controlled; the legacy/Compose comparison within each build is. The debug build remains useful as
a stress test, not as a shipping-performance estimate.

There is no release empty-state sample: the non-debuggable release package's existing preferences
were preserved, and disabling its provider would have changed the user's app settings.

## APK size

| Artifact | Phase 0 | Phase 9 | Change |
|---|---:|---:|---:|
| `betaDebug` | 27,359,812 B | 35,919,659 B | +8,559,847 B (+31.3%) |
| `fdroidDebug` | 24,469,817 B | 32,969,916 B | +8,500,099 B (+34.7%) |
| `betaRelease` (R8) | 9,267,408 B | 9,910,520 B | +643,112 B (+6.9%) |
| `fdroidRelease` (R8) | 8,219,078 B | 8,898,278 B | +679,200 B (+8.3%) |

The debug increase is dominated by the Compose toolchain/runtime and debug artifacts. The release
increase is the more relevant shipping number; it is still a real size cost that needs review.

## Conclusions and open items

- On the debug build, Compose cold start is 0.56 s slower at the median (+8.0%). On the release
  build the difference is only 15.5 ms (+2.1%) and is within the run-to-run spread.
- Steady configured-session PSS is neutral or slightly better for Compose in both samples. The
  release PSS is about half the debug PSS, though the playlists differ (139 vs 262 videos).
- The debug empty-state whole-build PSS is substantially above the Phase 0 baseline. The release
  configured-session PSS (about 88 MB) is close to that 89 MB Phase 0 empty figure, but it is not
  a same-state comparison. A release empty-state measurement is still missing and the debug
  regression still needs investigation.
- UI-thread jank is higher for Compose on debug and lower for Compose on release in this sample;
  `gfxinfo` excludes video presentation and the release sample is small.
- No fatal exception or app ANR was found in the Phase 9 performance logs.
- Repeat on at least one other low-RAM device before the human performance review is signed off.
  The release data removes the debug-build cold-start concern, but it does not cover the dream/
  phone paths or a second device.
