#!/usr/bin/env bash
git add -A && git commit -m "${1:-fix}" && git push origin main
sleep 8
ID=$(gh run list --limit 1 --json databaseId -q '.[0].databaseId')
echo "Run $ID started..."
if gh run watch "$ID" --exit-status >/dev/null 2>&1; then
  echo "BUILD SUCCEEDED ✅"
else
  echo "BUILD FAILED ❌"
  gh run view "$ID" --log-failed | grep -E "e: |error:|ERROR|FAIL|NOT ALLOWED|What went wrong|##\[error\]" -B1 -A3 | head -n 70
fi
echo
echo "---- warnings / notices in this run (if any) ----"
gh run view "$ID" --log 2>/dev/null | grep -E "##\[warning\]|WARNING|FAIL |CHECK|NOT ALLOWED|INCONCLUSIVE|RESULT:" | sed 's/^[^	]*	[^	]*	//' | head -n 40
echo "---- annotations ----"
gh run view "$ID" 2>/dev/null | sed -n '/ANNOTATIONS/,/ARTIFACTS/p' | head -n 30
echo
echo "Download APK + report:  gh run download $ID -n ccompiler-pro-release"
