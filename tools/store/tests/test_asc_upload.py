#!/usr/bin/env python3
"""Offline tests for tools/store/asc_upload.py: a fake App Store Connect API records every call; nothing goes out.

    python3 tools/store/tests/test_asc_upload.py      (or: python3 -m unittest discover -s tools/store/tests)
"""
import contextlib
import copy
import hashlib
import io
import json
import os
import pathlib
import sys
import tempfile
import types
import unittest
import urllib.error
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
try:
    import jwt  # noqa: F401  (asc_upload imports it; the fake API never makes a token)
except ImportError:
    sys.modules["jwt"] = types.ModuleType("jwt")
import asc_upload  # noqa: E402

IPHONE_OLD = ["prayer", "qibla", "mosques-map", "ask", "classics", "widgets", "island", "audio", "mosques-radar", "prayer-dark"]
IPHONE_NEW = ["prayer", "prayer-weather", "qibla", "mosques-map", "ask", "classics", "widgets", "island", "audio", "mosques-radar"]
IPAD_OLD = ["prayer", "qibla", "mosques-map", "ask", "classics", "widgets", "audio", "widgets-qibla", "onboarding"]
IPAD_NEW = ["prayer", "prayer-weather", "qibla", "mosques-map", "ask", "classics", "widgets", "audio", "widgets-qibla", "onboarding"]
MAC_NEW = ["prayer", "prayer-weather", "mosques-map", "ask", "classics", "widgets", "audio", "qibla"]
VISION_OLD = ["prayer", "mosques-map", "ask", "classics", "audio", "onboarding"]
VISION_NEW = ["prayer", "prayer-weather", "mosques-map", "ask", "classics", "audio", "onboarding"]
OLD_SUM = "0123456789abcdef0123456789abcdef"   # sourceFileChecksum of a screenshot uploaded from an older file
ENV_KEYS = ("DISPLAY_TYPES", "SCREENS", "REMOVE_SCREENS", "REPLACE_SCREENS", "CONFIRM_DELETE", "DELETE_FIRST_IN_FULL_SETS", "TEXT",
            "PLATFORMS", "DRY_RUN", "REPLACE_SETS", "ALLOW_SHORT_SETS")
SECRET = "-----BEGIN PRIVATE KEY-----not-a-real-key"


def names(order: list) -> list:
    return [f"{i:02d}-{s}.png" for i, s in enumerate(order, 1)]


class FakeASC:
    """Just enough of the App Store Connect API for asc_upload.py, in memory."""

    def __init__(self):
        self.calls = []
        self.n = 0
        self.versions = {}   # platform -> [version]
        self.locs = {}       # version id -> [localization]
        self.sets = {}       # localization id -> [set]
        self.shots = {}      # set id -> [screenshot], in store order
        self.refuse_whats_new = False
        self.infos = []          # appInfos of the app
        self.info_locs = {}      # appInfo id -> [appInfoLocalization]
        self.fail_names = set()  # uploads of these file names end FAILED in processing
        self.fail_commit = set()  # the commit (PATCH uploaded) of these file names is refused
        self.slow_names = set()   # uploads of these file names stay processing (UPLOAD_COMPLETE)
        self.fail_commit_in = {}  # set id -> file names whose commit is refused in that set only

    def nid(self, kind: str) -> str:
        self.n += 1
        return f"{kind}{self.n}"

    def add_version(self, platform: str, state: str = "PREPARE_FOR_SUBMISSION", version: str = "2.0") -> str:
        vid = self.nid("ver")
        self.versions.setdefault(platform, []).append(
            {"id": vid, "attributes": {"versionString": version, "appVersionState": state}})
        self.locs[vid] = []
        return vid

    def add_locale(self, vid: str, locale: str, **text) -> str:
        lid = self.nid("loc")
        self.locs[vid].append({"id": lid, "attributes": {"locale": locale, **text}})
        self.sets[lid] = []
        return lid

    def add_info(self, **states) -> str:
        """An appInfo with the given state attributes (state=..., appStoreState=...)."""
        iid = self.nid("info")
        self.infos.append({"id": iid, "attributes": dict(states)})
        self.info_locs[iid] = []
        return iid

    def add_info_locale(self, iid: str, locale: str, **attrs) -> str:
        lid = self.nid("infoloc")
        self.info_locs[iid].append({"id": lid, "attributes": {"locale": locale, **attrs}})
        return lid

    def info_localization(self, lid: str) -> dict:
        return next(l for ls in self.info_locs.values() for l in ls if l["id"] == lid)

    def add_set(self, lid: str, display_type: str, files: list, states: dict | None = None,
                checksums: dict | None = None) -> str:
        sid = self.nid("set")
        self.sets[lid].append({"id": sid, "attributes": {"screenshotDisplayType": display_type}})
        self.shots[sid] = [{"id": self.nid("shot"), "attributes": {
            "fileName": f, "assetDeliveryState": {"state": (states or {}).get(f, "COMPLETE")},
            **({"sourceFileChecksum": checksums[f]} if f in (checksums or {}) else {})}} for f in files]
        return sid

    def file_names(self, sid: str) -> list:
        return [s["attributes"]["fileName"] for s in self.shots[sid]]

    def shot(self, sid: str, name: str) -> dict:
        return next(s for s in self.shots[sid] if s["attributes"]["fileName"] == name)

    def writes(self) -> list:
        return [c for c in self.calls if c[0] != "GET"]

    def localization(self, lid: str) -> dict:
        return next(l for ls in self.locs.values() for l in ls if l["id"] == lid)

    def api(self, method, path, params=None, body=None):
        self.calls.append((method, path, copy.deepcopy(params), copy.deepcopy(body)))
        p = path.split("?")[0].strip("/").split("/")
        if method == "GET" and p == ["apps"]:
            return {"data": [{"id": "app1"}]}
        if method == "GET" and p[0] == "apps" and p[2] == "appInfos":
            return {"data": copy.deepcopy(self.infos)}
        if method == "GET" and p[0] == "appInfos" and p[2] == "appInfoLocalizations":
            return {"data": copy.deepcopy(self.info_locs[p[1]])}
        if method == "PATCH" and p[0] == "appInfoLocalizations":
            attrs = body["data"]["attributes"]
            assert set(attrs) == {"privacyPolicyText"}, f"only the privacy text may change, not {sorted(attrs)}"
            self.info_localization(p[1])["attributes"].update(attrs)
            return {}
        if method == "GET" and p[0] == "apps" and p[2] == "appStoreVersions":
            return {"data": copy.deepcopy(self.versions.get(params["filter[platform]"], []))}
        if method == "GET" and p[0] == "appStoreVersions" and p[2] == "appStoreVersionLocalizations":
            return {"data": copy.deepcopy(self.locs[p[1]])}
        if method == "GET" and p[0] == "appStoreVersionLocalizations" and p[2] == "appScreenshotSets":
            return {"data": copy.deepcopy(self.sets[p[1]])}
        if method == "POST" and p == ["appScreenshotSets"]:
            lid = body["data"]["relationships"]["appStoreVersionLocalization"]["data"]["id"]
            return {"data": {"id": self.add_set(lid, body["data"]["attributes"]["screenshotDisplayType"], [])}}
        if method == "GET" and p[0] == "appScreenshotSets" and p[2] == "appScreenshots":
            return {"data": copy.deepcopy(self.shots[p[1]])}
        if method == "POST" and p == ["appScreenshots"]:
            sid = body["data"]["relationships"]["appScreenshotSet"]["data"]["id"]
            name = body["data"]["attributes"]["fileName"]
            state = "FAILED" if name in self.fail_names else "UPLOAD_COMPLETE" if name in self.slow_names else "COMPLETE"
            shot = {"id": self.nid("shot"), "attributes": {"fileName": name, "assetDeliveryState": {"state": state}}}
            self.shots[sid].append(shot)
            return {"data": {"id": shot["id"], "attributes": {"uploadOperations": []}}}
        if method == "PATCH" and p[0] == "appScreenshots":
            sid, shot = next((sid, s) for sid, ss in self.shots.items() for s in ss if s["id"] == p[1])
            if shot["attributes"]["fileName"] in self.fail_commit | self.fail_commit_in.get(sid, set()):
                raise urllib.error.HTTPError(path, 409, "Conflict", {}, io.BytesIO(b'{"errors":[]}'))
            attrs = body["data"]["attributes"]
            if attrs.get("sourceFileChecksum"):
                shot["attributes"]["sourceFileChecksum"] = attrs["sourceFileChecksum"]
            return {}
        if method == "GET" and p[0] == "appScreenshots":
            shot = next((s for ss in self.shots.values() for s in ss if s["id"] == p[1]), None)
            state = ((shot or {}).get("attributes", {}).get("assetDeliveryState") or {}).get("state", "COMPLETE")
            return {"data": {"attributes": {"assetDeliveryState": {"state": state}}}}
        if method == "DELETE" and p[0] == "appScreenshots":
            for sid in self.shots:
                self.shots[sid] = [s for s in self.shots[sid] if s["id"] != p[1]]
            return {}
        if method == "PATCH" and p[0] == "appScreenshotSets" and p[2:] == ["relationships", "appScreenshots"]:
            ids = [d["id"] for d in body["data"]]
            have = {s["id"]: s for s in self.shots[p[1]]}
            assert sorted(ids) == sorted(have), "a reorder must name every screenshot in the set, and only those"
            self.shots[p[1]] = [have[i] for i in ids]
            return {}
        if method == "PATCH" and p[0] == "appStoreVersionLocalizations":
            attrs = body["data"]["attributes"]
            if self.refuse_whats_new and "whatsNew" in attrs:
                raise urllib.error.HTTPError(path, 409, "Conflict", {}, io.BytesIO(
                    b'{"errors":[{"code":"ENTITY_ERROR.ATTRIBUTE.INVALID","title":"An attribute value is invalid."}]}'))
            self.localization(p[1])["attributes"].update(attrs)
            return {}
        raise AssertionError(f"unexpected call {method} {path}")


def no_network(*a, **k):
    raise AssertionError("the test tried to reach the network")


class Clock:
    """asc_upload's clock in the tests: sleeping moves it on at once, so waits end without real time passing."""

    def __init__(self):
        self.now = 1_000_000.0

    def time(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.now += seconds


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = pathlib.Path(self.tmp.name) / "store"
        self.fake = FakeASC()
        self.entries = []

    def tearDown(self):
        self.tmp.cleanup()

    def manifest(self, display_type: str, platform: str, order: list, locales=("en-US", "ar-SA")):
        for locale in locales:
            for pos, screen in enumerate(order, 1):
                rel = f"{display_type}/{locale}/{pos:02d}-{screen}.png"
                f = self.store / rel
                f.parent.mkdir(parents=True, exist_ok=True)
                f.write_bytes(f"png {rel}".encode())
                self.entries.append({"displayType": display_type, "ascPlatform": platform, "locale": locale,
                                     "position": pos, "screen": screen, "file": rel,
                                     "md5": hashlib.md5(f.read_bytes()).hexdigest()})
        (self.store / "manifest.json").write_text(json.dumps({"screenshots": self.entries}))

    def describe(self, types: dict, **lists):
        """Adds what finish.py also writes: each entry's device platform and app language, each display type's
        platform and screens ({displayType: (platform, order)}), and lists such as missing, failed and skipped."""
        for e in self.entries:
            e["platform"] = types[e["displayType"]][0]
            e["appLanguage"] = e["locale"].split("-")[0]
        (self.store / "manifest.json").write_text(json.dumps({
            "displayTypes": {dt: {"platform": p, "screens": order} for dt, (p, order) in types.items()},
            "screenshots": self.entries, **lists}))

    def run_tool(self, listing: dict | None = None, **env) -> tuple[int, str]:
        clean = {k: v for k, v in os.environ.items() if k not in ENV_KEYS}
        clean.update({"BUNDLE_ID": "sa.zood.nearmosque", "ASC_PRIVATE_KEY": SECRET, **env})
        out = io.StringIO()
        patches = [mock.patch.dict(os.environ, clean, clear=True),
                   mock.patch.object(asc_upload, "api", self.fake.api),
                   mock.patch.object(asc_upload, "time", Clock()),
                   mock.patch("urllib.request.urlopen", no_network),
                   mock.patch.object(sys, "argv", ["asc_upload.py", str(self.store)])]
        if listing is not None:
            path = pathlib.Path(self.tmp.name) / "listing.json"
            path.write_text(json.dumps(listing, ensure_ascii=False))
            patches.append(mock.patch.object(asc_upload, "LISTING", path))
        with contextlib.ExitStack() as stack:
            for p in patches:
                stack.enter_context(p)
            stack.enter_context(contextlib.redirect_stdout(out))
            code = asc_upload.main()
        text = out.getvalue()
        self.assertNotIn(SECRET, text)
        return code, text


class Recognition(unittest.TestCase):
    NAMES = {"prayer", "prayer-weather", "prayer-dark", "qibla", "widgets-qibla", "watch-qibla", "ask", "watch-ask"}

    def test_longest_name_wins(self):
        s = asc_upload.screen_of
        self.assertEqual(s("03-qibla.png", self.NAMES), "qibla")
        self.assertEqual(s("09-widgets-qibla.png", self.NAMES), "widgets-qibla")
        self.assertEqual(s("02-watch-qibla.png", self.NAMES), "watch-qibla")
        self.assertEqual(s("02-prayer-weather.png", self.NAMES), "prayer-weather")
        self.assertEqual(s("10-prayer-dark.png", self.NAMES), "prayer-dark")
        self.assertEqual(s("ar-SA-01-prayer.png", self.NAMES), "prayer")
        self.assertIsNone(s("IMG_0001.png", self.NAMES))
        self.assertIsNone(s("prayer.png", self.NAMES))       # needs "-<screen>.png"
        self.assertIsNone(s(None, self.NAMES))

    def test_order(self):
        shots = [{"id": str(i), "attributes": {"fileName": f}} for i, f in enumerate(
            ["IMG_0001.png", "03-qibla.png", "01-prayer.png", "10-prayer-dark.png", "02-prayer-weather.png", "Shot 2.png"])]
        got = [s["attributes"]["fileName"] for s in asc_upload.in_order(shots, ["prayer", "prayer-weather", "qibla"], self.NAMES)]
        self.assertEqual(got, ["01-prayer.png", "02-prayer-weather.png", "03-qibla.png",
                               "IMG_0001.png", "10-prayer-dark.png", "Shot 2.png"])


class Screens(Base):
    def setUp(self):
        super().setUp()
        self.ios = self.fake.add_version("IOS")
        self.en = self.fake.add_locale(self.ios, "en-US")
        self.ar = self.fake.add_locale(self.ios, "ar-SA")

    def test_insert_into_nine_item_set(self):
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW)
        en = self.fake.add_set(self.en, "APP_IPAD_PRO_3GEN_129", names(IPAD_OLD))
        ar = self.fake.add_set(self.ar, "APP_IPAD_PRO_3GEN_129", names(IPAD_OLD))
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", SCREENS="prayer-weather")
        self.assertEqual(code, 0, out)
        want = ["01-prayer.png", "02-prayer-weather.png", "02-qibla.png", "03-mosques-map.png", "04-ask.png",
                "05-classics.png", "06-widgets.png", "07-audio.png", "08-widgets-qibla.png", "09-onboarding.png"]
        self.assertEqual(self.fake.file_names(en), want)
        self.assertEqual(self.fake.file_names(ar), want)
        posts = [c for c in self.fake.writes() if c[0] == "POST"]
        self.assertEqual([c[3]["data"]["attributes"]["fileName"] for c in posts], ["02-prayer-weather.png"] * 2)
        self.assertFalse([c for c in self.fake.writes() if c[0] == "DELETE"])

    def test_skip_full_set_without_delete(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertEqual(self.fake.file_names(en), names(IPHONE_OLD))
        self.assertIn("APP_IPHONE_67 en-US: would hold 11 screenshots (at most 10); skipped", out)

    def test_delete_without_confirm_deletes_nothing(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        for confirm in (None, "false", "TRUE", "True", "yes", "1", " true"):
            self.fake.calls.clear()
            env = {"DISPLAY_TYPES": "APP_IPHONE_67", "SCREENS": "prayer-weather", "REMOVE_SCREENS": "prayer-dark"}
            if confirm is not None:
                env["CONFIRM_DELETE"] = confirm
            code, out = self.run_tool(**env)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [], f"CONFIRM_DELETE={confirm!r}")
            self.assertIn('APP_IPHONE_67 en-US: would delete 10-prayer-dark.png (CONFIRM_DELETE is not "true": nothing deleted).', out)
            self.assertIn("would hold 11", out)
        self.assertEqual(self.fake.file_names(en), names(IPHONE_OLD))

    def test_remove_only_without_confirm_deletes_nothing(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", REMOVE_SCREENS="prayer-dark")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("1 not deleted without confirm_delete", out)

    def test_delete_with_confirm_then_insert_then_idempotent(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        ar = self.fake.add_set(self.ar, "APP_IPHONE_67", names(IPHONE_OLD))
        env = {"DISPLAY_TYPES": "APP_IPHONE_67", "SCREENS": "prayer-weather", "REMOVE_SCREENS": "prayer-dark",
               "CONFIRM_DELETE": "true"}
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        kinds = [(c[0], c[1].split("/")[1]) for c in self.fake.writes() if c[1].startswith("/appScreenshot")]
        # Per set: delete, then upload (create, commit), then one reorder.
        self.assertEqual(kinds[:4], [("DELETE", "appScreenshots"), ("POST", "appScreenshots"),
                                     ("PATCH", "appScreenshots"), ("PATCH", "appScreenshotSets")])
        self.assertEqual(kinds[4:8], kinds[:4])
        self.assertEqual(len([c for c in self.fake.writes() if c[0] == "DELETE"]), 2)  # ar-SA and en-US
        want = ["01-prayer.png", "02-prayer-weather.png", "02-qibla.png", "03-mosques-map.png", "04-ask.png",
                "05-classics.png", "06-widgets.png", "07-island.png", "08-audio.png", "09-mosques-radar.png"]
        self.assertEqual(self.fake.file_names(en), want)
        self.assertEqual(self.fake.file_names(ar), want)
        # A second run with the same inputs changes nothing.
        self.fake.calls.clear()
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("APP_IPHONE_67 en-US: nothing to change (10 there).", out)

    def test_idempotent_insert(self):
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW)
        en = self.fake.add_set(self.en, "APP_IPAD_PRO_3GEN_129", names(IPAD_OLD))
        self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", SCREENS="prayer-weather")
        first = self.fake.file_names(en)
        self.fake.calls.clear()
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", SCREENS="prayer-weather")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertEqual(self.fake.file_names(en), first)

    def test_present_but_misplaced_is_only_reordered(self):
        self.manifest("APP_DESKTOP", "MAC_OS", MAC_NEW)
        mac = self.fake.add_version("MAC_OS")
        lid = self.fake.add_locale(mac, "en-US")
        sid = self.fake.add_set(lid, "APP_DESKTOP", ["01-prayer.png", "02-mosques-map.png", "03-ask.png", "04-classics.png",
                                                     "05-widgets.png", "06-audio.png", "07-qibla.png", "08-prayer-weather.png"])
        code, out = self.run_tool(DISPLAY_TYPES="APP_DESKTOP", SCREENS="prayer-weather")
        self.assertEqual(code, 0, out)
        self.assertEqual([c[0] for c in self.fake.writes()], ["PATCH"])
        self.assertEqual(self.fake.file_names(sid)[:2], ["01-prayer.png", "08-prayer-weather.png"])

    def test_reorder_keeps_unrecognised_after(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPHONE_67", ["IMG_0001.png", "03-qibla.png", "01-prayer.png",
                                                          "10-prayer-dark.png", "09-audio.png", "Screenshot 2.png"])
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(en), ["01-prayer.png", "02-prayer-weather.png", "03-qibla.png", "09-audio.png",
                                                    "IMG_0001.png", "10-prayer-dark.png", "Screenshot 2.png"])

    def test_remove_never_takes_a_longer_name(self):
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPAD_PRO_3GEN_129", names(IPAD_OLD) + ["IMG_0001.png"])
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", REMOVE_SCREENS="qibla", CONFIRM_DELETE="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(len([c for c in self.fake.writes() if c[0] == "DELETE"]), 1)
        left = self.fake.file_names(en)
        self.assertNotIn("02-qibla.png", left)
        self.assertIn("08-widgets-qibla.png", left)
        self.assertIn("IMG_0001.png", left)

    def test_dry_run_changes_nothing(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather", REMOVE_SCREENS="prayer-dark",
                                  CONFIRM_DELETE="true", DRY_RUN="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("APP_IPHONE_67 en-US: would delete 10-prayer-dark.png.", out)
        self.assertIn("APP_IPHONE_67 en-US: would upload 02-prayer-weather.png.", out)
        self.assertIn("APP_IPHONE_67 en-US: would order: 01-prayer.png 02-prayer-weather.png 02-qibla.png", out)

    def test_missing_or_empty_set_is_left_to_the_default_mode(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        self.fake.add_set(self.en, "APP_IPHONE_67", [])
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("APP_IPHONE_67 en-US: no screenshots there; left as it is", out)
        self.assertIn("APP_IPHONE_67 ar-SA: no screenshots there; left as it is", out)

    def test_platform_without_editable_version_is_reported_and_others_go_on(self):
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW, locales=("en-US",))
        self.manifest("APP_DESKTOP", "MAC_OS", MAC_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPAD_PRO_3GEN_129", names(IPAD_OLD))
        self.fake.add_version("MAC_OS", state="WAITING_FOR_REVIEW")
        code, out = self.run_tool(DISPLAY_TYPES="APP_DESKTOP APP_IPAD_PRO_3GEN_129", SCREENS="prayer-weather")
        self.assertEqual(code, 0, out)
        self.assertIn("MAC_OS: no editable version (Prepare for Submission or rejected); the version is in review or live: nothing changed.", out)
        self.assertEqual(len(self.fake.file_names(en)), 10)

    def test_failed_upload_is_uploaded_again(self):
        # A new screenshot that ends FAILED in processing must not count as "there" on the next run.
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        env = {"DISPLAY_TYPES": "APP_IPHONE_67", "SCREENS": "prayer-weather", "REMOVE_SCREENS": "prayer-dark",
               "CONFIRM_DELETE": "true"}
        self.fake.fail_names = {"02-prayer-weather.png"}
        code, out = self.run_tool(**env)
        self.assertEqual(code, 1, out)
        self.fake.fail_names = set()
        self.fake.calls.clear()
        code, out = self.run_tool(**env)       # "run again", as the warning says
        states = [s["attributes"].get("assetDeliveryState", {}).get("state", "COMPLETE") for s in self.fake.shots[en]]
        self.assertNotIn("FAILED", states, out)
        self.assertIn("02-prayer-weather.png", self.fake.file_names(en))

    def test_unfinished_screenshot_already_there_stops_its_set(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_NEW), states={"02-prayer-weather.png": "FAILED"})
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather", REMOVE_SCREENS="prayer-dark",
                                  CONFIRM_DELETE="true")
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertEqual(self.fake.file_names(en), names(IPHONE_NEW))
        self.assertIn("::error::APP_IPHONE_67 en-US: 02-prayer-weather.png is in the set but not ready", out)
        self.assertIn("remove_screens prayer-weather and confirm_delete", out)

    def test_upload_error_after_delete_stops_cleanly(self):
        # ar-SA comes first: its commit is refused after its prayer-dark was deleted; en-US is never reached.
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        ar = self.fake.add_set(self.ar, "APP_IPHONE_67", names(IPHONE_OLD))
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        self.fake.fail_commit = {"02-prayer-weather.png"}
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather", REMOVE_SCREENS="prayer-dark",
                                  CONFIRM_DELETE="true")
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(ar), names(IPHONE_OLD)[:9])   # no half-made screenshot left
        self.assertEqual(self.fake.file_names(en), names(IPHONE_OLD))
        self.assertIn("::error::APP_IPHONE_67 ar-SA: uploading stopped (HTTPError HTTP 409) after deleting 10-prayer-dark.png", out)
        self.assertIn("Done: 0 uploaded, 1 deleted", out)

    def test_confirmed_delete_only_where_the_new_screen_goes_in(self):
        # ar-SA has no weather capture in the manifest (missing in finish.py): its prayer-dark makes room for nothing.
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        self.manifest("APP_IPHONE_67", "IOS", [s for s in IPHONE_NEW if s != "prayer-weather"], locales=("ar-SA",))
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        ar = self.fake.add_set(self.ar, "APP_IPHONE_67", names(IPHONE_OLD))
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather", REMOVE_SCREENS="prayer-dark",
                                  CONFIRM_DELETE="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(ar), names(IPHONE_OLD), out)
        self.assertIn("::warning::APP_IPHONE_67 ar-SA: the manifest has no prayer-weather for this set", out)
        # en-US has the weather capture: there the delete makes room for it as before.
        self.assertNotIn("10-prayer-dark.png", self.fake.file_names(en))
        self.assertEqual(self.fake.file_names(en)[1], "02-prayer-weather.png")
        self.assertEqual(len([c for c in self.fake.writes() if c[0] == "DELETE"]), 1)

    ROOM = names(IPAD_OLD[:8]) + ["09-prayer-dark.png"]   # 9: room for the new screen without the delete
    ROOM_ENV = {"DISPLAY_TYPES": "APP_IPAD_PRO_3GEN_129", "SCREENS": "prayer-weather", "REMOVE_SCREENS": "prayer-dark",
                "CONFIRM_DELETE": "true"}

    def test_set_with_room_uploads_before_deleting(self):
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPAD_PRO_3GEN_129", self.ROOM)
        code, out = self.run_tool(DRY_RUN="true", **self.ROOM_ENV)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("with confirm_delete, 09-prayer-dark.png is deleted only after 02-prayer-weather.png is uploaded "
                      "and ready; if it fails, nothing is deleted.", out)
        code, out = self.run_tool(**self.ROOM_ENV)
        self.assertEqual(code, 0, out)
        kinds = [(c[0], c[1].split("/")[1]) for c in self.fake.writes() if c[1].startswith("/appScreenshot")]
        # Upload (create, commit), then the delete, then one reorder.
        self.assertEqual(kinds, [("POST", "appScreenshots"), ("PATCH", "appScreenshots"),
                                 ("DELETE", "appScreenshots"), ("PATCH", "appScreenshotSets")])
        self.assertEqual(self.fake.file_names(en), ["01-prayer.png", "02-prayer-weather.png"] + names(IPAD_OLD[:8])[1:])
        self.assertIn("deleted 09-prayer-dark.png (the new screenshot is ready).", out)

    def test_set_with_room_keeps_its_screenshot_when_the_new_one_fails(self):
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPAD_PRO_3GEN_129", self.ROOM)
        self.fake.fail_names = {"02-prayer-weather.png"}
        code, out = self.run_tool(**self.ROOM_ENV)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(en), self.ROOM)   # the failed upload deleted again, prayer-dark kept
        self.assertIn("::warning::APP_IPAD_PRO_3GEN_129 en-US: 09-prayer-dark.png not deleted, because the new "
                      "screenshot is not ready", out)
        self.assertIn("Done: 0 uploaded, 0 deleted", out)
        # Once the file is fixed, a second run uploads it and only then deletes prayer-dark.
        self.fake.fail_names = set()
        code, out = self.run_tool(**self.ROOM_ENV)
        self.assertEqual(code, 0, out)
        self.assertNotIn("09-prayer-dark.png", self.fake.file_names(en))
        self.assertEqual(self.fake.file_names(en)[1], "02-prayer-weather.png")

    def test_set_with_room_keeps_its_screenshot_when_the_upload_stops(self):
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPAD_PRO_3GEN_129", self.ROOM)
        self.fake.fail_commit = {"02-prayer-weather.png"}
        code, out = self.run_tool(**self.ROOM_ENV)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(en), self.ROOM)
        self.assertIn("uploading stopped (HTTPError HTTP 409); 09-prayer-dark.png not deleted; not reordered", out)
        self.assertIn("Done: 0 uploaded, 0 deleted", out)

    def test_full_set_deletes_first_and_says_it_is_not_brought_back(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        en = self.fake.add_set(self.en, "APP_IPHONE_67", names(IPHONE_OLD))
        env = {"DISPLAY_TYPES": "APP_IPHONE_67", "SCREENS": "prayer-weather", "REMOVE_SCREENS": "prayer-dark"}
        code, out = self.run_tool(**env)    # the report the owner confirms from, confirm_delete off
        self.assertEqual(code, 0, out)
        self.assertIn("with confirm_delete, the set is full, so 10-prayer-dark.png is deleted before "
                      "02-prayer-weather.png is uploaded; if that upload fails, 10-prayer-dark.png is not brought back.", out)
        self.fake.fail_names = {"02-prayer-weather.png"}
        code, out = self.run_tool(CONFIRM_DELETE="true", **env)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(en), names(IPHONE_OLD)[:9])
        self.assertIn("deleted 10-prayer-dark.png (the set is full: deleted before uploading).", out)
        self.assertIn("::error::APP_IPHONE_67 en-US: 10-prayer-dark.png was deleted to make room and is not brought "
                      "back; the set stays without it until a run uploads the new screenshot.", out)

    def test_delete_without_the_new_screen_is_reported_in_a_dry_run_too(self):
        self.manifest("APP_IPHONE_67", "IOS", [s for s in IPHONE_NEW if s != "prayer-weather"], locales=("ar-SA",))
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        self.fake.add_set(self.ar, "APP_IPHONE_67", names(IPHONE_OLD))
        for env in ({"DRY_RUN": "true", "CONFIRM_DELETE": "true"}, {}):
            self.fake.calls.clear()
            code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather",
                                      REMOVE_SCREENS="prayer-dark", **env)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [])
            self.assertIn("ar-SA: the manifest has no prayer-weather for this set", out)
            self.assertNotIn("would delete", out)

    def test_input_errors(self):
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        cases = [{"SCREENS": "prayer-weather"},
                 {"DISPLAY_TYPES": "APP_IPHONE_67", "SCREENS": "prayer-dark", "REMOVE_SCREENS": "prayer-dark"},
                 {"DISPLAY_TYPES": "APP_IPHONE_67", "SCREENS": "prayer-wether"},
                 {"TEXT": "true"},
                 {"TEXT": "true", "PLATFORMS": "IOS WATCH_OS"}]
        for env in cases:
            code, out = self.run_tool(**env)
            self.assertEqual(code, 1, env)
            self.assertIn("::error::", out)
        self.assertEqual(self.fake.writes(), [])


class Replace(Base):
    """REPLACE_SCREENS: a changed screen's new file goes in, and only once it is ready does its old one go."""
    DT = "APP_APPLE_VISION_PRO"
    # The store's Apple Vision Pro set: the old order with the weather screen added, an old onboarding, and a
    # screenshot the tool does not recognise.
    HELD = ["01-prayer.png", "02-prayer-weather.png", "02-mosques-map.png", "03-ask.png", "04-classics.png",
            "05-audio.png", "06-onboarding.png", "IMG_0001.png"]
    CONFIRMED = {"DISPLAY_TYPES": DT, "REPLACE_SCREENS": "onboarding", "CONFIRM_DELETE": "true"}

    def setUp(self):
        super().setUp()
        self.vision = self.fake.add_version("VISION_OS")
        self.en = self.fake.add_locale(self.vision, "en-US")
        self.ar = self.fake.add_locale(self.vision, "ar-SA")

    def old_set(self, lid: str, files: list, dt: str = DT) -> str:
        return self.fake.add_set(lid, dt, files, checksums={f: OLD_SUM for f in files})

    def md5(self, locale: str, screen: str, dt: str = DT) -> str:
        return next(e["md5"] for e in self.entries if (e["displayType"], e["locale"], e["screen"]) == (dt, locale, screen))

    def kinds(self) -> list:
        return [(c[0], c[1].split("/")[1]) for c in self.fake.writes() if c[1].startswith("/appScreenshot")]

    def test_replace_with_differing_checksum(self):
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        en = self.old_set(self.en, self.HELD)
        old_id = self.fake.shot(en, "06-onboarding.png")["id"]
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        # Upload (create, commit), the old one deleted once the new one is ready, then one reorder.
        self.assertEqual(self.kinds(), [("POST", "appScreenshots"), ("PATCH", "appScreenshots"),
                                        ("DELETE", "appScreenshots"), ("PATCH", "appScreenshotSets")])
        self.assertEqual([c[1] for c in self.fake.writes() if c[0] == "DELETE"], [f"/appScreenshots/{old_id}"])
        self.assertEqual(self.fake.file_names(en), ["01-prayer.png", "02-prayer-weather.png", "02-mosques-map.png",
                                                    "03-ask.png", "04-classics.png", "05-audio.png",
                                                    "07-onboarding.png", "IMG_0001.png"])
        self.assertEqual(self.fake.shot(en, "07-onboarding.png")["attributes"]["sourceFileChecksum"],
                         self.md5("en-US", "onboarding"))
        self.assertIn("APP_APPLE_VISION_PRO en-US: replaced 06-onboarding.png with 07-onboarding.png", out)
        self.assertIn("1 replaced", out)
        # A second run finds the manifest's file there and changes nothing.
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("07-onboarding.png already is the manifest's onboarding file; nothing to replace.", out)

    def test_same_checksum_is_a_no_op(self):
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        files = names(VISION_NEW)
        # App Store Connect may give the checksum in capitals: still the same file.
        en = self.fake.add_set(self.en, self.DT, files, checksums={"07-onboarding.png": self.md5("en-US", "onboarding").upper()})
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertEqual(self.fake.file_names(en), files)
        self.assertIn("APP_APPLE_VISION_PRO en-US: 07-onboarding.png already is the manifest's onboarding file; "
                      "nothing to replace.", out)
        self.assertIn("APP_APPLE_VISION_PRO en-US: nothing to change (7 there).", out)

    def test_without_confirm_reports_only(self):
        self.manifest(self.DT, "VISION_OS", VISION_NEW)
        en = self.old_set(self.en, self.HELD)
        ar = self.old_set(self.ar, [f for f in self.HELD if "onboarding" not in f])
        for confirm in (None, "false", "TRUE", "True", "yes", "1", " true"):
            self.fake.calls.clear()
            env = {"DISPLAY_TYPES": self.DT, "REPLACE_SCREENS": "onboarding"}
            if confirm is not None:
                env["CONFIRM_DELETE"] = confirm
            code, out = self.run_tool(**env)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [], f"CONFIRM_DELETE={confirm!r}")
            self.assertIn('APP_APPLE_VISION_PRO en-US: would replace 06-onboarding.png with 07-onboarding.png (the file '
                          'differs from the manifest\'s; CONFIRM_DELETE is not "true": nothing replaced).', out)
            self.assertIn('APP_APPLE_VISION_PRO ar-SA: would add 07-onboarding.png (onboarding is not there; '
                          'CONFIRM_DELETE is not "true": nothing added).', out)
            self.assertIn("2 not replaced or added without confirm_delete", out)
            self.assertNotIn("nothing to change", out)
        self.assertEqual(self.fake.file_names(en), self.HELD)
        # A dry run with confirm_delete on says what each set would do, in the order it would do it.
        self.fake.calls.clear()
        code, out = self.run_tool(DRY_RUN="true", **self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("APP_APPLE_VISION_PRO en-US: would replace 06-onboarding.png with 07-onboarding.png: "
                      "07-onboarding.png is uploaded first and 06-onboarding.png deleted once it is ready; if it fails, "
                      "nothing is deleted.", out)
        self.assertIn("APP_APPLE_VISION_PRO en-US: would order: 01-prayer.png 02-prayer-weather.png 02-mosques-map.png "
                      "03-ask.png 04-classics.png 05-audio.png 07-onboarding.png IMG_0001.png.", out)
        self.assertIn("APP_APPLE_VISION_PRO ar-SA: would upload 07-onboarding.png.", out)
        self.assertEqual(self.fake.file_names(ar), [f for f in self.HELD if "onboarding" not in f])

    def test_failed_upload_deletes_nothing(self):
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        en = self.old_set(self.en, self.HELD)
        old_id = self.fake.shot(en, "06-onboarding.png")["id"]
        self.fake.fail_names = {"07-onboarding.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(en), self.HELD)          # the old one kept, nothing reordered
        deletes = [c[1] for c in self.fake.writes() if c[0] == "DELETE"]
        self.assertEqual(len(deletes), 1)                               # only this run's own failed upload
        self.assertNotEqual(deletes[0], f"/appScreenshots/{old_id}")
        self.assertFalse([c for c in self.fake.writes() if c[1].startswith("/appScreenshotSets/")])
        self.assertIn("::error::APP_APPLE_VISION_PRO en-US: 07-onboarding.png failed processing in App Store Connect; "
                      "06-onboarding.png kept (nothing deleted); not reordered.", out)
        self.assertIn("0 replaced", out)

    def test_upload_error_deletes_nothing_and_stops(self):
        # ar-SA comes first: its commit is refused; en-US is never reached.
        self.manifest(self.DT, "VISION_OS", VISION_NEW)
        ar = self.old_set(self.ar, self.HELD)
        en = self.old_set(self.en, self.HELD)
        self.fake.fail_commit = {"07-onboarding.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(ar), self.HELD)          # no half-made screenshot left either
        self.assertEqual(self.fake.file_names(en), self.HELD)
        self.assertIn("::error::APP_APPLE_VISION_PRO ar-SA: uploading 07-onboarding.png stopped (HTTPError HTTP 409); "
                      "06-onboarding.png not deleted; not reordered.", out)
        self.assertNotIn("APP_APPLE_VISION_PRO en-US", out)

    def test_not_ready_keeps_the_old_one_until_a_later_run(self):
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        en = self.old_set(self.en, self.HELD)
        self.fake.slow_names = {"07-onboarding.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(en), self.HELD + ["07-onboarding.png"])
        self.assertIn("::warning::APP_APPLE_VISION_PRO en-US: 07-onboarding.png is not ready yet (UPLOAD_COMPLETE); "
                      "06-onboarding.png not deleted", out)
        # Still processing on the next run: reported, nothing changed.
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("07-onboarding.png is the manifest's onboarding file but not ready", out)
        # Once it is ready, a run deletes the old one and orders the set.
        self.fake.shot(en, "07-onboarding.png")["attributes"]["assetDeliveryState"]["state"] = "COMPLETE"
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.kinds(), [("DELETE", "appScreenshots"), ("PATCH", "appScreenshotSets")])
        self.assertEqual(self.fake.file_names(en)[-2:], ["07-onboarding.png", "IMG_0001.png"])
        self.assertNotIn("06-onboarding.png", self.fake.file_names(en))

    def test_missing_manifest_file_leaves_the_set_alone(self):
        # ar-SA's onboarding capture failed in the screenshots run: finish.py listed it as missing.
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        self.manifest(self.DT, "VISION_OS", [s for s in VISION_NEW if s != "onboarding"], locales=("ar-SA",))
        self.describe({self.DT: ("vision", VISION_NEW)}, missing=["vision/ar/07-onboarding.png"])
        ar = self.old_set(self.ar, self.HELD)
        en = self.old_set(self.en, self.HELD)
        ar_ids = [s["id"] for s in self.fake.shots[ar]]
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(ar), self.HELD)
        self.assertEqual([s["id"] for s in self.fake.shots[ar]], ar_ids)
        self.assertFalse([c for c in self.fake.writes() if any(i in c[1] for i in ar_ids + [ar])])
        self.assertIn("::warning::APP_APPLE_VISION_PRO ar-SA: the manifest has no onboarding for this set (missing in "
                      "the screenshots run), so 06-onboarding.png is left as it is.", out)
        self.assertIn("07-onboarding.png", self.fake.file_names(en))
        self.assertNotIn("06-onboarding.png", self.fake.file_names(en))

    def test_adds_a_screen_that_is_not_there(self):
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        held = names(VISION_NEW)[:6]
        en = self.old_set(self.en, held)
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(en), held + ["07-onboarding.png"])
        self.assertFalse([c for c in self.fake.writes() if c[0] == "DELETE"])
        self.assertIn("APP_APPLE_VISION_PRO en-US: uploaded 07-onboarding.png: 1 of 1 ready", out)

    def test_full_set_is_left(self):
        # A set of 10 has no room to take the new file before the old one goes: nothing is deleted.
        ios = self.fake.add_version("IOS")
        lid = self.fake.add_locale(ios, "en-US")
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW, locales=("en-US",))
        ipad = self.old_set(lid, names(IPAD_NEW), dt="APP_IPAD_PRO_3GEN_129")
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", REPLACE_SCREENS="onboarding", CONFIRM_DELETE="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertEqual(self.fake.file_names(ipad), names(IPAD_NEW))
        self.assertIn("::warning::APP_IPAD_PRO_3GEN_129 en-US: holds 10 screenshots (at most 10), so there is no room "
                      "to upload 10-onboarding.png before deleting 10-onboarding.png; onboarding not replaced (nothing "
                      "deleted).", out)
        self.assertNotIn("nothing to change", out)

    def test_combined_with_screens_in_one_run(self):
        # The iPad set of the old order (9, an old onboarding): the weather screen added and onboarding replaced.
        ios = self.fake.add_version("IOS")
        lid = self.fake.add_locale(ios, "en-US")
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW, locales=("en-US",))
        ipad = self.old_set(lid, names(IPAD_OLD), dt="APP_IPAD_PRO_3GEN_129")
        old_id = self.fake.shot(ipad, "09-onboarding.png")["id"]
        env = {"DISPLAY_TYPES": "APP_IPAD_PRO_3GEN_129", "SCREENS": "prayer-weather", "REPLACE_SCREENS": "onboarding",
               "CONFIRM_DELETE": "true"}
        code, out = self.run_tool(DRY_RUN="true", **env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("would replace 09-onboarding.png with 10-onboarding.png", out)
        self.assertIn("would upload 02-prayer-weather.png.", out)
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        # The replace first (room for one more while 9), then the insert, then one reorder: never over 10.
        self.assertEqual(self.kinds(), [("POST", "appScreenshots"), ("PATCH", "appScreenshots"), ("DELETE", "appScreenshots"),
                                        ("POST", "appScreenshots"), ("PATCH", "appScreenshots"), ("PATCH", "appScreenshotSets")])
        self.assertEqual([c[1] for c in self.fake.writes() if c[0] == "DELETE"], [f"/appScreenshots/{old_id}"])
        self.assertEqual(self.fake.file_names(ipad), ["01-prayer.png", "02-prayer-weather.png", "02-qibla.png",
                                                      "03-mosques-map.png", "04-ask.png", "05-classics.png", "06-widgets.png",
                                                      "07-audio.png", "08-widgets-qibla.png", "10-onboarding.png"])
        self.assertEqual(self.fake.shot(ipad, "10-onboarding.png")["attributes"]["sourceFileChecksum"],
                         self.md5("en-US", "onboarding", "APP_IPAD_PRO_3GEN_129"))
        # Run again: nothing left to do.
        self.fake.calls.clear()
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])

    def test_combined_with_remove_in_a_full_set(self):
        # A full iPhone set: the replace has no room and is left; screens and remove_screens go on as before.
        ios = self.fake.add_version("IOS")
        lid = self.fake.add_locale(ios, "en-US")
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        iphone = self.old_set(lid, names(IPHONE_OLD), dt="APP_IPHONE_67")
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67", SCREENS="prayer-weather", REMOVE_SCREENS="prayer-dark",
                                  REPLACE_SCREENS="ask", CONFIRM_DELETE="true")
        self.assertEqual(code, 0, out)
        self.assertIn("no room to upload 05-ask.png before deleting 04-ask.png; ask not replaced", out)
        left = self.fake.file_names(iphone)
        self.assertEqual(left[1], "02-prayer-weather.png")
        self.assertNotIn("10-prayer-dark.png", left)
        self.assertIn("04-ask.png", left)
        self.assertEqual(self.fake.shot(iphone, "04-ask.png")["attributes"]["sourceFileChecksum"], OLD_SUM)

    def test_input_errors(self):
        self.manifest(self.DT, "VISION_OS", VISION_NEW)
        cases = [{"REPLACE_SCREENS": "onboarding", "CONFIRM_DELETE": "true"},
                 {"DISPLAY_TYPES": self.DT, "SCREENS": "onboarding", "REPLACE_SCREENS": "onboarding"},
                 {"DISPLAY_TYPES": self.DT, "REMOVE_SCREENS": "onboarding", "REPLACE_SCREENS": "onboarding"},
                 {"DISPLAY_TYPES": self.DT, "REPLACE_SCREENS": "onbaording", "CONFIRM_DELETE": "true"},
                 {"DISPLAY_TYPES": self.DT, "REPLACE_SCREENS": "widgets-qibla", "CONFIRM_DELETE": "true"},
                 {"DISPLAY_TYPES": self.DT, "REMOVE_SCREENS": "onboarding", "CONFIRM_DELETE": "true",
                  "DELETE_FIRST_IN_FULL_SETS": "true"}]
        for env in cases:
            code, out = self.run_tool(**env)
            self.assertEqual(code, 1, env)
            self.assertIn("::error::", out)
        self.assertEqual(self.fake.writes(), [])

    def test_older_file_is_deleted_only_with_confirm(self):
        # A replace whose old one was never deleted: the manifest's file is there and ready, next to the older one.
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        held = self.HELD + ["07-onboarding.png"]
        sums = {**{f: OLD_SUM for f in self.HELD}, "07-onboarding.png": self.md5("en-US", "onboarding")}
        en = self.fake.add_set(self.en, self.DT, held, checksums=sums)
        old_id = self.fake.shot(en, "06-onboarding.png")["id"]
        for env in ({}, {"CONFIRM_DELETE": "false"}, {"CONFIRM_DELETE": "TRUE"}, {"CONFIRM_DELETE": "true", "DRY_RUN": "true"}):
            self.fake.calls.clear()
            code, out = self.run_tool(DISPLAY_TYPES=self.DT, REPLACE_SCREENS="onboarding", **env)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [], env)
            self.assertIn("would delete 06-onboarding.png (an older file", out)
        self.assertEqual(self.fake.file_names(en), held)
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual([c[1] for c in self.fake.writes() if c[0] == "DELETE"], [f"/appScreenshots/{old_id}"])
        self.assertEqual(self.fake.file_names(en), ["01-prayer.png", "02-prayer-weather.png", "02-mosques-map.png",
                                                    "03-ask.png", "04-classics.png", "05-audio.png",
                                                    "07-onboarding.png", "IMG_0001.png"])

    def test_own_failed_leftover_does_not_block_the_replace(self):
        # A replace whose new file was still processing when its run ended, and which App Store Connect then failed:
        # the iPad set of 9 now holds 10, one of them this tool's own FAILED upload of the manifest's file (the same
        # checksum). That screenshot is unusable and is the run's own upload, so the next run must clear it and do
        # the replace (new one ready before the old one goes, never over 10), not call the set full, leave the
        # FAILED screenshot in the store and steer the owner to the remove-then-add gap.
        ios = self.fake.add_version("IOS")
        lid = self.fake.add_locale(ios, "en-US")
        dt = "APP_IPAD_PRO_3GEN_129"
        self.manifest(dt, "IOS", IPAD_NEW, locales=("en-US",))
        ipad = self.old_set(lid, names(IPAD_OLD), dt=dt)
        self.fake.shots[ipad].append({"id": "shot-failed", "attributes": {
            "fileName": "10-onboarding.png", "sourceFileChecksum": self.md5("en-US", "onboarding", dt),
            "assetDeliveryState": {"state": "FAILED"}}})
        old_id = self.fake.shot(ipad, "09-onboarding.png")["id"]
        most, real_api = [len(self.fake.shots[ipad])], self.fake.api

        def counting(method, path, params=None, body=None):
            result = real_api(method, path, params, body)
            most[0] = max(most[0], len(self.fake.shots[ipad]))
            return result

        self.fake.api = counting
        code, out = self.run_tool(DISPLAY_TYPES=dt, REPLACE_SCREENS="onboarding", CONFIRM_DELETE="true")
        self.assertLessEqual(most[0], 10, out)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(ipad), names(IPAD_OLD)[:8] + ["10-onboarding.png"], out)
        new = self.fake.shot(ipad, "10-onboarding.png")
        self.assertNotEqual(new["id"], "shot-failed")
        self.assertEqual(new["attributes"]["assetDeliveryState"]["state"], "COMPLETE")
        writes = self.fake.writes()
        post = next(i for i, c in enumerate(writes) if c[0] == "POST")
        old_delete = next(i for i, c in enumerate(writes) if c == ("DELETE", f"/appScreenshots/{old_id}", None, None))
        self.assertGreater(old_delete, post, "the old onboarding may go only after the new one is uploaded")

    def ipad(self, files: list, locales=("en-US",), order: list = IPAD_NEW) -> tuple:
        """An iOS version whose locales each hold an iPad set of `files` (older checksums); returns (version, {locale: set})."""
        ios = self.fake.add_version("IOS")
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", order, locales=locales)
        return ios, {loc: self.old_set(self.fake.add_locale(ios, loc), files, dt="APP_IPAD_PRO_3GEN_129") for loc in locales}

    def test_own_failed_leftover_is_only_reported_without_confirm(self):
        _, sets = self.ipad(names(IPAD_OLD))
        ipad = sets["en-US"]
        self.fake.shots[ipad].append({"id": "shot-failed", "attributes": {
            "fileName": "10-onboarding.png", "sourceFileChecksum": self.md5("en-US", "onboarding", "APP_IPAD_PRO_3GEN_129"),
            "assetDeliveryState": {"state": "FAILED"}}})
        held = self.fake.file_names(ipad)
        for env in ({}, {"CONFIRM_DELETE": "TRUE"}):
            self.fake.calls.clear()
            code, out = self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", REPLACE_SCREENS="onboarding", **env)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [])
            self.assertIn("would delete 10-onboarding.png (the manifest's own file, failed processing in App Store "
                          "Connect; CONFIRM_DELETE is not \"true\": nothing deleted).", out)
            self.assertIn("would replace 09-onboarding.png with 10-onboarding.png (the file differs", out)
            self.assertNotIn("no room", out)
        self.fake.calls.clear()
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", REPLACE_SCREENS="onboarding",
                                  CONFIRM_DELETE="true", DRY_RUN="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("would delete 10-onboarding.png first (the manifest's own file", out)
        self.assertEqual(self.fake.file_names(ipad), held)

    def test_absent_screen_is_warned_about_only_as_the_manifest_says(self):
        # The manifest says why each set has no onboarding: ar's capture failed, fr's was still loading, de's is
        # simply absent (an older manifest would say nothing), and the iPhone has no onboarding screen at all.
        fr, de = self.fake.add_locale(self.vision, "fr-FR"), self.fake.add_locale(self.vision, "de-DE")
        ios = self.fake.add_version("IOS")
        phone = self.fake.add_locale(ios, "en-US")
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        self.manifest(self.DT, "VISION_OS", VISION_NEW[:-1], locales=("ar-SA", "fr-FR", "de-DE"))
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        self.describe({self.DT: ("vision", VISION_NEW), "APP_IPHONE_67": ("iphone", IPHONE_NEW)},
                      missing=["vision/ar/07-onboarding.png"],
                      failed=[{"platform": "vision", "appLanguage": "ar", "screen": "onboarding", "position": 7,
                               "reason": "screenshot failed after 3 attempts: timed out"},
                              {"platform": "vision", "appLanguage": "fr", "screen": "onboarding", "position": 7,
                               "reason": "still loading when captured"}],
                      skipped=["vision/fr/07-onboarding.png: still loading when captured"])
        sets = {lid: self.old_set(lid, self.HELD) for lid in (self.en, self.ar, fr, de)}
        iphone = self.old_set(phone, names(IPHONE_NEW), dt="APP_IPHONE_67")
        code, out = self.run_tool(DISPLAY_TYPES=f"APP_IPHONE_67 {self.DT}", REPLACE_SCREENS="onboarding",
                                  CONFIRM_DELETE="true")
        self.assertEqual(code, 0, out)
        self.assertIn("::warning::APP_APPLE_VISION_PRO ar-SA: the manifest has no onboarding for this set (its capture "
                      "failed in the screenshots run), so 06-onboarding.png is left as it is.", out)
        self.assertIn("::warning::APP_APPLE_VISION_PRO fr-FR: the manifest has no onboarding for this set (left out in "
                      "the screenshots run: still loading when captured), so 06-onboarding.png is left as it is.", out)
        self.assertIn("::warning::APP_APPLE_VISION_PRO de-DE: the manifest has no onboarding for this set, so "
                      "06-onboarding.png is left as it is.", out)
        self.assertIn("APP_IPHONE_67 en-US: onboarding is not a screen of this set, so nothing is added.", out)
        self.assertNotIn("::warning::APP_IPHONE_67", out)
        self.assertNotIn("timed out", out)   # the capture's reason stays in the manifest, not in the upload log
        for lid in (self.ar, fr, de):
            self.assertEqual(self.fake.file_names(sets[lid]), self.HELD)
        self.assertEqual(self.fake.file_names(iphone), names(IPHONE_NEW))
        self.assertIn("07-onboarding.png", self.fake.file_names(sets[self.en]))

    def test_replace_addition_with_remove_in_a_full_set_says_it_deletes_first(self):
        # The report the owner confirms from (confirm_delete off) says the full set deletes before it uploads, as it
        # does for screens; the confirmed run then does exactly that.
        ios = self.fake.add_version("IOS")
        lid = self.fake.add_locale(ios, "en-US")
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW, locales=("en-US",))
        iphone = self.old_set(lid, names(IPHONE_OLD), dt="APP_IPHONE_67")
        env = {"DISPLAY_TYPES": "APP_IPHONE_67", "REPLACE_SCREENS": "prayer-weather", "REMOVE_SCREENS": "prayer-dark"}
        notice = ("APP_IPHONE_67 en-US: with confirm_delete, the set is full, so 10-prayer-dark.png is deleted before "
                  "02-prayer-weather.png is uploaded; if that upload fails, 10-prayer-dark.png is not brought back.")
        for extra in ({}, {"CONFIRM_DELETE": "true", "DRY_RUN": "true"}):
            self.fake.calls.clear()
            code, out = self.run_tool(**env, **extra)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [])
            self.assertIn(notice, out)
        self.fake.calls.clear()
        code, out = self.run_tool(CONFIRM_DELETE="true", **env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.kinds()[0], ("DELETE", "appScreenshots"))
        self.assertEqual(self.fake.file_names(iphone)[1], "02-prayer-weather.png")
        self.assertNotIn("10-prayer-dark.png", self.fake.file_names(iphone))

    def test_remove_with_replace_only_where_the_manifest_has_the_replace_file(self):
        # ar-SA has no new onboarding in the manifest: its audio is not deleted either, as with screens.
        self.manifest(self.DT, "VISION_OS", VISION_NEW, locales=("en-US",))
        self.manifest(self.DT, "VISION_OS", VISION_NEW[:-1], locales=("ar-SA",))
        ar = self.old_set(self.ar, self.HELD)
        en = self.old_set(self.en, self.HELD)
        for extra in ({}, {"DRY_RUN": "true", "CONFIRM_DELETE": "true"}, {"CONFIRM_DELETE": "true"}):
            code, out = self.run_tool(DISPLAY_TYPES=self.DT, REPLACE_SCREENS="onboarding", REMOVE_SCREENS="audio", **extra)
            self.assertEqual(code, 0, out)
            self.assertIn("::warning::APP_APPLE_VISION_PRO ar-SA: the manifest has no onboarding for this set, so nothing "
                          "would take the place of 05-audio.png; left as it is (nothing deleted, added, replaced or "
                          "reordered).", out)
            self.assertEqual(self.fake.file_names(ar), self.HELD)
        self.assertEqual(self.fake.file_names(en), ["01-prayer.png", "02-prayer-weather.png", "02-mosques-map.png",
                                                    "03-ask.png", "04-classics.png", "07-onboarding.png", "IMG_0001.png"])

    FULL = {"DISPLAY_TYPES": "APP_IPAD_PRO_3GEN_129", "REPLACE_SCREENS": "onboarding", "DELETE_FIRST_IN_FULL_SETS": "true"}

    def test_full_set_deletes_first_only_when_asked_and_only_where_the_manifest_has_the_file(self):
        # Full iPad sets (10, the weather screen in, the old onboarding last). ar-SA's new onboarding is missing.
        ios, sets = self.ipad(names(IPAD_NEW))
        en = sets["en-US"]
        self.manifest("APP_IPAD_PRO_3GEN_129", "IOS", IPAD_NEW[:-1], locales=("ar-SA",))
        ar = self.old_set(self.fake.add_locale(ios, "ar-SA"), names(IPAD_NEW), dt="APP_IPAD_PRO_3GEN_129")
        old_id = self.fake.shot(en, "10-onboarding.png")["id"]
        for extra in ({}, {"CONFIRM_DELETE": "TRUE"}):
            self.fake.calls.clear()
            code, out = self.run_tool(**self.FULL, **extra)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [])
            self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: would replace 10-onboarding.png with 10-onboarding.png (the file "
                          "differs from the manifest's; the set is full, so 10-onboarding.png would be deleted first and "
                          "not brought back if 10-onboarding.png then failed; CONFIRM_DELETE is not \"true\": nothing "
                          "replaced).", out)
        self.fake.calls.clear()
        code, out = self.run_tool(CONFIRM_DELETE="true", DRY_RUN="true", **self.FULL)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("would replace 10-onboarding.png with 10-onboarding.png: the set is full, so 10-onboarding.png is "
                      "deleted first and 10-onboarding.png uploaded; if 10-onboarding.png then fails, 10-onboarding.png "
                      "is not brought back.", out)
        most, real_api = [10], self.fake.api

        def counting(method, path, params=None, body=None):
            result = real_api(method, path, params, body)
            most[0] = max(most[0], *(len(self.fake.shots[s]) for s in (en, ar)))
            return result

        self.fake.api = counting
        self.fake.calls.clear()
        code, out = self.run_tool(CONFIRM_DELETE="true", **self.FULL)
        self.assertEqual(code, 0, out)
        self.assertLessEqual(most[0], 10, out)
        self.assertEqual(self.kinds(), [("DELETE", "appScreenshots"), ("POST", "appScreenshots"), ("PATCH", "appScreenshots")])
        self.assertEqual(self.fake.writes()[0][1], f"/appScreenshots/{old_id}")
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))
        self.assertEqual(self.fake.shot(en, "10-onboarding.png")["attributes"]["sourceFileChecksum"],
                         self.md5("en-US", "onboarding", "APP_IPAD_PRO_3GEN_129"))
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: deleted 10-onboarding.png (the set is full: deleted before uploading "
                      "10-onboarding.png).", out)
        self.assertEqual(self.fake.file_names(ar), names(IPAD_NEW))   # no new file for ar-SA: its old one stays
        self.assertEqual(self.fake.shot(ar, "10-onboarding.png")["attributes"]["sourceFileChecksum"], OLD_SUM)
        self.assertIn("::warning::APP_IPAD_PRO_3GEN_129 ar-SA: the manifest has no onboarding for this set", out)
        # Without the flag the same full set is left, as before.
        self.fake.calls.clear()
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPAD_PRO_3GEN_129", REPLACE_SCREENS="onboarding", CONFIRM_DELETE="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])

    def test_full_set_delete_first_reports_the_loss_when_the_new_one_fails(self):
        _, sets = self.ipad(names(IPAD_NEW))
        en = sets["en-US"]
        self.fake.fail_names = {"10-onboarding.png"}
        code, out = self.run_tool(CONFIRM_DELETE="true", **self.FULL)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW)[:9])   # the old one gone, the failed upload deleted again
        self.assertIn("::error::APP_IPAD_PRO_3GEN_129 en-US: 10-onboarding.png failed processing in App Store Connect; "
                      "10-onboarding.png was deleted to make room and is not brought back; not reordered.", out)
        self.assertIn("1 failed.", out)
        # A later run adds it as a screen that is not there.
        self.fake.fail_names = set()
        code, out = self.run_tool(CONFIRM_DELETE="true", **self.FULL)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))


def generic(prefix: str, count: int) -> list:
    """File names of a set uploaded by hand (ipad_01.jpg ...): no screen name matches them."""
    return [f"{prefix}_{i:02d}.jpg" for i in range(1, count + 1)]


class ReplaceSets(Base):
    """REPLACE_SETS: hand-uploaded sets (generic file names) replaced whole by the manifest's files."""
    DT = "APP_IPAD_PRO_3GEN_129"
    HELD = generic("ipad", 9)
    CONFIRMED = {"DISPLAY_TYPES": DT, "REPLACE_SETS": "true", "CONFIRM_DELETE": "true"}

    def setUp(self):
        super().setUp()
        self.ios = self.fake.add_version("IOS", state="DEVELOPER_REJECTED", version="0.5.0")
        self.ar = self.fake.add_locale(self.ios, "ar-SA")
        self.en = self.fake.add_locale(self.ios, "en-US")

    def by_hand(self, lid: str, files: list = HELD, dt: str = DT) -> str:
        # App Store Connect keeps a checksum for a file uploaded in the browser too; it is not the manifest's.
        return self.fake.add_set(lid, dt, files, checksums={f: OLD_SUM for f in files})

    def md5s(self, locale: str, dt: str = DT) -> list:
        return [e["md5"] for e in sorted((e for e in self.entries if (e["displayType"], e["locale"]) == (dt, locale)),
                                         key=lambda e: e["position"])]

    def sums(self, sid: str) -> list:
        return [s["attributes"].get("sourceFileChecksum") for s in self.fake.shots[sid]]

    def kinds(self) -> list:
        return [(c[0], c[1].split("/")[1]) for c in self.fake.writes() if c[1].startswith("/appScreenshot")]

    def watch_size(self, sids: list) -> list:
        """The most screenshots any of these sets held after any call, so a test can check it never went over 10."""
        most, real_api = [0], self.fake.api

        def counting(method, path, params=None, body=None):
            result = real_api(method, path, params, body)
            most[0] = max(most[0], *(len(self.fake.shots[s]) for s in sids))
            return result

        self.fake.api = counting
        return most

    def test_replaces_a_generic_set_of_nine_with_the_manifest_of_ten(self):
        self.manifest(self.DT, "IOS", IPAD_NEW)
        ar, en = self.by_hand(self.ar), self.by_hand(self.en)
        old_ids = [s["id"] for s in self.fake.shots[ar]]
        most = self.watch_size([ar, en])
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertLessEqual(most[0], 10, out)
        for sid, locale in ((ar, "ar-SA"), (en, "en-US")):
            self.assertEqual(self.fake.file_names(sid), names(IPAD_NEW))
            self.assertEqual(self.sums(sid), self.md5s(locale))
            states = [s["attributes"]["assetDeliveryState"]["state"] for s in self.fake.shots[sid]]
            self.assertEqual(states, ["COMPLETE"] * 10)
        # Per set: every old screenshot deleted, then each file created and committed in position order, then one order.
        per_set = [("DELETE", "appScreenshots")] * 9 + [("POST", "appScreenshots"), ("PATCH", "appScreenshots")] * 10 \
            + [("PATCH", "appScreenshotSets")]
        self.assertEqual(self.kinds(), per_set * 2)
        self.assertEqual([c[1] for c in self.fake.writes() if c[0] == "DELETE"][:9], [f"/appScreenshots/{i}" for i in old_ids])
        posts = [c[3]["data"]["attributes"]["fileName"] for c in self.fake.writes() if c[0] == "POST"]
        self.assertEqual(posts, names(IPAD_NEW) * 2)
        commits = [c[3]["data"]["attributes"]["sourceFileChecksum"] for c in self.fake.writes()
                   if c[0] == "PATCH" and c[1].startswith("/appScreenshots/")]
        self.assertEqual(commits, self.md5s("ar-SA") + self.md5s("en-US"))
        self.assertIn("APP_IPAD_PRO_3GEN_129 ar-SA: replaced: deleted 9, uploaded 10 of 10, ordered.", out)
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: replaced: deleted 9, uploaded 10 of 10, ordered.", out)
        self.assertIn("Done (replace sets): 2 set(s) replaced (18 deleted, 20 uploaded)", out)
        self.assertIn("0 failed.", out)

    def test_without_confirm_makes_no_writes(self):
        self.manifest(self.DT, "IOS", IPAD_NEW)
        ar, en = self.by_hand(self.ar), self.by_hand(self.en)
        for confirm in (None, "false", "TRUE", "True", "yes", "1", " true"):
            self.fake.calls.clear()
            env = {"DISPLAY_TYPES": self.DT, "REPLACE_SETS": "true"}
            if confirm is not None:
                env["CONFIRM_DELETE"] = confirm
            code, out = self.run_tool(**env)
            self.assertEqual(code, 0, out)
            self.assertEqual(self.fake.writes(), [], f"CONFIRM_DELETE={confirm!r}")
            self.assertIn('REPLACE_SETS: CONFIRM_DELETE is not "true", so nothing is deleted, uploaded or ordered', out)
            self.assertIn(f"APP_IPAD_PRO_3GEN_129 en-US: would delete 9 ({' '.join(self.HELD)}) and upload 10 "
                          f"({' '.join(names(IPAD_NEW))}), then order the set; CONFIRM_DELETE is not \"true\": nothing "
                          f"changed.", out)
            self.assertIn("Done (replace sets): 2 set(s) would be replaced (18 would be deleted, 20 would be uploaded)", out)
        self.assertEqual(self.fake.file_names(ar), self.HELD)
        self.assertEqual(self.fake.file_names(en), self.HELD)

    def test_dry_run_makes_no_writes(self):
        # Confirmed, but a dry run: it only reports. ar-SA's onboarding capture is missing: the dry run says the
        # confirmed run would leave that set as it is, and fails, so the owner sees it before anything is deleted.
        self.manifest(self.DT, "IOS", IPAD_NEW, locales=("en-US",))
        self.manifest(self.DT, "IOS", IPAD_NEW[:-1], locales=("ar-SA",))
        self.describe({self.DT: ("ipad", IPAD_NEW)}, missing=["ipad/ar/10-onboarding.png"])
        ar, en = self.by_hand(self.ar), self.by_hand(self.en)
        code, out = self.run_tool(DRY_RUN="true", **self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn(f"APP_IPAD_PRO_3GEN_129 en-US: holds 9: {' '.join(self.HELD)}", out)
        self.assertIn(f"APP_IPAD_PRO_3GEN_129 en-US: would delete 9 ({' '.join(self.HELD)}) and upload 10 "
                      f"({' '.join(names(IPAD_NEW))}), then order the set.", out)
        self.assertIn("::error::APP_IPAD_PRO_3GEN_129 ar-SA: the manifest has no onboarding (missing in the screenshots "
                      "run) for this locale (9 of the 10 screens), so replacing it would leave it shorter; it would be "
                      "left as it is (nothing deleted or uploaded).", out)
        self.assertNotIn("APP_IPAD_PRO_3GEN_129 ar-SA: would delete", out)
        self.assertIn("1 set(s) would be replaced (9 would be deleted, 10 would be uploaded)", out)
        self.assertIn("1 left as they are (would end shorter)", out)
        self.assertIn("(dry run)", out)
        self.assertEqual(self.fake.file_names(ar), self.HELD)
        self.assertEqual(self.fake.file_names(en), self.HELD)
        # With allow_short_sets the dry run says it would replace ar-SA too, with 9.
        code, out = self.run_tool(DRY_RUN="true", ALLOW_SHORT_SETS="true", **self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("::warning::APP_IPAD_PRO_3GEN_129 ar-SA: the manifest has no onboarding (missing in the screenshots "
                      "run) for this locale (9 of the 10 screens); allow_short_sets is on, so the set would get 9 "
                      "screenshot(s).", out)
        self.assertIn("APP_IPAD_PRO_3GEN_129 ar-SA: would delete 9", out)

    def test_set_the_manifest_lacks_a_screen_for_is_left_unless_allowed(self):
        # Confirmed: Apple Vision Pro hu-HU's audio capture was left out as still loading. Its hand-uploaded set of 6
        # stays (it would go to 6 new ones without audio, not the complete 7); en-US is replaced.
        vision = self.fake.add_version("VISION_OS", state="DEVELOPER_REJECTED", version="0.5.0")
        dt = "APP_APPLE_VISION_PRO"
        self.manifest(dt, "VISION_OS", VISION_NEW, locales=("en-US",))
        self.manifest(dt, "VISION_OS", [s for s in VISION_NEW if s != "audio"], locales=("hu-HU",))
        self.describe({dt: ("vision", VISION_NEW)}, skipped=["vision/hu/06-audio.png: still loading"])
        en = self.by_hand(self.fake.add_locale(vision, "en-US"), generic("vision", 6), dt)
        hu = self.by_hand(self.fake.add_locale(vision, "hu-HU"), generic("vision", 6), dt)
        env = {"DISPLAY_TYPES": dt, "REPLACE_SETS": "true", "CONFIRM_DELETE": "true"}
        code, out = self.run_tool(**env)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(hu), generic("vision", 6))
        self.assertFalse([c for c in self.fake.writes() if c[0] == "DELETE" and
                          c[1].rsplit("/", 1)[1] in {s["id"] for s in self.fake.shots[hu]}])
        self.assertEqual(self.fake.file_names(en), names(VISION_NEW))
        self.assertIn("::error::APP_APPLE_VISION_PRO hu-HU: the manifest has no audio (left out in the screenshots run: "
                      "still loading when captured) for this locale (6 of the 7 screens), so replacing it would leave it "
                      "shorter; left as it is (nothing deleted or uploaded). Redo the missing captures first, or, once "
                      "the owner agrees to a shorter set, run with allow_short_sets.", out)
        self.assertIn("1 set(s) replaced (6 deleted, 7 uploaded)", out)
        self.assertIn("1 left as they are (would end shorter), 0 failed.", out)
        # The owner agrees: allow_short_sets replaces it with the 6 the manifest has; en-US is already up to date.
        self.fake.calls.clear()
        code, out = self.run_tool(ALLOW_SHORT_SETS="true", **env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(hu), names([s for s in VISION_NEW if s != "audio"]))
        self.assertIn("allow_short_sets is on, so the set gets 6 screenshot(s).", out)
        self.assertIn("APP_APPLE_VISION_PRO en-US: already up to date", out)

    def test_set_holding_more_than_the_manifest_has_is_left_unless_allowed(self):
        # No displayTypes in the manifest (nothing says a screen is missing), but the Mac set holds 9 by hand and the
        # manifest has 8 for it: replacing would lose one, so it is left unless allow_short_sets.
        mac = self.fake.add_version("MAC_OS", state="DEVELOPER_REJECTED", version="0.5.0")
        self.manifest("APP_DESKTOP", "MAC_OS", MAC_NEW, locales=("en-US",))
        sid = self.by_hand(self.fake.add_locale(mac, "en-US"), generic("mac", 9), "APP_DESKTOP")
        env = {"DISPLAY_TYPES": "APP_DESKTOP", "REPLACE_SETS": "true", "CONFIRM_DELETE": "true"}
        code, out = self.run_tool(**env)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertEqual(self.fake.file_names(sid), generic("mac", 9))
        self.assertIn("::error::APP_DESKTOP en-US: it holds 9 now and the manifest has only 8 for it, so replacing it "
                      "would leave it shorter; left as it is", out)
        code, out = self.run_tool(ALLOW_SHORT_SETS="true", **env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(sid), names(MAC_NEW))
        self.assertIn("::warning::APP_DESKTOP en-US: it holds 9 now and the manifest has only 8 for it; allow_short_sets "
                      "is on, so the set gets 8 screenshot(s).", out)

    def test_second_run_is_idempotent(self):
        self.manifest(self.DT, "IOS", IPAD_NEW)
        ar, en = self.by_hand(self.ar), self.by_hand(self.en)
        env = dict(self.CONFIRMED)
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        ids = {sid: [s["id"] for s in self.fake.shots[sid]] for sid in (ar, en)}
        self.fake.calls.clear()
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("APP_IPAD_PRO_3GEN_129 ar-SA: already up to date (10, the manifest's files in order).", out)
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: already up to date (10, the manifest's files in order).", out)
        self.assertIn("Done (replace sets): 0 set(s) replaced (0 deleted, 0 uploaded), 0 set(s) only reordered, "
                      "2 already up to date", out)
        # App Store Connect may give the checksum in capitals: still the same files.
        for s in self.fake.shots[en]:
            s["attributes"]["sourceFileChecksum"] = s["attributes"]["sourceFileChecksum"].upper()
        self.fake.calls.clear()
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertEqual({sid: [s["id"] for s in self.fake.shots[sid]] for sid in (ar, en)}, ids)
        # The same files in another order are only ordered: nothing deleted or uploaded.
        self.fake.shots[en][0], self.fake.shots[en][1] = self.fake.shots[en][1], self.fake.shots[en][0]
        self.fake.calls.clear()
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.kinds(), [("PATCH", "appScreenshotSets")])
        self.assertEqual([s["id"] for s in self.fake.shots[en]], ids[en])
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: held the manifest's files in another order; ordered (deleted 0, "
                      "uploaded 0).", out)

    def test_upload_failure_is_reported_and_other_sets_go_on(self):
        # ar-SA comes first: its fourth commit is refused. en-US is still replaced; the run exits 1.
        self.manifest(self.DT, "IOS", IPAD_NEW)
        ar, en = self.by_hand(self.ar), self.by_hand(self.en)
        self.fake.fail_commit_in = {ar: {"04-mosques-map.png"}}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(ar), names(IPAD_NEW)[:3])   # short; no half-made screenshot left
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))
        self.assertIn("APP_IPAD_PRO_3GEN_129 ar-SA: deleted 9, uploaded 3 of 10.", out)
        self.assertIn("::error::APP_IPAD_PRO_3GEN_129 ar-SA: uploading 04-mosques-map.png stopped (HTTPError HTTP 409), "
                      "so the 6 file(s) after it were not uploaded; the set now holds 3 of the manifest's 10 and is not "
                      "ordered. A re-run with replace_sets replaces it again (it no longer holds what the manifest says).", out)
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: replaced: deleted 9, uploaded 10 of 10, ordered.", out)
        self.assertNotIn("Stopped:", out)
        self.assertIn("1 failed.", out)
        self.assertFalse([c for c in self.fake.writes() if c[1] == f"/appScreenshotSets/{ar}/relationships/appScreenshots"])
        # Fixed: a re-run replaces the short set again and leaves the other alone.
        self.fake.fail_commit_in = {}
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(ar), names(IPAD_NEW))
        self.assertEqual(self.sums(ar), self.md5s("ar-SA"))
        self.assertIn("APP_IPAD_PRO_3GEN_129 ar-SA: replaced: deleted 3, uploaded 10 of 10, ordered.", out)
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: already up to date", out)

    def test_file_failing_processing_is_deleted_again_and_reported(self):
        self.manifest(self.DT, "IOS", IPAD_NEW)
        ar, en = self.by_hand(self.ar), self.by_hand(self.en)
        self.fake.fail_names = {"05-ask.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        want = [f for f in names(IPAD_NEW) if f != "05-ask.png"]
        self.assertEqual(self.fake.file_names(ar), want)    # both reached; two such sets in a row then stop the run
        self.assertEqual(self.fake.file_names(en), want)
        self.assertIn("::error::APP_IPAD_PRO_3GEN_129 en-US: 05-ask.png failed processing in App Store Connect (deleted "
                      "again); the set now holds 9 of the manifest's 10 and is not ordered.", out)
        self.fake.fail_names = set()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(ar), names(IPAD_NEW))
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))

    def test_still_processing_counts_as_up_to_date_once_ready(self):
        self.manifest(self.DT, "IOS", IPAD_NEW, locales=("en-US",))
        en = self.by_hand(self.en)
        self.fake.slow_names = {"10-onboarding.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))
        self.assertIn("::error::APP_IPAD_PRO_3GEN_129 en-US: 10-onboarding.png still processing; not ordered yet.", out)
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("holds the manifest's files, but 10-onboarding.png is not ready in App Store Connect "
                      "(UPLOAD_COMPLETE); left as it is.", out)
        self.fake.shot(en, "10-onboarding.png")["attributes"]["assetDeliveryState"]["state"] = "COMPLETE"
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-US: already up to date", out)

    def three_locales(self) -> dict:
        de = self.fake.add_locale(self.ios, "de-DE")
        self.manifest(self.DT, "IOS", IPAD_NEW, locales=("ar-SA", "de-DE", "en-US"))
        return {"ar-SA": self.by_hand(self.ar), "de-DE": self.by_hand(de), "en-US": self.by_hand(self.en)}

    def test_two_sets_in_a_row_ending_short_stop_the_run(self):
        # Every fourth commit refused: ar-SA and de-DE end short (3 of 10) and fail; en-US is never deleted.
        sets = self.three_locales()
        self.fake.fail_commit = {"04-mosques-map.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(sets["ar-SA"]), names(IPAD_NEW)[:3])
        self.assertEqual(self.fake.file_names(sets["de-DE"]), names(IPAD_NEW)[:3])
        self.assertEqual(self.fake.file_names(sets["en-US"]), self.HELD)
        self.assertIn("::error::Stopped: two sets ended short or not ready with no fully replaced set between them (a "
                      "delete or an upload stopped with an error, or a file failed or is still processing), so no more "
                      "sets are deleted; sets not reached yet are left as they are.", out)
        self.assertNotIn("APP_IPAD_PRO_3GEN_129 en-US", out)

    def test_two_sets_in_a_row_with_a_file_failing_processing_stop_the_run(self):
        # The same file fails processing in every locale: each set ends with 9 of 10, so two of them stop the run.
        sets = self.three_locales()
        self.fake.fail_names = {"05-ask.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        want = [f for f in names(IPAD_NEW) if f != "05-ask.png"]
        self.assertEqual(self.fake.file_names(sets["ar-SA"]), want)
        self.assertEqual(self.fake.file_names(sets["de-DE"]), want)
        self.assertEqual(self.fake.file_names(sets["en-US"]), self.HELD)
        self.assertIn("::error::Stopped: two sets ended short or not ready", out)

    def test_two_sets_still_processing_in_a_row_stop_the_run(self):
        # A file still processing may fail yet: such sets count too (the files are in, so nothing is lost so far).
        sets = self.three_locales()
        self.fake.slow_names = {"10-onboarding.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(sets["ar-SA"]), names(IPAD_NEW))
        self.assertEqual(self.fake.file_names(sets["de-DE"]), names(IPAD_NEW))
        self.assertEqual(self.fake.file_names(sets["en-US"]), self.HELD)
        self.assertIn("::error::Stopped: two sets ended short or not ready", out)

    def test_a_fully_replaced_set_between_two_short_ones_lets_the_run_go_on(self):
        sets = self.three_locales()
        self.fake.fail_commit_in = {sets["ar-SA"]: {"04-mosques-map.png"}, sets["en-US"]: {"04-mosques-map.png"}}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(sets["ar-SA"]), names(IPAD_NEW)[:3])
        self.assertEqual(self.fake.file_names(sets["de-DE"]), names(IPAD_NEW))
        self.assertEqual(self.fake.file_names(sets["en-US"]), names(IPAD_NEW)[:3])
        self.assertNotIn("Stopped:", out)
        self.assertIn("2 failed.", out)

    def test_set_left_with_none_of_its_files_stops_the_run_at_once(self):
        # Every first commit refused: ar-SA is emptied and gets nothing back, so the run stops before de-DE.
        sets = self.three_locales()
        self.fake.fail_commit = {"01-prayer.png"}
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(sets["ar-SA"]), [])
        self.assertEqual(self.fake.file_names(sets["de-DE"]), self.HELD)
        self.assertEqual(self.fake.file_names(sets["en-US"]), self.HELD)
        self.assertIn("::error::Stopped: APP_IPAD_PRO_3GEN_129 ar-SA ended with none of the manifest's files in it (9 "
                      "deleted, every upload failed), so no more sets are deleted", out)
        self.assertNotIn("APP_IPAD_PRO_3GEN_129 de-DE", out)
        self.assertNotIn("APP_IPAD_PRO_3GEN_129 en-US", out)

    def test_every_file_failing_processing_stops_the_run_at_once(self):
        # E.g. a size App Store Connect does not take for this display type: each upload and commit is accepted,
        # then processing fails. ar-SA is emptied (9 deleted) and gets nothing back; no other set is deleted.
        sets = self.three_locales()
        self.fake.fail_names = set(names(IPAD_NEW))
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.file_names(sets["ar-SA"]), [])
        self.assertEqual(self.fake.file_names(sets["de-DE"]), self.HELD)
        self.assertEqual(self.fake.file_names(sets["en-US"]), self.HELD)
        self.assertIn("::error::Stopped: APP_IPAD_PRO_3GEN_129 ar-SA ended with none of the manifest's files in it", out)
        self.assertIn("Done (replace sets): 0 set(s) replaced (9 deleted, 0 uploaded)", out)
        self.assertIn("1 failed.", out)
        # Fixed: a re-run replaces all three.
        self.fake.fail_names = set()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        for sid in sets.values():
            self.assertEqual(self.fake.file_names(sid), names(IPAD_NEW))

    def test_combined_modes_and_bad_values_are_input_errors(self):
        self.manifest(self.DT, "IOS", IPAD_NEW)
        self.by_hand(self.en)
        combined = [{"SCREENS": "prayer-weather"}, {"REMOVE_SCREENS": "prayer-dark"}, {"REPLACE_SCREENS": "onboarding"}]
        for extra in combined:
            code, out = self.run_tool(**self.CONFIRMED, **extra)
            self.assertEqual(code, 1, extra)
            self.assertIn("::error::REPLACE_SETS replaces whole sets and cannot be combined with SCREENS, REMOVE_SCREENS "
                          "or REPLACE_SCREENS", out)
        for env in ({"REPLACE_SETS": "true", "CONFIRM_DELETE": "true"},                  # no DISPLAY_TYPES
                    {"DISPLAY_TYPES": self.DT, "REPLACE_SETS": "yes", "CONFIRM_DELETE": "true"},
                    {"DISPLAY_TYPES": self.DT, "REPLACE_SETS": "TRUE", "CONFIRM_DELETE": "true"},
                    {**self.CONFIRMED, "DELETE_FIRST_IN_FULL_SETS": "true"},
                    {**self.CONFIRMED, "ALLOW_SHORT_SETS": "yes"},
                    {"DISPLAY_TYPES": self.DT, "ALLOW_SHORT_SETS": "true"},                # not with REPLACE_SETS
                    {"DISPLAY_TYPES": self.DT, "REPLACE_SETS": "false", "ALLOW_SHORT_SETS": "true"}):
            code, out = self.run_tool(**env)
            self.assertEqual(code, 1, env)
            self.assertIn("::error::", out)
        self.assertEqual(self.fake.calls, [])   # stopped before App Store Connect was asked anything

    def test_same_file_as_locale_gets_the_files_of_its_source_locale(self):
        # As finish.py writes it: en-GB has its own entries, with the en-US file and md5 and sameFileAs "en-US".
        gb = self.fake.add_locale(self.ios, "en-GB")
        self.manifest(self.DT, "IOS", IPAD_NEW, locales=("en-US",))
        for e in list(self.entries):
            e["sameFileAs"] = None
            self.entries.append({**e, "locale": "en-GB", "sameFileAs": "en-US"})
        (self.store / "manifest.json").write_text(json.dumps({"screenshots": self.entries}))
        ar, en, en_gb = self.by_hand(self.ar), self.by_hand(self.en), self.by_hand(gb)
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertFalse((self.store / self.DT / "en-GB").exists())
        self.assertEqual(self.fake.file_names(en_gb), names(IPAD_NEW))
        self.assertEqual(self.sums(en_gb), self.md5s("en-US"))
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-GB: replaced: deleted 9, uploaded 10 of 10, ordered.", out)
        # ar-SA has no files in this manifest: its hand-made set stays.
        self.assertEqual(self.fake.file_names(ar), self.HELD)
        self.assertIn("APP_IPAD_PRO_3GEN_129 ar-SA: the manifest has no files for this set; left as it is.", out)
        self.fake.calls.clear()
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("APP_IPAD_PRO_3GEN_129 en-GB: already up to date", out)

    def test_missing_locale_and_set_are_skipped_and_reported(self):
        self.manifest(self.DT, "IOS", IPAD_NEW, locales=("ar-SA", "en-US", "fr-FR"))
        self.fake.add_set(self.ar, "APP_IPHONE_67", names(IPHONE_NEW))   # ar-SA has no iPad set at all
        en = self.by_hand(self.en)
        code, out = self.run_tool(**self.CONFIRMED)
        self.assertEqual(code, 0, out)
        self.assertIn("::warning::IOS: the version has no fr-FR localization; those sets are skipped", out)
        self.assertIn("::warning::APP_IPAD_PRO_3GEN_129 ar-SA: no such set in App Store Connect; skipped", out)
        self.assertIn("2 skipped (not in App Store Connect)", out)
        self.assertFalse([c for c in self.fake.writes() if c[1] == "/appScreenshotSets"])   # never creates a set
        self.assertEqual(len(self.fake.sets[self.ar]), 1)   # only its iPhone set
        self.assertEqual(self.fake.file_names(self.fake.sets[self.ar][0]["id"]), names(IPHONE_NEW))
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))

    def test_ipad_mac_and_vision_in_every_locale(self):
        # The owner's case: iPad, Mac and Apple Vision Pro sets uploaded by hand, the versions rejected (editable).
        mac = self.fake.add_version("MAC_OS", state="DEVELOPER_REJECTED", version="0.5.0")
        vision = self.fake.add_version("VISION_OS", state="DEVELOPER_REJECTED", version="0.5.0")
        self.manifest(self.DT, "IOS", IPAD_NEW)
        self.manifest("APP_DESKTOP", "MAC_OS", MAC_NEW)
        self.manifest("APP_APPLE_VISION_PRO", "VISION_OS", VISION_NEW)
        held = {}
        for lid_ios, locale in ((self.ar, "ar-SA"), (self.en, "en-US")):
            held[(self.DT, locale)] = self.by_hand(lid_ios)
            held[("APP_DESKTOP", locale)] = self.by_hand(self.fake.add_locale(mac, locale), generic("mac", 7), "APP_DESKTOP")
            held[("APP_APPLE_VISION_PRO", locale)] = self.by_hand(self.fake.add_locale(vision, locale), generic("vision", 6),
                                                                  "APP_APPLE_VISION_PRO")
        iphone = self.fake.add_set(self.en, "APP_IPHONE_67", generic("iphone", 10))   # not in DISPLAY_TYPES: untouched
        env = {"DISPLAY_TYPES": f"{self.DT} APP_DESKTOP APP_APPLE_VISION_PRO", "REPLACE_SETS": "true", "CONFIRM_DELETE": "true"}
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        orders = {self.DT: IPAD_NEW, "APP_DESKTOP": MAC_NEW, "APP_APPLE_VISION_PRO": VISION_NEW}
        for (dt, locale), sid in held.items():
            self.assertEqual(self.fake.file_names(sid), names(orders[dt]), (dt, locale))
            self.assertEqual(self.sums(sid), self.md5s(locale, dt), (dt, locale))
        self.assertEqual(self.fake.file_names(iphone), generic("iphone", 10))
        self.assertIn("APP_DESKTOP en-US: replaced: deleted 7, uploaded 8 of 8, ordered.", out)
        self.assertIn("APP_APPLE_VISION_PRO ar-SA: replaced: deleted 6, uploaded 7 of 7, ordered.", out)
        self.assertIn("Done (replace sets): 6 set(s) replaced (44 deleted, 50 uploaded)", out)
        self.fake.calls.clear()
        code, out = self.run_tool(**env)
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("6 already up to date", out)

    def test_platform_in_review_is_reported_and_others_go_on(self):
        self.fake.add_version("MAC_OS", state="WAITING_FOR_REVIEW", version="0.5.0")
        self.manifest(self.DT, "IOS", IPAD_NEW, locales=("en-US",))
        self.manifest("APP_DESKTOP", "MAC_OS", MAC_NEW, locales=("en-US",))
        en = self.by_hand(self.en)
        code, out = self.run_tool(DISPLAY_TYPES=f"{self.DT} APP_DESKTOP", REPLACE_SETS="true", CONFIRM_DELETE="true")
        self.assertEqual(code, 0, out)
        self.assertIn("MAC_OS: no editable version (Prepare for Submission or rejected); the version is in review or "
                      "live: nothing changed.", out)
        self.assertEqual(self.fake.file_names(en), names(IPAD_NEW))


class DefaultMode(Base):
    def test_fills_empty_sets_only(self):
        ios = self.fake.add_version("IOS")
        en = self.fake.add_set(self.fake.add_locale(ios, "en-US"), "APP_IPHONE_67", names(IPHONE_OLD))
        ar = self.fake.add_set(self.fake.add_locale(ios, "ar-SA"), "APP_IPHONE_67", [])
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.file_names(en), names(IPHONE_OLD))
        self.assertEqual(self.fake.file_names(ar), names(IPHONE_NEW))
        self.assertFalse([c for c in self.fake.writes() if c[0] == "DELETE"])
        self.assertIn("APP_IPHONE_67 en-US: already has 10 screenshot(s); left as it is.", out)

    def test_no_editable_version_still_stops(self):
        self.fake.add_version("IOS", state="READY_FOR_SALE")
        self.manifest("APP_IPHONE_67", "IOS", IPHONE_NEW)
        code, out = self.run_tool(DISPLAY_TYPES="APP_IPHONE_67")
        self.assertEqual(code, 1)
        self.assertIn("::error::No editable IOS version", out)


def listing(**locales) -> dict:
    return {"limits": {"name": 30, "subtitle": 30, "promotionalText": 170, "keywords": 100, "description": 4000, "whatsNew": 4000},
            "locales": locales}


EN = {"subtitle": "Prayer times, Qibla, mosques", "promotionalText": "New promo", "keywords": "adhan,qibla",
      "description": "New description with the weather sky.", "whatsNew": "New: Sky follows the weather."}


class Text(Base):
    def setUp(self):
        super().setUp()
        self.fake.add_version("IOS", state="READY_FOR_SALE", version="1.0")
        self.ios = self.fake.add_version("IOS")
        self.en = self.fake.add_locale(self.ios, "en-US", description="Old", keywords="old", promotionalText="Old promo",
                                       whatsNew="Old notes")
        self.ar = self.fake.add_locale(self.ios, "ar-SA", description="قديم", keywords="قديم,كلمات", promotionalText="قديم")
        self.de = self.fake.add_locale(self.ios, "de-DE", description="Alt")

    def text_patches(self) -> list:
        return [c for c in self.fake.writes() if c[1].startswith("/appStoreVersionLocalizations/")]

    def test_fields(self):
        data = listing(**{
            "en-US": {"appLanguage": "en", "IOS": EN},
            "ar-SA": {"appLanguage": "ar", "IOS": {"subtitle": "مواقيت", "description": "وصف جديد", "keywords": "", "promotionalText": "  "}},
            "fr-FR": {"appLanguage": "fr", "IOS": EN},
        })
        code, out = self.run_tool(listing=data, TEXT="true", PLATFORMS="IOS")
        self.assertEqual(code, 0, out)
        for c in self.text_patches():
            attrs = c[3]["data"]["attributes"]
            self.assertFalse({"subtitle", "name"} & set(attrs), attrs)
            self.assertTrue(set(attrs) <= set(asc_upload.TEXT_FIELDS))
        en = self.fake.localization(self.en)["attributes"]
        self.assertEqual({k: en[k] for k in ("description", "keywords", "promotionalText", "whatsNew")},
                         {k: EN[k] for k in ("description", "keywords", "promotionalText", "whatsNew")})
        ar = self.fake.localization(self.ar)["attributes"]
        self.assertEqual(ar["description"], "وصف جديد")
        self.assertEqual(ar["keywords"], "قديم,كلمات")       # empty in the file: left, never blanked
        self.assertEqual(ar["promotionalText"], "قديم")
        self.assertEqual(self.fake.localization(self.de)["attributes"], {"locale": "de-DE", "description": "Alt"})
        self.assertIn("IOS: not in App Store Connect, skipped (add the locale there first): fr-FR", out)
        self.assertIn("IOS: no text in listing.json for de-DE; left as it is.", out)
        # whatsNew goes on its own, after the other fields.
        en_calls = [c[3]["data"]["attributes"] for c in self.text_patches() if c[1].endswith(self.en)]
        self.assertEqual([sorted(a) for a in en_calls], [["description", "keywords", "promotionalText"], ["whatsNew"]])
        self.assertNotIn(EN["description"], out)               # logs name fields, not their text
        # Second run: nothing left to change.
        self.fake.calls.clear()
        code, out = self.run_tool(listing=data, TEXT="true", PLATFORMS="IOS")
        self.assertEqual(self.text_patches(), [])
        self.assertIn("IOS en-US: text already up to date.", out)

    def test_first_version_gets_no_whats_new(self):
        self.fake.versions["IOS"] = [v for v in self.fake.versions["IOS"] if v["id"] == self.ios]
        code, out = self.run_tool(listing=listing(**{"en-US": {"IOS": EN}}), TEXT="true", PLATFORMS="IOS")
        self.assertEqual(code, 0, out)
        sent = [k for c in self.text_patches() for k in c[3]["data"]["attributes"]]
        self.assertNotIn("whatsNew", sent)
        self.assertIn("description", sent)
        self.assertIn("first version", out)
        self.assertEqual(self.fake.localization(self.en)["attributes"]["whatsNew"], "Old notes")

    def test_whats_new_refused_is_skipped(self):
        self.fake.refuse_whats_new = True
        code, out = self.run_tool(listing=listing(**{"en-US": {"IOS": EN}}), TEXT="true", PLATFORMS="IOS")
        self.assertEqual(code, 0, out)
        en = self.fake.localization(self.en)["attributes"]
        self.assertEqual(en["description"], EN["description"])
        self.assertEqual(en["whatsNew"], "Old notes")
        self.assertIn("IOS en-US: App Store Connect refused whatsNew; skipped.", out)
        self.assertIn("1 whatsNew refused", out)

    def test_over_limit_field_is_left(self):
        long = dict(EN, keywords="k" * 101)
        code, out = self.run_tool(listing=listing(**{"en-US": {"IOS": long}}), TEXT="true", PLATFORMS="IOS")
        self.assertEqual(code, 1, out)
        self.assertEqual(self.fake.localization(self.en)["attributes"]["keywords"], "old")
        self.assertIn("keywords is 101 characters (limit 100)", out)

    def test_dry_run_and_platform_in_review(self):
        self.fake.add_version("MAC_OS", state="IN_REVIEW")
        data = listing(**{"en-US": {"IOS": EN, "MAC_OS": EN}})
        code, out = self.run_tool(listing=data, TEXT="true", PLATFORMS="MAC_OS IOS", DRY_RUN="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn("MAC_OS: no editable version (Prepare for Submission or rejected); the version is in review or live: nothing changed.", out)
        self.assertIn("IOS en-US: would update description, keywords, promotionalText, whatsNew.", out)

    def test_text_only_needs_no_screenshots(self):
        # No DISPLAY_TYPES and no store folder: text mode alone.
        code, out = self.run_tool(listing=listing(**{"en-US": {"IOS": EN}}), TEXT="true", PLATFORMS="IOS")
        self.assertEqual(code, 0, out)
        self.assertFalse(self.store.exists())
        self.assertFalse([c for c in self.fake.calls if "Screenshot" in c[1]])


OLD_POLICY = "Near Mosque has no account, no ads and no tracking. The app goes online only for mosque search, maps, downloads and links."
NEW_POLICY = ("Near Mosque has no account, no ads, no analytics and no tracking. On iPhone, iPad, Mac and Apple Vision Pro, "
              "the optional \"Sky follows the weather\" is off until you turn it on. Full privacy policy: "
              "https://nearmosque.net/privacy.html")
AR_POLICY = "لا حساب في «أقرب مسجد» ولا إعلانات ولا تحليلات ولا تتبّع. النص الكامل لسياسة الخصوصية: https://nearmosque.net/privacy.html"
TV = {"subtitle": "Prayer times, Qibla, mosques", "promotionalText": "TV promo", "keywords": "adhan,qibla",
      "description": "The Apple TV description, without the weather.", "whatsNew": "TV notes"}


class PrivacyText(Base):
    """TEXT with TV_OS also sets appInfoLocalizations.privacyPolicyText (App Information → Privacy Policy Text)."""

    def setUp(self):
        super().setUp()
        tv = self.fake.add_version("TV_OS")
        self.tv_en = self.fake.add_locale(tv, "en-US", description="Old TV")
        # The live app information comes first, as App Store Connect may list it; it must never be touched.
        self.live = self.fake.add_info(state="READY_FOR_DISTRIBUTION", appStoreState="READY_FOR_SALE")
        self.live_en = self.fake.add_info_locale(self.live, "en-US", name="Near Mosque", privacyPolicyText=OLD_POLICY)
        self.info = self.fake.add_info(state="PREPARE_FOR_SUBMISSION", appStoreState="PREPARE_FOR_SUBMISSION")
        self.info_en = self.fake.add_info_locale(
            self.info, "en-US", name="Near Mosque", subtitle="Prayer times, Qibla, mosques",
            privacyPolicyUrl="https://nearmosque.net/privacy.html", privacyPolicyText=OLD_POLICY)
        self.info_ar = self.fake.add_info_locale(self.info, "ar-SA", name="أقرب مسجد", privacyPolicyText=AR_POLICY)
        self.info_de = self.fake.add_info_locale(self.info, "de-DE", name="Near Mosque", privacyPolicyText="Alt")
        self.data = listing(**{
            "en-US": {"appLanguage": "en", "TV_OS": TV, "APP_INFO": {"privacyPolicyText": NEW_POLICY}},
            "ar-SA": {"appLanguage": "ar", "TV_OS": TV, "APP_INFO": {"privacyPolicyText": AR_POLICY}},
            "fr-FR": {"appLanguage": "fr", "TV_OS": TV, "APP_INFO": {"privacyPolicyText": NEW_POLICY}},
        })

    def privacy_patches(self) -> list:
        return [c for c in self.fake.writes() if c[1].startswith("/appInfoLocalizations/")]

    def test_updated_only_where_it_differs(self):
        code, out = self.run_tool(listing=self.data, TEXT="true", PLATFORMS="TV_OS")
        self.assertEqual(code, 0, out)
        patches = self.privacy_patches()
        self.assertEqual([c[1] for c in patches], [f"/appInfoLocalizations/{self.info_en}"])
        self.assertEqual(patches[0][3]["data"]["attributes"], {"privacyPolicyText": NEW_POLICY})
        en = self.fake.info_localization(self.info_en)["attributes"]
        self.assertEqual(en, {"locale": "en-US", "name": "Near Mosque", "subtitle": "Prayer times, Qibla, mosques",
                              "privacyPolicyUrl": "https://nearmosque.net/privacy.html", "privacyPolicyText": NEW_POLICY})
        self.assertEqual(self.fake.info_localization(self.info_ar)["attributes"]["privacyPolicyText"], AR_POLICY)
        self.assertEqual(self.fake.info_localization(self.info_de)["attributes"]["privacyPolicyText"], "Alt")
        self.assertEqual(self.fake.info_localization(self.live_en)["attributes"]["privacyPolicyText"], OLD_POLICY)
        self.assertIn(f"App information en-US: updated privacyPolicyText ({len(OLD_POLICY)} -> {len(NEW_POLICY)} characters).", out)
        self.assertIn("App information ar-SA: privacy text already up to date.", out)
        self.assertIn("App information: not in App Store Connect, skipped (add the locale there first): fr-FR", out)
        self.assertIn("App information: no privacy text in listing.json for de-DE; left as it is.", out)
        self.assertNotIn(NEW_POLICY, out)                      # locales and lengths, never the text
        self.assertNotIn("Sky follows", out)
        # The TV version's own text is still updated as before.
        self.assertEqual(self.fake.localization(self.tv_en)["attributes"]["description"], TV["description"])
        # Second run: nothing left to change.
        self.fake.calls.clear()
        code, out = self.run_tool(listing=self.data, TEXT="true", PLATFORMS="TV_OS")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.privacy_patches(), [])
        self.assertIn("App information en-US: privacy text already up to date.", out)

    def test_not_editable_app_information_is_reported_and_text_goes_on(self):
        for states in ({"state": "WAITING_FOR_REVIEW"}, {"appStoreState": "IN_REVIEW"},
                       {"state": "READY_FOR_REVIEW"}, {"state": "PREPARE_FOR_SUBMISSION", "appStoreState": "IN_REVIEW"}):
            self.fake.calls.clear()
            self.fake.infos[1]["attributes"] = dict(states)
            code, out = self.run_tool(listing=self.data, TEXT="true", PLATFORMS="TV_OS")
            self.assertEqual(code, 0, out)
            self.assertEqual(self.privacy_patches(), [], states)
            self.assertIn("app information is in review or live: privacy text not changed", out)
            self.assertFalse([c for c in self.fake.calls if "appInfoLocalizations" in c[1]], states)
        self.assertEqual(self.fake.info_localization(self.info_en)["attributes"]["privacyPolicyText"], OLD_POLICY)
        # The editable TV version's text was still updated.
        self.assertEqual(self.fake.localization(self.tv_en)["attributes"]["description"], TV["description"])

    def test_older_attribute_alone_counts(self):
        self.fake.infos[1]["attributes"] = {"appStoreState": "PREPARE_FOR_SUBMISSION"}
        code, out = self.run_tool(listing=self.data, TEXT="true", PLATFORMS="TV_OS")
        self.assertEqual(code, 0, out)
        self.assertEqual(len(self.privacy_patches()), 1)

    def test_dry_run_makes_no_writes(self):
        code, out = self.run_tool(listing=self.data, TEXT="true", PLATFORMS="TV_OS", DRY_RUN="true")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.writes(), [])
        self.assertIn(f"App information en-US: would update privacyPolicyText ({len(OLD_POLICY)} -> {len(NEW_POLICY)} characters).", out)
        self.assertIn("Privacy text done: 1 locale(s) would be updated, 0 problem(s). (dry run)", out)
        self.assertNotIn(NEW_POLICY, out)
        self.assertEqual(self.fake.info_localization(self.info_en)["attributes"]["privacyPolicyText"], OLD_POLICY)

    def test_only_when_tv_os_is_in_platforms(self):
        ios = self.fake.add_version("IOS")
        self.fake.add_locale(ios, "en-US", description="Old")
        data = copy.deepcopy(self.data)
        data["locales"]["en-US"]["IOS"] = EN
        for platforms in ("IOS", "IOS MAC_OS VISION_OS"):
            self.fake.calls.clear()
            code, out = self.run_tool(listing=data, TEXT="true", PLATFORMS=platforms)
            self.assertEqual(code, 0, out)
            self.assertFalse([c for c in self.fake.calls if "appInfo" in c[1]], platforms)
            self.assertNotIn("privacy text", out.lower())
        self.assertEqual(self.fake.info_localization(self.info_en)["attributes"]["privacyPolicyText"], OLD_POLICY)

    def test_over_limit_is_left(self):
        data = copy.deepcopy(self.data)
        data["limits"]["privacyPolicyText"] = 4000
        data["locales"]["en-US"]["APP_INFO"]["privacyPolicyText"] = "p" * 4001
        code, out = self.run_tool(listing=data, TEXT="true", PLATFORMS="TV_OS")
        self.assertEqual(code, 1, out)
        self.assertEqual(self.privacy_patches(), [])
        self.assertIn("App information en-US: privacyPolicyText is 4001 characters (limit 4000); left as it is.", out)


if __name__ == "__main__":
    unittest.main(verbosity=2)
