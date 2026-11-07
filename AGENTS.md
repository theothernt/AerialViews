# Aerial Views — AGENTS.md

Instructions for AI coding agents working on the Aerial Views project.

## Project at a glance
- Android screensaver app for TV / Google TV / phones (Nvidia Shield, Fire TV, Google TV Streamer) primarily, but also Android phones.
- Stack: Kotlin 2.4+, Android Gradle Plugin 9+, JDK 21+, Kotlin Coroutines + Flow, kotlinx.serialization, Room 3 (KSP), Coil 3, ExoPlayer/Media3, Ktor server (bundled message API, default port 8081, user-configurable), Kotpref, Firebase (Crashlytics/Perf/Analytics).
- Root package: `com.neilturner.aerialviews`. Always build with the Gradle wrapper `./gradlew`.

## Modules
- `:app` — main application.
- `:projectivyapi` — API used by the Projectivy launcher.
- `:baselineprofile` — baseline (startup) profile.
- `:microbenchmark` — macrobenchmarks (run on a device; not part of CI unit tests).

## Flavors & build types
- Flavor dimension `version`; flavors: `github`, `beta` (default), `googleplay`, `googleplaybeta`, `amazon`, `fdroid`.
- Build types: `debug` (applicationIdSuffix `.debug`, minify off, LeakCanary on) and `release` (R8 code shrinking + resource optimization via `optimization { enable = true }`). Keep rules live in `app/src/main/keepRules/*.keep` — the `keepRules` source set, auto-discovered by AGP 9.3+. There is no `proguard-rules.pro`.
- Use the **beta** flavor for local development and verification. Debug variant = `betaDebug`; release variant = `betaRelease`.

## Build commands (prefix with `:app`)
| Goal | Command |
|---|---|
| Run unit tests | `./gradlew :app:testBetaDebugUnitTest` |
| Lint (spotless) | `./gradlew :app:spotlessCheck` |
| Auto-format | `./gradlew :app:spotlessApply` |
| Debug APK | `./gradlew :app:assembleBetaDebug` |
| Install on device | `./gradlew :app:installBetaDebug` |
| Release APK (signed) | `./gradlew :app:assembleBetaRelease` |

## Verify a code change
```sh
./gradlew :app:testBetaDebugUnitTest :app:spotlessCheck :app:assembleBetaDebug
```
Run unit tests first — they fail fast on compile errors. Release builds are signed from `signing/release.properties` and `signing/legacy.properties` (plus `signing/signing.keystore`), resolved from the repo root and gitignored; if those files are absent the APK/AAB is unsigned, so rely on CI for signed release packaging.

## Testing
- JVM unit tests live in `app/src/test`. Framework: JUnit 5 (Jupiter) via `de.mannodermaus.android-junit5`, MockK for mocks, `kotlinx-coroutines-test` for coroutines.
- Task naming follows `<Flavor><BuildType>` (same pattern as CI's `testGithubReleaseUnitTest`): `testBetaDebugUnitTest`, `testBetaReleaseUnitTest`.
- CI (`.github/workflows/ci.yml`) only runs on `workflow_dispatch`, so tests do **not** run automatically on PRs — run them locally before pushing.
- No instrumentation tests currently (`app/src/androidTest` is empty).
- Test logging is verbose (full stack traces, started/skipped/passed/failed, stdout shown) — keep it.

## Code style
- `kotlin.code.style=official`, enforced by Spotless (`com.diffplug.spotless`) with ktlint. Run `:app:spotlessApply` before finishing a change; `:app:spotlessCheck` must pass. Spotless also runs automatically as part of `check`. Its `misc` format trims trailing whitespace and enforces a final newline, but it is declared in `:app`, so it only covers files under `app/` (Kotlin sources in `app/src` and any `app/**/*.md`); root-level docs such as this file are **not** checked (Spotless rejects targets outside the project dir).
- Kotlin idioms: prefer small, pure functions; coroutines + Flow for async; sealed classes for UI state; null-safety (avoid `!!` on injected/optional values).
- ViewBinding is enabled (`buildFeatures.viewBinding = true`).
- `src/common/java` holds code shared by all flavors except F-Droid; F-Droid substitutes its own `src/fdroid/java` (e.g. a no-op `FirebaseHelper`), and flavor-specific resources live in `src/<flavor>/res`.

## Architecture & conventions
- Packages under `com.neilturner.aerialviews`: `data` (storage/network/db), `models`, `providers` (media sources, e.g. immich, nextcloud memories, webdav, samba, custom feeds, local, Amazon/Apple), `services` (media playback + Ktor message API), `ui` (leanback screens + `ui/core` helpers), `utils`, `testconfig` (runtime test-config receiver, `-PtestConfig=true` only).
- Preferences via Kotpref (e.g. `GeneralPrefs`).
- Room 3 DB (KSP codegen; schemas exported to `app/schemas`) — commit the schema JSON. The DB is a cache: it uses `fallbackToDestructiveMigration(dropAllTables = true)` plus `AutoMigration`s, so hand-written migrations are not normally needed.
- `BuildConfig` fields: `OPEN_WEATHER` (per variant from `secrets.properties`), `BUILD_TIME`, and `TEST_CONFIG` (only enabled with `-PtestConfig=true`).
- Keep logic testable: place business logic in pure functions under `utils`/`ui/core` and add unit tests.

## Environment
- Always use `./gradlew` (the wrapper), never a system Gradle. JDK 21+ is required: CI and the library modules' `jvmToolchain(21)` pin 21, and newer JDKs (e.g. 25) also build.
- On Windows, pass `--no-daemon` to Gradle commands (e.g. `./gradlew --no-daemon :app:spotlessCheck`) to avoid stale/locked daemon JVMs.
- Repositories are centralized in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`) and dependency versions in `gradle/libs.versions.toml`. Add new deps/versions in the catalog, not in build files.
- When bumping Gradle, keep `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` in sync — never hand-edit `distributionUrl`. Run `./gradlew wrapper --gradle-version <ver> --gradle-distribution-sha256-sum <sha>` twice (so the new version regenerates the scripts/JAR), using the `-bin` checksum (this repo does not use `-all`) from https://gradle.org/release-checksums or `https://services.gradle.org/distributions/gradle-<ver>-bin.zip.sha256`. The sum is only verified at download time, so confirm with `grep distributionSha256Sum gradle/wrapper/gradle-wrapper.properties`. AGP is not part of the wrapper: bump it in `gradle/libs.versions.toml` and leave this file alone.

## Git / hygiene
- Do not commit build outputs (`app/build/`, `.gradle/`), `*.apk/aab`, or `*.log`.
- Do not add secrets, keystores.
- Stage only intended files and write concise, repo-style commit messages. Commit only when explicitly asked.
