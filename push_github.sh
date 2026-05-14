#!/bin/bash
cd ~/Al-Zahra-Full
TS=$(date '+%Y-%m-%d %H:%M:%S')
git branch -M main 2>/dev/null
git add .
git commit -m "Al-Zahra v4.1 - $TS" 2>/dev/null
git push -f origin main
echo "Pushed at $TS"
