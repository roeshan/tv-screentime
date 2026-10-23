# TV Screen Time

A daily screen-time limit for Android TV, Google TV, and Android phones and tablets, locked with a PIN. No root,
no account, no internet permission. Install it over ADB (Wi-Fi or USB), or hand this repo to your coding agent
and let it do the install.

- **Daily quota.** 20 minutes on weekdays and 2 hours on weekends by default, reset at midnight. Both are
  changeable on the device.
- **Only watching counts.** Time is counted only while the screen is on and a video or Live TV app is in front:
  YouTube, Netflix, Jellyfin, Plex, Live TV and HDMI on a TV, YouTube, Netflix and TikTok on a phone, and others
  (see [`Apps.java`](app/src/main/java/io/github/roeshan/tvscreentime/Apps.java)). Menus, the launcher and
  background apps such as a VPN don't count.
- **Warnings** at 5 minutes and 1 minute left.
- **When time runs out:** playback pauses, sound mutes, and a full-screen PIN lock covers everything, including
  HDMI and Live TV. With nobody at the PIN for 60 seconds the device goes to standby, and the PIN lock is back
  when it is switched on again.
- **After the PIN:** add 15, 30 or 60 minutes for today, or turn the limit off for the rest of the day.
- **Hard to get around.** Home, Back and the app buttons on a remote do nothing while the lock is up. Settings,
  the Play Store and the uninstall dialog need the PIN, so the limiter can't be switched off, cleared or
  uninstalled on the device. Usage is saved as it happens, so pulling the plug doesn't reset it. Five wrong PINs
  lock entry for a minute.
- **Remote or touch.** On a TV everything works with the D-pad and number keys; on a phone or tablet, tap.
- **English and Indonesian**, following the system language.

Runs on Android 11 or newer: Android TV, Google TV, and Android phones and tablets. **LG (webOS) and Samsung
(Tizen) TVs aren't Android**, so the APK doesn't work there; for LG see [LG webOS TVs](#lg-webos-tvs) below.
Fire TV runs an Android-based system but hasn't been tried.

## Install with a coding agent

You need a computer and a coding agent with shell access, such as Claude Code, Codex or Gemini CLI. The computer
connects to the device over Wi-Fi (same network) or a USB cable.

1. **On the device**, turn on developer options and debugging:
   - **TV:** Settings → System → About (on some TVs Device Preferences → About), press OK on **Build** seven
     times. In **Developer options**, turn on **USB debugging**, then **Wireless debugging** (Google TV,
     Android 11+) or **Network debugging** (older Android TV). A USB cable works too if the TV's USB port supports
     debugging (common on sticks and projectors, rare on TV sets).
   - **Phone or tablet:** Settings → About phone, tap **Build number** seven times (on Xiaomi: **MIUI/OS
     version**). In **Developer options**, turn on **USB debugging** and plug in a cable, or turn on **Wireless
     debugging**.
2. **On your computer**, give your agent this prompt:

   > Install TV Screen Time from https://github.com/roeshan/tv-screentime on my `<TV / phone / tablet>`. Follow
   > AGENTS.md in that repo. It's a `<brand and model>`, and I connect with `<Wireless debugging / Network
   > debugging / USB>`. Its IP address is `<IP, for Wi-Fi only>`.

3. **Follow along.** Accept "Allow debugging?" on the device, read out the pairing code when asked, tell the
   agent which apps should count, and create your PIN on the device when it says so. Don't type the PIN into the
   chat.

The agent's step-by-step guide is [AGENTS.md](AGENTS.md).

## Install by hand

You need [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`).

**1. Connect to the device**, with one of these:

```bash
# Wireless debugging (Google TV, phones and tablets, Android 11+): pair once with the code shown on the device,
# then connect. The pairing port and the connect port are different; both are on the Wireless debugging screen.
adb pair <ip>:<pairing-port> <6-digit-code>
adb connect <ip>:<connect-port>

# Network debugging (older Android TV)
adb connect <ip>:5555

# USB
adb devices
```

`adb devices` should list the device as `device`. Accept the prompt on the device if it says `unauthorized`.

**2. Install.** Either take the prebuilt APK:

```bash
git clone https://github.com/roeshan/tv-screentime && cd tv-screentime
curl -fLo tv-screentime.apk https://github.com/roeshan/tv-screentime/releases/latest/download/tv-screentime.apk
scripts/install.sh tv-screentime.apk
```

or build from source. This needs JDK 17 and an Android SDK; on macOS:
`brew install openjdk@17 && brew install --cask android-commandlinetools`.

```bash
scripts/install.sh
```

The script installs the app, turns on its accessibility service without touching any others, takes it off
battery optimisation, and checks that Android started it.

**3. On the device**, open **TV Screen Time** (shown as **Pembatas TV** in Indonesian) from the app list and
create a 4-digit PIN. Nothing is limited until a PIN exists.

## Using it

Open the app and enter the PIN to:

- set the weekday and weekend quota (◀ ▶)
- turn the limit off or on for today
- reset today's usage
- change the PIN

Check the state from the computer:

```bash
adb shell dumpsys activity service io.github.roeshan.tvscreentime/.LimitService
```

```
pin set:        true
off today:      false
quota today:    20 min + 0 bonus
used today:     12 min 40 s
remaining:      7 min 20 s
weekday/weekend:20 / 120 min
current app:    com.google.android.youtube.tv (counted)
lock showing:   false
```

Changing the quota keeps the time already used. Usage goes back to zero at midnight or with **Reset today's
usage**, which also drops any minutes added from the lock screen that day.

## On phones and tablets

The same app and the same install. A few things behave differently from a TV:

- **Phone calls are never blocked.** The lock steps aside while a call is ringing or going on, and comes back
  afterwards. Time in a call doesn't count.
- **The system lock screen comes first.** After the device locks, the PIN lock only shows again once it is
  unlocked, so emergency calls from the lock screen keep working.
- **Settings needs the PIN.** That covers Wi-Fi and every other setting, not only this app's. The quick settings
  tiles in the notification shade still work. After the PIN, Settings stays open for 10 minutes.
- **Xiaomi, Oppo, Vivo, Realme** and some others stop background apps on their own. If the limit stops counting
  after a reboot, allow **Autostart** for TV Screen Time in the device's app settings, then run
  `scripts/install.sh` again.

## Which apps count

Edit [`Apps.java`](app/src/main/java/io/github/roeshan/tvscreentime/Apps.java), then run `scripts/install.sh` again.
Your PIN and today's usage are kept.

- `TRACKED`: apps whose screen time counts. To find an app's package, open it on the device and run
  `adb shell dumpsys window | grep mCurrentFocus`. On a TV, do the same with Live TV and an HDMI input open,
  because every brand uses a different Live TV app. Games and other apps can be added the same way.
- `GATED`: apps that need the PIN to open. Add your VPN app here if it shouldn't be switched off.
- `IGNORED`: windows that sit on top of an app without replacing it, such as the keyboard and system UI.

## Forgot the PIN, or want it gone

These need the computer that has ADB access to the device:

```bash
# Forget the PIN and all usage, then switch the service back on and set a new PIN on the device
adb shell pm clear io.github.roeshan.tvscreentime
scripts/install.sh tv-screentime.apk    # or scripts/install.sh to build

# Remove the app completely
adb uninstall io.github.roeshan.tvscreentime
```

## Limits

- Anyone with ADB access to the device (a paired computer, or a USB cable with USB debugging on) can switch it
  off. So can a factory reset. Turn off Wireless debugging and USB debugging once you're done if that matters.
- Android may stop the accessibility service when the app is updated. `scripts/install.sh` turns it back on.
- Standby after the 60-second timeout uses Android's lock-screen action. A few TV firmwares may only blank the
  screen instead.
- Tested so far on one TV, a Motorola SWTV-24AE-FHD (Skyworth, MediaTek, Google TV 14): time counting and the
  PIN lock pausing YouTube. Phone and tablet support is new and not yet tested on a device. Reports are welcome.

## Optional: debloat the TV for better performance

Budget TVs often ship with telemetry, ad services and preloaded apps that use memory and slow down the home
screen. [CleanYourTV](https://github.com/Gathin23/cleanyourtv) removes them over the same ADB connection (it
disables packages, nothing is uninstalled, and it has a one-line restore) and can replace the ad-filled home
screen with a clean launcher.

It's separate from this project and entirely optional. If you use both:

- Run CleanYourTV first, then this project's `scripts/install.sh`. The script adds TV Screen Time to the
  accessibility services already turned on, such as the CleanYourTV launcher's remote-button helpers, and
  leaves them in place.
- Keep `com.android.tv.settings` enabled. TV Screen Time uses it as the PIN-locked way into the TV settings.

## LG webOS TVs

LG TVs run webOS, not Android: they can't install an APK, have no ADB, and have no accessibility services, so
TV Screen Time can't run on them. Two things that can help instead:

- **A clean home screen without LG's ads:** [webos-vibehack](https://github.com/OmkarK97/webos-vibehack), the same
  Apple TV-style launcher as CleanYourTV rebuilt as a webOS app. No root needed; it installs through LG's
  Developer Mode. It's a separate community project, so follow its own README.
- **PIN locks:** LG's own Settings → General → Safety (the name varies by webOS version) can lock chosen apps
  and inputs behind a PIN. It isn't a daily quota: a locked app stays locked until the PIN is entered.

## How it works

The app is an accessibility service. Every 5 seconds it checks which app is on screen and whether the screen is
on, and adds the time to today's total when the app is in `TRACKED`. The lock is an accessibility overlay drawn
above every window. A remote's keys are read through the service's key filter, which is why Home and the app
buttons can't get past it. The app has no internet permission, and the PIN is stored as a salted SHA-256 hash.

## License

[PolyForm Noncommercial 1.0.0](LICENSE). Free to use, change and share for any noncommercial purpose: at home,
for your family, for learning or research, and in schools, charities and other noncommercial organisations.
Selling it, bundling it into a paid product or service, or other commercial use needs separate permission;
open an issue to ask.

The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) is Gradle's, under the Apache License 2.0.
