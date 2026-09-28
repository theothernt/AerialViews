# Aerial Views — AGENTS.md

Instructions for AI coding agents working on the Aerial Views project.

## Project at a glance
- Android screensaver app for TV / Google TV / phones (Nvidia Shield, Fire TV, Google TV Streamer) primarily, but also Android phones.
- Stack: Kotlin 2.4+, Android Gradle Plugin 9+, JDK 21+, Kotlin Coroutines + Flow, kotlinx.serialization, Room 3 (KSP), Coil 3, ExoPlayer/Media3, Ktor server (bundled message API on port 8081), Kotpref, Firebase (Crashlytics/Perf/Analytics).
- Root package: `com.neilturner.aerialviews`. Always build with the Gradle wrapper `./gradlew`.

## Modules
- `:app` — main application.
- `:projectivyapi` — API used by the Projectivy launcher.
- `:baselineprofile` — baseline (startup) profile.
- `:microbenchmark` — macrobenchmarks (run on a device; not part of CI unit tests).

## Flavors & build types
- Flavor dimension `version`; flavors: `github`, `beta` (default), `googleplay`, `googleplaybeta`, `amazon`, `fdroid`.
- Build types: `debug` (applicationIdSuffix `.debug`, minify off, LeakCanary on) and `release` (R8 + resource shrinking, `proguard-rules.pro`).
- Use the **beta** flavor for local development and verification. Debug variant = `betaDebug`; release variant = `betaRelease`.

## Build commands (prefix with `:app`)
| Goal | Command |
|---|---|
| Run unit tests | `./gradlew :app:testBetaDebugUnitTest` |
| Lint (kotlinter) | `./gradlew :app:lintKotlin` |
| Auto-format | `./gradlew :app:formatKotlin` |
| Debug APK | `./gradlew :app:assembleBetaDebug` |
| Install on device | `./gradlew :app:installBetaDebug` |
| Release APK (signed) | `./gradlew :app:assembleBetaRelease` |

## Verify a code change
```sh
./gradlew :app:testBetaDebugUnitTest :app:lintKotlin :app:assembleBetaDebug
```
Run unit tests first — they fail fast on compile errors. Signed `betaRelease` builds require local signing files (see Secrets); if absent, rely on CI for release packaging.

## Testing
- JVM unit tests live in `app/src/test`. Framework: JUnit 5 (Jupiter) via `de.mannodermaus.android-junit5`, MockK for mocks, `kotlinx-coroutines-test` for coroutines.
- Task naming follows `<Flavor><BuildType>` (same pattern as CI's `testGithubReleaseUnitTest`): `testBetaDebugUnitTest`, `testBetaReleaseUnitTest`.
- No instrumentation tests currently (`app/src/androidTest` is empty).
- Test logging is verbose (full stack traces, started/skipped/passed/failed, stdout shown) — keep it.

## Code style
- `kotlin.code.style=official`, enforced by kotlinter (`org.jmailen.kotlinter`). Run `:app:formatKotlin` before finishing a change; `:app:lintKotlin` must pass.
- Kotlin idioms: prefer small, pure functions; coroutines + Flow for async; sealed classes for UI state; null-safety (avoid `!!` on injected/optional values).
- ViewBinding is enabled (`buildFeatures.viewBinding = true`).
- `src/common/java` holds code shared across flavors; F-Droid-specific code is in `src/fdroid/java`.

## Architecture & conventions
- Packages under `com.neilturner.aerialviews`: `data` (storage/network/db), `models`, `providers` (media sources: immich, nextcloud memories, webdav, samba, custom feeds), `services` (media playback + Ktor message API), `ui` (leanback screens + `ui/core` helpers), `utils`.
- Preferences via Kotpref (e.g. `GeneralPrefs`).
- Room 3 DB (KSP codegen; schemas exported to `app/schemas`) — commit schema JSON and add migrations when entities change.
- `BuildConfig` fields: `OPEN_WEATHER` (per variant from `secrets.properties`), `BUILD_TIME`.
- Keep logic testable: place business logic in pure functions under `utils`/`ui/core` and add unit tests.

## Environment
- Always use `./gradlew` (the wrapper), never a system Gradle. JDK 21 is required.
- Repositories are centralized in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`) and dependency versions in `gradle/libs.versions.toml`. Add new deps/versions in the catalog, not in build files.

## Git / hygiene
- Do not commit build outputs (`app/build/`, `.gradle/`), `*.apk/aab`, or `*.log`.
- Do not add secrets, keystores.
- Stage only intended files and write concise, repo-style commit messages. Commit only when explicitly asked.