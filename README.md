# Aerial Views

[![Latest GitHub release](https://img.shields.io/github/v/release/theothernt/AerialViews.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/theothernt/AerialViews/releases/latest)
[![GitHub Downloads](https://img.shields.io/github/downloads/theothernt/AerialViews/total?color=blue&label=Downloads&logo=github)](https://github.com/theothernt/AerialViews/releases/latest)
[![Google Play Downloads](https://img.shields.io/static/v1?style=flat&color=brightgreen&logo=google-play&logoColor=FFFFFF&label=Downloads&message=50k%2B)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews)
[![Amazon Fire TV](https://img.shields.io/static/v1?style=flat&color=FC4C02&logo=Amazon&logoColor=FFFFFF&label=Downloads&message=2k%2B)](https://www.amazon.com/gp/product/B0B4PPSNT6)

[![API](https://img.shields.io/badge/API-22%2B-lightgrey.svg?style=flat)](https://android-arsenal.com/api?level=22)
[![License](https://img.shields.io/:license-gpl%20v3-lightgrey.svg?style=flat)](https://raw.githubusercontent.com/theothernt/AerialViews/master/LICENSE)

A screensaver for Android TV devices including Nvidia Shield, Fire TV, and Chromecast with Google TV. Inspired by Apple TV's video screensaver.

*Please read if you have a [Nvidia Shield](#nvidia-shield-users), [Chromecast with Google TV](#chromecast-with-google-tv-users) or [Amazon Fire TV](#amazon-fire-tv-users)*

## Features include...

* 4K Dolby Vision (HDR) videos, if your TV supports it
* Over 150 videos from Apple, Jetson Creative and Robin Fourcade
* Option to avoid burn-in on QD/OLED TVs
* Play videos from your device, USB storage or network share
* Skip videos, change speed with the d-pad
* Refresh rate switching

## Support the project

If you enjoy using the app, please consider [buying me a coffee](https://ko-fi.com/theothernt).

[![Ko-fi badge](docs/images/kofi_badge.png)](https://ko-fi.com/theothernt)

## How to get Aerial Views...

[![Google Play Store badge](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews) &nbsp;&nbsp;
[<img alt="Amazon Appstore badge" src="https://images-na.ssl-images-amazon.com/images/G/01/mobile-apps/devportal2/res/images/amazon-appstore-badge-english-black.png" width="153">](http://www.amazon.com/gp/mas/dl/android?p=com.neilturner.aerialviews)

Or [download the APK from the Releases page](https://github.com/theothernt/AerialViews/releases) and install it manually

## Translations

If Aerial Views is not available in your language but you have some free time to help translate menu text and video descriptions, please get in touch!

We use the [Lokalise](https://lokalise.com/) platform to coordinate translations for Aerial Views and thank them for their support of this open-source project.

[![Lokalise logo](docs/images/lokalise_logo.png)](https://lokalise.com/)

## Where to download videos for offline/local playback

Follow these links to download the videos from...

* [Apple](https://aerial-videos.netlify.app/#apple) (114 videos)
* [Jetson Creative](https://aerial-videos.netlify.app/#jetson-creative) (20 community videos)
* [Robin Fourcade](https://aerial-videos.netlify.app/#robin-fourcade) (18 community videos)

## HDR videos don't seem to play, I only see black screen...

Apple's videos only support Dolby Vision HDR. Even if your TV supports HDR, it might not support Dolby Vision.

Here are some things to try...

* Find the make and model of your TV, search online for a product page for that device and it should list the supported HDR modes (there are a few!)

* If you use a Nvidia Shield, please [follow their instructions on how to enable (or confirm) that Dolby Vision playback is possible](https://www.nvidia.com/en-us/shield/support/shield-tv/enable-dolby-vision-hdr10-on-shield/)

* Confirm that Dolby Vision playback works in other apps like Netflix, Disney+, or Amazon Prime Video

## Nvidia Shield users

If your device is running Android 11 (Shield Experience 9+) and you want to play videos from a USB storage device, make sure the following setting is enabled:

`Settings > Device Preferences > Storage > Scan for
media automatically`

## Chromecast with Google TV users

Unfortunately, as of July 2022, an update to Google TV removed user-interface option to set Aerial Views (or any other 3rd party screensaver) as default, or change the screensaver timeout.

The only way to achieve this is...

1. Download and install the Android [SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools) for Mac, Windows or Linux
2. Enable developer mode on your Android TV device
3. Using ADB, connect to your device and run the following ADB command to set Aerial Views as the default screensaver:
  
  ```sh
  adb shell settings put secure screensaver_components com.neilturner.aerialviews/.ui.screensaver.DreamActivity
  ```

To restore the default Ambient screensaver, use the following ADB command...

```sh
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
```

## Amazon Fire TV users

Install the app from either the Amazon Appstore or sideload the APK.

On a Fire TV device there is no user-interface option to set Aerial Views (or any other 3rd party screensaver) as default.

The only way to achieve this is...

1. Download and install the Android [SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools) for Mac, Windows or Linux
2. Enable developer mode on your Android TV device
3. Using ADB, connect to your device and run the following ADB command to set Aerial Views as the default screensaver:
  
  ```sh
  adb shell settings put secure screensaver_components com.neilturner.aerialviews/com.neilturner.aerialviews.ui.screensaver.DreamActivity
  ```

To restore the default screensaver, use the following ADB command...

```sh
adb shell settings put secure screensaver_components com.amazon.bueller.photos/.daydream.ScreenSaverService
```

## Weather data
Thanks to [OpenWeather](https://openweathermap.org/) for providing weather data to this and other open-source projects.

[![OpenWeather logo](docs/images/openweather_logo.png)](https://openweathermap.org/)

## About

Aerial Views is based on [Aerial Dream](https://github.com/cachapa/AerialDream), which was created by Daniel Cachapa in late 2015.

Aerial Views started in early 2020 as a fork with a couple of fixes and features to improve the experience on an old Sony Android TV. Around this time, Aerial Dream was not in active development anymore.

Shortly after putting the code on GitHub, others found the fork and started requesting new builds with fixes and additional features. This led to the eventual release of Aerial Views on the Google Play Store in 2022, and later, the Amazon Appstore.
