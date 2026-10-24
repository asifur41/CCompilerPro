#!/usr/bin/env bash
# Checks that the files in this repository are exactly the files that were reviewed for version 2.5.4.
# Run from anywhere:  bash scripts/verify_repo.sh
cd "$(dirname "$0")/.." || exit 1
LIST=scripts/expected-hashes.txt
[ -f "$LIST" ] || { echo "scripts/expected-hashes.txt is missing"; exit 2; }

TOTAL=$(wc -l < "$LIST")
BAD=$(sha256sum -c "$LIST" 2>&1 | grep -v ': OK$' | grep -v '^sha256sum: WARNING' || true)

# source files that exist in the repo but are not in the reviewed list
EXTRA=$(find app/src/main/java app/src/main/res app/src/main/assets/samples app/src/main/assets/toolchain/include \
          -type f ! -path '*/res/font/*' ! -name '.gitkeep' 2>/dev/null | sort | while read -r f; do
          grep -q "  $f\$" "$LIST" || echo "$f"; done)

echo "Checked $TOTAL reviewed files."
if [ -z "$BAD" ] && [ -z "$EXTRA" ]; then
  echo "RESULT: OK - every reviewed file is present and unchanged."
  exit 0
fi
[ -n "$BAD" ]   && { echo; echo "Changed or missing files:"; echo "$BAD" | sed 's/^/   /'; }
[ -n "$EXTRA" ] && { echo; echo "Extra files that were not reviewed:"; echo "$EXTRA" | sed 's/^/   /'; }
echo
echo "RESULT: DIFFERENT - send this list to your assistant."
exit 1
