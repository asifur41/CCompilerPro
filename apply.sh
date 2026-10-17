#!/usr/bin/env bash
# Run from the repo root after `unzip -o patch3-trust.zip`
set -e
for f in scripts/build_tcc.sh scripts/trust_report.sh app/src/main/java/com/ccompiler/pro/compiler/ExecutorEngine.kt; do
  [ -f "$f" ] || { echo "MISSING $f -> apply the earlier patches first"; exit 1; }
done
grep -q "TICK_MS" app/src/main/java/com/ccompiler/pro/compiler/ExecutorEngine.kt || { echo "patch2 (interactive input) is not applied"; exit 1; }
grep -q 'signingConfigs' app/build.gradle.kts || { echo "build.gradle.kts not updated"; exit 1; }
touch .gitignore
for pat in '*.jks' '*.keystore' 'trust-out/' 'build-info/'; do grep -qxF "$pat" .gitignore || echo "$pat" >> .gitignore; done
chmod +x ci.sh scripts/*.sh gradlew 2>/dev/null || true
rm -f patch3-trust.zip
echo
git status --short | head -20
cat <<'MSG'

Patch applied. NEXT (one time only): create the signing key and store it as GitHub secrets.
Follow "Step 2" of the instructions, then run:

    ./ci.sh "release signing, trust report, guards"
MSG
