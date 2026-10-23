#!/usr/bin/env bash
# Build TV Screen Time, install it on the TV that adb is connected to, and switch on its accessibility service.
# Safe to run again: it reinstalls over the old build (usage and PIN are kept) and re-enables the service.
#
#   scripts/install.sh                          # build from source, install on the only device adb sees
#   scripts/install.sh path/to/tv-screentime.apk # install a prebuilt APK (from GitHub Releases), no build
#   ANDROID_SERIAL=<ip:port or serial> scripts/install.sh
#
# The TV can be connected over Wi-Fi (Wireless debugging or Network debugging) or a USB cable; see README.
set -euo pipefail

APK=${1:-}
# Resolve a prebuilt APK against the caller's directory before moving to the repo root.
if [ -n "$APK" ] && [ -f "$APK" ]; then APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"; fi
cd "$(dirname "$0")/.."

PKG=io.github.roeshan.tvscreentime
SERVICE=$PKG/.LimitService

die() { echo "ERROR: $*" >&2; exit 1; }

# 1. A TV must answer over adb.
command -v adb >/dev/null || die "adb not found. Install Android platform-tools (macOS: brew install --cask android-platform-tools)."
STATE=$(adb get-state 2>/dev/null || true)
[ "$STATE" = device ] || die "no TV connected (adb get-state: '${STATE:-none}'). Pair and connect first, see README."
SDK=$(adb shell getprop ro.build.version.sdk | tr -dc '0-9')
[ "${SDK:-0}" -ge 30 ] || die "the TV runs Android SDK $SDK; this app needs Android 11 (SDK 30) or newer."

# 2. Build with JDK 17 and an Android SDK, unless a prebuilt APK was given.
if [ -n "$APK" ]; then
  [ -f "$APK" ] || die "APK not found: $APK"
else
APK=app/build/outputs/apk/release/app-release.apk
if [ -z "${JAVA_HOME:-}" ]; then
  for jh in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 "$(/usr/libexec/java_home -v 17 2>/dev/null || true)"; do
    if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then JAVA_HOME=$jh; break; fi
  done
fi
[ -n "${JAVA_HOME:-}" ] || die "JDK 17 not found. Install it (macOS: brew install openjdk@17) or set JAVA_HOME."
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
if [ ! -f local.properties ]; then
  for sdk in "${ANDROID_HOME:-}" ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools; do
    if [ -n "$sdk" ] && [ -d "$sdk" ]; then echo "sdk.dir=$sdk" > local.properties; break; fi
  done
fi
[ -f local.properties ] || die "Android SDK not found. Install it (macOS: brew install --cask android-commandlinetools) or set ANDROID_HOME."
./gradlew assembleRelease -q --console=plain
fi

# 3. Install over any previous build.
adb install -r "$APK" || die "install failed. INSTALL_FAILED_UPDATE_INCOMPATIBLE means an older build signed with a
different key is on the TV: run 'adb uninstall $PKG' (this deletes the PIN and today's usage) and try again."

# 4. Enable the service without dropping other accessibility services (a launcher's key helpers, for example).
#    Android drops a service from its bound list when its APK is replaced, so clear and rewrite the list to rebind.
CURRENT=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$CURRENT" in
  null|"") LIST=$SERVICE ;;
  *"$SERVICE"*) LIST=$CURRENT ;;
  *) LIST="$CURRENT:$SERVICE" ;;
esac
adb shell "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0"
sleep 1
adb shell "settings put secure enabled_accessibility_services '$LIST'; settings put secure accessibility_enabled 1"
sleep 3

# 5. Check that Android bound it.
if adb shell dumpsys activity service "$SERVICE" | grep -q "pin set:"; then
  echo
  adb shell dumpsys activity service "$SERVICE" | sed -n '/pin set:/,$p'
  echo
  echo "Installed and running."
  echo "Next, on the TV: open 'TV Screen Time' (or 'Pembatas TV') from the app list and create a 4-digit PIN."
  echo "Nothing is limited until a PIN exists."
else
  die "installed, but the accessibility service is not running. Check: adb shell dumpsys accessibility | grep -A3 'Bound services'"
fi
