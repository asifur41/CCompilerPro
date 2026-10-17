#!/usr/bin/env bash
# Run from the repo root after `unzip -o patch2.zip`
set -e
echo "== checking that the previous toolchain patch is present =="
for f in scripts/build_tcc.sh app/src/main/assets/toolchain/include/stdio.h; do
  [ -f "$f" ] || { echo "MISSING $f -> apply toolchain-patch.zip first"; exit 1; }
done
grep -q "TICK_MS" app/src/main/java/com/ccompiler/pro/compiler/ExecutorEngine.kt || { echo "new ExecutorEngine not in place"; exit 1; }
grep -q "targetSdk = 34" app/build.gradle.kts || { echo "build.gradle.kts not updated"; exit 1; }
chmod +x ci.sh scripts/*.sh gradlew 2>/dev/null || true
rm -f patch2.zip
echo
git status --short | head -20
echo
echo "Patch applied. Now run:  ./ci.sh \"interactive input, error links, hints, targetSdk 34\""
