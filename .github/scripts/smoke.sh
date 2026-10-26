#!/usr/bin/env bash
# Installs the APK on the emulator, walks the main screens, records screenshots + app log + crashes.
set -u
OUT=smoke
mkdir -p "$OUT"
APK=$(ls out/*.apk | head -1)
PKG=net.kravos.vesti

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity > "$OUT/start.txt" 2>&1
sleep 30

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1" >> "$OUT/steps.txt"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; }

# tap "<exact text or content-desc>" [index]
tap_text() {
  dump
  python3 - "$1" "${2:-0}" "$OUT/ui.xml" <<'PY' > "$OUT/tap.txt"
import re, sys, xml.etree.ElementTree as ET
want, idx, path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
try: root = ET.parse(path).getroot()
except Exception: sys.exit(0)
hits = []
for n in root.iter('node'):
    if n.get('text') == want or n.get('content-desc') == want:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        hits.append(((x1 + x2) // 2, (y1 + y2) // 2))
if len(hits) > idx: print(*hits[idx])
PY
  if [ -s "$OUT/tap.txt" ]; then adb shell input tap $(cat "$OUT/tap.txt"); echo "tapped $1" >> "$OUT/steps.txt"
  else echo "NOT FOUND: $1" >> "$OUT/steps.txt"; fi
  sleep 3
}

# tap the first node whose text fully matches a regex, below the masthead
tap_regex() {
  dump
  python3 - "$1" "$OUT/ui.xml" <<'PY' > "$OUT/tap.txt"
import re, sys, xml.etree.ElementTree as ET
pat, path = re.compile(sys.argv[1]), sys.argv[2]
try: root = ET.parse(path).getroot()
except Exception: sys.exit(0)
for n in root.iter('node'):
    t = n.get('text') or ''
    if pat.fullmatch(t):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        if y1 > 350:
            print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
  if [ -s "$OUT/tap.txt" ]; then adb shell input tap $(cat "$OUT/tap.txt"); echo "tapped /$1/" >> "$OUT/steps.txt"
  else echo "NOT FOUND /$1/" >> "$OUT/steps.txt"; fi
  sleep 4
}

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
scroll() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; sleep 2; }

shot 01-danas
scroll; shot 02-danas-scroll
scroll; shot 03-danas-scroll2
scroll; shot 04-danas-scroll3

adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.ui.MainActivity >/dev/null 2>&1; sleep 8
shot 05-warm-start
tap_regex '[0-9]+ izvora'
sleep 3
shot 10-story
scroll; shot 11-story-scroll
tap_regex 'pre [0-9]+ (min|h)'
sleep 10
shot 20-reader
scroll; shot 21-reader-scroll
scroll; shot 22-reader-scroll2
tap_text "Sačuvaj"
sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2

tap_text "Srbija"; sleep 3; shot 30-srbija
tap_text "Najnovije"; sleep 3; shot 31-srbija-najnovije
tap_text "Svet"; sleep 3; shot 40-svet
tap_regex '[0-9]+ izvora'; sleep 3; shot 41-svet-story
tap_regex 'pre [0-9]+ (min|h)'; sleep 10; shot 42-svet-reader
scroll; shot 43-svet-reader-scroll
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "Bezbednost"; sleep 3; shot 50-bezbednost
tap_regex '.{40,}'; sleep 10; shot 51-bezb-reader
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "Sačuvano"; sleep 3; shot 60-sacuvano
tap_text "Danas"; sleep 2
tap_text "Podešavanja"; sleep 3; shot 70-settings
scroll; shot 71-settings-scroll
scroll; shot 72-settings-scroll2
tap_text "Tamna"; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 3
shot 80-dark-danas
tap_regex '[0-9]+ izvora'; sleep 3
tap_regex 'pre [0-9]+ (min|h)'; sleep 10; shot 81-dark-reader

PID=$(adb shell pidof $PKG | tr -d '\r')
echo "pid=$PID" > "$OUT/status.txt"
adb logcat -d > "$OUT/logcat.txt"
grep -n -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt" || true
grep -n -E "KravosVesti|AndroidRuntime|net.kravos" "$OUT/logcat.txt" | tail -500 > "$OUT/app-log.txt" || true
if [ -s "$OUT/crash.txt" ]; then echo "CRASH" >> "$OUT/status.txt"; fi
rm -f "$OUT/logcat.txt" "$OUT/ui.xml"
cat "$OUT/status.txt" "$OUT/steps.txt"
exit 0
