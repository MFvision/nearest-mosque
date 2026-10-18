#!/usr/bin/env python3
"""App Store review submissions of the app (CI, App Store Connect API).

    asc_review.py status    # each platform's newest versions and their states, and the open review submissions
    asc_review.py cancel    # cancel the submissions waiting for review (only with CONFIRM=true), then report again

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY (.p8 contents), BUNDLE_ID, PLATFORMS (space-separated, default
IOS MAC_OS TV_OS VISION_OS), CONFIRM ("true" to really cancel).
Cancelling takes a version out of review so its build, screenshots and text can change again; it changes no build,
metadata or release setting. A submission already in review is reported and left alone. Logs show platforms, states
and dates only. Needs PyJWT and cryptography.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_upload import all_pages, api  # noqa: E402

CANCELLABLE = {"WAITING_FOR_REVIEW"}
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


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "status"
    if mode not in ("status", "cancel"):
        print(__doc__)
        return 2
    platforms = os.environ.get("PLATFORMS", "").split() or ["IOS", "MAC_OS", "TV_OS", "VISION_OS"]
    app = api("GET", "/apps", {"filter[bundleId]": os.environ["BUNDLE_ID"]})["data"][0]
    versions(app["id"], platforms)
    subs = submissions(app["id"], platforms)
    show(subs)
    if mode == "status":
        return 0
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
