# Aerial Views

[![Latest GitHub release](https://img.shields.io/github/v/release/theothernt/AerialViews.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/theothernt/AerialViews/releases/latest)
[![GitHub Downloads](https://img.shields.io/github/downloads/theothernt/AerialViews/total?color=blue&label=Downloads&logo=github)](https://github.com/theothernt/AerialViews/releases/latest)
[![Google Play Downloads](https://img.shields.io/static/v1?style=flat&color=brightgreen&logo=google-play&logoColor=FFFFFF&label=Downloads&message=170k%2B)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews)
[![Amazon Fire TV](https://img.shields.io/static/v1?style=flat&color=FC4C02&logo=Amazon&logoColor=FFFFFF&label=Downloads&message=9k%2B)](https://www.amazon.com/gp/product/B0B4PPSNT6)

[![API](https://img.shields.io/badge/API-22%2B-lightgrey.svg?style=flat)](https://android-arsenal.com/api?level=22)
[![License](https://img.shields.io/:license-gpl%20v3-lightgrey.svg?style=flat)](https://raw.githubusercontent.com/theothernt/AerialViews/master/LICENSE)

A screensaver for Android/Google TV devices and phones including Nvidia Shield, Fire TV, and Google TV Streamer.

Inspired by Apple TV's beautiful video screensaver!

*Please read if you have an [Nvidia Shield](#nvidia-shield-users) or [a recent Google TV device.](#how-to-set-aerial-views-as-the-default-screensaver)*

## Features include...

* 4K Dolby Vision (HDR) videos, if your TV supports it
* Over 150 videos from Apple, Jetson Creative and Robin Fourcade
* Show videos & photos from USB storage, [Immich server](https://immich.app/), Samba, or WebDAV
* Place overlays in the corners of the screen such as clock, description/location, music playing, date, custom message
* Alternate the position of overlays to avoid burn-in on QD/OLED TVs
* Many playlist options to limit media length or loop certain videos
* Use the D-Pad or swipe (on phones, tablets, etc) to skip media, skip songs, change speed, seek, pause and more
* Refresh rate switching 24fps, 50fps, etc content

## Support the project

If you enjoy using the app, please consider [buying me a coffee](https://ko-fi.com/theothernt).

[![Ko-fi badge](docs/images/kofi_badge.png)](https://ko-fi.com/theothernt)

## How to get Aerial Views...

[![Google Play Store badge](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=com.neilturner.aerialviews) &nbsp;&nbsp;
[<img alt="Amazon Appstore badge" src="https://images-na.ssl-images-amazon.com/images/G/01/mobile-apps/devportal2/res/images/amazon-appstore-badge-english-black.png" width="153">](http://www.amazon.com/gp/mas/dl/android?p=com.neilturner.aerialviews)

Or [download the APK from the Releases page](https://github.com/theothernt/AerialViews/releases) and install it manually

## Want to Contribute?

Aerial Views is an open-source project — contributions are welcome! Whether it’s a bug fix, new feature, or improving translations, feel free to [open an issue](https://github.com/theothernt/AerialViews/issues) or submit a pull request.

And please get in contact before submitting pull requests, thanks!

## Translations

If Aerial Views is not available in your language but you have some free time to help translate menu text and video descriptions, please get in touch!

We use the [Lokalise](https://lokalise.com/) platform to coordinate translations for Aerial Views and thank them for their support of this open-source project.

[![Lokalise logo](docs/images/lokalise_logo.png)](https://lokalise.com/)

## Where to download videos for offline/local playback

Download curated videos from...

* [Apple](https://aerial-videos.netlify.app/#apple) (114 videos)
* [Jetson Creative](https://aerial-videos.netlify.app/#jetson-creative) (20 community videos)
* [Robin Fourcade](https://aerial-videos.netlify.app/#robin-fourcade) (18 community videos)

## How to set Aerial Views as the default screensaver

Since 2023, nearly all devices that ship with Google TV, running Android TV 12 or later, have no user-interface to change the screensaver to a 3rd party one...

* __Chromecast with Google TV, Google TV Streamer__
* __Recent MECOOL devices__
* __Recent TCL, Philips, and Sony TVs__
* __onn. Google TV devices (excluding the 2021 model)__
* __Fire TV (won't work with Fire OS 8.1 and above)__

But it can be done manually. Here is an overview of the steps...

1. Enable Developer mode, enable USB debugging, then find the IP address of your device
2. Use a Mac, iPhone, PC or Android phone with the required software or app
3. Connect to your Android/Google/Fire TV device
4. Run two ADB commands, one to set Aerial Views as the default screensaver, the other to set how long it takes the screensaver to start

The full instructions are below, please click or tap to expand each step.

Another option is to use the *TDUK Screensaver Manager* app. Details on the app are below.

<details>
<summary>Enable Developer Mode on your Android/Google TV</summary>
&nbsp;

Navigate to the Settings menu on your device, then to the About screen. Depending on the device…

`Settings > System > About` or
`Settings > Device Preferences > About`

Scroll down to __Build__ and select __Build__ several times until you get the message "You are now a developer!"

Return to __Settings__ and look for the newly enabled __Developer options__ page.

On the __Developer options__ page, look for the __USB debugging__ option and enable it.

Next, find the __IP address__ of your device. Try looking in the Network & Internet settings of the device, check the properties of the current LAN or WIFI connection - that should list the current IP address eg. 192.168.1.105
</details>

<details>
<summary>Enable Developer Mode on Fire Stick/TV</summary>
&nbsp;

Open __Settings__, then navigate to __My Fire TV__ then the __About__ screen.

Highlight your device name and press the action button on your remote seven times.

You'll now see a message confirming "You are now a developer", and it'll unlock the __Developer Options__ in the previous menu.

Navigate to the __Developer Options__ page, look for the __ADB debugging__ option and enable it.

Next, find the IP address of your device and make a note of it. Navigate to the __About__ then __Network__ screen, which will show your current IP address eg. 192.168.1.120
</details>

<details>
<summary>Allow Auto Launch on TCL TVs</summary>
&nbsp;

If you have a TCL TV with Google TV, you need to allow the Auto Launch permission so that Aerial Views can be launched from the background when the screensaver starts.

Otherwise, the screensaver cannot be started, either automatically, or manually via the Screensaver menu shortcut, unless the Aerial Views app has been recently opened (see [#191](https://github.com/theothernt/AerialViews/issues/191) for details).

1. Open the __Safety Guard__ app on your TV
2. Navigate to `Permission Shield > Auto Launch Permission`
3. Change the `Auto manager` at the top to `Closed` - this allows you to manually select which apps can auto-launch instead of the system deciding automatically
4. Scroll to __Aerial Views__ and change it to `Opened`

</details>

<details>
<summary>Connect using an iPhone</summary>
&nbsp;

Find an iPhone app that is capable of running ADB commands, [such as iSH Shell](https://ish.app/), which is free.

Once installed, run the app and install the Android Tools with the following commands…

```sh
apk update
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
&nbsp;

Find an Android app that is capable of running ADB commands, [such as Remote Termux](https://play.google.com/store/apps/details?id=com.termux), which is free.

Once installed, run the app and install the Android Tools with the following commands…

```sh
pkg update
pkg install android-tools
```

To check if the ADB command is working, try typing…

```sh
adb version 
```

After pressing return, you should see something like this

```sh
Android Debug Bridge version 1.0.41
Version  34.0.0p1-android-tools
```

Now you can execute ADB commands.

</details>

<details>
<summary>Connect using a Mac</summary>
&nbsp;

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
&nbsp;

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
&nbsp;

Connect to your Android TV device and start a command shell...

```sh
adb connect <ip_address>
```

:information_source: *Use the IP address of your device from earlier steps, it should be something like 192.168.1.98*

```sh
adb shell
```

:information_source: *The first time you connect to your Android TV device, you will probably see a confirmation dialogue asking to "allow" the connection*

Next, set Aerial Views as the default screensaver with this command…

```sh
settings put secure screensaver_components com.neilturner.aerialviews/.ui.screensaver.DreamActivity
```

Optional: Confirm that the command was run successfully, as there is no confirmation when the command above is run.

```sh
settings get secure screensaver_components
```

If set correctly, you should see... 

```sh
com.neilturner.aerialviews/.ui.screensaver.DreamActivity
```

</details>

<details>
<summary>ADB command - extra command for Fire TV + Fire OS 7.6.9.x</summary>
&nbsp;

Recent updates to Fire OS mean extra commands are required for Aerial Views to function properly as the default screensaver.

Like with previous ADB commands, connect to your Android TV device and start a command shell. Then run the following commands...

```sh
settings put secure screensaver_default_component com.neilturner.aerialviews/.ui.screensaver.DreamActivity
settings put secure contextual_screen_off_timeout 300000 
settings put secure screensaver_enabled 1
```

</details>

<details>
<summary>ADB command - change the screensaver timeout</summary>
&nbsp;

To change the default timeout use this command with a value in milliseconds. So, 5 minutes is 300000, 10 minutes is 600000 and so on.

```sh
settings put system screen_off_timeout 600000
```

</details>

<details>
<summary>How to revert back to the default screensaver</summary>
&nbsp;

For whatever reason, if you would like to stop using Aerial Views and revert back to the original screensaver, there are two options…

* Reset your device. Doing so will also reset the screensaver preference
* Use an ADB commands to enable the default screensaver, depending on your device

1. Follow the instructions above to connect to your Android/Google TV device using an iPhone, Android phone, Mac, PC, etc
2. Run one of the following commands...

### To restore the default Google TV ambient screensaver

```sh
settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
```

### To restore the default Fire TV screensaver

```sh
settings put secure screensaver_components com.amazon.bueller.photos/.daydream.ScreenSaverService
```

### To restore the default (older) Android TV backdrop screensaver

```sh
settings put secure screensaver_components com.google.android.backdrop/.Backdrop
```

</details>

<details>
<summary>Use the TDUK Screensaver Manager app</summary>
&nbsp;

The [TDUK Screensaver Manager](https://play.google.com/store/apps/details?id=com.tduk.scrmgr) is a paid app (approx. $2/£2/€2) which allows you to easily change the active screensaver on your Android/Google TV device using a simple interface.

Please make sure to enable **Developer Mode** and **USB/Networking Debugging**. Instructions are above.

:information_source: This app will not work on recent Fire TV devices due to changes by Amazon.

</details>

## Frequently asked questions

Please click or tap to expand each item below...

<details>
<summary>Playing local media on the Nvidia Shield </summary>
&nbsp;

If your device is running Android 11 (Shield Experience 9+) and you want to play videos from a USB storage device, make sure the following setting is enabled:

`Settings > Device Preferences > Storage > Scan for
media automatically`

To change the default screensaver on your Nvidia Shield, use the following menu:

`Settings > Device Preferences > Screen saver`

</details>

<details>
<summary>Fire TV and Frame Rate Switching</summary>
&nbsp;

Fire OS has no menu to allow advanced permissions for apps, so it must be done manually with an ADB command...

```sh
adb shell appops set com.neilturner.aerialviews SYSTEM_ALERT_WINDOW allow
```

</details>

<details>
<summary>HDR videos don't seem to play, I only see black screen...</summary>
&nbsp;

Apple's videos only support Dolby Vision HDR. Even if your TV supports HDR, it might not support Dolby Vision.

Here are some things to try...

* Find the make and model of your TV, search online for a product page for that device and it should list the supported HDR modes (there are a few!)

* If you use a Nvidia Shield, please [follow their instructions on how to enable (or confirm) that Dolby Vision playback is possible](https://www.nvidia.com/en-us/shield/support/shield-tv/enable-dolby-vision-hdr10-on-shield/)

* Confirm that Dolby Vision playback works in other apps like Netflix, Disney+, or Amazon Prime Video

</details>

<details>
<summary>How to launch Aerial Views from other apps</summary>
&nbsp;

Android screensaver use a special intent (DreamService) which cannot be called by 3rd party apps, only the OS itself.

Instead, Aerial Views uses a standard intent which can be called by 3rd party to launch the 'Test screensaver' (Activity) which works in the same way as launching the screensaver.

To do this, launch the following intent in apps like [Button Mapper](https://play.google.com/store/apps/details?id=flar2.homebutton) or [tvQuickActions](https://play.google.com/store/apps/details?id=dev.vodik7.tvquickactions.free)

```sh
com.neilturner.aerialviews/.ui.screensaver.TestActivity
```

If you are using Fully Kiosk, try the following...

```sh
intent://#Intent;component=com.neilturner.aerialviews/.ui.screensaver.TestActivity;end
```

</details>

## Weather data

Thanks to [OpenWeather](https://openweathermap.org/) for providing weather data to this and other open-source projects.

[![OpenWeather logo](docs/images/openweather_logo.png)](https://openweathermap.org/)

## About

Aerial Views is based on [Aerial Dream](https://github.com/cachapa/AerialDream), which was created by Daniel Cachapa in late 2015.

Aerial Views started in early 2020 as a fork with a couple of fixes and features to improve the experience on an old Sony Android TV. Around this time, Aerial Dream was not in active development anymore.

Shortly after putting the code on GitHub, others found the fork and started requesting new builds with fixes and additional features. This led to the eventual release of Aerial Views on the Google Play Store in 2022, and later, the Amazon Appstore.
