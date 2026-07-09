#!/usr/bin/env bash
# Build the debug APK, ensure the emulator is running, install, and launch HiddenWindy.
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD="hiddenwindy34"
PKG="com.hiddenwindy"
ACTIVITY="$PKG/.MainActivity"

cd "$(dirname "$0")"

# 1. Build first — bail out before touching the emulator if it fails.
echo "==> Building debug APK..."
./gradlew assembleDebug --no-daemon

# 2. Start the emulator only if no device is already connected.
if ! "$ADB" devices | grep -qw "device"; then
  echo "==> Starting emulator $AVD..."
  nohup "$EMULATOR" -avd "$AVD" -gpu auto -no-snapshot -no-boot-anim \
    > /tmp/emulator.log 2>&1 &
fi

# 3. Wait for the device and full boot.
echo "==> Waiting for device to boot..."
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 2
done

# 4. Install and launch.
echo "==> Installing APK..."
./gradlew installDebug --no-daemon
echo "==> Launching app..."
"$ADB" shell am start -n "$ACTIVITY" >/dev/null
echo "==> Done. $PKG is running."
