#!/usr/bin/env bash
# Run from the repo root after `unzip -o patch8-logo-2.5.4.zip`
set -e
grep -q "promptFileName" app/src/main/java/com/ccompiler/pro/files/FileBrowser.kt || { echo "This patch needs patch 7 (UI update) first. Upload patch7-ui-update.zip, run its apply.sh, then try again."; exit 1; }
grep -q "versionCode = 20504" app/build.gradle.kts || { echo "patch8 files were not unpacked"; exit 1; }
rm -f app/src/main/res/drawable/ic_launcher_foreground.xml      # the old purple icon drawing
chmod +x ci.sh scripts/*.sh gradlew 2>/dev/null || true
rm -f patch8-logo-2.5.4.zip patch7-ui-update.zip patch6-arrow-verify.zip patch5-fixes-updates.zip
echo
git status --short | head -40
echo
echo "Check that your repo matches the reviewed files:  bash scripts/verify_repo.sh"
echo 'Then build:  ./ci.sh "new logo, splash screen, v2.5.4"'
