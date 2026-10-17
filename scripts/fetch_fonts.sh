#!/usr/bin/env bash
# Downloads the four font files into res/font so they are BUNDLED in the APK (fully offline at runtime).
# If a download fails the app still builds and falls back to the system monospace font.
set -u
D="app/src/main/res/font"
mkdir -p "$D"
get() {
  out="$1"; url="$2"
  if [ -s "$D/$out" ]; then echo "have $out"; return; fi
  if curl -fsSL --retry 3 -o "$D/$out" "$url"; then echo "ok   $out"; else echo "WARNING: could not fetch $out"; rm -f "$D/$out"; fi
}
get roboto_mono.ttf     "https://github.com/google/fonts/raw/main/apache/robotomono/RobotoMono%5Bwght%5D.ttf"
get fira_code.ttf       "https://github.com/google/fonts/raw/main/ofl/firacode/FiraCode%5Bwght%5D.ttf"
get jetbrains_mono.ttf  "https://github.com/google/fonts/raw/main/ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf"
get courier_prime.ttf   "https://github.com/google/fonts/raw/main/ofl/courierprime/CourierPrime-Regular.ttf"
exit 0
