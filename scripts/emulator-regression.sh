#!/usr/bin/env bash
# Kairos regression suite on an emulator (google_apis image: needs `adb root` and sqlite3).
#
#   scripts/emulator-regression.sh <serial> <new.apk> [old.apk]
#
# Installs [old.apk] (or <new.apk>), onboards, then runs the cases below against <new.apk> and prints
# PASS/FAIL per case. With old.apk the update path (DB migration, locked copy, recents) is covered too.
# It wipes the app's data on that emulator; use a throwaway AVD.
set -u
SERIAL=$1; NEW=$2; OLD=${3:-}
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
PKG=com.kairos.app
TMP=$(mktemp -d)
FAILS=0

a() { "$ADB" -s "$SERIAL" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }
q() { sh_ "sqlite3 /data/data/$PKG/databases/kairos.db \"$1\""; }
api() { sh_ getprop ro.build.version.sdk; }
now_ms() { echo "$(sh_ date +%s)000"; }
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1: $2"; FAILS=$((FAILS + 1)); }
check() { if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "expected '$3', got '$2'"; fi; }
# eventually <name> <expected> <command...>: polls for up to 90 s. Boot and update broadcasts can take
# half a minute to reach the app on a busy emulator.
eventually() {
    local name=$1 want=$2; shift 2
    local got i
    for i in $(seq 1 30); do got=$("$@" 2>/dev/null); [ "$got" = "$want" ] && break; sleep 3; done
    check "$name" "$got" "$want"
}

cat > "$TMP/ui.py" <<'EOF'
import sys, re
x = sys.stdin.read()
want = sys.argv[1] if len(sys.argv) > 1 else None
def g(n, k):
    m = re.search(k + r'="([^"]*)"', n)
    return m.group(1) if m else ''
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0); t = g(n, 'text'); d = g(n, 'content-desc')
    if want is None:
        if t or d: print(t or d)
    elif want in (t, d):
        p = list(map(int, re.findall(r'\d+', g(n, 'bounds'))))
        print((p[0] + p[2]) // 2, (p[1] + p[3]) // 2); break
EOF
UIPY=$TMP/ui.py
command -v cygpath >/dev/null && UIPY=$(cygpath -w "$TMP/ui.py")
dump() { a shell uiautomator dump /data/local/tmp/u.xml >/dev/null 2>&1; a shell cat /data/local/tmp/u.xml | python "$UIPY"; }
coords() { # centre of the element with that text or description, from a fresh dump
    a shell uiautomator dump /data/local/tmp/u.xml >/dev/null 2>&1
    a shell cat /data/local/tmp/u.xml | python "$UIPY" "$1"
}
tap() { local xy; xy=$(coords "$1"); [ -n "$xy" ] && a shell input tap $xy; }
resumed() { sh_ dumpsys activity activities | grep -m1 -E "ResumedActivity" | grep -o "$PKG/[.A-Za-z]*"; }
# Active ones only: older Android also lists recently dismissed ones as StatusBarNotification lines.
notifs() { sh_ dumpsys notification | grep -c "NotificationRecord(.*pkg=$PKG user"; }
tasks() { sh_ dumpsys activity recents | grep -c "Recent #.*I=$PKG/.MainActivity"; }
wait_boot() {
    a wait-for-device
    until [ "$(sh_ getprop sys.boot_completed)" = 1 ]; do sleep 3; done
    sleep 8; a root >/dev/null 2>&1; sleep 3; a wait-for-device
}
start_app() { a shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep 2; }
# Rows written behind Room's back: stop the app, write, start it again (opening it re-arms nothing, so
# reschedule through the receiver the system uses after an exact-alarm grant).
seed() { # seed <title> <seconds from now> [repeatType]
    a shell am force-stop $PKG
    local secs; secs=$(( $(sh_ date +%s) + $2 ))
    if [ "$(q 'PRAGMA user_version;')" -ge 3 ]; then # the app always stores the wall time from v3 on
        local wall; wall=$(local_time "$secs")
        q "INSERT INTO reminders(title,notes,dueAt,dueLocal,repeatType,weekdays,anchorDay,sound,done,snoozeCount) VALUES('$1','',${secs}000,'$wall','${3:-NONE}',0,1,'bell',0,0);" >/dev/null
    else
        q "INSERT INTO reminders(title,notes,dueAt,repeatType,weekdays,anchorDay,sound,done,snoozeCount) VALUES('$1','',${secs}000,'${3:-NONE}',0,1,'bell',0,0);" >/dev/null
    fi
    start_app; resched; a shell input keyevent 3
}
# Wall time of epoch second $1 on the device, as the app stores it. Computed here from the device's UTC
# offset: toybox `date -d @` prints UTC on older Android.
local_time() {
    python -c "import sys,datetime as d;o=sys.argv[2];m=(int(o[1:3])*60+int(o[3:5]))*(1 if o[0]=='+' else -1);print(d.datetime.fromtimestamp(int(sys.argv[1]),d.timezone(d.timedelta(minutes=m))).strftime('%Y-%m-%dT%H:%M:%S'))" "$1" "$(sh_ date +%z)"
}
# Scrolls a screen's worth, starting above the navigation bar whatever the display height.
swipe_up() { local h; h=$(sh_ wm size | grep -o '[0-9]*$'); a shell input swipe 540 $((h * 7 / 10)) 540 $((h * 3 / 10)) 300; }
resched() { a shell am broadcast -a android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -n $PKG/.system.BootReceiver >/dev/null; sleep 2; }
# Deletes all reminders behind Room's back and restarts the app (cancel_all does not exist before API 30).
reset_data() {
    a shell am force-stop $PKG; q "DELETE FROM reminders;" >/dev/null
    a shell cmd notification cancel_all $PKG >/dev/null 2>&1 || true
    start_app; resched; a shell input keyevent 3
}
grant() {
    [ "$(api)" -ge 33 ] && a shell pm grant $PKG android.permission.POST_NOTIFICATIONS
    a shell appops set $PKG SYSTEM_ALERT_WINDOW allow
    [ "$(api)" -ge 31 ] && a shell appops set $PKG SCHEDULE_EXACT_ALARM allow
    a shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
}
set_tz() { # works on API 26+ through the alarm service
    if [ "$(api)" -ge 30 ]; then a shell cmd alarm set-timezone "$1" >/dev/null; else a shell service call alarm 3 s16 "$1" >/dev/null; fi
    sleep 4
}
crashes() { sh_ logcat -d | grep -cE "FATAL EXCEPTION|AndroidRuntime: Process: $PKG"; }
CRASHES=0
reboot_() { CRASHES=$((CRASHES + $(crashes))); a reboot; sleep 10; } # logcat does not survive a reboot

# ---------------------------------------------------------------- setup
wait_boot
echo "== API $(api), serial $SERIAL"
a logcat -c
a uninstall $PKG >/dev/null 2>&1
a install "${OLD:-$NEW}" | tail -1
grant
start_app
for i in 1 2 3 4 5 6 7; do tap Continue || break; sleep 1.5; done
tap "Your name"; a shell input text Test; a shell input keyevent 111; sleep 1
tap Continue; sleep 2; tap "Let's go"; sleep 2
dump | grep -q "Welcome back" && pass "onboarding" || fail "onboarding" "no task list"

if [ -n "$OLD" ]; then
    seed "Before update" 3600 DAILY
    a shell input keyevent 3
    from=$(q 'PRAGMA user_version;')
    a install -r "$NEW" | tail -1; sleep 6
    eventually "update: DB at v3" "3" q 'PRAGMA user_version;'
    if [ "$from" = 2 ]; then # updating from 1.4.5 or older: the v2 -> v3 migration
        check "update: wall time filled by migration" "$(q "SELECT dueLocal FROM reminders WHERE title='Before update';" | grep -c T)" "1"
    fi
    copy_exists() { a shell ls /data/user_de/0/$PKG/files/locked_state.json 2>/dev/null | grep -c locked_state; }
    eventually "update: locked copy written" "1" copy_exists
    # The installer's "Open": NEW_TASK|SINGLE_TOP|CLEAR_TOP with a package.
    a shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $PKG -f 0x34000000 -n $PKG/.MainActivity >/dev/null; sleep 3
    check "update: one task in recents" "$(tasks)" "1"
fi

# ---------------------------------------------------------------- alarm chain + snooze
reset_data
seed "Chain" 30
sleep 3
check "heads-up posted (T-10 already passed)" "$(notifs)" "1"
sleep 32
check "popup at T" "$(resumed)" "$PKG/.AlarmActivity"
tap "Snooze 5 min"; sleep 3
check "snooze stored" "$(q "SELECT snoozeCount, snoozeUntil IS NOT NULL FROM reminders WHERE title='Chain';")" "1|1"
check "snooze clears notifications" "$(notifs)" "0"

# ---------------------------------------------------------------- done on a repeating reminder (list tick)
reset_data
seed "Daily" 3600 DAILY
due=$(q "SELECT dueAt FROM reminders WHERE title='Daily';")
start_app; tap Tasks; sleep 1
set -- $(coords Daily); a shell input tap 110 "${2:-0}"; sleep 3 # the tick sits left of the title
check "daily done -> +1 day" "$(q "SELECT dueAt - $due FROM reminders WHERE title='Daily';")" "86400000"
a shell input keyevent 3

# ---------------------------------------------------------------- reboot restores alarms
reset_data
seed "After reboot" 3600
reboot_; wait_boot
armed() { [ "$(sh_ dumpsys alarm | grep -c "walarm\*:$PKG.FIRE")" -ge 1 ] && echo yes || echo no; }
eventually "reboot re-arms alarms" "yes" armed

# ---------------------------------------------------------------- time zone
reset_data
set_tz Europe/Istanbul
seed "Zone" 7200
before=$(q "SELECT dueAt FROM reminders WHERE title='Zone';")
set_tz Europe/Berlin
check "waiting reminder keeps its clock time (+2 h)" "$(q "SELECT dueAt - $before FROM reminders WHERE title='Zone';")" "7200000"
set_tz Europe/Istanbul

# ---------------------------------------------------------------- test alarm
reset_data
start_app; tap Settings; sleep 1
for i in 1 2 3; do swipe_up; done
tap "Test the alarm (15 s, lock your phone)"; sleep 1
tap "Test the alarm (15 s, lock your phone)"; sleep 1
check "test alarm: one at a time" "$(q 'SELECT count(*) FROM reminders;')" "1"
a shell input keyevent 3; sleep 20
tap "Snooze 5 min"; sleep 3
check "test alarm: gone after answer" "$(q 'SELECT count(*) FROM reminders;')" "0"

# ---------------------------------------------------------------- direct boot
# Only with file-based encryption. With full-disk encryption (e.g. the API 26 image) the PIN is asked
# before Android starts at all, so there is no "booted but locked" phase to test.
if [ "$(sh_ getprop ro.crypto.type)" != file ]; then
    echo "SKIP  direct boot: ro.crypto.type=$(sh_ getprop ro.crypto.type)"
else
reset_data
seed "Locked" 75
a shell locksettings set-pin 1234 >/dev/null
reboot_; a wait-for-device
until [ "$(sh_ getprop sys.boot_completed)" = 1 ]; do sleep 3; done
sleep 10; a root >/dev/null 2>&1; sleep 3; a wait-for-device
locked=$(sh_ dumpsys user | grep -o "RUNNING_LOCKED" | head -1)
check "device still locked" "$locked" "RUNNING_LOCKED"
pending=$(sh_ dumpsys alarm | grep -c "walarm\*:$PKG.FIRE")
[ "$pending" -ge 1 ] && pass "before unlock: alarms armed from the copy" || fail "before unlock: alarms armed" "none"
due=$(sh_ cat /data/user_de/0/$PKG/files/locked_state.json | python -c "import json,sys;print([r['dueAt'] for r in json.load(sys.stdin)['reminders'] if r['title']=='Locked'][0])")
until [ "$(now_ms)" -gt $((due + 8000)) ]; do sleep 3; done
check "before unlock: popup over the lock screen" "$(resumed)" "$PKG/.AlarmActivity"
tap "Snooze 5 min"; sleep 3
a shell input keyevent 3; sleep 1; a shell input keyevent 82; sleep 2
a shell input text 1234; a shell input keyevent 66
eventually "after unlock: snooze carried over" "1" q "SELECT snoozeCount FROM reminders WHERE title='Locked';"
a shell locksettings clear --old 1234 >/dev/null
fi

# ---------------------------------------------------------------- crashes
check "no crashes in logcat" "$((CRASHES + $(crashes)))" "0"
echo "== $FAILS failed"
rm -rf "$TMP"
exit $FAILS
