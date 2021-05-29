# AerialDream

[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=23)
[![License](https://img.shields.io/:license-gpl%20v3-brightgreen.svg?style=flat)](https://raw.githubusercontent.com/cachapa/AerialDream/master/LICENSE)

### A screensaver for Android devices inspired on Apple TV's video screensaver.

<br/>

This is a modified (forked) version which features the following changes...

- Great performance and support for older Android TVs (eg. 2015/ATV1 platform)

- Supports *only* the latest Apple video feed (it contains nearly all previous videos too!)

- Option to play local version of video

- HDR support (HDR10, Dolby Vision tested)

- Skip video feature (Swipe right on your phone or press right on your TV remote's d-pad)

- Alternate text position to help avoid burn-in on OLED TVs

<br/>

## How to install...

This version of the app is not available in the Play Store, yet, so your options are...

1. [Download the APK from the Releases page](https://github.com/theothernt/AerialDream/releases) and install it manually

2. Clone this repo to your PC or Mac, download Android Studio, then build and deploy

<br/>

## Special note for Fire TV owners...

The app can be installed and configured, like with any other Android TV device, but the ability to set Aerial Dream as the default screensaver has been disabled in the FireOS menus.

To get around this, you'll have to install a command-line developer tool, run a command - and that's it. I would recommend having a look at [this guide over at Nerds Chalk](https://nerdschalk.com/change-fire-tv-screensaver-apple-tv/) for the exact steps. 

Just note that the command is little different due to a name change in the code, try this instead:

`adb shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService:com.codingbuffalo.aerialdream/.ui.screensaver.DreamActivity settings put secure screensaver_default_component com.codingbuffalo.aerialdream/.ui.screensaver.DreamActivity`


<br/>

## About remote and local playback...

- The app contains a JSON manifest (ie. a list of URLs) which it uses to figure out what videos to play in which format (1080p, 4K, HDR, etc)

- When using **local** playback, the app searches for the same filenames (eg. xyz.mov) locally instead of making a remote call

- It's important to note that **local** playback ignores videos that do not match the filenames found in the JSON manifest

- **Local** videos can be placed in any folder

- When using **local and remote** playback, local videos are used if found, but for missing videos, the remote version is used.

<br/>

## Device support and testing...

- The plan is the keep support and great performance for older Android TVs

- Newer features, which require more powerful devices (eg. Nvidia Shield or Google TV) can be disabled or enabled

- The app has been tested with...
  - Sony Bravia (2015, Android TV v6)
  - Nvidia Shield (2015, Android TV v7)
  - Fire TV Stick 4K (2018, Android v7)
  - Nvidia Shield (2019, Android TV v9)
  - Chromecast/Google TV (2020, Android TV v10)

<br/>

## Known issues...

- HDR videos may not play properly for everyone, even with a TV that supports HDR/HDR10 and Dolby Vision. If you get a black screen or the colours are not correct, please use the SDR option

- This app cannot be installed at the same time as Aeriel Dream from the Google Play Store

- If set to local playback only and there are no videos, there is no error message or notification - only a black screen with the time

- This app is designed to play Apple's video only. It will not play any random video you have on the device or USB storage

- Network connection issues are not handled well, the videos will simply pause
