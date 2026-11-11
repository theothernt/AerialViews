# Phase 9a: overlay screenshot parity

**Status:** agent-captured side-by-side evidence, not signed off. The human must review the report
before this closes the Phase 9 gate. No `PARITY.md` row is marked verified by this document.

## Why this exists

Phase 9 proved the opt-in path, guard, release performance and full build, but it did not compare
the rendered overlays. The Phase 8 matrix still marked every visual row as unverified. This addendum
closes that evidence gap for the overlay content types without treating the screenshots as human
verification.

## Device and fixture

- Device: Chromecast (`sabrina`), Android 14 / API 34, 1920x1080, 2 GB.
- Build: `betaDebug`, explicit `TestActivity` path. No release APK was needed for this visual check.
- Media: a local custom feed with one 1920x1080 still, served on `127.0.0.1:8765` through
  `adb reverse`. The same still is used for both renderers.
- Metadata: the still title `Overlay parity still`.
- Weather: live OpenWeather response for London (`51.5074, -0.1278`), three forecast days.
- Music: live Spotify TV media session playing
  `Beneath The Silence - Hybrid Extended Mix` by
  `Planet Of Souls, Samsaruh, Hybrid`. The notification-listener grant was temporary and restored.
- Clock/date/countdown: live device time; the captures were taken within adjacent minutes, not with
  a frozen clock.

Preferences were backed up before the run and restored exactly afterwards. The final hash matches
`06698a865445c451b367693ecfff1f9c66c9a9344ef044df12144eaa987bd6cc`.

## Layouts

Both layouts use `ALWAYS_VISIBLE`, both gradients enabled, 100% opacity, 48/40 dp edge padding,
`google-sans` in the legacy path, fixed 24-32 sp sizes and alternating text position disabled.

| Layout | Top left | Top right | Bottom left | Bottom right |
|---|---|---|---|---|
| A | Clock, Date | Weather now, Weather forecast | Metadata 1, Metadata 2 | Message 1, Countdown |
| B | Metadata 3, Metadata 4 | Music 1, Music 2 | Message 2, Message 3 | Message 4, Empty |

All 15 visual `OverlayType` values are covered across A and B. `EMPTY` is included in layout B to
exercise an unpopulated slot; it is not expected to render anything. The progress bar is a separate
view, not an `OverlayType`, and is listed as an open row below.

## Evidence

Raw captures:

- `build/phase9/overlays/captures/config-a-legacy.png`
- `build/phase9/overlays/captures/config-a-compose.png`
- `build/phase9/overlays/captures/config-b-music-legacy-retry.png`
- `build/phase9/overlays/captures/config-b-music-compose.png`

Side-by-side report:

- `build/phase9/overlays/report/index.html`
- `build/phase9/overlays/report/config-a-side-by-side.png`
- `build/phase9/overlays/report/config-b-music-side-by-side.png`

Per-run logs and start timings are alongside the PNGs in `build/phase9/overlays/captures/`.

## Results

| Overlay | Layout | Result |
|---|---|---|
| Clock | A | Rendered in both; same top-left placement and 24-hour format. The capture minute differs by one minute. |
| Date | A | Rendered in both; same full-date text and placement. |
| Weather now | A | Same London temperature, icon, summary and right-side placement. |
| Weather forecast | A | Same three days and temperatures. **Difference:** legacy icons have opaque coloured tile backgrounds; Compose draws transparent tinted icons. |
| Metadata 1 | A | Same still title and lower-left placement. |
| Metadata 2 | A | Same still title and upper-lower-left placement. |
| Metadata 3 | B | Same still title and top-left placement. |
| Metadata 4 | B | Same still title and second top-left placement. |
| Message 1 | A | Same text, size and bottom-right placement. |
| Message 2 | B | Same text and bottom-left placement. |
| Message 3 | B | Same text and second bottom-left placement. |
| Message 4 | B | Same text and bottom-right placement. |
| Music 1 | B | Same live track data. **Difference:** with the long track string, the legacy line overflows into the top-left metadata column; Compose remains right-aligned in this capture. |
| Music 2 | B | Same artist-only line and placement. |
| Countdown | A | Same label and day/hour/minute countdown; the minute value differs because the captures were taken at different times. |
| Gradients | A/B | Top and bottom gradients render in both. |
| Empty slot | B | No content in either renderer, as expected. |

No fatal exception or app ANR appeared in the capture logs. Weather and music were live sources, so
their values are not deterministic fixtures; the values above are the values present in this run.

## Differences and known gaps

1. **Legacy now-playing overflow:** a long `Music 1` line can cross the column boundary and overlap
   metadata. This is a legacy layout issue exposed by the fixture, not a Compose-only regression.
2. **Forecast icon tiles:** legacy weather icons include coloured backgrounds; Compose tints the
   icon itself. This needs a deliberate decision, not an implicit visual drift.
3. **Font (at capture time):** Compose used `FontFamily.Default`; legacy used the configured
   `google-sans` typeface, so text widths and line breaks differed. This was fixed in the follow-up
   below.
4. **Metadata selection:** all four metadata slots were configured to show the same photo
   description. Per-slot `overlay_metadata*_photos/videos` selection was not exercised, and the
   Compose session still publishes one short description to every metadata slot.
5. **Clock/countdown:** live time means the screenshots are not pixel-identical. A future run should
   freeze the clock or crop it out when comparing layouts.
6. **Not captured:** progress bar, `ALWAYS_HIDDEN`, `HIDE_AFTER_DELAY`, `SHOW_AFTER_DELAY` and the
   input-reveal path. The progress bar is not implemented in Compose; record it as missing rather
   than parity.
7. **No second device:** this is the same 2 GB Chromecast used for the performance run.

## Follow-up: bundled fonts and loading row (2026-11-11)

The font and loading-view gaps were implemented after the overlay capture:

- The Google Sans and Open Sans resources moved from `:app` to `:core`, so both renderers load the
  same files. Compose now applies the resolved font family and weight to overlay text, and the
  loading text uses the configured loading size, weight and font.
- The Compose loading row now follows `loading_view.xml`: a bottom-left 24dp framework spinner with
  a 12dp gap, 32dp horizontal and 26dp bottom padding, the loading-text visibility preference, the
  configured loading background, white text and a 1px shadow.
- A forced-loading capture of both renderers on the same 2 GB Chromecast measured identical
  151x36px text bounds. Aligning the text crops gave a pixel-identical comparison (`AE 0`); the
  remaining difference is a 4px baseline offset, and the spinner phase is animation-dependent.

Evidence: `build/phase10/loading/loading-side-by-side.png`, with raw captures and logs in
`build/phase10/loading/`. The original test prefs were restored
(`06698a865445c451b367693ecfff1f9c66c9a9344ef044df12144eaa987bd6cc`). This is agent evidence; it does
not close the Phase 9a gate or mark a `PARITY.md` row verified.

## Gate

This addendum is complete only when:

- every `OverlayType` and the progress-bar/visibility rows have a legacy and Compose capture;
- every difference is classified as intentional, a known gap, or a bug to fix;
- the human has reviewed the side-by-side report; and
- `PARITY.md` records the result without calling agent screenshots human verification.
