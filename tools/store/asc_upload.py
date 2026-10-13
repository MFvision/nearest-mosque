#!/usr/bin/env python3
"""Uploads finished App Store screenshots to App Store Connect (CI, App Store Connect API).

    asc_upload.py <store folder>        # the folder finish.py wrote (manifest.json and APP_* folders)

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY (.p8 contents), BUNDLE_ID,
DISPLAY_TYPES (space-separated, e.g. "APP_WATCH_ULTRA"; required, so a run never touches more than asked),
DRY_RUN ("true": report what would be uploaded and change nothing).

It only fills empty slots: a screenshot set that already holds screenshots is left as it is and reported, so
nothing already in App Store Connect is replaced or deleted (docs/09-app-store.md). It uses the editable version
of each platform (Prepare for Submission or rejected). Logs show locales and counts only.
Needs PyJWT and cryptography.
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


def upload_file(set_id: str, path: pathlib.Path, md5: str) -> str:
    data = path.read_bytes()
    shot = api("POST", "/appScreenshots", body={"data": {
        "type": "appScreenshots",
        "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": set_id}}},
    }})["data"]
    for op in shot["attributes"]["uploadOperations"]:
        part = data[op["offset"]: op["offset"] + op["length"]]
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders", [])}
        req = urllib.request.Request(op["url"], data=part, method=op["method"], headers=headers)
        with urllib.request.urlopen(req, timeout=300):
            pass
    api("PATCH", f"/appScreenshots/{shot['id']}", body={"data": {
        "type": "appScreenshots", "id": shot["id"], "attributes": {"uploaded": True, "sourceFileChecksum": md5}}})
    return shot["id"]


def wait_complete(ids: list[str], minutes: int = 15) -> tuple[int, int]:
    deadline = time.time() + minutes * 60
    pending = set(ids)
    failed = 0
    while pending and time.time() < deadline:
        for i in list(pending):
            state = (api("GET", f"/appScreenshots/{i}")["data"]["attributes"].get("assetDeliveryState") or {}).get("state")
            if state == "COMPLETE":
                pending.discard(i)
            elif state == "FAILED":
                pending.discard(i)
                failed += 1
        if pending:
            time.sleep(10)
    return len(ids) - len(pending) - failed, failed


def main() -> int:
    store = pathlib.Path(sys.argv[1])
    manifest = json.loads((store / "manifest.json").read_text())
    wanted = set(os.environ.get("DISPLAY_TYPES", "").split())
    if not wanted:
        print("::error::DISPLAY_TYPES is empty; name the screenshot sets to upload (e.g. APP_WATCH_ULTRA).")
        return 1
    dry = os.environ.get("DRY_RUN", "").lower() == "true"
    entries = [e for e in manifest["screenshots"] if e["displayType"] in wanted]
    if not entries:
        print("::error::The manifest has no screenshots for " + " ".join(sorted(wanted)))
        return 1
    for e in entries:
        f = store / e["file"]
        if not f.exists() or hashlib.md5(f.read_bytes()).hexdigest() != e["md5"]:
            print(f"::error::{e['file']} is missing or differs from the manifest")
            return 1

    app = api("GET", "/apps", {"filter[bundleId]": os.environ["BUNDLE_ID"]})["data"][0]
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
            ids = [upload_file(set_id, store / e["file"], e["md5"]) for e in files]
            done, failed = wait_complete(ids)
            failed_total += failed
            if done == len(ids):
                api("PATCH", f"/appScreenshotSets/{set_id}/relationships/appScreenshots",
                    body={"data": [{"type": "appScreenshots", "id": i} for i in ids]})
            uploaded += done
            print(f"{dt} {locale}: uploaded {done} of {len(ids)}" + (f", {failed} failed processing" if failed else ""), flush=True)
    print(f"Done: {uploaded} uploaded, {skipped} set(s) left as they were, {failed_total} failed." + (" (dry run)" if dry else ""))
    return 1 if failed_total else 0


if __name__ == "__main__":
    sys.exit(main())
