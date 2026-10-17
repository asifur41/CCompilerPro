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

echo "=== VERIFY: real tcc diagnostics, hint keys, run wrapper (non-fatal; read this log) ==="
set +e
T="$WORK/tinycc/tcc_host"
OPTS=(-nostdinc -nostdlib -I"$ROOT/app/src/main/assets/toolchain/include" -I"$INC")
D="$WORK/diag"; mkdir -p "$D"; cd "$D"
printf '#include <stdio.h>\nint main() {\n    int x = 5\n    printf("%%d\\n", x);\n    return 0;\n}\n' > semi.c
printf '#include <stdio.h>\nint main() {\n    printf("%%d\\n", y);\n    return 0;\n}\n' > undecl.c
printf 'int main() {\n    foo();\n    return 0;\n}\n' > implicit.c
printf '#include <stdio.h>\nint main() {\n    printf("hi");\n    return 0;\n' > brace.c
printf '#include <stdio.h>\nint main() {\n    printf("hi";\n    return 0;\n}\n' > paren.c
printf 'int add(int a, int b) { return a + b; }\nint main() {\n    return add(1);\n}\n' > few.c
printf 'int add(int a, int b) { return a + b; }\nint main() {\n    return add(1, 2, 3);\n}\n' > many.c
cat > check.py <<'PY'
import re, sys
rx = re.compile(r"^([^\s:][^:\n]*):(\d+):(?:(\d+):)?\s*(error|warning)\s*:\s*(.*)$", re.M)
hints = ["';' expected", "undeclared", "implicit declaration", "too many arguments", "too few arguments",
         "')' expected", "'}' expected", "']' expected", "'(' expected", "'{' expected", "'[' expected",
         "declaration expected"]
name, out, want = sys.argv[1], open(sys.argv[2]).read(), sys.argv[3]
ds = [(int(m.group(2)), m.group(4), m.group(5)) for m in rx.finditer(out) if m.group(1).split("/")[-1] == name]
print("   parsed:", ds)
if not ds: print("   RESULT: FAIL - no diagnostic line parsed"); sys.exit(0)
msg = ds[0][2].lower()
hit = [h for h in hints if h in msg]
print("   RESULT:", "OK" if want in msg and hit else "CHECK", "| first message:", ds[0][2], "| hint key:", hit[:1])
PY
for pair in "semi.c:';' expected" "undecl.c:undeclared" "implicit.c:implicit declaration" "brace.c:'}' expected" "paren.c:')' expected" "few.c:too few arguments" "many.c:too many arguments"; do
  f="${pair%%:*}"; want="${pair#*:}"
  echo "-- $f (expecting: $want)"
  "$T" "${OPTS[@]}" -Wall -c -o /dev/null "$f" > "$f.out" 2>&1
  sed 's/^/   | /' "$f.out" | head -4
  python3 check.py "$f" "$f.out" "$want"
done

echo "-- run wrapper (same text the app writes) with the host build"
printf '#include <stdio.h>\nint main(void) {\n    int n;\n    printf("Enter a number: ");\n    scanf("%%d", &n);\n    printf("Square: %%d\\n", n * n);\n    return 0;\n}\n' > prog.c
cat > prog_run.c <<'WR'
#define main __ccp_user_main
#include "prog.c"
#undef main
#include <stdio.h>
int main(int argc, char **argv) {
    setvbuf(stdout, 0, _IONBF, 0);
    return ((int (*)(int, char **))__ccp_user_main)(argc, argv);
}
WR
echo 7 | "$T" "${OPTS[@]}" -run prog_run.c
echo "   exit code: $? (expected: 'Enter a number: Square: 49' and exit code 0)"
set -e
cd "$ROOT"
ls -l "$ROOT/app/src/main/jniLibs/arm64-v8a/"
