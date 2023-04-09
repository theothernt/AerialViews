# Aerial Views

[![Latest GitHub release](https://img.shields.io/github/v/release/theothernt/AerialViews.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/theothernt/AerialViews/releases/latest)
[![GitHub Downloads](https://img.shields.io/github/downloads/theothernt/AerialViews/total?color=blue&label=Downloads&logo=github)](https://github.com/theothernt/AerialViews/releases/latest)
[![Latest Google Play version](https://img.shields.io/endpoint?color=brightgreen&logo=google-play&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.neilturner.aerialviews%26l%3DGoogle%2520Play%26m%3D%24version)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews)
[![Google Play Downloads](https://img.shields.io/endpoint?color=brightgreen&logo=google-play&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.neilturner.aerialviews%26l%3DDownloads%26m%3D%24totalinstalls)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews)
[![API](https://img.shields.io/badge/API-22%2B-lightgrey.svg?style=flat)](https://android-arsenal.com/api?level=22)
[![License](https://img.shields.io/:license-gpl%20v3-lightgrey.svg?style=flat)](https://raw.githubusercontent.com/theothernt/AerialViews/master/LICENSE)

A screensaver for Android TV devices including Nvidia Shield, Fire TV, and Chromecast with Google TV. Inspired by Apple TV's video screensaver.

*Please read if you have a [Nvidia Shield](#nvidia-shield-users), [Chromecast with Google TV](#chromecast-with-google-tv-users) or [Amazon Fire TV](#amazon-fire-tv-users)*

## Features include...

* 4K Dolby Vision (HDR) videos, if your TV supports it
* Option to avoid burn-in on QD/OLED TVs
* Option to play videos locally or from a network share
* Skip to next or previous video (press left or right on your TV remote's d-pad)
* Supports the latest Apple video feed with over 100 videos

## How to get Aerial Views...

[![Google Play Store badge](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews) &nbsp;&nbsp;
[<img alt="Amazon Appstore badge" src="https://images-na.ssl-images-amazon.com/images/G/01/mobile-apps/devportal2/res/images/amazon-appstore-badge-english-black.png" width="153">](http://www.amazon.com/gp/mas/dl/android?p=com.neilturner.aerialviews)

Or [download the APK from the Releases page](https://github.com/theothernt/AerialViews/releases) and install it manually

## Where to download Apple videos for offline/local playback

Please [visit this web page to download the tvOS 15 videos](https://aerial-videos.netlify.app/) supported by this screensaver.

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
  ```
  adb shell settings put secure screensaver_components com.neilturner.aerialviews/.ui.screensaver.DreamActivity
  ```

To restore the default Ambient screensaver, use the following ADB command...

``` 
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
```

## Amazon Fire TV users

Install the app from either the Amazon Appstore or sideload the APK.

On a Fire TV device there is no user-interface option to set Aerial Views (or any other 3rd party screensaver) as default.

The only way to achieve this is...
1. Download and install the Android [SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools) for Mac, Windows or Linux
2. Enable developer mode on your Android TV device
3. Using ADB, connect to your device and run the following ADB command to set Aerial Views as the default screensaver:
  ```
  adb shell settings put secure screensaver_components com.neilturner.aerialviews/com.neilturner.aerialviews.ui.screensaver.DreamActivity
  ```

## How remote and local playback of Apple videos works

* The app contains a video manifest (ie. a list of links) which it uses to figure out what videos to play in which format (1080p, 4K, HDR, etc)

* When using **local** playback, the app searches for the same filenames (eg. xyz.mov) locally instead of making a request to a remote web server

* **Local** videos can be placed in any folder

* When using **local and remote** playback, videos are used if found, but for missing videos, the remote version is used
