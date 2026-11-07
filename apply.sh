#!/usr/bin/env bash
# Patch 10: Program arguments (default off) + 30 sample programs + launcher label.
# Run this AFTER unzip -o patch10-arguments-samples.zip, from the repo root:
#   bash apply.sh
set -e

fail() {
  echo
  echo "STOP: $1"
  echo "Nothing from this patch was removed. If something looks broken, undo with:"
  echo "  git checkout -- . && git clean -fd app scripts docs README.md PRIVACY.md"
  exit 1
}

# --- Prerequisite checks, on files this patch does NOT overwrite ---
[ -f app/src/main/java/com/ccompiler/pro/utils/DataTools.kt ] || fail "DataTools.kt is missing - this doesn't look like the patch 9 (2.6.0) codebase."
grep -q "Monokai" app/src/main/java/com/ccompiler/pro/settings/ThemeManager.kt 2>/dev/null || fail "ThemeManager.kt has no Monokai theme - this doesn't look like the patch 9 (2.6.0) codebase."
grep -q 'android:label="@string/app_name_short"' app/src/main/AndroidManifest.xml 2>/dev/null || fail "AndroidManifest.xml is missing the app_name_short launcher label."

echo "Prerequisite checks passed."

# --- Cleanup ---
chmod +x scripts/*.sh gradlew ci.sh 2>/dev/null || true
rm -f patch10-arguments-samples.zip

echo
echo "Patch applied."
echo
git status --short 2>/dev/null || true
echo
echo "Next:"
echo "  bash scripts/verify_repo.sh      (expect RESULT: OK)"
echo "  ./ci.sh \"patch 10: program arguments, 30 samples, launcher label\""
