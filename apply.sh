#!/usr/bin/env bash
# Run from the repo root after `unzip -o patch9-settings-tabs-logo.zip`
set -e
# These two checks look at files this patch does NOT replace, so they tell the truth about your repo.
grep -q "Theme.CCompiler.Splash" app/src/main/res/values/themes.xml || { echo "This patch needs patch 8 (logo and splash) first."; echo "Undo what was just unpacked:  git checkout -- . && git clean -fd app scripts docs"; echo "Then apply patch8-logo-2.5.4.zip first."; exit 1; }
[ -f app/src/main/res/drawable/ic_chevron_right.xml ] || { echo "This patch needs patch 7 (UI update) first."; echo "Undo what was just unpacked:  git checkout -- . && git clean -fd app scripts docs"; echo "Then apply patch7-ui-update.zip first."; exit 1; }
grep -q "versionCode = 20600" app/build.gradle.kts || { echo "patch9 files were not unpacked"; exit 1; }
chmod +x ci.sh scripts/*.sh gradlew 2>/dev/null || true
rm -f patch9-settings-tabs-logo.zip patch8-logo-2.5.4.zip patch7-ui-update.zip
echo
git status --short | head -60
echo
echo "Check that your repo matches the reviewed files:  bash scripts/verify_repo.sh"
echo 'Then build:  ./ci.sh "settings groups, tabs, snippets, Monokai, new logo, v2.6.0"'
