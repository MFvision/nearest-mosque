#!/usr/bin/env python3
"""App Store review submissions of the app (CI, App Store Connect API).

    asc_review.py status    # each platform's newest versions and their states, and the open review submissions
    asc_review.py cancel    # cancel the submissions waiting for review (only with CONFIRM=true), then report again
    asc_review.py submit    # attach BUILD to each platform's editable version and submit it for review (CONFIRM=true)

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY (.p8 contents), BUNDLE_ID, PLATFORMS (space-separated, default
IOS MAC_OS TV_OS VISION_OS), CONFIRM ("true" to really cancel or submit), BUILD (submit: the build number).
Cancelling takes a version out of review so its build, screenshots and text can change again; it changes no build,
metadata or release setting. A submission already in review is reported and left alone. Submitting attaches the
processed build with that number to the platform's version that can still be edited, keeps or sets automatic release
after approval, and sends the version for review; a platform with no editable version or no such build is left alone.
Logs show platforms, states, build numbers and dates only. Needs PyJWT and cryptography.
"""
import os
import sys
import time
import urllib.error

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_upload import all_pages, api  # noqa: E402

CANCELLABLE = {"WAITING_FOR_REVIEW"}
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}
SETTLED = {"COMPLETE", "READY_FOR_REVIEW", "UNRESOLVED_ISSUES", "IN_REVIEW"}


def versions(app_id: str, platforms: list[str]) -> None:
    for p in platforms:
        vs = all_pages(f"/apps/{app_id}/appStoreVersions", {"filter[platform]": p, "limit": "5"})
        states = ", ".join(f"{v['attributes'].get('versionString')} {v['attributes'].get('appVersionState') or v['attributes'].get('appStoreState')}"
                           for v in vs[:2]) or "no version"
        print(f"{p}: {states}", flush=True)


def submissions(app_id: str, platforms: list[str]) -> list[dict]:
    subs = all_pages("/reviewSubmissions", {"filter[app]": app_id, "limit": "50"})
    return [s for s in subs if s["attributes"].get("platform") in platforms and s["attributes"].get("state") != "COMPLETE"]


def show(subs: list[dict]) -> None:
    if not subs:
        print("Open review submissions: none.", flush=True)
    for s in subs:
        a = s["attributes"]
        print(f"Submission {a.get('platform')}: {a.get('state')}, submitted {a.get('submittedDate') or '-'}", flush=True)


def state(v: dict) -> str:
    return v["attributes"].get("appVersionState") or v["attributes"].get("appStoreState") or ""


def submit(app_id: str, platform: str, build_number: str, confirm: bool) -> bool:
    """Attaches the build to the platform's editable version and submits it; False when something is missing."""
    vs = all_pages(f"/apps/{app_id}/appStoreVersions", {"filter[platform]": platform, "limit": "5"})
    editable = [v for v in vs if state(v) in EDITABLE]
    if not editable:
        print(f"::warning::{platform}: no version that can be submitted ({', '.join(state(v) for v in vs[:2]) or 'no version'}).", flush=True)
        return False
    v = editable[0]
    name = f"{platform} {v['attributes'].get('versionString')}"
    builds = api("GET", "/builds", {"filter[app]": app_id, "filter[version]": build_number,
                                    "filter[preReleaseVersion.platform]": platform, "filter[processingState]": "VALID", "limit": "5"})["data"]
    builds = [b for b in builds if not b["attributes"].get("expired")]
    if not builds:
        print(f"::warning::{name}: no processed build {build_number} for this platform.", flush=True)
        return False
    build = builds[0]
    current = api("GET", f"/appStoreVersions/{v['id']}/build").get("data")
    attached = current["attributes"].get("version") if current else "none"
    release = v["attributes"].get("releaseType")
    print(f"{name}: {state(v)}, build {attached} attached, release {release}", flush=True)
    if not confirm:
        print(f"{name}: would attach build {build_number}, keep automatic release and submit; run again with confirm on to do it.", flush=True)
        return True
    if attached != build_number:
        api("PATCH", f"/appStoreVersions/{v['id']}/relationships/build", body={"data": {"type": "builds", "id": build["id"]}})
        print(f"{name}: build {build_number} attached.", flush=True)
    if release != "AFTER_APPROVAL":
        api("PATCH", f"/appStoreVersions/{v['id']}",
            body={"data": {"type": "appStoreVersions", "id": v["id"], "attributes": {"releaseType": "AFTER_APPROVAL"}}})
        print(f"{name}: release set to automatic after approval.", flush=True)
    # A draft submission for this platform (left from an earlier try) is used again; Apple allows one at a time.
    drafts = [s for s in submissions(app_id, [platform]) if s["attributes"].get("state") == "READY_FOR_REVIEW"]
    if drafts:
        sub = drafts[0]
    else:
        sub = api("POST", "/reviewSubmissions", body={"data": {"type": "reviewSubmissions", "attributes": {"platform": platform},
                                                               "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]
    items = all_pages(f"/reviewSubmissions/{sub['id']}/items", {"include": "appStoreVersion", "limit": "50"})
    if not any((i.get("relationships", {}).get("appStoreVersion", {}).get("data") or {}).get("id") == v["id"] for i in items):
        api("POST", "/reviewSubmissionItems", body={"data": {"type": "reviewSubmissionItems", "relationships": {
            "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sub["id"]}},
            "appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
    api("PATCH", f"/reviewSubmissions/{sub['id']}", body={"data": {"type": "reviewSubmissions", "id": sub["id"], "attributes": {"submitted": True}}})
    print(f"{name}: submitted for review with build {build_number}.", flush=True)
    return True


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "status"
    if mode not in ("status", "cancel", "submit"):
        print(__doc__)
        return 2
    platforms = os.environ.get("PLATFORMS", "").split() or ["IOS", "MAC_OS", "TV_OS", "VISION_OS"]
    app = api("GET", "/apps", {"filter[bundleId]": os.environ["BUNDLE_ID"]})["data"][0]
    versions(app["id"], platforms)
    subs = submissions(app["id"], platforms)
    show(subs)
    if mode == "status":
        return 0
    if mode == "submit":
        build_number = os.environ.get("BUILD", "").strip()
        if not build_number.isdigit():
            print("::error::Give the build number to submit (BUILD).", flush=True)
            return 2
        confirm = os.environ.get("CONFIRM", "").lower() == "true"
        ok = []
        for p in platforms:
            try:
                ok.append(submit(app["id"], p, build_number, confirm))
            except urllib.error.HTTPError:
                print(f"::error::{p}: not submitted (Apple's answer above).", flush=True)
                ok.append(False)
        if confirm:
            print("After submitting:", flush=True)
            versions(app["id"], platforms)
            show(submissions(app["id"], platforms))
        return 0 if all(ok) else 1
    targets = [s for s in subs if s["attributes"].get("state") in CANCELLABLE]
    for s in subs:
        if s["attributes"].get("state") == "IN_REVIEW":
            print(f"::warning::{s['attributes'].get('platform')}: already in review; left alone.", flush=True)
    if not targets:
        print("Nothing waiting for review to cancel.", flush=True)
        return 0
    if os.environ.get("CONFIRM", "").lower() != "true":
        print(f"Would cancel {len(targets)} submission(s); run again with confirm on to do it.", flush=True)
        return 0
    for s in targets:
        api("PATCH", f"/reviewSubmissions/{s['id']}",
            body={"data": {"type": "reviewSubmissions", "id": s["id"], "attributes": {"canceled": True}}})
        print(f"Cancel requested: {s['attributes'].get('platform')}", flush=True)
    # Apple moves a cancelled submission through CANCELING to COMPLETE; the version then becomes editable.
    for _ in range(40):
        time.sleep(15)
        left = [s for s in submissions(app["id"], platforms) if s["attributes"].get("state") not in SETTLED]
        if not left:
            break
    print("After cancelling:", flush=True)
    versions(app["id"], platforms)
    show(submissions(app["id"], platforms))
    return 0


if __name__ == "__main__":
    sys.exit(main())
