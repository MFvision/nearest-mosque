#!/usr/bin/env python3
"""Uploads finished App Store screenshots and the listing text to App Store Connect (CI, App Store Connect API).

    asc_upload.py <store folder>        # the folder finish.py wrote (manifest.json and APP_* folders)

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY (.p8 contents), BUNDLE_ID,
DRY_RUN ("true": report what would change and change nothing, in every mode), and:

  DISPLAY_TYPES   Screenshot sets, space-separated (e.g. "APP_WATCH_ULTRA"). Needed for any screenshot mode, so a
                  run never touches more sets than asked; may be empty when only TEXT is on.
                  On its own (the default mode) it only fills EMPTY sets: a set that already holds screenshots is
                  left as it is and reported, so nothing already in App Store Connect is replaced or deleted.
  SCREENS         Screen names, space-separated (e.g. "prayer-weather"). Adds the manifest's files of those screens
                  to sets that already hold screenshots, where no screenshot there is that screen yet (so a second
                  run adds nothing), then orders the set as the manifest does; screenshots the manifest does not
                  know keep their order after the ones it knows. A set that would go over 10 is skipped and
                  reported. A missing or empty set is left to the default mode. Only a screenshot App Store Connect
                  finished processing (COMPLETE) counts as there: one of those screens in any other state (failed,
                  never uploaded, still processing) is reported as an error and its set is left as it is, so remove
                  it with REMOVE_SCREENS and CONFIRM_DELETE first. A new screenshot of this run that fails, or that
                  could not be uploaded and committed, is deleted again at once (it is the run's own upload).
  REMOVE_SCREENS  Screen names, space-separated (e.g. "prayer-dark"). Deletes those screens' screenshots from the
                  sets being processed, only when CONFIRM_DELETE is exactly "true"; otherwise it reports what it
                  would delete and deletes nothing.
                  With SCREENS or REPLACE_SCREENS, only in a set the manifest has every SCREENS and REPLACE_SCREENS
                  file for (that displayType and locale) and whose insert is not skipped (a set that would go over
                  10, or that holds one of those screens unfinished, is left whole); a set the manifest lacks one of
                  them for is reported and left exactly as it is (nothing deleted, added, replaced or reordered). On
                  its own it checks no manifest: it deletes in every set processed. A set with room takes the new
                  screenshots (SCREENS, and REPLACE_SCREENS additions) first and deletes only once every one of them
                  is ready (COMPLETE); if one fails, nothing is deleted there. A full set has to delete first to make
                  room: if the new screenshot then fails processing or cannot be uploaded, the deleted one is not
                  brought back (the run reports it, and an upload error stops the run). Reports and dry runs say
                  which of the two each set does.
                  A deleted screenshot cannot be brought back. Screenshots it does not recognise are never deleted
                  (only REPLACE_SETS, below, deletes whole sets).
  REPLACE_SCREENS Screen names, space-separated (e.g. "onboarding"): the screens whose picture changed. Only with
                  CONFIRM_DELETE exactly "true"; otherwise it changes nothing and reports what it would replace or
                  add. In each set being processed, for each of those screens the manifest has a file for (that
                  displayType and locale; a set it has none for is left as it is for that screen, with a warning
                  when the manifest lists it under "failed", "missing" or "skipped" for that device and language,
                  and a plain note when it is not a screen of that display type at all):
                  * a screenshot of that screen there whose sourceFileChecksum differs from the manifest's md5: the
                    new file is uploaded, and only once it is ready (COMPLETE) is the old one deleted; if the new one
                    fails or is not ready, the old one is kept (the failed upload, the run's own, is deleted again),
                    the set is not reordered and nothing else changes in it. That needs room for one more: a set
                    that already holds 10 is reported and left (nothing deleted), unless DELETE_FIRST_IN_FULL_SETS
                    is exactly "true": then, there only, the old one is deleted first and the new one uploaded, and
                    if the new one fails or cannot be uploaded the old one is not brought back (reported as failed).
                  * the same checksum and ready: nothing to do. An older file of that screen next to it (left by a
                    replace whose old one was never deleted) is deleted. The same checksum but still processing:
                    reported. The same checksum and FAILED (a replace's upload that was still processing when its run
                    ended, then failed): unusable, so it is deleted first and does not count toward the room.
                  * no screenshot of that screen: added as SCREENS adds it (a set that would go over 10 is skipped).
                  Replacements go first, one screen at a time, then SCREENS and REMOVE_SCREENS as above; then the set
                  is ordered as the manifest does (screenshots it does not know keep their order after). A screen
                  may be named in only one of SCREENS, REMOVE_SCREENS and REPLACE_SCREENS. The report without
                  CONFIRM_DELETE and the dry run say which sets delete first.
  DELETE_FIRST_IN_FULL_SETS  "true" (exactly): with REPLACE_SCREENS and CONFIRM_DELETE, a set that holds 10 deletes
                  the old screenshot of a changed screen before uploading the new one, as described above.
  REPLACE_SETS    "true" (exactly; "false" or empty is off, anything else an input error): replaces whole sets, for
                  sets uploaded by hand under file names no screen name matches (ipad_01.jpg). Only with
                  CONFIRM_DELETE exactly "true"; otherwise it reports what it would delete and upload and changes
                  nothing. For each set in DISPLAY_TYPES in each locale of the platform's editable version that the
                  manifest has at least one file for (a sameFileAs locale has its own entries, with the files of its
                  source locale, as the default mode uses them):
                  * it already holds exactly the manifest's files in position order (as many, each sourceFileChecksum
                    equal to the md5 in any case, all COMPLETE): "already up to date", nothing changes. The same
                    files in another order: only ordered. The same files with one still processing: reported, left.
                  * the manifest lacks a screen of the display type's order for that locale (as "missing", "failed" or
                    "skipped" say), or has fewer files for it than the set holds now: the set would end shorter, so it
                    is left as it is (nothing deleted or uploaded) and reported as an error naming the missing screens,
                    and the run exits non-zero; unless ALLOW_SHORT_SETS is exactly "true": then a warning, and the
                    set is replaced as below.
                  * otherwise every screenshot in it is deleted, recognised or not, then the manifest's files are
                    uploaded in position order, each committed with its md5, and once all are COMPLETE the set is
                    ordered. A deleted screenshot cannot be brought back.
                  A set whose delete or upload stops with an error, whose file fails processing (deleted again: the
                  run's own upload) or whose file is still processing when the wait ends is reported: it may now be
                  short, and a re-run replaces it again, as it no longer holds what the manifest says (files only
                  still processing count as up to date once they are ready). The other sets go on and the run exits
                  non-zero, but two such sets with no fully replaced set between them stop the run, and a set that
                  ends with none of the manifest's files in it (every upload failed) stops it at once, so a broken
                  upload, or files App Store Connect does not process for that display type, never empties every set
                  it reaches. A manifest set of more than 10 files is reported and left. A locale or a set missing in
                  App Store Connect is skipped and reported (the default mode creates a missing set). It cannot be
                  combined with SCREENS, REMOVE_SCREENS or REPLACE_SCREENS. Reports give per set how many are
                  deleted and uploaded.
  ALLOW_SHORT_SETS "true" (exactly; "false" or empty is off, anything else an input error): with REPLACE_SETS only.
                  Replaces a set even where the manifest lacks screens for it or has fewer files than it holds, so
                  it ends shorter (a warning names the screens). Only once the owner has agreed to the shorter sets.
  TEXT            "true": updates description, keywords and promotionalText of each locale of the editable version
                  of each platform in PLATFORMS ("IOS MAC_OS VISION_OS TV_OS") from shared/store/listing.json, and
                  whatsNew unless it is the app's first version on that platform (if App Store Connect refuses it,
                  it is skipped and reported). Never the name or the subtitle; a field the file does not have is left
                  as it is, never blanked; locales missing in App Store Connect are skipped and reported.
                  With TV_OS in PLATFORMS it also updates the app information's Privacy Policy Text (shown on Apple
                  TV; appInfoLocalizations.privacyPolicyText) from each locale's APP_INFO in listing.json, only
                  where it differs and only on an editable app information (Prepare for Submission or rejected);
                  otherwise it reports "app information is in review or live: privacy text not changed" and goes
                  on. Never the name, the subtitle or the privacy policy URL. A dry run shows locales and lengths.

A screenshot is recognised by its fileName: "<anything>-<screen>.png", the longest known screen name winning, so
"09-widgets-qibla.png" is widgets-qibla and never qibla. Only REPLACE_SETS deletes a screenshot it does not recognise
(it replaces the whole set). Screenshot modes use the editable version of each platform (Prepare for Submission or
rejected). In the SCREENS, REMOVE_SCREENS, REPLACE_SCREENS, REPLACE_SETS and TEXT modes a
platform without one is reported ("in review or live: nothing changed") and the others go on. Logs show locales, counts and the
screenshots' file names only. Needs PyJWT and cryptography. Offline test: tools/store/tests/test_asc_upload.py.
"""
import hashlib
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import jwt

BASE = "https://api.appstoreconnect.apple.com/v1"
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}
HERE = pathlib.Path(__file__).resolve().parent
LISTING = HERE.parents[1] / "shared" / "store" / "listing.json"
MAX_PER_SET = 10
TEXT_PLATFORMS = ("IOS", "MAC_OS", "VISION_OS", "TV_OS")
# The appStoreVersionLocalizations fields TEXT may change. Not subtitle (appInfoLocalizations) and not the name.
TEXT_FIELDS = ("description", "keywords", "promotionalText", "whatsNew")
# With TV_OS in PLATFORMS, TEXT also sets this one appInfoLocalizations field (Privacy Policy Text, shown on Apple
# TV) from each locale's APP_INFO in listing.json. Never the name, the subtitle or the privacy policy URL.
APP_INFO = "APP_INFO"
PRIVACY_FIELD = "privacyPolicyText"


def token() -> str:
    now = int(time.time())
    payload = {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 900, "aud": "appstoreconnect-v1"}
    return jwt.encode(payload, os.environ["ASC_PRIVATE_KEY"], algorithm="ES256", headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"})


def api(method: str, path: str, params: dict | None = None, body: dict | None = None) -> dict:
    url = (path if path.startswith("https://") else BASE + path) + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Authorization": "Bearer " + token(), "Content-Type": "application/json"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                text = r.read()
                return json.loads(text) if text else {}
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503) and attempt < 3:
                time.sleep(5 * (attempt + 1))
                continue
            # Only Apple's codes and titles (the detail can echo back data that was sent).
            try:
                errors = json.loads(e.read() or b"{}").get("errors", [])
                summary = "; ".join(f"{x.get('code', '')}: {x.get('title', '')}" for x in errors)
            except ValueError:
                summary = ""
            print(f"App Store Connect API {method} {path.split('?')[0]}: HTTP {e.code} {summary}", flush=True)
            raise
    raise RuntimeError("unreachable")


def all_pages(path: str, params: dict | None = None) -> list:
    out, page = [], api("GET", path, params)
    out += page["data"]
    while page.get("links", {}).get("next"):
        page = api("GET", page["links"]["next"])
        out += page["data"]
    return out


def discard(shot_id: str, name: str, label: str) -> bool:
    """Deletes a screenshot this run created moments ago and that is not usable (never committed, or failed)."""
    try:
        api("DELETE", f"/appScreenshots/{shot_id}")
    except (urllib.error.URLError, OSError):
        print(f"::error::{label}: could not delete {name} again (this run's own upload, not usable); remove it with "
              f"remove_screens and confirm_delete before adding it again.", flush=True)
        return False
    print(f"{label}: deleted {name} again (this run's own upload, not usable).", flush=True)
    return True


def upload_file(set_id: str, path: pathlib.Path, md5: str, label: str = "") -> str:
    data = path.read_bytes()
    shot = api("POST", "/appScreenshots", body={"data": {
        "type": "appScreenshots",
        "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": set_id}}},
    }})["data"]
    try:
        for op in shot["attributes"]["uploadOperations"]:
            part = data[op["offset"]: op["offset"] + op["length"]]
            headers = {h["name"]: h["value"] for h in op.get("requestHeaders", [])}
            req = urllib.request.Request(op["url"], data=part, method=op["method"], headers=headers)
            with urllib.request.urlopen(req, timeout=300):
                pass
        api("PATCH", f"/appScreenshots/{shot['id']}", body={"data": {
            "type": "appScreenshots", "id": shot["id"], "attributes": {"uploaded": True, "sourceFileChecksum": md5}}})
    except Exception:
        # Created but not uploaded and committed: delete it, so a half-made screenshot never counts as there.
        discard(shot["id"], path.name, label or set_id)
        raise
    return shot["id"]


def state_of(shot: dict) -> str | None:
    return (shot["attributes"].get("assetDeliveryState") or {}).get("state")


def wait_states(ids: list[str], minutes: int = 15) -> dict:
    """The last processing state of each screenshot, once each is COMPLETE or FAILED or the time is up."""
    deadline = time.time() + minutes * 60
    states = {i: None for i in ids}
    pending = set(ids)
    while pending and time.time() < deadline:
        for i in list(pending):
            states[i] = state_of(api("GET", f"/appScreenshots/{i}")["data"])
            if states[i] in ("COMPLETE", "FAILED"):
                pending.discard(i)
        if pending:
            time.sleep(10)
    return states


def wait_complete(ids: list[str], minutes: int = 15) -> tuple[int, int]:
    states = list(wait_states(ids, minutes).values())
    return states.count("COMPLETE"), states.count("FAILED")


def order_set(set_id: str, ids: list[str]) -> None:
    """Sets a screenshot set's order; `ids` must name every screenshot it holds."""
    api("PATCH", f"/appScreenshotSets/{set_id}/relationships/appScreenshots",
        body={"data": [{"type": "appScreenshots", "id": i} for i in ids]})


# ---- Helpers shared by the modes ----

def find_app() -> dict:
    return api("GET", "/apps", {"filter[bundleId]": os.environ["BUNDLE_ID"]})["data"][0]


def versions_of(app: dict, platform: str) -> tuple[dict | None, list]:
    """The platform's editable version (or None) and all its versions."""
    versions = all_pages(f"/apps/{app['id']}/appStoreVersions", {"filter[platform]": platform, "limit": "50"})
    editable = [v for v in versions if (v["attributes"].get("appVersionState") or v["attributes"].get("appStoreState")) in EDITABLE]
    return (editable[0] if editable else None), versions


def not_editable(platform: str, versions: list) -> None:
    if not versions:
        print(f"::warning::{platform}: no version in App Store Connect: nothing changed.", flush=True)
        return
    states = ", ".join(f"{v['attributes'].get('versionString')} {v['attributes'].get('appVersionState') or v['attributes'].get('appStoreState')}"
                       for v in versions[:3])
    print(f"::warning::{platform}: no editable version (Prepare for Submission or rejected); "
          f"the version is in review or live: nothing changed. Versions: {states}.", flush=True)


def screen_of_entry(e: dict) -> str:
    # Manifests have "screen"; for an older one, the file name without its number: "03-qibla.png" -> "qibla".
    return e.get("screen") or pathlib.Path(e["file"]).stem.split("-", 1)[-1]


def known_screens(manifest: dict, extra: set) -> set:
    names = set(extra) | {screen_of_entry(e) for e in manifest.get("screenshots", [])}
    try:
        names |= set(json.loads((HERE / "screens.json").read_text())["screens"])
    except (OSError, ValueError, KeyError):
        pass
    return names


def screen_of(file_name: str | None, names: set) -> str | None:
    """The screen a screenshot in App Store Connect shows, from its fileName ("<anything>-<screen>.png").
    The longest matching name wins ("09-widgets-qibla.png" is widgets-qibla, not qibla). None: not recognised."""
    if not file_name:
        return None
    best = None
    for n in names:
        if file_name.endswith("-" + n + ".png") and (best is None or len(n) > len(best)):
            best = n
    return best


def file_name(shot: dict) -> str:
    return shot["attributes"].get("fileName") or "?"


def checksum_of(shot: dict) -> str:
    # The MD5 the screenshot's upload was committed with (sourceFileChecksum); empty when it never was.
    return (shot["attributes"].get("sourceFileChecksum") or "").lower()


def stand_in(e: dict) -> dict:
    """A screenshot this run would upload, for working out the order before it exists."""
    return {"id": "new", "attributes": {"fileName": pathlib.Path(e["file"]).name}}


def why_absent(manifest: dict, dt: str, files: list, screen: str) -> tuple[bool, str]:
    """Why the manifest has no file of `screen` for a set (its displayType `dt`, its manifest entries `files`), as
    the manifest itself says: (worth a warning, the reason; "" when the manifest does not say)."""
    screens = ((manifest.get("displayTypes") or {}).get(dt) or {}).get("screens")
    if screens is not None and screen not in screens:
        return False, "not a screen of this set"
    platform = next((e["platform"] for e in files if e.get("platform")), None)
    lang = next((e["appLanguage"] for e in files if e.get("appLanguage")), None)
    if platform and lang:
        name = re.compile(rf"{re.escape(platform)}/{re.escape(lang)}/\d{{2}}-{re.escape(screen)}\.png")
        if any(name.fullmatch(str(k).split(":", 1)[0]) for k in manifest.get("skipped") or []):
            return True, "left out in the screenshots run: still loading when captured"
        if any((f.get("platform"), f.get("appLanguage"), f.get("screen")) == (platform, lang, screen)
               for f in manifest.get("failed") or []):
            return True, "its capture failed in the screenshots run"
        if any(name.fullmatch(str(m)) for m in manifest.get("missing") or []):
            return True, "missing in the screenshots run"
    return True, ""


def plan_replace(label: str, manifest: dict, dt: str, files: list, order: list, shots: list, names: set,
                 replace: set, n: dict) -> tuple:
    """What REPLACE_SCREENS does in one set: (swaps [(manifest entry, its screen's screenshots there)], additions
    [manifest entry], stale [screenshot], own_failed [screenshot]). Reports each screen it leaves as it is, and why.
    own_failed: a screenshot of the manifest's own file (the same checksum) that failed processing, such as this
    tool's upload that was still processing when its run ended. It is unusable, so it is deleted first (with
    CONFIRM_DELETE) and does not count toward the room a swap needs."""
    swaps, additions, stale, own_failed = [], [], [], []
    for screen in [s for s in order if s in replace] + sorted(replace - set(order)):
        e = next((f for f in files if screen_of_entry(f) == screen), None)
        olds = [s for s in shots if screen_of(file_name(s), names) == screen]
        if e is None:
            # Nothing to put in. Say why only as the manifest says it: a failed or missing capture is worth a
            # warning; a screen that is not in this set's order (no onboarding on the iPhone) is not.
            warn, why = why_absent(manifest, dt, files, screen)
            rest = f"{' '.join(file_name(s) for s in olds)} is left as it is." if olds else "nothing is added."
            if warn:
                print(f"::warning::{label}: the manifest has no {screen} for this set" + (f" ({why})" if why else "")
                      + ", so " + rest, flush=True)
            else:
                print(f"{label}: {screen} is {why}, so {rest}")
            continue
        same = [s for s in olds if checksum_of(s) == e["md5"].lower()]
        failed = [s for s in same if state_of(s) == "FAILED"]
        own_failed += failed
        olds = [s for s in olds if s not in failed]
        same = [s for s in same if s not in failed]
        ready = [s for s in same if state_of(s) == "COMPLETE"]
        busy = [s for s in same if s not in ready]
        if ready:
            older = [s for s in olds if s not in same]
            if older:
                stale += older   # the old one of a replace that stopped before deleting it
            else:
                print(f"{label}: {file_name(ready[0])} already is the manifest's {screen} file; nothing to replace.")
            continue
        if busy:
            print(f"::error::{label}: {' '.join(file_name(s) for s in busy)} is the manifest's {screen} file but not ready "
                  f"in App Store Connect ({state_of(busy[0]) or 'no state'}); {screen} left as it is. If it is still "
                  f"processing, wait and run again.", flush=True)
            n["failed"] += len(busy)
            continue
        if olds:
            swaps.append((e, olds))
        else:
            additions.append(e)   # none there, or only a failed copy of the manifest's file (deleted first)
    return swaps, additions, stale, own_failed


def in_order(shots: list, order: list, names: set) -> list:
    """The screenshots in the manifest's order for the screens it knows (stable for duplicates); the ones it does
    not know (unrecognised, or a screen not in this set's manifest order) keep their relative order after them."""
    rank = {s: i for i, s in enumerate(order)}
    known = sorted(((rank[screen_of(file_name(s), names)], i, s) for i, s in enumerate(shots)
                    if screen_of(file_name(s), names) in rank), key=lambda t: (t[0], t[1]))
    rest = [s for s in shots if screen_of(file_name(s), names) not in rank]
    return [s for _, _, s in known] + rest


# ---- Default mode: fill empty sets only ----

def fill_empty(store: pathlib.Path, entries: list, app: dict, dry: bool) -> int:
    uploaded = skipped = failed_total = 0
    for platform in sorted({e["ascPlatform"] for e in entries}):
        versions = all_pages(f"/apps/{app['id']}/appStoreVersions", {"filter[platform]": platform, "limit": "50"})
        editable = [v for v in versions if (v["attributes"].get("appVersionState") or v["attributes"].get("appStoreState")) in EDITABLE]
        if not editable:
            print(f"::error::No editable {platform} version (Prepare for Submission) to add screenshots to.")
            return 1
        version = editable[0]
        print(f"{platform}: version {version['attributes']['versionString']}", flush=True)
        locs = {l["attributes"]["locale"]: l["id"] for l in all_pages(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations", {"limit": "50"})}
        mine = [e for e in entries if e["ascPlatform"] == platform]
        for (dt, locale) in sorted({(e["displayType"], e["locale"]) for e in mine}):
            files = sorted((e for e in mine if e["displayType"] == dt and e["locale"] == locale), key=lambda e: e["position"])
            if locale not in locs:
                print(f"::warning::{dt} {locale}: the version has no {locale} localization; skipped.")
                skipped += 1
                continue
            sets = {s["attributes"]["screenshotDisplayType"]: s for s in
                    all_pages(f"/appStoreVersionLocalizations/{locs[locale]}/appScreenshotSets", {"limit": "50"})}
            existing = sets.get(dt)
            if existing:
                count = len(all_pages(f"/appScreenshotSets/{existing['id']}/appScreenshots", {"limit": "50"}))
                if count:
                    print(f"{dt} {locale}: already has {count} screenshot(s); left as it is.")
                    skipped += 1
                    continue
            if dry:
                print(f"{dt} {locale}: would upload {len(files)}.")
                continue
            set_id = existing["id"] if existing else api("POST", "/appScreenshotSets", body={"data": {
                "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": dt},
                "relationships": {"appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": locs[locale]}}},
            }})["data"]["id"]
            ids = [upload_file(set_id, store / e["file"], e["md5"], f"{dt} {locale}") for e in files]
            done, failed = wait_complete(ids)
            failed_total += failed
            if done == len(ids):
                order_set(set_id, ids)
            uploaded += done
            print(f"{dt} {locale}: uploaded {done} of {len(ids)}" + (f", {failed} failed processing" if failed else ""), flush=True)
    print(f"Done: {uploaded} uploaded, {skipped} set(s) left as they were, {failed_total} failed." + (" (dry run)" if dry else ""))
    return 1 if failed_total else 0


# ---- SCREENS / REMOVE_SCREENS / REPLACE_SCREENS: change sets that already hold screenshots ----

def update_sets(store: pathlib.Path, manifest: dict, entries: list, app: dict, add: set, remove: set,
                confirm: bool, dry: bool, replace: set = frozenset(), delete_first: bool = False) -> int:
    names = known_screens(manifest, add | remove | replace)
    n = {"uploaded": 0, "deleted": 0, "would_delete": 0, "replaced": 0, "would_replace": 0, "reordered": 0,
         "full": 0, "left": 0, "failed": 0}
    if replace and not confirm:
        print(f"REPLACE_SCREENS {' '.join(sorted(replace))}: CONFIRM_DELETE is not \"true\", so nothing is replaced or "
              f"added for them; this only reports what would be.", flush=True)
    stopped = False
    for platform in sorted({e["ascPlatform"] for e in entries}):
        if stopped:
            break
        version, versions = versions_of(app, platform)
        if not version:
            not_editable(platform, versions)
            continue
        print(f"{platform}: version {version['attributes']['versionString']}", flush=True)
        locs = {l["attributes"]["locale"]: l["id"] for l in all_pages(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations", {"limit": "50"})}
        mine = [e for e in entries if e["ascPlatform"] == platform]
        for (dt, locale) in sorted({(e["displayType"], e["locale"]) for e in mine}):
            label = f"{dt} {locale}"
            files = sorted((e for e in mine if e["displayType"] == dt and e["locale"] == locale), key=lambda e: e["position"])
            order = [screen_of_entry(e) for e in files]
            wanted = [e for e in files if screen_of_entry(e) in add]
            if locale not in locs:
                print(f"::warning::{label}: the version has no {locale} localization; skipped.")
                n["left"] += 1
                continue
            sets = {s["attributes"]["screenshotDisplayType"]: s for s in
                    all_pages(f"/appStoreVersionLocalizations/{locs[locale]}/appScreenshotSets", {"limit": "50"})}
            existing = sets.get(dt)
            shots = all_pages(f"/appScreenshotSets/{existing['id']}/appScreenshots", {"limit": "50"}) if existing else []
            if not shots:
                print(f"{label}: no screenshots there; left as it is (run without screens to fill an empty set).")
                n["left"] += 1
                continue
            if dry:
                print(f"{label}: holds {len(shots)}: {' '.join(file_name(s) for s in shots)}")
            # A screenshot of an added screen that App Store Connect has not finished (failed, never uploaded,
            # still processing) is neither "there" nor something to order: leave the set and say how to fix it.
            stuck = [s for s in shots if screen_of(file_name(s), names) in add and state_of(s) != "COMPLETE"]
            if stuck:
                for s in stuck:
                    screen = screen_of(file_name(s), names)
                    print(f"::error::{label}: {file_name(s)} is in the set but not ready in App Store Connect "
                          f"({state_of(s) or 'no state'}); nothing changed in this set. Remove it with remove_screens "
                          f"{screen} and confirm_delete (with {screen} not in screens), then add it again"
                          + ("; if it is still processing, wait and run again first." if state_of(s) == "UPLOAD_COMPLETE" else "."),
                          flush=True)
                n["failed"] += len(stuck)
                continue
            present = {screen_of(file_name(s), names) for s in shots if state_of(s) == "COMPLETE"}
            doomed = [s for s in shots if screen_of(file_name(s), names) in remove]
            # With SCREENS or REPLACE_SCREENS, a removal makes room for the new screens: it happens only in a set
            # the manifest has every one of them for (this displayType and locale). Otherwise nothing would take the
            # deleted screenshots' place, so the set is reported and left exactly as it is. This checks the
            # manifest, not App Store Connect: whether the new screenshot then arrives is handled below (delete
            # after it is ready in a set with room; a full set deletes first and reports it if the upload fails).
            missing = sorted((add | replace) - set(order)) if (add or replace) and doomed else []
            if missing:
                print(f"::warning::{label}: the manifest has no {' '.join(missing)} for this set, so nothing would take "
                      f"the place of {' '.join(file_name(s) for s in doomed)}; left as it is (nothing deleted, added, "
                      f"replaced or reordered).", flush=True)
                n["left"] += 1
                continue
            new, seen = [], set()
            for e in wanted:
                if screen_of_entry(e) not in present and screen_of_entry(e) not in seen:
                    new.append(e)
                    seen.add(screen_of_entry(e))
            # REPLACE_SCREENS: each changed screen's new file goes in before its old one is deleted, one screen at a
            # time, so each needs room for one more screenshot at that moment. A screen not there yet joins `new`.
            swaps, additions, stale, own_failed = (plan_replace(label, manifest, dt, files, order, shots, names, replace, n)
                                                   if replace else ([], [], [], []))
            count = len(shots) - len(stale) - len(own_failed)
            # (manifest entry, old screenshots, delete first): a swap that fits uploads first; in a full set it is
            # held, or with DELETE_FIRST_IN_FULL_SETS it deletes the old one first.
            fits, held = [], 0
            for e, olds in swaps:
                gone, coming = " ".join(file_name(s) for s in olds), pathlib.Path(e["file"]).name
                first = count + 1 > MAX_PER_SET
                if first and not delete_first:
                    print(f"::warning::{label}: holds {count} screenshots (at most {MAX_PER_SET}), so there is no room to "
                          f"upload {coming} before deleting {gone}; {screen_of_entry(e)} not replaced (nothing deleted). "
                          f"To replace it here, run with delete_first_in_full_sets (it deletes {gone} first, only where "
                          f"the manifest has {screen_of_entry(e)}), or leave this set out.", flush=True)
                    n["full"] += 1
                    held += 1
                    continue
                fits.append((e, olds, first))
                count += 1 - len(olds)
            # What the confirmed run would add, and whether the set is then full, worked out before a report without
            # confirm_delete clears the plan, so that report says which sets delete first.
            coming_new = new + [e for e in additions if screen_of_entry(e) not in seen]
            full = count + len(coming_new) > MAX_PER_SET
            if replace and not confirm:
                for e, olds, first in fits:
                    gone, coming = " ".join(file_name(s) for s in olds), pathlib.Path(e["file"]).name
                    print(f"{label}: would replace {gone} with {coming} (the file differs from the manifest's"
                          + (f"; the set is full, so {gone} would be deleted first and not brought back if {coming} "
                             f"then failed" if first else "")
                          + "; CONFIRM_DELETE is not \"true\": nothing replaced).")
                for e in additions:
                    print(f"{label}: would add {pathlib.Path(e['file']).name} ({screen_of_entry(e)} is not there; "
                          f"CONFIRM_DELETE is not \"true\": nothing added).")
                if stale:
                    print(f"{label}: would delete {' '.join(file_name(s) for s in stale)} (an older file of a screen whose "
                          f"manifest file is already there; CONFIRM_DELETE is not \"true\": nothing deleted).")
                if own_failed:
                    print(f"{label}: would delete {' '.join(file_name(s) for s in own_failed)} (the manifest's own file, "
                          f"failed processing in App Store Connect; CONFIRM_DELETE is not \"true\": nothing deleted).")
                n["would_replace"] += len(fits) + len(additions)
                n["would_delete"] += len(stale) + len(own_failed)
                held += len(fits) + len(additions) + len(stale) + len(own_failed)
                fits, additions, stale, own_failed, count = [], [], [], [], len(shots)
            new += [e for e in additions if screen_of_entry(e) not in seen]
            if doomed and not confirm:
                print(f"{label}: would delete {' '.join(file_name(s) for s in doomed)} "
                      f"(CONFIRM_DELETE is not \"true\": nothing deleted).")
                n["would_delete"] += len(doomed)
            deleting = doomed if confirm else []
            # A set with room takes the new screenshots first and deletes only once every one of them is ready. A
            # full set has to delete first to make room: if a new screenshot then fails or cannot be uploaded, the
            # deleted one is not brought back. Said before any confirmed delete, so the owner knows which sets.
            if doomed and coming_new and (dry or not confirm):
                gone, coming = " ".join(file_name(s) for s in doomed), " ".join(pathlib.Path(e["file"]).name for e in coming_new)
                print(f"{label}: with confirm_delete, " + (
                    f"the set is full, so {gone} is deleted before {coming} is uploaded; if that upload fails, "
                    f"{gone} is not brought back." if full else
                    f"{gone} is deleted only after {coming} is uploaded and ready; if it fails, nothing is deleted."))
            after = count - len(deleting) + len(new)
            if after > MAX_PER_SET:
                print(f"::warning::{label}: would hold {after} screenshots (at most {MAX_PER_SET}); skipped. "
                      f"Make room with remove_screens and confirm_delete, or leave this set out.")
                n["full"] += 1
                continue
            # The set as it is once the replacements are done, then without the deleted screenshots.
            swapped = {s["id"] for _, olds, _ in fits for s in olds} | {s["id"] for s in stale + own_failed}
            current = [s for s in shots if s["id"] not in swapped] + [stand_in(e) for e, _, _ in fits]
            remaining = [s for s in current if s not in deleting]
            touched = bool((add and wanted) or fits or stale or own_failed or additions)
            if touched:
                planned = remaining + [stand_in(e) for e in new]
                reorder = [file_name(s) for s in in_order(planned, order, names)] != [file_name(s) for s in planned]
            else:
                reorder = False
            if not deleting and not new and not reorder and not fits and not stale and not own_failed:
                if held:
                    pass  # what REPLACE_SCREENS would do, or why it cannot, is reported above
                elif add and not wanted:
                    print(f"{label}: the manifest has none of {' '.join(sorted(add))} for this set; nothing to change.")
                else:
                    print(f"{label}: nothing to change ({len(shots)} there).")
                continue
            if dry:
                if stale:
                    print(f"{label}: would delete {' '.join(file_name(s) for s in stale)} (an older file of a screen whose "
                          f"manifest file is already there and ready).")
                    n["deleted"] += len(stale)
                if own_failed:
                    print(f"{label}: would delete {' '.join(file_name(s) for s in own_failed)} first (the manifest's own "
                          f"file, failed processing in App Store Connect: unusable).")
                    n["deleted"] += len(own_failed)
                for e, olds, first in fits:
                    gone, coming = " ".join(file_name(s) for s in olds), pathlib.Path(e["file"]).name
                    print(f"{label}: would replace {gone} with {coming}: " + (
                        f"the set is full, so {gone} is deleted first and {coming} uploaded; if {coming} then fails, "
                        f"{gone} is not brought back." if first else
                        f"{coming} is uploaded first and {gone} deleted once it is ready; if it fails, nothing is deleted."))
                    n["replaced"] += 1
                if deleting:
                    print(f"{label}: would delete {' '.join(file_name(s) for s in deleting)}.")
                    n["deleted"] += len(deleting)
                if new:
                    print(f"{label}: would upload {' '.join(pathlib.Path(e['file']).name for e in new)}.")
                    n["uploaded"] += len(new)
                if reorder:
                    print(f"{label}: would order: {' '.join(file_name(s) for s in in_order(planned, order, names))}.")
                    n["reordered"] += 1
                continue
            # REPLACE_SCREENS first: an older file next to the manifest's ready one goes; then each changed screen's
            # new file is uploaded, and its old one deleted only once the new one is ready. If it is not, the old one
            # stays and nothing else changes in this set (no reorder).
            for s in stale:
                api("DELETE", f"/appScreenshots/{s['id']}")
                print(f"{label}: deleted {file_name(s)} (an older file of a screen whose manifest file is already there "
                      f"and ready).", flush=True)
                n["deleted"] += 1
            for s in own_failed:
                # The manifest's own file that failed processing (a replace's upload that was still processing when
                # its run ended): unusable, and in the way of the room the swap needs, so it goes first.
                api("DELETE", f"/appScreenshots/{s['id']}")
                print(f"{label}: deleted {file_name(s)} (the manifest's own file, failed processing in App Store "
                      f"Connect: unusable).", flush=True)
                n["deleted"] += 1
            halted = False
            for e, olds, first in fits:
                gone, coming = " ".join(file_name(s) for s in olds), pathlib.Path(e["file"]).name
                if first:
                    # DELETE_FIRST_IN_FULL_SETS: the set is full, so the old one goes before the new one is uploaded.
                    for s in olds:
                        api("DELETE", f"/appScreenshots/{s['id']}")
                    n["deleted"] += len(olds)
                    print(f"{label}: deleted {gone} (the set is full: deleted before uploading {coming}).", flush=True)
                lost = f"{gone} was deleted to make room and is not brought back; " if first else ""
                try:
                    shot_id = upload_file(existing["id"], store / e["file"], e["md5"], label)
                    state = wait_states([shot_id])[shot_id]
                except (urllib.error.URLError, OSError) as err:  # HTTPError is a URLError
                    n["failed"] += 1
                    code = f" HTTP {err.code}" if isinstance(err, urllib.error.HTTPError) else ""
                    print(f"::error::{label}: uploading {coming} stopped ({type(err).__name__}{code}); "
                          + (lost if first else f"{gone} not deleted; ")
                          + "not reordered. The run stops here: sets not reached yet are left as they are.", flush=True)
                    stopped = True
                    break
                if state != "COMPLETE":
                    n["failed"] += 1
                    halted = True
                    if state == "FAILED":
                        # This run's own upload of moments ago: deleted again, so the set keeps only the old one.
                        discard(shot_id, coming, label)
                        print(f"::error::{label}: {coming} failed processing in App Store Connect; "
                              + (lost if first else f"{gone} kept (nothing deleted); ")
                              + "not reordered. Check the file, then run again.", flush=True)
                    else:
                        print(f"::warning::{label}: {coming} is not ready yet ({state or 'no state'}); "
                              + (lost if first else f"{gone} not deleted and ")
                              + f"the set not reordered. Run again later"
                              + ("." if first else f": once {coming} is ready, {gone} is deleted."), flush=True)
                    break
                if not first:
                    for s in olds:
                        api("DELETE", f"/appScreenshots/{s['id']}")
                n["replaced"] += 1
                print(f"{label}: replaced {gone} with {coming} ("
                      + (f"{gone} deleted first, then {coming} uploaded and ready)." if first else
                         f"uploaded and ready, then {gone} deleted)."), flush=True)
            if stopped:
                break
            if halted:
                continue
            # Only a full set deletes before uploading (it needs the room); a set with room deletes once every new
            # screenshot is ready, so a failed upload there leaves the set exactly as it was.
            first = deleting if full else []
            later = [s for s in deleting if s not in first]
            for s in first:
                api("DELETE", f"/appScreenshots/{s['id']}")
                print(f"{label}: deleted {file_name(s)} (the set is full: deleted before uploading).", flush=True)
                n["deleted"] += 1
            if new:
                ids = []
                try:
                    for e in new:
                        ids.append(upload_file(existing["id"], store / e["file"], e["md5"], label))
                    states = wait_states(ids)
                except (urllib.error.URLError, OSError) as err:  # HTTPError is a URLError
                    n["failed"] += len(new)
                    code = f" HTTP {err.code}" if isinstance(err, urllib.error.HTTPError) else ""
                    print(f"::error::{label}: uploading stopped ({type(err).__name__}{code})"
                          + (f" after deleting {' '.join(file_name(s) for s in first)} (not brought back)" if first else "")
                          + (f"; {' '.join(file_name(s) for s in later)} not deleted" if later else "")
                          + "; not reordered. The run stops here: sets not reached yet are left as they are.", flush=True)
                    stopped = True
                    break
                file_of = {i: pathlib.Path(e["file"]).name for i, e in zip(ids, new)}
                done = [i for i in ids if states[i] == "COMPLETE"]
                failed = [i for i in ids if states[i] == "FAILED"]
                waiting = [i for i in ids if i not in done and i not in failed]
                n["uploaded"] += len(done)
                print(f"{label}: uploaded {' '.join(file_of[i] for i in ids)}: {len(done)} of {len(ids)} ready"
                      + (f", {len(failed)} failed processing" if failed else ""), flush=True)
                if failed or waiting:
                    n["failed"] += len(failed) + len(waiting)
                    for i in failed:
                        # This run's own upload of moments ago, not store content: delete it, so it never stays
                        # in the set looking like the screen is there.
                        if discard(i, file_of[i], label):
                            print(f"::error::{label}: {file_of[i]} failed processing in App Store Connect and was "
                                  f"deleted again; not reordered. Check the file, then run again to upload it.", flush=True)
                    if waiting:
                        print(f"::warning::{label}: not reordered while {' '.join(file_of[i] for i in waiting)} is still "
                              f"processing; run again later to order it.", flush=True)
                    if later:
                        print(f"::warning::{label}: {' '.join(file_name(s) for s in later)} not deleted, because the new "
                              f"screenshot is not ready; run again once it is.", flush=True)
                    elif first and failed:
                        print(f"::error::{label}: {' '.join(file_name(s) for s in first)} was deleted to make room and is "
                              f"not brought back; the set stays without it until a run uploads the new screenshot.",
                              flush=True)
                    continue
            for s in later:
                api("DELETE", f"/appScreenshots/{s['id']}")
                print(f"{label}: deleted {file_name(s)}" + (" (the new screenshot is ready)." if new else "."), flush=True)
                n["deleted"] += 1
            if touched:
                # Read the set again, so the order names every screenshot it holds now and drops none.
                now = all_pages(f"/appScreenshotSets/{existing['id']}/appScreenshots", {"limit": "50"})
                target = in_order(now, order, names)
                if [s["id"] for s in target] != [s["id"] for s in now]:
                    api("PATCH", f"/appScreenshotSets/{existing['id']}/relationships/appScreenshots",
                        body={"data": [{"type": "appScreenshots", "id": s["id"]} for s in target]})
                    n["reordered"] += 1
                    print(f"{label}: ordered: {' '.join(file_name(s) for s in target)}.", flush=True)
    if stopped:
        print("::error::Stopped after the upload error above; run again once it is fixed.", flush=True)
    verb = "would be " if dry else ""
    print(f"Done: {n['uploaded']} {verb}uploaded, {n['deleted']} {verb}deleted, "
          + (f"{n['replaced']} {verb}replaced, " if replace else "")
          + f"{n['reordered']} set(s) {verb}reordered, "
          f"{n['full']} set(s) skipped (would go over {MAX_PER_SET}), {n['left']} set(s) left as they were, "
          f"{n['would_delete']} not deleted without confirm_delete, "
          + (f"{n['would_replace']} not replaced or added without confirm_delete, " if replace else "")
          + f"{n['failed']} failed." + (" (dry run)" if dry else ""))
    return 1 if n["failed"] else 0


# ---- REPLACE_SETS: replace whole sets with the manifest's files ----

def error_name(err: Exception) -> str:
    return type(err).__name__ + (f" HTTP {err.code}" if isinstance(err, urllib.error.HTTPError) else "")


def holds_manifest(shots: list, files: list) -> str:
    """How a set compares with the manifest's files for it, by sourceFileChecksum against md5 (in any case):
    "same" (exactly those files, in position order, all COMPLETE), "order" (the same files in another order, all
    COMPLETE), "processing" (the same files, one not COMPLETE yet and none FAILED) or "differs"."""
    want = [e["md5"].lower() for e in files]
    have = [checksum_of(s) for s in shots]
    states = [state_of(s) for s in shots]
    if "FAILED" in states or sorted(have) != sorted(want):
        return "differs"
    if any(s != "COMPLETE" for s in states):
        return "processing"
    return "same" if have == want else "order"


def manifest_order(shots: list, files: list) -> list[str]:
    """The ids of a set that holds the manifest's files (holds_manifest "order"), in position order."""
    pool, ids = list(shots), []
    for e in files:
        s = next(s for s in pool if checksum_of(s) == e["md5"].lower())
        pool.remove(s)
        ids.append(s["id"])
    return ids


def lacking(manifest: dict, dt: str, files: list) -> tuple[list, int]:
    """The screens of the display type's order the manifest has no file of for this set (with why, as the manifest
    says), and how many screens that order has (0 when the manifest does not say)."""
    order = ((manifest.get("displayTypes") or {}).get(dt) or {}).get("screens") or []
    have = {screen_of_entry(e) for e in files}
    out = []
    for screen in order:
        if screen not in have:
            why = why_absent(manifest, dt, files, screen)[1]
            out.append(f"{screen} ({why})" if why else screen)
    return out, len(order)


def replace_sets(store: pathlib.Path, manifest: dict, entries: list, app: dict, confirm: bool, dry: bool,
                 allow_short: bool = False) -> int:
    """REPLACE_SETS: each set in DISPLAY_TYPES in each locale of the editable version, where the manifest has files
    for it, ends holding exactly those files in position order. Deletes everything in a set that does not, then
    uploads; never touches a set that already does. A set that would end shorter (the manifest lacks screens for
    it, or has fewer files than it holds) is left as it is unless `allow_short`. Only with CONFIRM_DELETE;
    otherwise, and in a dry run, it reports."""
    n = {"replaced": 0, "deleted": 0, "uploaded": 0, "reordered": 0, "current": 0, "skipped": 0, "no_files": 0,
         "short": 0, "failed": 0}
    report = dry or not confirm
    if not confirm:
        print('REPLACE_SETS: CONFIRM_DELETE is not "true", so nothing is deleted, uploaded or ordered; this only '
              'reports what would be.', flush=True)
    # Sets this run deleted from that did not end fully replaced and ready: a delete or an upload stopped with an
    # error, a file failed processing, or one is still processing. Only a fully replaced set resets the count. Two
    # stop the run, and a set that ends with none of the manifest's files in it stops it at once: a broken upload,
    # or files App Store Connect does not process for this display type (each set is emptied before its uploads),
    # would otherwise empty every set it reaches. A dry run cannot catch that. `stopped` says why it stopped.
    streak, stopped = 0, ""
    two_in_a_row = ("two sets ended short or not ready with no fully replaced set between them (a delete or an upload "
                    "stopped with an error, or a file failed or is still processing)")
    for platform in sorted({e["ascPlatform"] for e in entries}):
        if stopped:
            break
        version, versions = versions_of(app, platform)
        if not version:
            not_editable(platform, versions)
            continue
        print(f"{platform}: version {version['attributes']['versionString']}", flush=True)
        locs = {l["attributes"]["locale"]: l["id"] for l in all_pages(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations", {"limit": "50"})}
        mine = [e for e in entries if e["ascPlatform"] == platform]
        types = sorted({e["displayType"] for e in mine})
        absent = sorted({e["locale"] for e in mine} - set(locs))
        if absent:
            print(f"::warning::{platform}: the version has no {' '.join(absent)} localization; those sets are skipped "
                  f"(add the locale in App Store Connect first).", flush=True)
            n["skipped"] += len({(e["displayType"], e["locale"]) for e in mine if e["locale"] in absent})
        for locale in sorted(locs):
            if stopped:
                break
            sets = None
            for dt in types:
                label = f"{dt} {locale}"
                files = sorted((e for e in mine if e["displayType"] == dt and e["locale"] == locale), key=lambda e: e["position"])
                if not files:
                    print(f"{label}: the manifest has no files for this set; left as it is.")
                    n["no_files"] += 1
                    continue
                if len(files) > MAX_PER_SET:
                    print(f"::error::{label}: the manifest has {len(files)} files for this set (at most {MAX_PER_SET}); "
                          f"left as it is.", flush=True)
                    n["failed"] += 1
                    continue
                if sets is None:
                    sets = {s["attributes"]["screenshotDisplayType"]: s for s in
                            all_pages(f"/appStoreVersionLocalizations/{locs[locale]}/appScreenshotSets", {"limit": "50"})}
                existing = sets.get(dt)
                if not existing:
                    print(f"::warning::{label}: no such set in App Store Connect; skipped (run without replace_sets to "
                          f"create and fill it).", flush=True)
                    n["skipped"] += 1
                    continue
                set_id = existing["id"]
                shots = all_pages(f"/appScreenshotSets/{set_id}/appScreenshots", {"limit": "50"})
                coming = [pathlib.Path(e["file"]).name for e in files]
                unconfirmed = "" if confirm else '; CONFIRM_DELETE is not "true": nothing changed'
                if dry:
                    print(f"{label}: holds {len(shots)}" + (f": {' '.join(file_name(s) for s in shots)}" if shots else ""))
                match = holds_manifest(shots, files)
                if match == "same":
                    print(f"{label}: already up to date ({len(shots)}, the manifest's files in order).")
                    n["current"] += 1
                    continue
                if match == "processing":
                    busy = [s for s in shots if state_of(s) != "COMPLETE"]
                    print(f"::error::{label}: holds the manifest's files, but {' '.join(file_name(s) for s in busy)} is not "
                          f"ready in App Store Connect ({state_of(busy[0]) or 'no state'}); left as it is. If it is still "
                          f"processing, wait and run again.", flush=True)
                    n["failed"] += 1
                    continue
                if match == "order":
                    target = manifest_order(shots, files)
                    n["reordered"] += 1
                    if report:
                        print(f"{label}: holds the manifest's files in another order; would order them{unconfirmed}.")
                        continue
                    order_set(set_id, target)
                    print(f"{label}: held the manifest's files in another order; ordered (deleted 0, uploaded 0).", flush=True)
                    continue
                # The owner agreed to complete sets (the same screens and more): a set that would end shorter than
                # the display type's order, or than it is now, is left as it is unless ALLOW_SHORT_SETS says otherwise.
                lack, total = lacking(manifest, dt, files)
                fewer = len(files) < len(shots)
                if lack or fewer:
                    why = "; ".join(
                        ([f"the manifest has no {', '.join(lack)} for this locale ({len(files)} of the {total} screens)"]
                         if lack else [])
                        + ([f"it holds {len(shots)} now and the manifest has only {len(files)} for it"] if fewer else []))
                    if not allow_short:
                        fix = ("Redo the missing captures first" if lack else
                               "Check which screens the set holds that the manifest has not")
                        print(f"::error::{label}: {why}, so replacing it would leave it shorter; "
                              f"{'it would be left' if report else 'left'} as it is (nothing deleted or uploaded). {fix}, "
                              f"or, once the owner agrees to a shorter set, run with allow_short_sets.", flush=True)
                        n["short"] += 1
                        continue
                    print(f"::warning::{label}: {why}; allow_short_sets is on, so the set "
                          f"{'would get' if report else 'gets'} {len(files)} screenshot(s).", flush=True)
                gone = " ".join(file_name(s) for s in shots)
                if report:
                    print(f"{label}: would delete {len(shots)}" + (f" ({gone})" if shots else "")
                          + f" and upload {len(files)} ({' '.join(coming)}), then order the set{unconfirmed}.")
                    n["replaced"] += 1
                    n["deleted"] += len(shots)
                    n["uploaded"] += len(files)
                    continue
                # Everything in the set goes first: a set of 9 cannot take 10 new ones next to them (at most 10).
                deleted = 0
                try:
                    for s in shots:
                        api("DELETE", f"/appScreenshots/{s['id']}")
                        deleted += 1
                except (urllib.error.URLError, OSError) as err:  # HTTPError is a URLError
                    n["deleted"] += deleted
                    n["failed"] += 1
                    print(f"::error::{label}: deleting stopped after {deleted} of {len(shots)} ({error_name(err)}); nothing "
                          f"uploaded, so the set may now be short. A re-run with replace_sets replaces it again.", flush=True)
                    streak += 1
                    if streak >= 2:
                        stopped = two_in_a_row
                        break
                    continue
                n["deleted"] += deleted
                ids, err = [], None
                try:
                    for e in files:
                        ids.append(upload_file(set_id, store / e["file"], e["md5"], label))
                except (urllib.error.URLError, OSError) as x:  # HTTPError is a URLError
                    err = x   # upload_file deleted its half-made screenshot; the files after it are not uploaded
                states = wait_states(ids) if ids else {}
                file_of = dict(zip(ids, coming))
                done = [i for i in ids if states[i] == "COMPLETE"]
                bad = [i for i in ids if states[i] == "FAILED"]
                waiting = [i for i in ids if i not in done and i not in bad]
                for i in bad:
                    discard(i, file_of[i], label)   # this run's own upload of moments ago, unusable
                n["uploaded"] += len(done)
                if err is not None or bad or waiting:
                    n["failed"] += 1
                    print(f"{label}: deleted {deleted}, uploaded {len(done)} of {len(files)}.", flush=True)
                    problems = []
                    if err is not None:
                        after = len(files) - len(ids) - 1
                        problems.append(f"uploading {coming[len(ids)]} stopped ({error_name(err)})"
                                        + (f", so the {after} file(s) after it were not uploaded" if after else ""))
                    if bad:
                        problems.append(f"{' '.join(file_of[i] for i in bad)} failed processing in App Store Connect "
                                        f"(deleted again)")
                    if waiting:
                        problems.append(f"{' '.join(file_of[i] for i in waiting)} still processing")
                    if err is None and not bad:
                        tail = ("not ordered yet. Run again later: once they are ready the set counts as up to date "
                                "(their order is already the manifest's).")
                    else:
                        tail = (f"the set now holds {len(done) + len(waiting)} of the manifest's {len(files)} and is not "
                                f"ordered. A re-run with replace_sets replaces it again (it no longer holds what the "
                                f"manifest says).")
                    print(f"::error::{label}: {'; '.join(problems)}; {tail}", flush=True)
                    # Not fully replaced and ready, whatever the cause: a file that failed processing leaves the set as
                    # short as an upload error does, and a file still processing may fail yet.
                    streak += 1
                    if not done and not waiting:
                        stopped = (f"{label} ended with none of the manifest's files in it ({deleted} deleted, every "
                                   f"upload failed)")
                    elif streak >= 2:
                        stopped = two_in_a_row
                    if stopped:
                        break
                    continue
                streak = 0
                # Read the set again, so the order names every screenshot it holds now and drops none.
                now = [s["id"] for s in all_pages(f"/appScreenshotSets/{set_id}/appScreenshots", {"limit": "50"})]
                order_set(set_id, [i for i in ids if i in now] + [i for i in now if i not in ids])
                n["replaced"] += 1
                print(f"{label}: replaced: deleted {deleted}, uploaded {len(done)} of {len(files)}, ordered.", flush=True)
    if stopped:
        print(f"::error::Stopped: {stopped}, so no more sets are deleted; sets not reached yet are left as they are. "
              f"Run again with replace_sets once it is fixed.", flush=True)
    verb = "would be " if report else ""
    print(f"Done (replace sets): {n['replaced']} set(s) {verb}replaced ({n['deleted']} {verb}deleted, {n['uploaded']} "
          f"{verb}uploaded), {n['reordered']} set(s) {verb}only reordered, {n['current']} already up to date, "
          f"{n['skipped']} skipped (not in App Store Connect), {n['no_files']} without manifest files, "
          f"{n['short']} left as they are (would end shorter), "
          f"{n['failed']} failed." + (" (dry run)" if dry else "") + ("" if confirm else ' (CONFIRM_DELETE is not "true")'))
    return 1 if n["failed"] or n["short"] or stopped else 0


# ---- TEXT: the listing text of the editable versions ----

def update_text(app: dict, platforms: list, listing: dict, dry: bool) -> int:
    limits = listing.get("limits", {})
    have = listing.get("locales", {})
    problems = updated = refused_whats_new = 0
    for platform in platforms:
        version, versions = versions_of(app, platform)
        if not version:
            not_editable(platform, versions)
            continue
        first = all(v["id"] == version["id"] for v in versions)
        print(f"{platform} text: version {version['attributes']['versionString']}"
              + (" (the app's first version on this platform: whatsNew is left out)" if first else ""), flush=True)
        locs = {l["attributes"]["locale"]: l for l in all_pages(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations", {"limit": "50"})}
        ours = sorted(l for l in have if isinstance(have[l].get(platform), dict))
        absent = [l for l in ours if l not in locs]
        if absent:
            print(f"::warning::{platform}: not in App Store Connect, skipped (add the locale there first): {' '.join(absent)}")
        no_text = sorted(l for l in locs if l not in ours)
        if no_text:
            print(f"{platform}: no text in listing.json for {' '.join(no_text)}; left as it is.")
        for locale in [l for l in ours if l in locs]:
            src, loc = have[locale][platform], locs[locale]
            current = loc.get("attributes") or {}
            change = {}
            for f in TEXT_FIELDS:
                value = src.get(f)
                if not isinstance(value, str) or not value.strip():
                    continue  # never blank a field the file does not have
                if f == "whatsNew" and first:
                    continue
                if f in limits and len(value) > limits[f]:
                    print(f"::warning::{platform} {locale}: {f} is {len(value)} characters (limit {limits[f]}); left as it is.")
                    problems += 1
                    continue
                if current.get(f) != value:
                    change[f] = value
            if not change:
                print(f"{platform} {locale}: text already up to date.")
                continue
            if dry:
                print(f"{platform} {locale}: would update {', '.join(f for f in TEXT_FIELDS if f in change)}.")
                continue
            whats_new = change.pop("whatsNew", None)
            done = []
            if change:
                try:
                    api("PATCH", f"/appStoreVersionLocalizations/{loc['id']}", body={"data": {
                        "type": "appStoreVersionLocalizations", "id": loc["id"], "attributes": change}})
                    done += [f for f in TEXT_FIELDS if f in change]
                except urllib.error.HTTPError:
                    print(f"::error::{platform} {locale}: App Store Connect refused {', '.join(change)}; left as it is.", flush=True)
                    problems += 1
                    continue
            if whats_new is not None:
                # On its own, so a refusal (a first version has no "What's New") leaves the other fields in.
                try:
                    api("PATCH", f"/appStoreVersionLocalizations/{loc['id']}", body={"data": {
                        "type": "appStoreVersionLocalizations", "id": loc["id"], "attributes": {"whatsNew": whats_new}}})
                    done.append("whatsNew")
                except urllib.error.HTTPError:
                    print(f"::warning::{platform} {locale}: App Store Connect refused whatsNew; skipped.", flush=True)
                    refused_whats_new += 1
            if done:
                updated += 1
                print(f"{platform} {locale}: updated {', '.join(done)}.", flush=True)
    print(f"Text done: {updated} locale(s) updated, {refused_whats_new} whatsNew refused, {problems} problem(s)."
          + (" (dry run)" if dry else ""))
    return 1 if problems else 0


# ---- TEXT with TV_OS: the Privacy Policy Text of the app information (shown on Apple TV) ----

def info_states(info: dict) -> list:
    attrs = info.get("attributes") or {}
    return [attrs[k] for k in ("appStoreState", "state") if attrs.get(k)]


def info_editable(info: dict) -> bool:
    # appStoreState (older) or state (newer); when both are there, both must be editable.
    states = info_states(info)
    return bool(states) and all(s in EDITABLE for s in states)


def update_privacy_text(app: dict, listing: dict, dry: bool) -> int:
    """appInfoLocalizations.privacyPolicyText from listing.json (each locale's APP_INFO), only where it differs and
    only on an editable app information. Never the name, the subtitle or the privacy policy URL. Logs show locales
    and lengths, never the text."""
    limit = listing.get("limits", {}).get(PRIVACY_FIELD)
    have = {l: v[APP_INFO][PRIVACY_FIELD] for l, v in listing.get("locales", {}).items()
            if isinstance(v.get(APP_INFO), dict) and isinstance(v[APP_INFO].get(PRIVACY_FIELD), str)
            and v[APP_INFO][PRIVACY_FIELD].strip()}
    # The same editable states as the versions: an app information that is waiting for review, in review,
    # ready for review (already staged in a submission) or live is left as it is.
    infos = all_pages(f"/apps/{app['id']}/appInfos", {"limit": "50"})
    editable = [i for i in infos if info_editable(i)]
    if not editable:
        print("::warning::Privacy Policy Text (Apple TV): app information is in review or live: privacy text not "
              "changed.", flush=True)
        return 0
    info = editable[0]
    print(f"App information ({'/'.join(dict.fromkeys(info_states(info)))}): Privacy Policy Text (Apple TV).", flush=True)
    locs = {l["attributes"]["locale"]: l for l in all_pages(f"/appInfos/{info['id']}/appInfoLocalizations", {"limit": "50"})}
    absent = sorted(l for l in have if l not in locs)
    if absent:
        print(f"::warning::App information: not in App Store Connect, skipped (add the locale there first): {' '.join(absent)}")
    no_text = sorted(l for l in locs if l not in have)
    if no_text:
        print(f"App information: no privacy text in listing.json for {' '.join(no_text)}; left as it is.")
    problems = updated = 0
    for locale in sorted(l for l in have if l in locs):
        value, loc = have[locale], locs[locale]
        if limit and len(value) > limit:
            print(f"::warning::App information {locale}: {PRIVACY_FIELD} is {len(value)} characters (limit {limit}); left as it is.")
            problems += 1
            continue
        current = (loc.get("attributes") or {}).get(PRIVACY_FIELD)
        if current == value:
            print(f"App information {locale}: privacy text already up to date.")
            continue
        sizes = f"{len(current or '')} -> {len(value)} characters"
        if dry:
            print(f"App information {locale}: would update {PRIVACY_FIELD} ({sizes}).")
            updated += 1
            continue
        try:
            # Only this attribute: never name, subtitle, privacyPolicyUrl or privacyChoicesUrl.
            api("PATCH", f"/appInfoLocalizations/{loc['id']}", body={"data": {
                "type": "appInfoLocalizations", "id": loc["id"], "attributes": {PRIVACY_FIELD: value}}})
        except urllib.error.HTTPError:
            print(f"::error::App information {locale}: App Store Connect refused {PRIVACY_FIELD}; left as it is.", flush=True)
            problems += 1
            continue
        updated += 1
        print(f"App information {locale}: updated {PRIVACY_FIELD} ({sizes}).", flush=True)
    verb = "would be " if dry else ""
    print(f"Privacy text done: {updated} locale(s) {verb}updated, {problems} problem(s)." + (" (dry run)" if dry else ""))
    return 1 if problems else 0


def main() -> int:
    wanted = set(os.environ.get("DISPLAY_TYPES", "").split())
    add = set(os.environ.get("SCREENS", "").split())
    remove = set(os.environ.get("REMOVE_SCREENS", "").split())
    replace = set(os.environ.get("REPLACE_SCREENS", "").split())
    confirm = os.environ.get("CONFIRM_DELETE", "") == "true"  # exactly "true", nothing else
    delete_first = os.environ.get("DELETE_FIRST_IN_FULL_SETS", "") == "true"  # exactly "true", nothing else
    whole_raw = os.environ.get("REPLACE_SETS", "")
    whole = whole_raw == "true"  # exactly "true"
    short_raw = os.environ.get("ALLOW_SHORT_SETS", "")
    allow_short = short_raw == "true"  # exactly "true"
    text = os.environ.get("TEXT", "").lower() == "true"
    platforms = os.environ.get("PLATFORMS", "").split()
    dry = os.environ.get("DRY_RUN", "").lower() == "true"
    if not wanted and not text:
        print("::error::DISPLAY_TYPES is empty; name the screenshot sets to upload (e.g. APP_WATCH_ULTRA).")
        return 1
    if (add or remove or replace) and not wanted:
        print("::error::SCREENS, REMOVE_SCREENS and REPLACE_SCREENS need DISPLAY_TYPES (the sets to change).")
        return 1
    if delete_first and not replace:
        print("::error::DELETE_FIRST_IN_FULL_SETS is only for REPLACE_SCREENS (name the screens to replace).")
        return 1
    if whole_raw not in ("", "true", "false"):
        print('::error::REPLACE_SETS is "true" or "false" (or empty).')
        return 1
    if short_raw not in ("", "true", "false"):
        print('::error::ALLOW_SHORT_SETS is "true" or "false" (or empty).')
        return 1
    if allow_short and not whole:
        print("::error::ALLOW_SHORT_SETS is only for REPLACE_SETS (it lets a replaced set end shorter).")
        return 1
    if whole and not wanted:
        print("::error::REPLACE_SETS needs DISPLAY_TYPES (the sets to replace).")
        return 1
    if whole and (add or remove or replace):
        print("::error::REPLACE_SETS replaces whole sets and cannot be combined with SCREENS, REMOVE_SCREENS or "
              "REPLACE_SCREENS; run them separately.")
        return 1
    for a, b, a_name, b_name in ((add, remove, "SCREENS", "REMOVE_SCREENS"), (add, replace, "SCREENS", "REPLACE_SCREENS"),
                                 (remove, replace, "REMOVE_SCREENS", "REPLACE_SCREENS")):
        if a & b:
            print(f"::error::A screen is in both {a_name} and {b_name} (name each screen in only one of SCREENS, "
                  f"REMOVE_SCREENS and REPLACE_SCREENS): " + " ".join(sorted(a & b)))
            return 1
    if text:
        bad = [p for p in platforms if p not in TEXT_PLATFORMS]
        if not platforms or bad:
            print("::error::TEXT needs PLATFORMS, space-separated, from: " + " ".join(TEXT_PLATFORMS))
            return 1
    status, app = 0, None
    if wanted:
        if len(sys.argv) < 2:
            print("::error::Name the store folder (manifest.json) to upload screenshots from.")
            return 1
        store = pathlib.Path(sys.argv[1])
        manifest = json.loads((store / "manifest.json").read_text())
        entries = [e for e in manifest["screenshots"] if e["displayType"] in wanted]
        if not entries:
            print("::error::The manifest has no screenshots for " + " ".join(sorted(wanted)))
            return 1
        unknown = sorted((add | replace) - {screen_of_entry(e) for e in entries})
        if unknown:
            print("::error::The manifest has no screenshots of " + " ".join(unknown) + " for " + " ".join(sorted(wanted)))
            return 1
        stray = sorted(remove - known_screens(manifest, set()))
        if stray:
            print("::warning::Not a known screen name (check the spelling): " + " ".join(stray))
        changing = add or remove or replace
        check = [e for e in entries if screen_of_entry(e) in add | replace] if changing else entries
        for e in check:
            f = store / e["file"]
            if not f.exists() or hashlib.md5(f.read_bytes()).hexdigest() != e["md5"]:
                print(f"::error::{e['file']} is missing or differs from the manifest")
                return 1
        app = find_app()
        if whole:
            for dt in sorted(wanted - {e["displayType"] for e in entries}):
                print(f"::warning::The manifest has no screenshots for {dt}; those sets are left as they are.")
            status |= replace_sets(store, manifest, entries, app, confirm, dry, allow_short)
        elif changing:
            status |= update_sets(store, manifest, entries, app, add, remove, confirm, dry, replace, delete_first)
        else:
            status |= fill_empty(store, entries, app, dry)
    if text:
        app = app or find_app()
        listing = json.loads(LISTING.read_text())
        status |= update_text(app, platforms, listing, dry)
        if "TV_OS" in platforms:
            status |= update_privacy_text(app, listing, dry)
    return status


if __name__ == "__main__":
    sys.exit(main())
