#!/usr/bin/env bash
# Runs inside an Android emulator (see .github/workflows/launch-smoke-test.yml).
# Installs the APK, taps the launcher, and fails if the app is not alive and in front after 20 seconds.
set -u
PKG=com.bas1minute.sync
APK="${1:?usage: ci-launch-check.sh <apk> <out-dir>}"
OUT="${2:?usage: ci-launch-check.sh <apk> <out-dir>}"
mkdir -p "$OUT"

adb install -r "$APK" 2>&1 | tee "$OUT/install.txt"
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > "$OUT/monkey.txt" 2>&1
sleep 20

adb logcat -d > "$OUT/logcat_full.txt"
adb logcat -d -b crash > "$OUT/logcat_crash.txt"
adb exec-out screencap -p > "$OUT/screen.png"
PID=$(adb shell pidof "$PKG" | tr -d '\r')
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -3 > "$OUT/resumed.txt"

if [ -z "$PID" ]; then
  echo "FAIL: the app process is not running (it crashed or exited)" | tee "$OUT/result.txt"; exit 1
elif ! grep -q "$PKG" "$OUT/resumed.txt"; then
  echo "FAIL: the process is alive but the app is not on screen" | tee "$OUT/result.txt"; exit 1
else
  echo "PASS: the app launched and is on screen (pid $PID)" | tee "$OUT/result.txt"
fi
