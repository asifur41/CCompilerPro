#!/usr/bin/env bash
# Builds TinyCC for Android arm64 with the NDK (runs in GitHub Actions) and installs it into the app:
#   app/src/main/jniLibs/arm64-v8a/libtccexe.so        (the compiler / runner executable)
#   app/src/main/assets/toolchain/tccinclude/*.h       (tcc's own stdarg.h, stddef.h, stdbool.h, float.h ...)
set -euo pipefail

NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then echo "ERROR: Android NDK not found"; exit 1; fi
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$BIN/aarch64-linux-android26-clang"
[ -x "$CC" ] || { echo "ERROR: $CC missing"; ls "$BIN" | head -50; exit 1; }
echo "NDK: $NDK"

ROOT="$(pwd)"
WORK="$(mktemp -d)"
git clone --depth 1 --branch release_0_9_27 https://github.com/TinyCC/tinycc.git "$WORK/tinycc" \
  || git clone --depth 1 https://repo.or.cz/tinycc.git "$WORK/tinycc"
cd "$WORK/tinycc"
git log -1 --format='tinycc commit %h %cd' || true

# tcc's generated config header
VER="0.9.27"
[ -f VERSION ] && VER="$(cat VERSION)"
echo "#define TCC_VERSION \"$VER\"" > config.h

# newer sources embed include/tccdefs.h through a generated header
if [ -f include/tccdefs.h ]; then
  gcc -DC2STR conftest.c -o c2str.exe
  ./c2str.exe include/tccdefs.h tccdefs_.h
fi

COMMON=(-O2 -w -DONE_SOURCE=1 '-DCONFIG_TCCDIR="/data/local/tmp/tcc"')

# 1) the Android arm64 executable
"$CC" "${COMMON[@]}" -DTCC_TARGET_ARM64 -Wl,-z,max-page-size=16384 tcc.c -o tcc_android -lm -ldl
"$BIN/llvm-strip" tcc_android || true
file tcc_android 2>/dev/null || true

# 2) host x86-64 build, only used to smoke-test our headers against the sample programs
gcc "${COMMON[@]}" -DTCC_TARGET_X86_64 tcc.c -o tcc_host -lm -ldl

mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a"
cp tcc_android "$ROOT/app/src/main/jniLibs/arm64-v8a/libtccexe.so"

INC="$ROOT/app/src/main/assets/toolchain/tccinclude"
rm -rf "$INC"; mkdir -p "$INC"
cp include/*.h "$INC/"
ls "$INC"

echo "--- smoke test: compile every sample with the host build of the same tcc + our headers ---"
FAIL=0
for f in "$ROOT"/app/src/main/assets/samples/*.c; do
  if ./tcc_host -nostdinc -nostdlib -I"$ROOT/app/src/main/assets/toolchain/include" -I"$INC" -Wall -c -o /dev/null "$f"; then
    echo "ok   $(basename "$f")"
  else
    echo "FAIL $(basename "$f")"; FAIL=1
  fi
done
[ "$FAIL" = 0 ] || echo "WARNING: some samples failed to compile (see above)"
ls -l "$ROOT/app/src/main/jniLibs/arm64-v8a/"
