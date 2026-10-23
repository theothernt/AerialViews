# Runtime provider testing

Test configuration for driving the app against real servers at runtime. This is aimed at
verifying **release** builds (R8 code/resource shrinking is enabled for `release` build types),
where reflection breakage only shows up.

## Model: one config file per provider

Each file in [`configs/`](configs) enables exactly **one** provider. Before applying a file the
applier disables every provider, including the built in community feeds, so a test run only ever
talks to the server under test and results cannot be polluted by a previously enabled provider.

The loop is:

1. push a config
2. relaunch the app
3. confirm image playback works
4. confirm video playback works
5. move to the next config

`general` holds the `GeneralPrefs` overrides that make the run deterministic (no playlist cache,
no shuffle, screensaver starts on launch, no time-of-day filtering).

## File format

```json
{
  "name": "samba",
  "description": "Human readable note about what this config points at",
  "general": {
    "playlistCache": false,
    "startScreensaverOnLaunch": true,
    "shuffleVideos": false,
    "autoTimeOfDay": false,
    "wifiOnly": false
  },
  "providers": {
    "samba": {
      "enabled": true,
      "hostName": "192.0.2.50"
    }
  }
}
```

Top level keys:

| Key | Required | Meaning |
|---|---|---|
| `name` | no | Identifier for the config, used in logs |
| `description` | no | Free text note, ignored by the app |
| `general` | no | `GeneralPrefs` overrides, see below |
| `providers` | yes | Provider blocks, see below |

### `general` keys

| Key | Pref | Notes |
|---|---|---|
| `playlistCache` | `GeneralPrefs.playlistCache` | Keep `false` so every run re-fetches from the server |
| `startScreensaverOnLaunch` | `GeneralPrefs.startScreensaverOnLaunch` | `true` starts playback on launch, no UI interaction needed. The applier maps the OK button to `EXIT_TO_SETTINGS` if no button is mapped already |
| `shuffleVideos` | `GeneralPrefs.shuffleVideos` | `false` keeps ordering stable between runs |
| `autoTimeOfDay` | `GeneralPrefs.autoTimeOfDay` | `false` stops the time of day filter hiding content |
| `wifiOnly` | `GeneralPrefs.wifiOnly` | `false` allows the emulator/host network |
| `messageApiEnabled` | `GeneralPrefs.messageApiEnabled` | Optional, exposes the Ktor message API on port 8081 |
| `messageApiPort` | `GeneralPrefs.messageApiPort` | Optional, defaults to `8081` |

`MainActivity` deliberately only auto-starts the screensaver when a button is mapped to
`EXIT_TO_SETTINGS`, so a user can always get back to settings. When `startScreensaverOnLaunch`
is `true` and no such mapping exists, the applier maps OK press (`button_ok_press`) to
`EXIT_TO_SETTINGS` for the test run. It does not overwrite an existing mapping.

### Provider blocks

`providers` accepts these keys. Any provider not present in the file is disabled before the
file is applied.

The `samba2` and `webdav2` blocks are the second account slots and take the same fields as
`samba` and `webdav`. Blank fields fall back to the first slot.

#### `customFeed`

| Key | Pref |
|---|---|
| `enabled` | `CustomFeedPrefs.enabled` |
| `urls` | `CustomFeedPrefs.urls` (comma separated) |
| `quality` | `CustomFeedPrefs.quality` (`VIDEO_1080_H264`, `VIDEO_1080_SDR`, `VIDEO_1080_HDR`, `VIDEO_4K_SDR`, `VIDEO_4K_HDR`) |

The URL is a base path containing `manifest.json`, which points at `entries.json`.

#### `samba`

| Key | Pref |
|---|---|
| `enabled` | `SambaMediaPrefs.enabled` |
| `hostName` | `SambaMediaPrefs.hostName` |
| `domainName` | `SambaMediaPrefs.domainName` |
| `shareName` | `SambaMediaPrefs.shareName`, accepts `share/path/inside/share` |
| `userName` | `SambaMediaPrefs.userName` |
| `password` | `SambaMediaPrefs.password` |
| `searchSubfolders` | `SambaMediaPrefs.searchSubfolders` |
| `enableEncryption` | `SambaMediaPrefs.enableEncryption` |
| `mediaSelection` | `SambaMediaPrefs.mediaSelection` (`VIDEOS`, `PHOTOS`, `MUSIC`) |

#### `webdav`

| Key | Pref |
|---|---|
| `enabled` | `WebDavMediaPrefs.enabled` |
| `scheme` | `WebDavMediaPrefs.scheme` (`HTTP`, `HTTPS`) |
| `hostName` | `WebDavMediaPrefs.hostName` |
| `pathName` | `WebDavMediaPrefs.pathName` |
| `userName` | `WebDavMediaPrefs.userName` |
| `password` | `WebDavMediaPrefs.password` |
| `searchSubfolders` | `WebDavMediaPrefs.searchSubfolders` |
| `validateSsl` | `WebDavMediaPrefs.validateSsl` |
| `mediaSelection` | `WebDavMediaPrefs.mediaSelection` |

This provider is also the way to exercise a CalDAV/Nextcloud style endpoint, since those are
WebDAV servers. There is no separate CalDAV provider in the app.

#### `immich`

| Key | Pref |
|---|---|
| `enabled` | `ImmichMediaPrefs.enabled` |
| `url` | `ImmichMediaPrefs.url` (server root) |
| `pathName` | `ImmichMediaPrefs.pathName` (shared link path, for `SHARED_LINK`) |
| `apiKey` | `ImmichMediaPrefs.apiKey` |
| `authType` | `ImmichMediaPrefs.authType` (`SHARED_LINK`, `API_KEY`) |
| `validateSsl` | `ImmichMediaPrefs.validateSsl` |
| `imageType` | `ImmichMediaPrefs.imageType` (`PREVIEW`, `FULLSIZE`, `ORIGINAL`) |
| `videoType` | `ImmichMediaPrefs.videoType` (`TRANSCODED`, `ORIGINAL`) |
| `mediaSelection` | `ImmichMediaPrefs.mediaSelection` |
| `includeFavorites` | `ImmichMediaPrefs.includeFavorites` (`DISABLED`, `100`, `200`, `300`, `400`) |
| `includeRandom` | `ImmichMediaPrefs.includeRandom` (`DISABLED`, `100`, `200`, `300`, `400`) |
| `includeRecent` | `ImmichMediaPrefs.includeRecent` (`DISABLED`, `100`, `200`, `300`, `400`) |
| `includeRatings` | `ImmichMediaPrefs.includeRatings` (star ratings `1`-`5`) |

Albums are selected in the UI from the server's own album list, so they are not part of a config
file. A run therefore needs at least one of `includeFavorites`, `includeRandom`, `includeRecent`
or `includeRatings`; without one the provider has nothing to fetch and returns "No files found"
without logging anything.

#### `ncMemories`

| Key | Pref |
|---|---|
| `enabled` | `NCMemoriesMediaPrefs.enabled` |
| `url` | `NCMemoriesMediaPrefs.url` |
| `username` | `NCMemoriesMediaPrefs.username` |
| `password` | `NCMemoriesMediaPrefs.password` |
| `validateSsl` | `NCMemoriesMediaPrefs.validateSsl` |
| `favoritesName` | `NCMemoriesMediaPrefs.favoritesName` |
| `recentName` | `NCMemoriesMediaPrefs.recentName` |
| `mediaSelection` | `NCMemoriesMediaPrefs.mediaSelection` |

## Real credentials

The sample configs in [`configs/`](configs) contain placeholder values only. Keep the filled in
copies out of git:

```sh
mkdir -p local/test-configs
cp docs/testing/configs/samba.json local/test-configs/samba.json
# edit local/test-configs/samba.json with the real host/credentials
```

`local/test-configs/` is gitignored.

## Applying a config

The applier lives in `app/src/main/java/com/neilturner/aerialviews/testconfig/` and is registered
in the manifest as a receiver. It is **disabled unless the build opts in** with
`-PtestConfig=true`, so it is absent from distributed builds. The receiver also only accepts
requests from the shell.

Before applying a config the applier disables **every** provider, including the built in
community feeds (Comm1, Comm2, Amazon, Apple). Those are enabled by default and would otherwise
contribute hundreds of items and dominate the playlist, so a test run would take a long time to
reach the provider under test. A config file therefore only needs to name the provider it tests.

```sh
# 1. Build and install the build under test (R8 enabled for release builds)
./gradlew :app:assembleBetaRelease -PtestConfig=true
adb install -r app/build/outputs/apk/beta/release/app-beta-release.apk

# 2. Copy the config into the app's own files dir
#    (adb push into /sdcard/Android/data is blocked by scoped storage)
adb shell "run-as com.neilturner.aerialviews sh -c 'cat > files/test-config.json'" < local/test-configs/samba.json

# 3. Apply it
adb shell am broadcast \
  -n com.neilturner.aerialviews/com.neilturner.aerialviews.testconfig.TestConfigReceiver \
  -a com.neilturner.aerialviews.testconfig.APPLY \
  --es path /data/data/com.neilturner.aerialviews/files/test-config.json

# 4. Relaunch so the app picks up the new settings
adb shell am force-stop com.neilturner.aerialviews
adb shell am start -n com.neilturner.aerialviews/com.neilturner.aerialviews.ui.MainActivity
```

`run-as` only works on debuggable builds. For a release build, pass the config inline instead. The
quotes have to survive the trip through the device shell, so they are single quoted there:

```sh
adb shell "am broadcast \
  -n com.neilturner.aerialviews/com.neilturner.aerialviews.testconfig.TestConfigReceiver \
  -a com.neilturner.aerialviews.testconfig.APPLY \
  --es json '$(cat local/test-configs/samba.json)'"
```

The app id differs for debug builds, where it is `com.neilturner.aerialviews.debug`.

## Verifying a run

```sh
# Provider/fetch level logging
adb logcat -s MediaService:* SambaMediaProvider:* WebDavMediaProvider:* ImmichMediaProvider:* NCMemoriesMediaProvider:* CustomFeedProvider:*

# R8 breakage signatures
adb logcat | grep -E "ClassNotFoundException|NoSuchMethodException|NoSuchFieldError|NoSuchMethodError"
```

For image/video playback, use the Android CLI to capture the screen while the playlist runs:

```sh
android screen capture --output /tmp/aerial-check.png
```

The playlist is built from both photos and videos, so leave `mediaSelection` as
`["VIDEOS", "PHOTOS"]` and confirm the two media types both appear in the log output.

## The per provider loop

```sh
for config in local/test-configs/*.json; do
  adb shell "run-as com.neilturner.aerialviews sh -c 'cat > files/test-config.json'" < "$config"
  adb shell am broadcast \
    -n com.neilturner.aerialviews/com.neilturner.aerialviews.testconfig.TestConfigReceiver \
    -a com.neilturner.aerialviews.testconfig.APPLY \
    --es path /data/data/com.neilturner.aerialviews/files/test-config.json
  adb shell am force-stop com.neilturner.aerialviews
  adb shell am start -n com.neilturner.aerialviews/com.neilturner.aerialviews.ui.MainActivity
  # ... check image playback, then video playback, then move to the next config
done
```

If `startScreensaverOnLaunch` is enabled, `MainActivity` starts the screensaver automatically
because the applier provides the required `EXIT_TO_SETTINGS` button mapping. To skip the main
menu entirely for a one-off launch, start the screensaver directly:

```sh
adb shell am start -n com.neilturner.aerialviews/com.neilturner.aerialviews.ui.screensaver.TestActivity
```
