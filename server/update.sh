#!/bin/bash
echo "Al-Zahra Server Updater v4.1"
pkill -f "python3 bot.py" 2>/dev/null
sleep 2
pip3 install --upgrade aiohttp psutil 2>/dev/null
cp bot.py bot.py.backup 2>/dev/null
nohup python3 bot.py > /tmp/alzahra.log 2>&1 &
sleep 3
if pgrep -f "python3 bot.py" > /dev/null; then
    echo "Bot is running!"
else
    echo "Bot failed to start!"
fi
