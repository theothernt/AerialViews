# AerialDream

[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=23)
[![License](https://img.shields.io/:license-gpl%20v3-brightgreen.svg?style=flat)](https://raw.githubusercontent.com/cachapa/AerialDream/master/LICENSE)

### A screensaver for Android devices inspired on Apple TV's video screensaver.

<br/>

This is a modified (forked) version which features the following changes...

- Support for older Android TVs (2015/ATV1)

- Supports only the latest Apple video feed

- Option to play local version of video

- Skip video feature (Swipe right on your phone or press right on your TV remote's d-pad)

<br/>

## About remote and local playback...

- The app contains a JSON manifest (ie. a list of URLs) which it uses to figure out what videos to play in which format (1080p, 4K, HDR, etc)

- When using **local** playback, the app searches for the same filenames (eg. xyz.mov) locally instead of making a remote call

- It's important to note that **local** playback ignores videos that do not match the filenames found in the JSON manifest

- **Local** videos can be placed in any folder

- When using **local and remote** playback, local videos are used if found, but for missing vidoes, the remote version is used.

<br/>

## Device support and testing...

- The plan is the keep support and good performance for older Android TVs, but add options that will only work well on newer, more powerful devices (eg. Nvidia Shield or Google TV)

- The app has been tested on a Sony Bravia TV (2015, 1080p, SDR) and Google TV. If you're able to test on other devices, especially with 4K and HDR, please let me know!

<br/>

## Known issues...

- This app cannot be installed at the same time as Aeriel Dream from the Google Play Store

- If set to local playback only and there are no videos, there is no error message or notification - only a black screen with the time

- If certain video apps (eg. Plex) are left paused and the screensaver launches, you will only see a black screen with the time as the paused app did not release its lock on the video surface

- Network connection issues are not handled well, the videos will simply pause
