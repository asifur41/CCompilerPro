#!/usr/bin/env bash
git add -A && git commit -m "${1:-fix}" && git push origin main
sleep 8
ID=$(gh run list --limit 1 --json databaseId -q '.[0].databaseId')
if gh run watch "$ID" --exit-status >/dev/null 2>&1; then
  echo "BUILD SUCCEEDED ✅"
else
  echo "BUILD FAILED ❌"
  gh run view "$ID" --log-failed | grep -E "e: |error:|What went wrong" -A2 | head -n 60
fi
