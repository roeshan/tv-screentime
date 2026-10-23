# Installing TV Screen Time: guide for coding agents

You are installing a PIN-locked daily screen-time limit on the user's Android TV, Google TV, Android phone or
tablet, over ADB from the computer you run on. The same APK and steps work on all of them; differences are
called out below. Work through the steps in order, and check each one before going on. Read the README for
what the app does.

## Ground rules

- **The PIN belongs to the user.** They create it on the device. Never ask for it, never put it in chat, a file or a
  command.
- **Change only what this guide changes.** Don't disable, uninstall or "clean up" other packages. Debloating is
  [CleanYourTV](https://github.com/Gathin23/cleanyourtv)'s job: mention it once as optional, and only run it if the
  user asks.
- **Never overwrite `enabled_accessibility_services`.** Other apps (a launcher's remote-button helpers, a screen
  reader) may be in that list. `scripts/install.sh` adds to it; use the script.
- **Ask before anything the user would notice on the device**, such as opening an app or rebooting.
- If a step fails, stop and tell the user what failed and the exact output. Don't work around it by changing system
  settings.

## 1. Ask the user

- TV, phone or tablet, and its brand and model.
- How they connect: **Wireless debugging** (Google TV, phones and tablets, Android 11+), **Network debugging**
  (older Android TV) or **USB**. For Wi-Fi, the IP address (Settings → Network & Internet → the Wi-Fi network,
  or the Wireless debugging screen).
- Which apps should count. The default list is in
  [`Apps.java`](app/src/main/java/io/github/roeshan/tvscreentime/Apps.java) (`TRACKED`): YouTube, YouTube Kids,
  Netflix, Prime Video, Disney+, Jellyfin, Plex, Kodi, Vidio and common Live TV apps on a TV; YouTube, YouTube
  Kids, Netflix, Prime Video, Hotstar, Jellyfin, Vidio and TikTok on a phone. Games or other apps can be added.
- The quota. Defaults are 20 min Mon–Fri and 120 min Sat–Sun; the user can change both on the device later.

## 2. Get the tools

- `adb`: check with `adb version`. macOS: `brew install --cask android-platform-tools`. Linux: the
  `android-tools`/`adb` package. Windows: Google's platform-tools zip.
- Building from source also needs **JDK 17** and an **Android SDK** (macOS:
  `brew install openjdk@17 && brew install --cask android-commandlinetools`). Without them, use the prebuilt APK
  in step 5.

## 3. Connect to the device

Guide the user through turning on developer options and debugging:
- **TV:** Settings → System → About (or Device Preferences → About), press OK on **Build** seven times, then
  Developer options → **USB debugging** on, and the option for how they connect.
- **Phone or tablet:** Settings → About phone, tap **Build number** seven times (Xiaomi: **MIUI/OS version**;
  Samsung: Software information → Build number), then Developer options → **USB debugging** on, or
  **Wireless debugging** on. Xiaomi also needs **USB debugging (Security settings)** on.

**Wireless debugging.** The user opens Developer options → Wireless debugging → **Pair device with pairing
code**. The device shows an `ip:port` and a 6-digit code. Pair right away, because the code expires quickly:

```bash
adb pair <ip>:<pairing-port> <code>
adb connect <ip>:<connect-port>   # the port on the main Wireless debugging screen, not the pairing port
```

The connect port changes whenever Wireless debugging is switched on again, and Android switches it off on every
reboot and on a change of Wi-Fi network. After the first pairing, `adb devices` usually finds the device again
over mDNS once Wireless debugging is back on.

**Network debugging:** `adb connect <ip>:5555`.

**USB:** plug in the cable, then run `adb devices`.

Done when `adb devices` lists the device as `device`. If it says `unauthorized`, the user has to accept the
"Allow debugging?" prompt on the device and tick "Always allow".

Troubleshooting:
- On macOS, `No route to host` to a device that answers `ping` from the user's own Terminal means your process is
  blocked from the local network. Ask the user to allow your app in System Settings → Privacy & Security →
  Local Network, or to run the `adb` commands with a `!` prefix themselves.
- If several devices are listed, set `ANDROID_SERIAL=<ip:port or serial>` for every command below.
- The app needs Android 11 or newer: `adb shell getprop ro.build.version.sdk` must print 30 or more.

## 4. Check which apps count

For each app the user wants counted that isn't in `TRACKED`, and on a TV for **Live TV and an HDMI input**,
which differ by brand:

1. Ask the user to open it on the device.
2. Run `adb shell dumpsys window | grep mCurrentFocus`. The package comes before the `/`.

Every package already in `TRACKED`: use the prebuilt APK. If anything is missing: add it to `TRACKED` in
`Apps.java` with a short comment, and build from source.

If the user wants a VPN app (for example `com.tailscale.ipn`) protected from being switched off, add it to `GATED`.

## 5. Install

From the repo root:

```bash
# Prebuilt APK (no JDK or SDK needed)
curl -fLo tv-screentime.apk https://github.com/roeshan/tv-screentime/releases/latest/download/tv-screentime.apk
scripts/install.sh tv-screentime.apk

# Or build from source (needed after editing Apps.java)
scripts/install.sh
```

The script installs over any older build, turns the accessibility service on, and prints the service status.
Done when it ends with `Installed and running.`

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means a build signed with a different key is already on the device. Removing
it deletes the PIN and today's usage, so ask the user before running `adb uninstall io.github.roeshan.tvscreentime`.

**Phones from Xiaomi, Oppo, Vivo, Realme** and similar stop background apps by themselves. Ask the user to allow
**Autostart** for TV Screen Time (Settings → Apps → TV Screen Time) now, before the PIN exists, because Settings
needs the PIN afterwards. The script already takes the app off battery optimisation.

## 6. The user sets the PIN

Ask the user to open **TV Screen Time** (shown as **Pembatas TV** in Indonesian) from the app list, create a
4-digit PIN, repeat it, and set the quotas with ◀ ▶ (a remote's D-pad, or a tap on a phone). From now on the
device's Settings, Play Store and uninstall dialog ask for this PIN. Then check:

```bash
adb shell dumpsys activity service io.github.roeshan.tvscreentime/.LimitService
```

Done when it shows `pin set: true`.

## 7. Test (with the user's OK)

1. The user sets the weekday or weekend quota (whichever is today) to **5 minutes** on the device.
2. The user plays something in a counted app. `current app:` should say `(counted)` and `used today` should
   grow every few seconds.
3. At 0 left: `lock showing: true (quota)`. Optionally take a screenshot with
   `adb exec-out screencap -p > lock.png` and look at it.
4. Leave the lock alone for 60 seconds: the device should go to standby. When it's switched on (and, on a phone,
   unlocked), the lock should be back. ADB over Wi-Fi drops while the device is in standby; that is expected.
   On a phone, also check that an incoming call is shown over the lock.
5. The user enters the PIN, picks **Add 15 minutes**, opens Settings (it should ask for the PIN), then sets the
   quota back to what they want.

## 8. Hand over

Tell the user:
- how to check the status (the `dumpsys` command above)
- that `adb shell pm clear io.github.roeshan.tvscreentime` followed by `scripts/install.sh` resets a forgotten PIN
- that `adb uninstall io.github.roeshan.tvscreentime` removes the app
- that anyone with ADB access can bypass the limit, so they may want to switch Wireless, Network and USB
  debugging off now
- on a TV, optionally, that [CleanYourTV](https://github.com/Gathin23/cleanyourtv) can debloat it for better
  performance, and should run before `scripts/install.sh` if they use both
