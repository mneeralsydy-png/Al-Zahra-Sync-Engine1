#!/bin/bash
# ═══════════════════════════════════════════
#  Al-Zahra Server Update Script v4.1
# ═══════════════════════════════════════════

echo "═══════════════════════════════════════"
echo "  Al-Zahra Server Updater"
echo "═══════════════════════════════════════"

# التحقق من الجذر
if [ "$(id -u)" != "0" ]; then
    echo "⚠️  يحتاج صلاحيات root!"
    echo "   sudo bash /opt/alzahra/update.sh"
    exit 1
fi

cd /opt/alzahra

# إيقاف البوت
echo "⏹️  إيقاف البوت..."
pkill -f "python3 bot.py" 2>/dev/null
sleep 2

# تحديث المتطلبات
echo "📦 تحديث المتطلبات..."
pip3 install --upgrade aiohttp psutil 2>/dev/null

# نسخ احتياطي
echo "💾 إنشاء نسخة احتياطية..."
cp server/bot.py server/bot.py.backup.$(date +%Y%m%d_%H%M%S)

# تشغيل البوت
echo "🚀 تشغيل البوت..."
nohup python3 server/bot.py > /var/log/alzahra.log 2>&1 &
sleep 3

# التحقق
if pgrep -f "python3 bot.py" > /dev/null; then
    echo "✅ البوت يعمل على المنفذ 8443!"
    echo "📊 حالة البوت: RUNNING"
else
    echo "❌ فشل تشغيل البوت!"
    echo "📋 تحقق من الأخطاء:"
    tail -30 /var/log/alzahra.log
fi

echo ""
echo "═══════════════════════════════════════"
echo "  أوامر مفيدة:"
echo "═══════════════════════════════════════"
echo "  حالة:  pgrep -f bot.py"
echo "  إيقاف: pkill -f bot.py"
echo "  سجل:   tail -f /var/log/alzahra.log"
echo "═══════════════════════════════════════"
