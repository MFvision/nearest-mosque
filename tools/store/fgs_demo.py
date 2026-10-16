#!/usr/bin/env python3
"""Records the Google Play foreground-service demo of the Android app on an emulator (.github/workflows/fgs-demo-video.yml).

    fgs_demo.py prepare           # emulator settings: animations off, Asia/Riyadh, manual time, clock at START
    fgs_demo.py onboard           # off camera: the first-launch tour, Riyadh from the city search, back to Home
    fgs_demo.py record            # on camera: open the app, turn on the Adhan, leave, the Adhan at prayer time, Stop
    fgs_demo.py cut               # the edited video with captions: <out>/near-mosque-fgs-demo.mp4

The app is driven like a person would, with `adb shell input` and `uiautomator dump` (elements found by their
text, content description or class, never by fixed coordinates), at a calm pace. The Adhan comes through the
app's real path: after the Adhan is turned on the app's own AlarmManager alarm is read from `dumpsys alarm`, the
clock is moved to LEAD seconds before it, and the alarm fires ReminderReceiver -> PrayerAudioService (a
mediaPlayback foreground service with Stop on its notification). Nothing is broadcast by hand.

Every step that does not find its element stops the job with a screenshot and the screen's UI tree in
<out>/debug/failure-*.png/.xml. While recording, the moments of every action are written to <out>/raw/timeline.json;
`cut` keeps each action and a short moment after it (cutting the idle time spent reading the screen), adds a
caption per part and joins the audio captured from the emulator when there is any.

Environment: FGS_OUT (output folder, default fgs-demo), FGS_PRAYER (default Asr), FGS_START (local time the clock
starts at, default 13:00, so that the prayer is the next one), FGS_AUDIO_SOURCE (a PulseAudio source to capture,
e.g. fgs.monitor; empty for none), FGS_FONT (a TTF for the captions).
"""
import json
import os
import pathlib
import re
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from zoneinfo import ZoneInfo

PKG = "com.AVA.nearmosque"
ACTIVITY = PKG + "/sa.zood.nearmosque.MainActivity"
ZONE = "Asia/Riyadh"
OUT = pathlib.Path(os.environ.get("FGS_OUT", "fgs-demo")).resolve()
PRAYER = os.environ.get("FGS_PRAYER", "Asr")
START = os.environ.get("FGS_START", "13:00")
AUDIO_SOURCE = os.environ.get("FGS_AUDIO_SOURCE", "").strip()
FONT = os.environ.get("FGS_FONT", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
PAUSE = 1.5          # seconds between a person's steps
LEAD = 12            # the clock is set this many seconds before the Adhan alarm
SEGMENT_MAX = 160    # screenrecord stops at 180 s; a new segment starts before that
TARGET_SECONDS = 60  # the edited video aims at most this long

# The app's English strings (android/app/src/main/res/values/strings.xml).
S_SKIP = "Skip"
S_SEARCH_CITY = "Search for a city"
S_LOCATION_SET = "Prayer times for"
S_START = "Get started"
S_SETTINGS = "Settings"
S_AUDIO = "Prayer & Audio"
S_ADHAN_SECTION = "Adhan"
S_EACH_PRAYER = "Each prayer"
S_PLAY_ADHAN = "Play the Adhan"
S_SETUP = "Let’s set up"
S_EXACT = "Alarms & reminders"
S_STOP = "Stop"
APP_LABEL = "Near Mosque"

CAPTIONS = [
    ["1. Turn on the Adhan"],
    ["2. Leave the app"],
    ["3. At prayer time the Adhan", "plays in the background"],
    ["4. Stop from the notification"],
]

DEBUG = OUT / "debug"
RAW = OUT / "raw"
_last_xml = ""
_failures = 0


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


def summary(line):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")


# ---------------------------------------------------------------- adb

def adb(*args, check=True, timeout=90):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed ({r.returncode}): {r.stdout}{r.stderr}")
    return r.stdout


def sh(cmd, check=True, timeout=90):
    return adb("shell", cmd, check=check, timeout=timeout)


def screencap(path):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, timeout=30, check=False)
    return path


def fail(msg):
    """Stops the job loudly: a screenshot, the UI tree and the state that explains it."""
    global _failures
    _failures += 1
    DEBUG.mkdir(parents=True, exist_ok=True)
    name = f"failure-{_failures}"
    try:
        screencap(DEBUG / f"{name}.png")
        (DEBUG / f"{name}.xml").write_text(_last_xml, encoding="utf-8")
        for cmd, file in [("dumpsys notification --noredact", "notification"), ("dumpsys alarm", "alarm"),
                          ("dumpsys activity services " + PKG, "services"), ("logcat -d -t 3000", "logcat")]:
            (DEBUG / f"{name}-{file}.txt").write_text(sh(cmd, check=False, timeout=60), encoding="utf-8")
    except Exception as e:  # noqa: BLE001 - the failure itself matters more than its evidence
        log(f"could not save the failure evidence: {e}")
    if RECORDER.running:
        try:
            RECORDER.stop()
            write_timeline()
        except Exception as e:  # noqa: BLE001
            log(f"could not stop the recording: {e}")
    print(f"::error::{msg} (screenshot: debug/{name}.png in the fgs-demo-debug artifact)", flush=True)
    summary(f"**Failed:** {msg}")
    sys.exit(1)


# ---------------------------------------------------------------- screen

class Node:
    def __init__(self, e, path=()):
        self.path = path  # child indexes from the root: a node is inside another when its path starts with the other's
        self.scrollable = e.get("scrollable") == "true"
        self.text = e.get("text", "") or ""
        self.desc = e.get("content-desc", "") or ""
        self.rid = e.get("resource-id", "") or ""
        self.cls = e.get("class", "") or ""
        self.pkg = e.get("package", "") or ""
        self.checkable = e.get("checkable") == "true"
        self.checked = e.get("checked") == "true"
        self.enabled = e.get("enabled") != "false"
        x1, y1, x2, y2 = (int(v) for v in re.findall(r"-?\d+", e.get("bounds", "[0,0][0,0]"))[:4])
        self.x1, self.y1, self.x2, self.y2 = x1, y1, x2, y2
        self.cx, self.cy = (x1 + x2) // 2, (y1 + y2) // 2

    def __repr__(self):
        kind = self.cls.split(".")[-1]
        return f"<{kind} text={self.text!r} desc={self.desc!r} id={self.rid!r} [{self.x1},{self.y1}][{self.x2},{self.y2}]>"


_size = None


def screen_size():
    global _size
    if _size is None:
        out = sh("wm size")
        m = re.findall(r"(\d+)x(\d+)", out)
        _size = tuple(int(v) for v in m[-1])  # an override size, when there is one, comes last
    return _size


def parse_nodes(e, path=()):
    """Every node under e, in document order, with its path."""
    out = []
    for i, c in enumerate(c for c in e if c.tag == "node"):
        out.append(Node(c, path + (i,)))
        out += parse_nodes(c, path + (i,))
    return out


def dump():
    """The nodes on screen (uiautomator), after dismissing a system "isn't responding" dialog if one is up."""
    global _last_xml
    for _ in range(6):
        out = sh("rm -f /sdcard/fgs-ui.xml; uiautomator dump /sdcard/fgs-ui.xml 2>&1", check=False, timeout=60)
        xml = adb("exec-out", "cat", "/sdcard/fgs-ui.xml", check=False)
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            log(f"uiautomator dump: {out.strip()}")
            time.sleep(1)
            continue
        _last_xml = xml
        nodes = parse_nodes(root)
        wait = next((n for n in nodes if n.rid == "android:id/aerr_wait"), None)
        if wait:
            log("a system 'isn't responding' dialog is up: Wait")
            sh(f"input tap {wait.cx} {wait.cy}")
            time.sleep(1.5)
            continue
        return nodes
    fail("uiautomator could not read the screen")


def scope(nodes, title):
    """The part of the tree that is one screen: the closest ancestor of its title that also holds a scrolling list.
    Compose keeps the screen underneath (e.g. the Prayer screen under Settings) in the same window, and its nodes can
    show through in the dump; lookups on a screen stay inside its scope. None when the title is not on screen."""
    t = next((n for n in nodes if title(n)), None)
    if t is None:
        return None
    for k in range(len(t.path), -1, -1):
        if any(n.scrollable and n.path[:k] == t.path[:k] for n in nodes):
            return t.path[:k]
    return ()


SCOPED = []  # the nodes pick() is looking through, for predicates that compare a node with its neighbours


def pick(nodes, pred, within=None):
    """The first node matching pred (inside the screen whose title matches within, when given)."""
    global SCOPED
    if within is not None:
        root = scope(nodes, within)
        if root is None:
            return None
        nodes = [n for n in nodes if n.path[:len(root)] == root]
    SCOPED = nodes
    return next((n for n in nodes if pred(n)), None)


def find(pred, timeout=10.0, within=None):
    end = time.time() + timeout
    while True:
        nodes = dump()
        hit = pick(nodes, pred, within)
        if hit or time.time() > end:
            return hit, nodes
        time.sleep(0.5)


def need(pred, what, timeout=10.0, within=None):
    hit, _ = find(pred, timeout, within)
    if not hit:
        fail(f"{what} was not found on screen")
    return hit


def text_is(s, pkg=PKG):
    """A node showing exactly s; also a clickable row whose merged text starts with s (Compose may report "Title, note")."""
    return lambda n: (n.text == s or n.text.startswith(s + ", ")) and (pkg is None or n.pkg == pkg) and n.y2 > n.y1


def desc_is(s, pkg=PKG):
    return lambda n: n.desc == s and (pkg is None or n.pkg == pkg) and n.y2 > n.y1


# ---------------------------------------------------------------- actions (each one marked in the timeline)

EVENTS = []


def mark(name, hold=0.0, t=None):
    """A moment in the recording; hold > 0 keeps that many seconds after it in the edited video."""
    RECORDER.checkpoint()
    EVENTS.append({"name": name, "t": time.time() if t is None else t, "hold": hold})


def tap(node, name, hold=1.6, pause=PAUSE):
    log(f"tap {name}: {node}")
    mark(name, hold)
    sh(f"input tap {node.cx} {node.cy}")
    time.sleep(pause)


def key(code, name, hold=1.6, pause=PAUSE):
    log(f"key {code} ({name})")
    mark(name, hold)
    sh(f"input keyevent {code}")
    time.sleep(pause)


def swipe(x1, y1, x2, y2, ms, name, hold=1.0, pause=1.0):
    mark(name, hold)
    sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")
    time.sleep(pause)


def scroll_to(pred, what, top=0.14, bottom=0.82, forward=True, swipes=10, within=None):
    """Scrolls (like a finger) until a node matching pred sits between top and bottom (fractions of the height)."""
    w, h = screen_size()
    x = w // 2
    last = None
    for _ in range(swipes + 1):
        n = pick(dump(), pred, within)
        if n and top * h <= n.cy <= bottom * h:
            return n
        # The list cannot move further (its start or end): take the node where it is when it is on screen.
        if n and last is not None and abs(n.cy - last) < 8 and 0 < n.y1 and n.y2 < h:
            return n
        last = n.cy if n else None
        if n and n.cy > bottom * h:      # below: move the content up just enough
            d = min(int(n.cy - h * (top + bottom) / 2), int(h * 0.5))
            swipe(x, int(h * 0.75), x, int(h * 0.75) - max(d, 150), 700, f"scroll to {what}")
        elif n:                          # above: move the content down
            d = min(int(h * (top + bottom) / 2 - n.cy), int(h * 0.5))
            swipe(x, int(h * 0.3), x, int(h * 0.3) + max(d, 150), 700, f"scroll to {what}")
        elif forward:
            swipe(x, int(h * 0.78), x, int(h * 0.30), 650, f"scroll to {what}")
        else:
            swipe(x, int(h * 0.30), x, int(h * 0.78), 650, f"scroll to {what}")
    fail(f"{what} was not found after scrolling")


def switch_for(nodes, label):
    """The switch on the same row as a label (to its right, vertically centred with it)."""
    rows = max(70, label.y2 - label.y1)
    cands = [n for n in nodes if n.checkable and n.pkg == label.pkg and n.cx > label.cx and abs(n.cy - label.cy) < rows]
    return min(cands, key=lambda n: abs(n.cy - label.cy)) if cands else None


# ---------------------------------------------------------------- device state

def device_now():
    return int(sh("date +%s").strip())


def set_clock(ms):
    """Sets the wall clock (needs adb root and automatic time off) and checks it."""
    sh(f"cmd alarm set-time {ms}", check=False)
    if abs(device_now() - ms / 1000) > 3:
        log("cmd alarm set-time did not take; using date")
        sh(f"date @{ms // 1000}", check=False)
    now = device_now()
    if abs(now - ms / 1000) > 3:
        fail(f"could not set the emulator clock to {ms // 1000} (it reads {now})")
    log(f"clock set to {datetime.fromtimestamp(now, ZoneInfo(ZONE)):%Y-%m-%d %H:%M:%S} {ZONE}")


def granted(permission):
    return f"{permission}: granted=true" in sh(f"dumpsys package {PKG}", check=False)


def exact_alarms_allowed():
    return re.search(r"SCHEDULE_EXACT_ALARM: allow", sh(f"cmd appops get {PKG} SCHEDULE_EXACT_ALARM", check=False)) is not None


def reminder_alarms():
    """The app's reminder alarms (RTC_WAKEUP, ReminderReceiver) from dumpsys alarm: [(epoch ms, dump block)]."""
    out = sh("dumpsys alarm", timeout=90)
    blocks = re.split(r"\n(?=\s*(?:RTC_WAKEUP|RTC|ELAPSED_WAKEUP|ELAPSED) #\d+: Alarm\{)", out)
    found = []
    for b in blocks:
        head = b.strip().split("\n", 1)[0]
        if "Alarm{" not in head or PKG not in head or not head.strip().startswith("RTC_WAKEUP"):
            continue
        m = re.search(r"origWhen (\d{11,})", head)
        if m:
            ms = int(m.group(1))
        else:
            m = re.search(r"origWhen=(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)", b)
            if not m:
                continue
            ms = int(datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S").replace(tzinfo=ZoneInfo(ZONE)).timestamp() * 1000)
        found.append((ms, b.strip()))
    reminders = [a for a in found if "ReminderReceiver" in a[1]]
    return sorted(reminders or found)


def app_notifications():
    """The app's notifications now showing: [(id, channel, has the foreground-service flag, actions)]."""
    out = sh("dumpsys notification --noredact", check=False, timeout=60)
    out = re.split(r"\n\s*Archive \(", out)[0]  # removed notifications are listed after this
    found = []
    for m in re.finditer(r"key=0\|" + re.escape(PKG) + r"\|(\d+)\|[^\n]*?Notification\(channel=(\S+)([^\n]*)", out):
        rest = m.group(3)
        flags = re.search(r"flags=0x([0-9a-fA-F]+)", rest)
        actions = re.search(r"actions=(\d+)", rest)
        found.append((int(m.group(1)), m.group(2), bool(flags and int(flags.group(1), 16) & 0x40), int(actions.group(1)) if actions else 0))
    return found


def audio_playing():
    out = sh("dumpsys audio", check=False, timeout=60)
    return [line.strip() for line in out.splitlines() if "state:started" in line and "USAGE_NOTIFICATION_EVENT" in line]


# ---------------------------------------------------------------- recording

class Recorder:
    """screenrecord in segments (it stops at 180 s), and the emulator's audio from PulseAudio when there is a source."""

    def __init__(self):
        self.segments = []
        self.proc = None
        self.audio = None
        self.audio_start = None
        self.running = False

    def _start_segment(self):
        remote = f"/sdcard/fgs-demo-{len(self.segments)}.mp4"
        sh(f"rm -f {remote}", check=False)
        self.proc = subprocess.Popen(["adb", "shell", "screenrecord", "--bit-rate", "6000000", "--time-limit", "180", remote])
        # screenrecord takes a moment to start encoding; its first frame is about now.
        time.sleep(0.4)
        self.segments.append({"remote": remote, "start": time.time()})
        time.sleep(0.6)

    def _stop_segment(self):
        sh("pkill -INT screenrecord", check=False)
        try:
            self.proc.wait(timeout=20)
        except subprocess.TimeoutExpired:
            self.proc.kill()
        time.sleep(1.0)  # the file is finished after the process ends

    def _pull(self):
        RAW.mkdir(parents=True, exist_ok=True)
        for seg in self.segments:
            local = RAW / pathlib.Path(seg["remote"]).name
            adb("pull", seg["remote"], str(local), timeout=300)
            seg["file"] = str(local)

    def start(self):
        RAW.mkdir(parents=True, exist_ok=True)
        if AUDIO_SOURCE:
            wav = RAW / "audio.wav"
            try:
                self.audio = subprocess.Popen(
                    ["parecord", f"--device={AUDIO_SOURCE}", "--file-format=wav", "--rate=48000", "--channels=2", str(wav)])
                self.audio_start = time.time()
            except OSError as e:
                log(f"no audio capture: {e}")
                self.audio = None
        self._start_segment()
        self.running = True

    def checkpoint(self, max_age=SEGMENT_MAX):
        """Before an action: a new segment when this one nears screenrecord's limit (or is older than max_age)."""
        if self.running and time.time() - self.segments[-1]["start"] > max_age:
            log("starting a new recording segment")
            self._stop_segment()
            self._start_segment()

    def stop(self):
        if not self.running:
            return
        self.running = False
        self._stop_segment()
        self._pull()
        if self.audio:
            self.audio.send_signal(signal.SIGINT)
            try:
                self.audio.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.audio.kill()


RECORDER = Recorder()


def write_timeline(extra=None):
    RAW.mkdir(parents=True, exist_ok=True)
    data = {
        "segments": RECORDER.segments,
        "audio": {"file": str(RAW / "audio.wav"), "start": RECORDER.audio_start} if RECORDER.audio_start else None,
        "events": EVENTS,
    }
    data.update(extra or {})
    (RAW / "timeline.json").write_text(json.dumps(data, indent=2), encoding="utf-8")


# ---------------------------------------------------------------- steps

def prepare():
    """Emulator settings for the demo; run after adb root."""
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        sh(f"settings put global {k} 0")
    sh("settings put global auto_time 0")
    sh("settings put global auto_time_zone 0")
    sh(f"cmd alarm set-timezone {ZONE}", check=False)
    sh(f"setprop persist.sys.timezone {ZONE}", check=False)
    zone = sh("getprop persist.sys.timezone").strip()
    if zone != ZONE:
        fail(f"the time zone is {zone!r}, not {ZONE}")
    # Installed from adb without Play Protect asking to scan it.
    sh("settings put global verifier_verify_adb_installs 0", check=False)
    sh("settings put global package_verifier_enable 0", check=False)
    # Awake, unlocked, no Doze (the alarm path is the same; the demo just must not wait for a maintenance window).
    sh("svc power stayon true", check=False)
    sh("settings put system screen_off_timeout 1800000", check=False)
    sh("input keyevent KEYCODE_WAKEUP", check=False)
    sh("wm dismiss-keyguard", check=False)
    sh("dumpsys deviceidle disable", check=False)
    sh("cmd notification set_dnd off", check=False)
    # Notification and media volume up, so the Adhan is heard in the captured audio.
    for stream in (3, 5):
        sh(f"cmd media_session volume --stream {stream} --set 6", check=False)
    log("locale " + (sh("getprop persist.sys.locale").strip() or sh("getprop ro.product.locale").strip()))
    log("ringer: " + " ".join(re.findall(r"mRingerMode=\S+", sh("dumpsys audio", check=False))[:1]))
    # Today at START in Riyadh, so the chosen prayer is the next one.
    h, m = (int(v) for v in START.split(":"))
    start = datetime.now(ZoneInfo(ZONE)).replace(hour=h, minute=m, second=0, microsecond=0)
    set_clock(int(start.timestamp() * 1000))


def onboard():
    """Off camera: the first-launch tour, Riyadh from the city search, then back to the home screen."""
    sh(f"am start -W -n {ACTIVITY}", timeout=120)
    time.sleep(3)
    tap(need(text_is(S_SKIP), "the tour's Skip button", timeout=60), "skip tour")
    tap(scroll_to(text_is(S_SEARCH_CITY), "Search for a city", within=text_is(S_SETUP)), "search city")
    field = need(lambda n: n.cls == "android.widget.EditText" and n.pkg == PKG, "the city search field")
    tap(field, "city field")
    sh("input text Riyadh")
    time.sleep(2)
    city = need(lambda n: text_is("Riyadh")(n) and n.pkg == PKG and n.cls != "android.widget.EditText" and n.cy > field.cy,
                "Riyadh in the city results", timeout=30)
    tap(city, "Riyadh")
    need(lambda n: n.pkg == PKG and n.text.startswith(S_LOCATION_SET) and "Riyadh" in n.text, "'Prayer times for Riyadh'", timeout=15)
    tap(scroll_to(text_is(S_START), "Get started"), "get started")
    for _ in range(20):
        nodes = dump()
        if not any(text_is(S_START)(n) for n in nodes) and any(desc_is(S_SETTINGS)(n) for n in nodes):
            break
        time.sleep(0.5)
    else:
        fail("the tour did not close on Get started")
    key("KEYCODE_HOME", "home")
    EVENTS.clear()


def record():
    if granted("android.permission.POST_NOTIFICATIONS"):
        log("note: notifications are already allowed (no permission dialog will show)")
    w, h = screen_size()
    sh("input keyevent KEYCODE_HOME")
    time.sleep(2)
    RECORDER.start()
    mark("home screen", hold=1.5)
    time.sleep(1.5)

    # 1. Open Near Mosque from the app list.
    swipe(w // 2, int(h * 0.85), w // 2, int(h * 0.3), 350, "open the app list", hold=1.4, pause=1.5)
    icon, _ = find(lambda n: n.text == APP_LABEL and n.pkg != PKG and n.y2 > n.y1, timeout=6)
    if icon:
        tap(icon, "open Near Mosque", hold=2.2)
    else:
        log("::warning::the launcher's app list did not show Near Mosque; opening it with am start")
        mark("open Near Mosque", hold=2.2)
        sh(f"am start -n {ACTIVITY}")
        time.sleep(PAUSE)

    # 2. Settings, then Prayer & Audio.
    tap(need(desc_is(S_SETTINGS), "the Prayer screen's Settings button", timeout=20), "Settings")
    tap(scroll_to(text_is(S_AUDIO), "the Prayer & Audio row in Settings"), "Prayer & Audio")
    need(text_is(S_ADHAN_SECTION), "the Prayer & Audio screen")

    # 3. The prayer's own Adhan switch (under Each prayer; the prayer's row alone on its line, no time beside it).
    audio = text_is(S_AUDIO)

    def prayer_row(n):
        return text_is(PRAYER)(n) and not any(
            m is not n and m.pkg == PKG and m.text and abs(m.cy - n.cy) < 40 for m in SCOPED)

    row = scroll_to(prayer_row, f"the {PRAYER} row", top=0.12, bottom=0.5, within=audio)
    header, _ = find(text_is(S_EACH_PRAYER), timeout=0, within=audio)
    if header and header.cy > row.cy:
        fail(f"the {PRAYER} row found is above the Each prayer section")
    tap(row, f"expand {PRAYER}")
    label = scroll_to(text_is(S_PLAY_ADHAN), "Play the Adhan", top=0.12, bottom=0.85, within=audio)
    nodes = dump()
    label = pick(nodes, text_is(S_PLAY_ADHAN), audio) or label
    sw = switch_for(nodes, label)
    if not sw:
        fail("the Play the Adhan switch was not found")
    if sw.checked:
        log("the Adhan switch was already on")
    else:
        tap(sw, f"turn on the {PRAYER} Adhan", hold=1.8)

    # 4. Android's notification permission, asked when the switch is turned on.
    allow, _ = find(lambda n: n.rid == "com.android.permissioncontroller:id/permission_allow_button", timeout=6)
    if allow:
        tap(allow, "allow notifications", hold=1.6)
    if not granted("android.permission.POST_NOTIFICATIONS"):
        fail("notifications are not allowed for the app")
    nodes = dump()
    label = pick(nodes, text_is(S_PLAY_ADHAN), audio)
    sw = label and switch_for(nodes, label)
    if not sw or not sw.checked:
        fail(f"the {PRAYER} Adhan switch is not on")

    # 5. "Alarms & reminders" (exact alarms), when the screen asks for it: its system page and back.
    if exact_alarms_allowed():
        log("exact alarms are already allowed; no Alarms & reminders row")
    else:
        row = scroll_to(lambda n: n.pkg == PKG and S_EXACT in n.text, "the Alarms & reminders row", top=0.04, forward=False,
                        within=audio)
        tap(row, "Alarms & reminders row", hold=2.0)
        page = need(lambda n: n.pkg == "com.android.settings" and n.checkable,
                    "the Alarms & reminders switch in system settings", timeout=15)
        if not page.checked:
            tap(page, "allow alarms & reminders", hold=1.8)
        for _ in range(10):
            if exact_alarms_allowed():
                break
            time.sleep(0.5)
        else:
            fail("Alarms & reminders is still off for the app")
        key("KEYCODE_BACK", "back to the app", hold=1.6)
        need(text_is(S_AUDIO), "the Prayer & Audio screen after Alarms & reminders", timeout=15)

    # 6. Leave the app.
    key("KEYCODE_HOME", "leave the app", hold=1.8, pause=2.0)

    # 7. The app's own alarm for the Adhan; the clock moves to just before it.
    alarms = []
    for _ in range(3):
        now_ms = device_now() * 1000
        alarms = [a for a in reminder_alarms() if a[0] > now_ms + 30_000]
        if alarms:
            break
        # A clock change makes the app rebuild its alarms (RescheduleReceiver, TIME_SET), as on a real phone.
        log("no reminder alarm yet; nudging the clock by a second")
        set_clock(now_ms + 1000)
        time.sleep(3)
    if not alarms:
        fail("the app scheduled no alarm for the Adhan (dumpsys alarm)")
    at_ms, block = alarms[0]
    at = datetime.fromtimestamp(at_ms / 1000, ZoneInfo(ZONE))
    log(f"the Adhan alarm: {at:%Y-%m-%d %H:%M:%S} {ZONE}\n{block}")
    summary(f"- The {PRAYER} Adhan alarm (from `dumpsys alarm`): {at:%Y-%m-%d %H:%M:%S} {ZONE}")
    if "exactAllowReason" in block and "exactAllowReason=permission" not in block and "exactAllowReason=policy" not in block:
        log("::warning::the alarm does not look exact: " + " ".join(re.findall(r"exactAllowReason=\S+", block)))
    # From here to the end takes about a minute: in a segment of its own when this one could run out first.
    RECORDER.checkpoint(max_age=SEGMENT_MAX - 75)
    set_clock(at_ms - LEAD * 1000)
    mark("clock moved")

    # 8. The alarm fires: ReminderReceiver starts PrayerAudioService and its notification shows.
    deadline = time.time() + LEAD + 60
    shown = []
    while time.time() < deadline:
        shown = app_notifications()
        if shown:
            break
        time.sleep(0.4)
    t_notif = time.time()
    if not shown:
        fail(f"no notification came at the {PRAYER} time")
    log(f"notifications: {shown}")
    if not any(ch == "prayer_audio" for _, ch, _, _ in shown):
        fail(f"the Adhan notification is not the player's (channels {[c for _, c, _, _ in shown]}): "
             "Android did not start the foreground service")
    mark("before the Adhan", hold=2.6, t=t_notif - 2.9)
    EVENTS.sort(key=lambda e: e["t"])
    mark("the Adhan notification", hold=3.2)
    services = sh(f"dumpsys activity services {PKG}", check=False)
    fg = re.findall(r"(isForeground=\S+ foregroundId=\S+.*|foregroundServiceType=\S+)", services)
    log("PrayerAudioService: " + "; ".join(fg[:3]))
    playing = audio_playing()
    log("audio players started: " + (" | ".join(playing) if playing else "none reported"))
    sound = "playing" if playing else "not reported by dumpsys audio"
    summary(f"- Notification {shown}, service: {'; '.join(fg[:2]) or 'not listed'}, audio: {sound}")
    time.sleep(max(0.0, t_notif + 3.2 - time.time()))

    # 9. The notification shade, with the notification and its Stop button.
    swipe(w // 2, 8, w // 2, int(h * 0.62), 500, "open the notification shade", hold=3.4, pause=1.2)
    t_shade = EVENTS[-1]["t"]
    stop, _ = find(lambda n: n.pkg == "com.android.systemui" and n.text.lower() == S_STOP.lower(), timeout=4)
    if not stop and not find(lambda n: n.pkg == "com.android.systemui", timeout=1)[0]:
        # A swipe from the very top edge is not always taken as opening the shade on the emulator.
        log("the shade did not open from the swipe; opening it with cmd statusbar")
        swipe(w // 2, int(h * 0.02) + 30, w // 2, int(h * 0.62), 450, "open the notification shade", hold=1.0, pause=0.8)
        if not find(lambda n: n.pkg == "com.android.systemui", timeout=2)[0]:
            sh("cmd statusbar expand-notifications", check=False)
            time.sleep(1.5)
        stop, _ = find(lambda n: n.pkg == "com.android.systemui" and n.text.lower() == S_STOP.lower(), timeout=4)
    if not stop:
        expand, _ = find(lambda n: n.pkg == "com.android.systemui" and n.rid == "android:id/expand_button", timeout=2)
        if expand:
            tap(expand, "expand the notification", hold=1.5, pause=1.0)
        stop = need(lambda n: n.pkg == "com.android.systemui" and n.text.lower() == S_STOP.lower(),
                    "Stop on the Adhan notification", timeout=5)
    screencap(OUT / "notification-with-stop.png")
    time.sleep(max(0.0, t_shade + 3.4 - time.time()))

    # 10. Stop: the sound ends; the notification stays as an ordinary one, without Stop.
    tap(stop, "Stop", hold=3.2, pause=2.0)
    still, _ = find(lambda n: n.pkg == "com.android.systemui" and n.text.lower() == S_STOP.lower(), timeout=0)
    if still:
        fail("Stop is still on the notification after pressing it")
    screencap(OUT / "after-stop.png")
    services = sh(f"dumpsys activity services {PKG}", check=False)
    if "PrayerAudioService" in services and "isForeground=true" in services:
        fail("PrayerAudioService is still a foreground service after Stop")
    playing = audio_playing()
    if playing:
        log("::warning::an audio player still reports started after Stop: " + " | ".join(playing))
    summary(f"- After Stop: notifications {app_notifications()}, audio {'still reported' if playing else 'stopped'}")
    time.sleep(1.2)
    key("KEYCODE_BACK", "close the shade", hold=1.6, pause=2.2)
    RECORDER.stop()
    write_timeline({"prayer": PRAYER, "alarm": at_ms, "t_notif": t_notif, "t_shade": t_shade})
    log("recorded")


# ---------------------------------------------------------------- the edit

def run(*args, cwd=None):
    r = subprocess.run(list(args), capture_output=True, text=True, cwd=cwd)
    if r.returncode != 0:
        sys.exit(f"{args[0]} failed:\n{r.stderr[-4000:]}")
    return r


def probe_duration(path):
    r = run("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path))
    return float(r.stdout.strip() or 0)


def windows(events, scale):
    """The parts kept: each action from just before it to hold seconds after it (or the next action)."""
    pre = 0.3
    acts = sorted((e for e in events if e["hold"] > 0), key=lambda e: e["t"])
    out = []
    for i, e in enumerate(acts):
        hold = e["hold"] if e["name"] in FIXED_HOLDS else e["hold"] * scale
        a = e["t"] - pre
        b = e["t"] + hold
        if i + 1 < len(acts):
            b = min(b, acts[i + 1]["t"] - pre)
        if b - a < 0.15:
            continue
        if out and a - out[-1][1] < 0.15:
            out[-1][1] = max(out[-1][1], b)
        else:
            out.append([a, b])
    return out


FIXED_HOLDS = {"before the Adhan", "the Adhan notification", "open the notification shade", "Stop"}


def cut():
    tl = json.loads((RAW / "timeline.json").read_text(encoding="utf-8"))
    segs = tl["segments"]
    for s in segs:
        s["duration"] = probe_duration(s["file"])
        s["end"] = s["start"] + s["duration"]
    events = tl["events"]
    for scale in (1.0, 0.85, 0.7, 0.6):
        wins = windows(events, scale)
        total = sum(b - a for a, b in wins)
        if total <= TARGET_SECONDS:
            break
    # Each window within the segments that recorded it.
    pieces = []
    for a, b in wins:
        for k, s in enumerate(segs):
            lo, hi = max(a, s["start"]), min(b, s["end"])
            if hi - lo > 0.1:
                pieces.append({"seg": k, "a": lo - s["start"], "b": hi - s["start"], "host": lo, "len": hi - lo})
    if not pieces:
        sys.exit("nothing to cut: no recorded window")
    offset, spans = 0.0, []
    for p in pieces:
        spans.append((p["host"], p["host"] + p["len"], offset))
        offset += p["len"]
    total = offset

    def at(t):
        for lo, hi, off in spans:
            if t < lo:
                return off
            if t <= hi:
                return off + t - lo
        return total

    by = {e["name"]: e["t"] for e in events}
    t_home = at(by["leave the app"])
    t_notif = at(by["the Adhan notification"])
    t_shade = at(by["open the notification shade"])
    t_stop = at(by["Stop"])
    c4 = max(t_shade + 0.8, t_stop - 1.3)
    caption_times = [(0.0, t_home), (t_home, t_notif - 0.4), (t_notif - 0.4, c4), (c4, total)]

    audio = tl.get("audio")
    if audio and pathlib.Path(audio["file"]).exists():
        r = subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-i", audio["file"], "-af", "volumedetect", "-f", "null", "-"],
                           capture_output=True, text=True)
        level = re.search(r"max_volume: (-?[\d.]+|-inf) dB", r.stderr)
        if not level or level.group(1) == "-inf" or float(level.group(1)) < -60:
            print(f"the captured audio is silent ({level.group(1) if level else 'unknown'} dB): video only")
            audio = None
        else:
            print(f"captured audio: max {level.group(1)} dB")
    else:
        audio = None

    work = OUT / "cut"
    work.mkdir(parents=True, exist_ok=True)
    args = ["ffmpeg", "-nostdin", "-y", "-loglevel", "error"]
    for s in segs:
        args += ["-i", s["file"]]
    if audio:
        args += ["-i", audio["file"]]
    graph = []
    uses = {}
    for j, p in enumerate(pieces):
        uses.setdefault(p["seg"], []).append(j)
    for k, js in uses.items():
        outs = "".join(f"[s{j}]" for j in js)
        graph.append(f"[{k}:v]setpts=PTS-STARTPTS,fps=30,tpad=stop_mode=clone:stop_duration=6,scale=720:-2,setsar=1,format=yuv420p,split={len(js)}{outs}")
    for j, p in enumerate(pieces):
        graph.append(f"[s{j}]trim=start={p['a']:.3f}:end={p['b']:.3f},setpts=PTS-STARTPTS[v{j}]")
    if audio:
        ai = len(segs)
        graph.append(f"[{ai}:a]aresample=48000,apad=pad_dur=30,asplit={len(pieces)}" + "".join(f"[t{j}]" for j in range(len(pieces))))
        for j, p in enumerate(pieces):
            a0 = max(0.0, p["host"] - audio["start"])
            graph.append(f"[t{j}]atrim=start={a0:.3f}:end={a0 + p['len']:.3f},asetpts=PTS-STARTPTS[a{j}]")
        graph.append("".join(f"[v{j}][a{j}]" for j in range(len(pieces))) + f"concat=n={len(pieces)}:v=1:a=1[vc][ac]")
    else:
        graph.append("".join(f"[v{j}]" for j in range(len(pieces))) + f"concat=n={len(pieces)}:v=1:a=0[vc]")
    draws = []
    if pathlib.Path(FONT).exists():
        for i, (lines, (a, b)) in enumerate(zip(CAPTIONS, caption_times, strict=True)):
            if b - a < 0.3:
                continue
            for li, line in enumerate(lines):
                f = work / f"caption-{i}-{li}.txt"
                f.write_text(line, encoding="utf-8")
                y = f"h-210-{(len(lines) - 1 - li) * 58}"
                draws.append(
                    f"drawtext=fontfile={FONT}:textfile={f.name}:expansion=none:fontsize=36:fontcolor=white:"
                    f"box=1:boxcolor=black@0.62:boxborderw=14:x=(w-text_w)/2:y={y}:enable='between(t,{a:.2f},{b:.2f})'"
                )
    else:
        print(f"::warning::no caption font at {FONT}: the video has no captions")
    graph.append("[vc]" + (",".join(draws) if draws else "null") + "[vout]")
    args += ["-filter_complex", ";\n".join(graph), "-map", "[vout]"]
    if audio:
        args += ["-map", "[ac]", "-c:a", "aac", "-b:a", "128k"]
    target = OUT / "near-mosque-fgs-demo.mp4"
    for crf in (22, 27, 31):
        run(*args, "-c:v", "libx264", "-preset", "medium", "-crf", str(crf), "-profile:v", "high", "-pix_fmt", "yuv420p",
            "-r", "30", "-movflags", "+faststart", str(target), cwd=work)
        size = target.stat().st_size
        if size <= 25 * 1024 * 1024:
            break
    (work / "filter.txt").write_text(";\n".join(graph), encoding="utf-8")
    seconds = probe_duration(target)
    print(f"{target.name}: {seconds:.1f} s, {size / 1e6:.1f} MB, {'with' if audio else 'without'} audio, {len(pieces)} cuts")
    summary(f"- `{target.name}`: {seconds:.1f} s, {size / 1e6:.1f} MB, {'with' if audio else 'without'} audio")
    for (a, b), lines in zip(caption_times, CAPTIONS, strict=True):
        summary(f"  - {a:5.1f}-{b:5.1f} s: {' '.join(lines)}")
    if seconds > TARGET_SECONDS + 5:
        print(f"::warning::the video is {seconds:.0f} s (aimed at {TARGET_SECONDS} s or less)")


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("prepare", "onboard", "record", "cut"):
        sys.exit(__doc__)
    OUT.mkdir(parents=True, exist_ok=True)
    {"prepare": prepare, "onboard": onboard, "record": record, "cut": cut}[sys.argv[1]]()


if __name__ == "__main__":
    main()
