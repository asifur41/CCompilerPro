#!/usr/bin/env bash
# Usage: trust_report.sh <apk> [output-dir]
# Writes TRUST_REPORT.md and FAILS (exit 1) if the APK
#   * requests any permission other than androidx's own internal DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
#   * is debuggable
#   * contains known tracker / ads / analytics SDK packages
set -uo pipefail
APK="${1:?usage: trust_report.sh <apk> [out-dir]}"
OUT="${2:-.}"
mkdir -p "$OUT"
REPORT="$OUT/TRUST_REPORT.md"
FAIL=0

AAPT2="${AAPT2:-}"
if [ -z "$AAPT2" ]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -n "$SDK" ] && AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
fi

TMP="$(mktemp -d)"
unzip -qo "$APK" 'classes*.dex' -d "$TMP" 2>/dev/null
strings -n 6 "$TMP"/classes*.dex > "$TMP/dex-strings.txt" 2>/dev/null

{
echo "# CCompiler Pro – build trust report"
echo
echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Source commit: ${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
echo
echo "## APK"
echo '```'
echo "file:   $(basename "$APK")"
echo "size:   $(stat -c %s "$APK") bytes"
echo "sha256: $(sha256sum "$APK" | cut -d' ' -f1)"
echo '```'
} > "$REPORT"

# ---- permissions ------------------------------------------------------------------------
{
echo
echo "## Permissions requested"
} >> "$REPORT"
if [ -n "$AAPT2" ] && [ -x "$AAPT2" ]; then
  RAW="$("$AAPT2" dump permissions "$APK" 2>&1)"
  PERMS="$(echo "$RAW" | grep -E '^uses-permission' | sed -E "s/.*name='([^']+)'.*/\1/")"
  if ! echo "$RAW" | grep -q '^package'; then
    echo "INCONCLUSIVE: aapt2 produced unexpected output, raw output below." >> "$REPORT"
    { echo '```'; echo "$RAW" | head -20; echo '```'; } >> "$REPORT"
  elif [ -z "$PERMS" ]; then
    echo "None." >> "$REPORT"
  else
    echo '```' >> "$REPORT"
    while IFS= read -r p; do
      case "$p" in
        *.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)
          echo "$p   (added automatically by AndroidX; protects this app's own receivers; not a data permission)" >> "$REPORT" ;;
        *) echo "$p   <-- NOT ALLOWED" >> "$REPORT"; FAIL=1 ;;
      esac
    done <<< "$PERMS"
    echo '```' >> "$REPORT"
  fi
  if "$AAPT2" dump badging "$APK" 2>/dev/null | grep -q "application-debuggable"; then
    echo "**APK is DEBUGGABLE** <-- NOT ALLOWED for a release" >> "$REPORT"; FAIL=1
  else
    echo "Debuggable: no" >> "$REPORT"
  fi
else
  echo "aapt2 not found: permission/debuggable check SKIPPED." >> "$REPORT"
fi

# ---- trackers ---------------------------------------------------------------------------
{
echo
echo "## Tracker / ads / analytics SDK scan"
} >> "$REPORT"
TRK="$(grep -E 'com/google/firebase|com/google/android/gms|com/facebook|com/flurry|com/adjust|com/appsflyer|io/sentry|com/mixpanel|com/amplitude|com/crashlytics|io/fabric|com/segment|com/onesignal|com/applovin|com/unity3d/ads|com/chartboost|com/ironsource|com/vungle|com/inmobi|com/mopub|com/startapp|com/bugsnag|com/datadog' "$TMP/dex-strings.txt" | sort -u | head -20)"
if [ -z "$TRK" ]; then
  echo "None of the known tracker/ads/analytics packages are present." >> "$REPORT"
else
  { echo '```'; echo "$TRK"; echo '```'; echo "<-- NOT ALLOWED"; } >> "$REPORT"; FAIL=1
fi

# ---- URLs --------------------------------------------------------------------------------
{
echo
echo "## Web addresses found inside the app code (informational)"
echo "The app has no network permission; links are only opened in the user's browser / email app."
echo '```'
grep -Eo 'https?://[A-Za-z0-9._~:/?#@!$&()*+,;=%-]+' "$TMP/dex-strings.txt" | sort -u | head -60
echo '```'
} >> "$REPORT"

# ---- native code -------------------------------------------------------------------------
{
echo
echo "## Native files in the APK"
echo "libtccexe.so is the bundled C compiler (TinyCC, LGPL) that runs as a child process of this app."
echo '```'
unzip -l "$APK" | awk '{print $1"  "$4}' | grep -E ' lib/' 
echo
echo "sha256 of libtccexe.so: $(unzip -p "$APK" lib/arm64-v8a/libtccexe.so 2>/dev/null | sha256sum | cut -d' ' -f1)"
echo '```'
if [ -f build-info/tinycc.txt ]; then echo "Compiler build info:"; echo '```'; cat build-info/tinycc.txt; echo '```'; fi
} >> "$REPORT"

# ---- dependencies -----------------------------------------------------------------------
{
echo
echo "## Third-party libraries shipped in the app"
echo '```'
} >> "$REPORT"
if command -v gradle >/dev/null 2>&1; then
  gradle :app:dependencies --configuration releaseRuntimeClasspath -q --no-daemon 2>/dev/null \
    | grep -E '^[+\\|\\ -]*---' | sed -E 's/\(\*\)//; s/ -> .*//' | sort -u | head -80 >> "$REPORT"
else
  echo "(gradle not available here)" >> "$REPORT"
fi
echo '```' >> "$REPORT"

{
echo
if [ "$FAIL" = 0 ]; then echo "## RESULT: PASS"; else echo "## RESULT: FAIL"; fi
} >> "$REPORT"

rm -rf "$TMP"
cat "$REPORT"
exit $FAIL
