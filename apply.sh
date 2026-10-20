#!/usr/bin/env bash
# Run from the repo root after `unzip -o patch7-ui-update.zip`
set -e
grep -q "TICK_MS" app/src/main/java/com/ccompiler/pro/compiler/ExecutorEngine.kt || { echo "Earlier patches are missing - repo is not in the expected state"; exit 1; }
grep -q "RELEASES" app/src/main/java/com/ccompiler/pro/utils/LinkOpener.kt || { echo "Patch 5 (update link) is missing - apply it first"; exit 1; }
grep -q "versionCode = 6" app/build.gradle.kts || { echo "patch7 files were not unpacked"; exit 1; }
chmod +x ci.sh scripts/*.sh gradlew 2>/dev/null || true
rm -f patch7-ui-update.zip patch6-arrow-verify.zip patch5-fixes-updates.zip patch4-review.zip patch3-trust.zip patch2-interactive.zip toolchain-patch.zip
echo
git status --short | head -40
echo
echo "Check that your repo matches the reviewed files:  bash scripts/verify_repo.sh"
echo 'Then build:  ./ci.sh "UI update, new/rename file, zoom fix, v1.2.0"'
