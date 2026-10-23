# TV Screen Time

A daily watch-time limit for Android TV and Google TV, locked with a PIN. No root, no account, no internet
permission. Install it over ADB (Wi-Fi or USB), or hand this repo to your coding agent and let it do the install.

- **Daily quota.** 20 minutes on weekdays and 2 hours on weekends by default, reset at midnight. Both are
  changeable on the TV.
- **Only watching counts.** Time is counted only while the screen is on and a video or Live TV app is in front
  (YouTube, Netflix, Jellyfin, Plex, Live TV and HDMI, and others; see [`Apps.java`](app/src/main/java/io/github/roeshan/tvscreentime/Apps.java)).
  Menus, the launcher and background apps such as a VPN don't count.
- **Warnings** at 5 minutes and 1 minute left.
- **When time runs out:** playback pauses, sound mutes, and a full-screen PIN lock covers everything, including
  HDMI and Live TV. With nobody at the PIN for 60 seconds the TV goes to standby, and it comes back to the PIN lock
  when it is switched on again.
- **After the PIN:** add 15, 30 or 60 minutes for today, or turn the limit off for the rest of the day.
- **Hard to get around.** Home, Back and the app buttons on the remote do nothing while the lock is up. Settings
  and the Play Store need the PIN, so the limiter can't be switched off, cleared or uninstalled from the TV.
  Usage is saved as it happens, so pulling the plug doesn't reset it. Five wrong PINs lock entry for a minute.
- **English and Indonesian**, following the TV's system language.

## Install with a coding agent

You need a computer on the same Wi-Fi as the TV (or a USB cable to it) and a coding agent with shell access,
such as Claude Code, Codex or Gemini CLI.

1. **On the TV**, open Settings → System → About (on some TVs Device Preferences → About), press OK on
   **Build** seven times, go back to **Developer options** and turn on **USB debugging**. Then turn on one of
   these, whichever your TV has:
   - **Wireless debugging** (Google TV, Android 11 and newer)
   - **Network debugging** / **ADB over network** (older Android TV)
   - or plug a USB cable from the computer into the TV, if its USB port supports debugging (common on sticks and
     projectors, rare on TV sets)
2. **On your computer**, give your agent this prompt:

   > Install TV Screen Time on my Android TV from https://github.com/roeshan/tv-screentime. Follow AGENTS.md in
   > that repo. My TV is a `<brand and model>`, its IP address is `<IP shown in Settings → Network>`, and I
   > connect with `<Wireless debugging / Network debugging / USB>`.

3. **Follow along.** Accept "Allow debugging?" on the TV, read out the pairing code when asked, tell the agent
   which apps should count, and create your PIN on the TV when it says so. Don't type the PIN into the chat.

The agent's step-by-step guide is [AGENTS.md](AGENTS.md).

## Install by hand

You need [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`).

**1. Connect to the TV**, with one of these:

```bash
# Wireless debugging (Google TV / Android 11+): pair once with the code shown on the TV, then connect.
# The pairing port and the connect port are different; both are on the TV's Wireless debugging screen.
adb pair <tv-ip>:<pairing-port> <6-digit-code>
adb connect <tv-ip>:<connect-port>

# Network debugging (older Android TV)
adb connect <tv-ip>:5555

# USB
adb devices
```

`adb devices` should list the TV as `device`. Accept the prompt on the TV if it says `unauthorized`.

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

The script installs the app, turns on its accessibility service without touching any others, and checks that
Android started it.

**3. On the TV**, open **TV Screen Time** (shown as **Pembatas TV** in Indonesian) from the app list and create a
4-digit PIN. Nothing is limited until a PIN exists.

## Using it

Open the app on the TV and enter the PIN to:

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

## Which apps count

Edit [`Apps.java`](app/src/main/java/io/github/roeshan/tvscreentime/Apps.java), then run `scripts/install.sh` again.
Your PIN and today's usage are kept.

- `TRACKED`: apps whose screen time counts. To find an app's package, open it on the TV and run
  `adb shell dumpsys window | grep mCurrentFocus`. Do the same with Live TV and an HDMI input open, because
  every TV brand uses a different Live TV app.
- `GATED`: apps that need the PIN to open. Add your VPN app here if it shouldn't be switched off.
- `IGNORED`: windows that sit on top of an app without replacing it, such as the keyboard and system UI.

## Forgot the PIN, or want it gone

These need the computer that has ADB access to the TV:

```bash
# Forget the PIN and all usage, then switch the service back on and set a new PIN on the TV
adb shell pm clear io.github.roeshan.tvscreentime
scripts/install.sh tv-screentime.apk    # or scripts/install.sh to build

# Remove the app completely
adb uninstall io.github.roeshan.tvscreentime
```

## Limits

- Anyone with ADB access to the TV (a paired computer, or a USB cable with USB debugging on) can switch it off.
  So can a factory reset. Turn off Wireless debugging on the TV once you're done if that matters.
- Android may stop the accessibility service when the app is updated. `scripts/install.sh` turns it back on.
- Standby after the 60-second timeout uses Android's lock-screen action. A few vendor firmwares may only blank
  the screen instead.
- Tested so far on one TV, a Motorola SWTV-24AE-FHD (Skyworth, MediaTek, Google TV 14): time counting and the
  PIN lock pausing YouTube. Reports from other TVs are welcome.

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

## How it works

The app is an accessibility service. Every 5 seconds it checks which app is on screen and whether the screen is
on, and adds the time to today's total when the app is in `TRACKED`. The lock is an accessibility overlay drawn
above every window. The remote's keys are read through the service's key filter, which is why Home and the app
buttons can't get past it. The app has no internet permission, and the PIN is stored as a salted SHA-256 hash.

## License

[MIT](LICENSE)
