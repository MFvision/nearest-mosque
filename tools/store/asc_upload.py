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
                  sets being processed, before adding (so a full set can take the new screen), and only when
                  CONFIRM_DELETE is exactly "true"; otherwise it reports what it would delete and deletes nothing.
                  A deleted screenshot cannot be brought back. Screenshots it does not recognise are never deleted.
  TEXT            "true": updates description, keywords and promotionalText of each locale of the editable version
                  of each platform in PLATFORMS ("IOS MAC_OS VISION_OS TV_OS") from shared/store/listing.json, and
                  whatsNew unless it is the app's first version on that platform (if App Store Connect refuses it,
                  it is skipped and reported). Never the name or the subtitle; a field the file does not have is left
                  as it is, never blanked; locales missing in App Store Connect are skipped and reported.

A screenshot is recognised by its fileName: "<anything>-<screen>.png", the longest known screen name winning, so
"09-widgets-qibla.png" is widgets-qibla and never qibla. Screenshot modes use the editable version of each
platform (Prepare for Submission or rejected). In the SCREENS, REMOVE_SCREENS and TEXT modes a platform without
one is reported ("in review or live: nothing changed") and the others go on. Logs show locales, counts and the
screenshots' file names only. Needs PyJWT and cryptography. Offline test: tools/store/tests/test_asc_upload.py.
"""
import hashlib
import json
import os
import pathlib
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
    print(f"::warning::{platform}: no editable version (Prepare for Submission or rejected); "
          f"the version is in review or live: nothing changed.", flush=True)


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
                api("PATCH", f"/appScreenshotSets/{set_id}/relationships/appScreenshots",
                    body={"data": [{"type": "appScreenshots", "id": i} for i in ids]})
            uploaded += done
            print(f"{dt} {locale}: uploaded {done} of {len(ids)}" + (f", {failed} failed processing" if failed else ""), flush=True)
    print(f"Done: {uploaded} uploaded, {skipped} set(s) left as they were, {failed_total} failed." + (" (dry run)" if dry else ""))
    return 1 if failed_total else 0


# ---- SCREENS / REMOVE_SCREENS: change sets that already hold screenshots ----

def update_sets(store: pathlib.Path, manifest: dict, entries: list, app: dict, add: set, remove: set,
                confirm: bool, dry: bool) -> int:
    names = known_screens(manifest, add | remove)
    n = {"uploaded": 0, "deleted": 0, "would_delete": 0, "reordered": 0, "full": 0, "left": 0, "failed": 0}
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
            new, seen = [], set()
            for e in wanted:
                if screen_of_entry(e) not in present and screen_of_entry(e) not in seen:
                    new.append(e)
                    seen.add(screen_of_entry(e))
            if doomed and not confirm:
                print(f"{label}: would delete {' '.join(file_name(s) for s in doomed)} "
                      f"(CONFIRM_DELETE is not \"true\": nothing deleted).")
                n["would_delete"] += len(doomed)
            deleting = doomed if confirm else []
            after = len(shots) - len(deleting) + len(new)
            if after > MAX_PER_SET:
                print(f"::warning::{label}: would hold {after} screenshots (at most {MAX_PER_SET}); skipped. "
                      f"Make room with remove_screens and confirm_delete, or leave this set out.")
                n["full"] += 1
                continue
            remaining = [s for s in shots if s not in deleting]
            if add and wanted:
                planned = remaining + [{"id": "new", "attributes": {"fileName": pathlib.Path(e["file"]).name}} for e in new]
                reorder = [file_name(s) for s in in_order(planned, order, names)] != [file_name(s) for s in planned]
            else:
                reorder = False
            if not deleting and not new and not reorder:
                if add and not wanted:
                    print(f"{label}: the manifest has none of {' '.join(sorted(add))} for this set; nothing to change.")
                else:
                    print(f"{label}: nothing to change ({len(shots)} there).")
                continue
            if dry:
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
            # Deletions first, so a full set can take the new screen.
            for s in deleting:
                api("DELETE", f"/appScreenshots/{s['id']}")
                print(f"{label}: deleted {file_name(s)}.", flush=True)
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
                          + (f" after deleting {' '.join(file_name(s) for s in deleting)}" if deleting else "")
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
                    continue
            if add and wanted:
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
    print(f"Done: {n['uploaded']} {verb}uploaded, {n['deleted']} {verb}deleted, {n['reordered']} set(s) {verb}reordered, "
          f"{n['full']} set(s) skipped (would go over {MAX_PER_SET}), {n['left']} set(s) left as they were, "
          f"{n['would_delete']} not deleted without confirm_delete, {n['failed']} failed." + (" (dry run)" if dry else ""))
    return 1 if n["failed"] else 0


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


def main() -> int:
    wanted = set(os.environ.get("DISPLAY_TYPES", "").split())
    add = set(os.environ.get("SCREENS", "").split())
    remove = set(os.environ.get("REMOVE_SCREENS", "").split())
    confirm = os.environ.get("CONFIRM_DELETE", "") == "true"  # exactly "true", nothing else
    text = os.environ.get("TEXT", "").lower() == "true"
    platforms = os.environ.get("PLATFORMS", "").split()
    dry = os.environ.get("DRY_RUN", "").lower() == "true"
    if not wanted and not text:
        print("::error::DISPLAY_TYPES is empty; name the screenshot sets to upload (e.g. APP_WATCH_ULTRA).")
        return 1
    if (add or remove) and not wanted:
        print("::error::SCREENS and REMOVE_SCREENS need DISPLAY_TYPES (the sets to change).")
        return 1
    if add & remove:
        print("::error::A screen is in both SCREENS and REMOVE_SCREENS: " + " ".join(sorted(add & remove)))
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
        unknown = sorted(add - {screen_of_entry(e) for e in entries})
        if unknown:
            print("::error::The manifest has no screenshots of " + " ".join(unknown) + " for " + " ".join(sorted(wanted)))
            return 1
        stray = sorted(remove - known_screens(manifest, set()))
        if stray:
            print("::warning::Not a known screen name (check the spelling): " + " ".join(stray))
        check = [e for e in entries if screen_of_entry(e) in add] if (add or remove) else entries
        for e in check:
            f = store / e["file"]
            if not f.exists() or hashlib.md5(f.read_bytes()).hexdigest() != e["md5"]:
                print(f"::error::{e['file']} is missing or differs from the manifest")
                return 1
        app = find_app()
        if add or remove:
            status |= update_sets(store, manifest, entries, app, add, remove, confirm, dry)
        else:
            status |= fill_empty(store, entries, app, dry)
    if text:
        app = app or find_app()
        status |= update_text(app, platforms, json.loads(LISTING.read_text()), dry)
    return status


if __name__ == "__main__":
    sys.exit(main())
