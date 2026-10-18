#!/usr/bin/env bash
# Run from the repo root after `unzip -o patch5-fixes-updates.zip`
set -e
grep -q "TICK_MS" app/src/main/java/com/ccompiler/pro/compiler/ExecutorEngine.kt || { echo "Earlier patches are missing - repo is not in the expected state"; exit 1; }
grep -q "signingConfigs" app/build.gradle.kts || { echo "Patch 3 (signing) is missing"; exit 1; }
grep -q "versionCode = 4" app/build.gradle.kts || { echo "patch5 files were not unpacked"; exit 1; }
chmod +x ci.sh scripts/*.sh gradlew 2>/dev/null || true
rm -f patch5-fixes-updates.zip patch4-review.zip patch3-trust.zip patch2-interactive.zip toolchain-patch.zip
echo
git status --short | head -30
echo
echo 'Patch applied. Now run:  ./ci.sh "fixes, update link, v1.1.2"'
