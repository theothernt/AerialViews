# Aerial Views

[![Latest GitHub release](https://img.shields.io/github/v/release/theothernt/AerialViews.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/theothernt/AerialViews/releases/latest)
[![GitHub Downloads](https://img.shields.io/github/downloads/theothernt/AerialViews/total?color=blue&label=Downloads&logo=github)](https://github.com/theothernt/AerialViews/releases/latest)
[![Google Play Downloads](https://img.shields.io/static/v1?style=flat&color=brightgreen&logo=google-play&logoColor=FFFFFF&label=Downloads&message=50k%2B)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews)
[![Amazon Fire TV](https://img.shields.io/static/v1?style=flat&color=FC4C02&logo=Amazon&logoColor=FFFFFF&label=Downloads&message=2k%2B)](https://www.amazon.com/gp/product/B0B4PPSNT6)

[![API](https://img.shields.io/badge/API-22%2B-lightgrey.svg?style=flat)](https://android-arsenal.com/api?level=22)
[![License](https://img.shields.io/:license-gpl%20v3-lightgrey.svg?style=flat)](https://raw.githubusercontent.com/theothernt/AerialViews/master/LICENSE)

A screensaver for Android TV devices including Nvidia Shield, Fire TV, and Chromecast with Google TV. Inspired by Apple TV's video screensaver.

*Please read if you have a [Nvidia Shield](#nvidia-shield-users), [Chromecast with Google TV](#change-default), [onn. Google TV 4K Streaming Box](#change-default) or [Amazon Fire TV](#change-default)*

## Features include...

* 4K Dolby Vision (HDR) videos, if your TV supports it
* Over 150 videos from Apple, Jetson Creative and Robin Fourcade
* Play videos from your device, USB storage or network share
* Option to avoid burn-in on QD/OLED TVs
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

## How to set Aerial Views as the default screensaver<a id="change-default"></a>

The following devices have no user-interface to change the screensaver to a 3rd party one...

* __Chromecast with Google TV__
* __onn. Google TV 4K Streaming Box 2023 (not 2021) model__
* __Fire TV__

But it can be done manually. Here is an overview of the steps...

1. Enable Developer mode on your device and find its IP address
2. Use a Mac, iPhone, PC or Android phone with the required software or app
3. Connect to your Android/Google/Fire TV device
4. Run two ADB commands, one to set Aerial Views as the default screensaver, the other to set how long it takes the screensaver to start

<details>
<summary>Enable Developer Mode on your Android/Google TV</summary>

Navigate to the Settings menu on your device, then to the About screen. Depending on the device…

`Settings > System > About` or
`Settings > Device Preferences > About`

Scroll down to __Build__ and select __Build__ several times until you get the message "You are now a developer!"

Return to __Settings__ and look for the newly enabled __Developer options__ page.

On the __Developer options__ page, look for the __USB debugging__ option and enable it.

Next, find the IP address of your device. Try looking in the Network & Internet settings of the device, check the properties of the current LAN or WIFI connection - that should list the current IP address eg. 192.168.1.105
</details>

<details>
<summary>Enable Developer Mode on Fire Stick/TV</summary>

Open __Settings__, then navigate to __My Fire TV__ then the __About__ screen.

Highlight the first option on the list, which is usually your device's name, and press the action button on your remote seven times.

You'll now see a message confirming "You are now a developer", and it'll unlock the __Developer Options__ in the previous menu.

Navigate to the __Developer Options__ page, look for the __ADB debugging__ option and enable it.

Next, find the IP address of your device. Navigate to the __About__ then __Network__ screen, which will show your current IP address eg. 192.168.1.120
</details>

<details>
<summary>Connect using an iPhone</summary>

Find an iPhone app that is capable of running ADB commands, [such as iSH Shell](https://ish.app/), which is free.

Once installed, run the app and install the Android Tools with the following command…

```sh
apk add android-tools
```

To check if the ADB command is working, try typing…

```sh
adb version 
```

After pressing return, you should see something like this

```sh
Android Debug Bridge version 1.0.41
Version  31.0.0p1-android-tools
```

Now you can execute ADB commands.
</details>

<details>
<summary>Connect using an Android phone</summary>

Find an Android app that is capable of running ADB commands, [such as Remote ADB Shell](https://play.google.com/store/apps/details?id=com.cgutman.androidremotedebugger), which is free.

Once installed, run the app and connect to your device using its IP address.

To confirm the connection, try a command like `ls` which should show a list of files and folder.

Now you can execute ADB commands.
</details>

<details>
<summary>Connect using a Mac</summary>

Download the official [SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools) for Mac.

Extract the files from the ZIP archive to a folder. Then open a Terminal or Command Prompt and navigate to the folder.

To check if the ADB command is working, try typing…

```sh
adb version
```

After pressing return, you should see something like this

```sh
Android Debug Bridge version 1.0.41
Version  35.0.0-11411520
```

Now you can execute ADB commands.
</details>

<details>
<summary>Connect using a PC with Windows</summary>

Download the official [SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools) for Windows.

An alternate option is [Tiny ADB and Fastboot Tool (Portable version)](https://androidmtk.com/tiny-adb-and-fastboot-tool) but they both work in the same way.

Extract the files from the ZIP archive to a folder. Then open a Terminal or Command Prompt and navigate to the folder.

To check if the ADB command is working, try typing…

```sh
adb version
```

After pressing return, you should see something like this

```sh
Android Debug Bridge version 1.0.41
Version  35.0.0-11411520
```

</details>

<details>
<summary>ADB command - set Aerial Views as the default screensaver</summary>

Connect to your Android TV device and start a command shell…

(If you’re using an Android phone, skip these two commands)

```sh
adb connect ip_address
```

```sh
adb shell
```

Next, set Aerial Views as the default screensaver…

```sh
settings put secure screensaver_components com.neilturner.aerialviews/.ui.screensaver.DreamActivity
```

</details>

<details>
<summary>ADB command - change the screensaver timeout</summary>

To change the default timeout use this command with a value in milliseconds. So, 5 minutes is 30000, 10 minutes is 60000 and so on.

```sh
settings put system screen_off_timeout 60000
```

</details>

<details>
<summary>How to revert back to the default screensaver</summary>

For whatever reason, if you would like to stop using Aerial Views and revert back to the original screensaver, there are two options…

* Reset your device. Doing so will also reset the screensaver preference
* Use an ADB commands to enable the default screensaver, depending on your device

To restore the default Google TV ambient screensaver...

```sh
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
```

To restore the default Fire TV screensaver...

```sh
adb shell settings put secure screensaver_components com.amazon.bueller.photos/.daydream.ScreenSaverService
```

</details>

## Weather data

Thanks to [OpenWeather](https://openweathermap.org/) for providing weather data to this and other open-source projects.

[![OpenWeather logo](docs/images/openweather_logo.png)](https://openweathermap.org/)

## About

Aerial Views is based on [Aerial Dream](https://github.com/cachapa/AerialDream), which was created by Daniel Cachapa in late 2015.

Aerial Views started in early 2020 as a fork with a couple of fixes and features to improve the experience on an old Sony Android TV. Around this time, Aerial Dream was not in active development anymore.

Shortly after putting the code on GitHub, others found the fork and started requesting new builds with fixes and additional features. This led to the eventual release of Aerial Views on the Google Play Store in 2022, and later, the Amazon Appstore.
