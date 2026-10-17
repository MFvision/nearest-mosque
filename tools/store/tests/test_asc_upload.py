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
ENV_KEYS = ("DISPLAY_TYPES", "SCREENS", "REMOVE_SCREENS", "CONFIRM_DELETE", "TEXT", "PLATFORMS", "DRY_RUN")
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

    def add_set(self, lid: str, display_type: str, files: list, states: dict | None = None) -> str:
        sid = self.nid("set")
        self.sets[lid].append({"id": sid, "attributes": {"screenshotDisplayType": display_type}})
        self.shots[sid] = [{"id": self.nid("shot"), "attributes": {
            "fileName": f, "assetDeliveryState": {"state": (states or {}).get(f, "COMPLETE")}}} for f in files]
        return sid

    def file_names(self, sid: str) -> list:
        return [s["attributes"]["fileName"] for s in self.shots[sid]]

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
            state = "FAILED" if name in self.fail_names else "COMPLETE"
            shot = {"id": self.nid("shot"), "attributes": {"fileName": name, "assetDeliveryState": {"state": state}}}
            self.shots[sid].append(shot)
            return {"data": {"id": shot["id"], "attributes": {"uploadOperations": []}}}
        if method == "PATCH" and p[0] == "appScreenshots":
            shot = next(s for ss in self.shots.values() for s in ss if s["id"] == p[1])
            if shot["attributes"]["fileName"] in self.fail_commit:
                raise urllib.error.HTTPError(path, 409, "Conflict", {}, io.BytesIO(b'{"errors":[]}'))
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

    def run_tool(self, listing: dict | None = None, **env) -> tuple[int, str]:
        clean = {k: v for k, v in os.environ.items() if k not in ENV_KEYS}
        clean.update({"BUNDLE_ID": "sa.zood.nearmosque", "ASC_PRIVATE_KEY": SECRET, **env})
        out = io.StringIO()
        patches = [mock.patch.dict(os.environ, clean, clear=True),
                   mock.patch.object(asc_upload, "api", self.fake.api),
                   mock.patch.object(asc_upload.time, "sleep", lambda s: None),
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
