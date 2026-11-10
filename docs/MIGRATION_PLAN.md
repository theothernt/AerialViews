# AerialViews: Gradual Compose Screensaver Migration

Plan for an LLM coding agent. Read this whole file before starting. Work on **one phase at a time** and **stop at the end of each phase** for human review.

## Goal

Add a new Jetpack Compose screensaver implementation that users can opt into, running alongside the existing implementation. When it is stable, make it the default, then remove the legacy path.

## Target architecture

```
:core                  models, enums, prefs, providers, services, data, utils,
                       shared screensaver state holder (UI-agnostic)
:app                   legacy screensaver UI, settings, activities (DreamActivity, TestActivity)
                       composition root: depends on :core and :screensaver-compose
:screensaver-compose   new Compose screensaver UI (depends on :core ONLY, never on :app)
```

Dependency rules (enforce in every phase):
- `:core` depends on neither `:app` nor `:screensaver-compose`.
- `:core` contains no `Activity`, `DreamService`, `Window`, or View/Compose UI code.
- `:screensaver-compose` never imports from `:app`.

## Ground rules

1. **One phase per session.** Finish the phase, run the verification gate, write the stop report, then stop. Do not begin the next phase.
2. **One branch per phase**, named `compose-migration/phase-N-short-name`, with small, reviewable commits.
3. **Phases 1 to 6 are behavior-preserving refactors.** No functional change, no bug fixes, no cleanup of unrelated code. If you find a bug, note it in the stop report and leave it alone.
4. **Do not rename packages or classes when moving files.** Move first, refactor never (in the same commit). This keeps diffs reviewable and avoids breaking serialization, ProGuard rules, and string references.
5. **Component name stability.** Anything referenced from the manifest or stored/matched by name (DreamService, NotificationListenerService, activities, shortcuts, intent actions, class-name strings in prefs or serialization) must keep its fully qualified class name. The system stores the user's selected screensaver and notification-listener grants by component name, and changing it silently breaks existing installs.
6. **Verify before declaring done.** Discover the real build commands in Phase 0 and use them. A phase is not finished unless every build flavor assembles, unit tests pass, and lint/R8 gates (where defined) pass.
7. **Ask, don't guess.** If a requirement is ambiguous or the code contradicts this plan, stop and ask. Do not invent behavior.
8. **Do not touch settings UI** except where a phase explicitly says so.

## Stop report template (end of every phase)

- What changed (files/modules, summarized, not a diff dump)
- Verification results (commands run and outcomes)
- Anything surprising or deferred (bugs found, risks, deviations from plan)
- Recommended adjustments to later phases
- Confirmation that no behavior changed (phases 1 to 6)

---

## Phase 0: Audit (read-only, no code changes)

Produce `docs/migration/AUDIT.md` covering:

1. **Build**: modules, build flavors/dimensions, build types, minSdk/targetSdk, AGP/Kotlin/Gradle versions, existing version catalog or convention plugins, exact commands to build all flavors, run unit tests, run lint.
2. **Entry points**: `DreamActivity` (DreamService) and `TestActivity`: manifest declarations, FQNs, what each does in each lifecycle callback, how each differs (input handling, exit behavior, window setup).
3. **Screensaver core flow**: identify the class(es) that orchestrate playback (playlist advance, timers, provider loading, music/now-playing detection, error handling, skip/exit handling). For each, note which responsibilities are pure logic and which are view wiring. This is the most important finding. If logic and views are tangled, say exactly where.
4. **Overlays**: list every overlay (clock, date, weather, location, metadata, music, etc.), the class implementing it, and what state it reads.
5. **Playback layer**: player setup (ExoPlayer/Media3), SurfaceView vs TextureView usage, image loading, transitions, GL effects, and every device-specific workaround (with file and line references and the device/bug each addresses).
6. **Prefs mechanism**: how preferences are defined and accessed (singletons, delegates, SharedPreferences wrappers), and whether `Context` is captured statically.
7. **Resources**: which strings, drawables, and XML are used by screensaver code vs settings only.
8. **Android-framework coupling** in the packages slated for `:core`: list files that import `android.app.*`, `android.view.*`, `android.widget.*`, `Activity`, or `Window`, since these block extraction.
9. **Name-sensitive references**: manifest components, serialized class names, ProGuard/R8 rules, `BuildConfig` usage, reflection.
10. **Baselines**: APK size per flavor, and, if a device/emulator is available, idle memory while the screensaver runs. Otherwise state that baselines still need to be measured by the human.
11. Proposed refinements to Phases 1 to 9 based on findings.

**Gate:** AUDIT.md exists; the build/test commands in it were actually run and worked. **STOP.**

---

## Phase 1: Gradle foundation and empty `:core`

1. Create the `:core` Android library module (namespace distinct from `:app`), registered in `settings.gradle`.
2. Align flavor dimensions/build types with `:app` (use `missingDimensionStrategy`/matching fallbacks as needed) so all `:app` flavors resolve.
3. Enable `android.nonTransitiveRClass=true` if not already (resolve any resulting R-reference breakage in this phase only).
4. Make `:app` depend on `:core`. Add consumer ProGuard rules file to `:core` (empty is fine for now).
5. If no version catalog exists, add one. Convention plugins are optional; only add them if the audit showed config duplication worth removing.

**Gate:** all flavors assemble; unit tests pass; minified (R8) release build assembles for at least one flavor; app behavior unchanged. **STOP.**

## Phase 2: Move models and enums

1. Move pure-Kotlin models and enums to `:core` (keep package names).
2. Anything with Android framework dependencies that cannot move cleanly stays in `:app`; list it in the stop report rather than refactoring it.
3. Update `:app` imports only if the package changed (it should not).

**Gate:** same as Phase 1. **STOP.**

## Phase 3: Move prefs, utils, and shared resources

1. Move the prefs layer and utils to `:core`. If prefs rely on a statically captured `Context`, preserve that mechanism exactly and note how `:core` gets initialized.
2. Move resources (strings, drawables, XML) needed by both paths to `:core`. Resources used only by settings stay in `:app`. Fix R references; watch for resource name collisions.
3. Add or verify consumer ProGuard rules for moved code.

**Gate:** same as Phase 1, plus R8 release build runs and a quick manual or instrumented smoke test of screensaver start/stop on a device or emulator (if available; otherwise flag as human-verified). **STOP.**

## Phase 4: Move data, providers, and services

1. Move providers (Immich, WebDAV/Nextcloud, Plex, Samba, etc.), data layer, and non-UI services to `:core`.
2. **Do not move manifest-declared components** (e.g. a NotificationListenerService) if doing so would change their FQN. If they must live in `:app`, keep them there and expose an interface in `:core` for the logic they need.
3. Move in small batches (one provider or package per commit) so regressions are bisectable.
4. Move or add unit tests alongside moved code where tests exist.

**Gate:** same as Phase 3; moved providers' unit tests pass. **STOP.**

## Phase 5: Extract the shared screensaver state holder

This is the key phase. Use the Phase 0 audit findings.

1. Create a UI-agnostic controller/state holder in `:core` that owns playlist advance, timers, provider loading, music detection, and error/skip handling. It exposes immutable state (`StateFlow<ScreenState>`) and one-shot events (`Flow`/`SharedFlow`), and takes dependencies through constructors or interfaces so it is unit-testable with fakes.
2. Refactor the legacy screensaver to **consume** it. The legacy UI becomes a renderer of the state and a forwarder of input events.
3. Preserve exact behavior, including all device-specific workarounds, which stay in the legacy rendering/playback layer or move with their logic as appropriate. Document where each ended up.
4. Add unit tests for the state holder (playlist advance, error handling, skip, music state transitions) using fakes and a test dispatcher.
5. Lifecycle safety: construction must be idempotent and `release()` must be safe to call multiple times. Do not allow duplicate controller/player instances (leaked players are a known failure mode here).

**Gate:** same as Phase 3, plus new unit tests pass, plus a manual comparison of legacy behavior before and after on at least one device (human-verified). **STOP.**

## Phase 6: Host/UI seam and opt-in flag

1. Define in `:core`:
   ```kotlin
   interface ScreensaverHost {
       val context: Context
       val window: Window
       fun exit()
   }
   interface ScreensaverUi {
       fun attach(host: ScreensaverHost)
       fun onStart()
       fun onStop()
       fun onKeyEvent(event: KeyEvent): Boolean
       fun release()
   }
   ```
   (Adjust to what the audit shows is needed. Keep `:core` free of Activity/DreamService types; a `Window`/`Context` interface boundary is acceptable.)
2. In `:app`, add host adapters for `DreamActivity` (DreamService) and `TestActivity`, a `LegacyScreensaverUi` wrapping existing behavior, and a `ScreensaverUiFactory` that chooses based on a new pref `useComposeScreensaver` (default **false**).
3. `DreamActivity` and `TestActivity` stay in `:app` with unchanged FQNs and become thin delegates.
4. `TestActivity` accepts an intent extra (e.g. `ui_path=compose|legacy`) that overrides the pref, for testing without changing user-facing settings.
5. Add a Crashlytics custom key `ui_path` set from the factory.
6. Add a guard in the factory: if the compose path throws during init, fall back to legacy and record a non-fatal. Add a crash-loop counter that disables the pref automatically after N consecutive failures (N configurable, default 3).
7. Legacy is the only implementation at this stage. Nothing user-visible changes.

**Gate:** same as Phase 5; legacy path works identically via both entry points; intent override is exercised. **STOP.**

## Phase 7: Scaffold `:screensaver-compose`

1. Create the module depending on `:core` only. Add Compose BOM and the Compose compiler plugin here, not in `:app`.
2. Implement `ComposeScreensaverUi`:
   - Host a `ComposeView` in the host window.
   - Provide `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, and `ViewTreeViewModelStoreOwner` manually, since a DreamService does not provide them. Drive the lifecycle from the host callbacks (attach, start, stop, detach).
   - Dispose the composition and move the lifecycle to DESTROYED in `release()`; make `release()` idempotent.
3. Render a minimal placeholder driven by the shared state holder (e.g. black background plus the current item's identifier) to prove lifecycle, state flow, and disposal.
4. Wire into the factory in `:app` (`:app` now depends on `:screensaver-compose`).
5. Leak check: start and stop the screensaver repeatedly (at least 20 cycles), then confirm no leaked compositions, lifecycle owners, or players (heap dump or LeakCanary in debug).

**Gate:** all flavors assemble with the new module; toggling `useComposeScreensaver` (or the intent override) shows the placeholder; leak check passes; legacy path unaffected. **STOP.**

## Phase 8: Build out the Compose UI (parity)

Maintain a parity checklist in `docs/migration/PARITY.md` with one row per feature (status: not started / in progress / parity / verified on device). Seed it from the audit's overlay and workaround lists. Work through it in this order, committing per item:

1. **Playback layer:** wrap the existing player in `AndroidView`. Reuse the existing playback engine and its device-specific workarounds; do not reimplement them. Prefer `TextureView` where Compose animations/transforms are applied over video, and call out any case where `SurfaceView` must be retained.
2. **Image and video transitions.**
3. **Overlays**, one at a time, as pure state-to-UI composables with `@Preview`s.
4. **Input handling**: key and touch events, exit behavior, parity with legacy for both hosts.
5. **Edge cases**: errors, empty playlists, provider failures, no-network, music start/stop, screen-off schedule behavior.

Performance rules: avoid per-frame recomposition, use `graphicsLayer` for animated properties, read state as late as possible, and keep the composition tree small.

Add screenshot tests for overlays if the project has (or agrees to add) a screenshot-testing setup. Ask first.

**Gate (per batch of items):** build/tests pass; PARITY.md updated; items marked "verified" only if checked on a real device. **STOP after each logical batch** (playback layer; transitions; overlays; input; edge cases).

## Phase 9: Opt-in beta

1. Add an "experimental" toggle in settings (in `:app`) bound to `useComposeScreensaver`, with clear wording that it is a preview.
2. Ensure the settings preview/test path uses the same chosen implementation.
3. Verify the Crashlytics `ui_path` key is on all crash reports and non-fatals, and that the fallback and crash-loop guard work (simulate a failure).
4. Measure on the lowest-RAM supported devices: memory, cold start, dropped frames/jank, versus the Phase 0 baselines. Record results in `docs/migration/PERF.md`.
5. Draft release notes and a short feedback prompt for beta users.

**Gate:** guard behavior demonstrated; perf comparison recorded and reviewed by the human. **STOP.**

## Phase 10: Stabilize, flip default, retire legacy

Do not start without explicit human approval based on crash rates and feedback from Phase 9.

1. Flip the default of `useComposeScreensaver` to true. Keep the legacy path and the toggle for one or more release cycles.
2. After a stable period, and only on explicit approval: delete the legacy UI, the factory and its fallback, the pref and toggle, and unused resources. Collapse `DreamActivity`/`TestActivity` to use the Compose UI directly (FQNs unchanged).
3. Re-run all gates, including an R8 release build and a leak check.

---

## Known risks to watch throughout

| Risk | Mitigation |
|---|---|
| Duplicated device-specific workarounds drift between paths | Single shared playback engine and workarounds; no reimplementation (Phase 8) |
| Compose leaks or duplicate players in DreamService | Idempotent init/release, lifecycle owners driven manually, 20-cycle leak check (Phases 5, 7) |
| Low-RAM devices regress | Measure early and compare against the Phase 0 baseline (Phases 7, 9) |
| Broken screensaver selection or notification-listener grant after refactor | FQN stability rule for manifest/name-stored components (all phases) |
| Resource/R8 breakage from module split | `nonTransitiveRClass`, consumer ProGuard rules, R8 build in every gate |
| Animated Compose layers over `SurfaceView` misbehave | Use `TextureView` where animating over video; document exceptions |
