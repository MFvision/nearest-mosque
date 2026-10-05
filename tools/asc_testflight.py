#!/usr/bin/env python3
"""TestFlight helper for CI (App Store Connect API).

Reports the latest builds of the app, optionally waits for a given build to finish Apple's processing,
answers export compliance (the app only uses HTTPS: usesNonExemptEncryption = false) and gives every
internal TestFlight group access to the build.

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY (.p8 contents), BUNDLE_ID,
optional BUILD_NUMBER (default: newest build), WAIT_MINUTES (default 0).
Needs PyJWT and cryptography.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import jwt

BASE = "https://api.appstoreconnect.apple.com/v1"


def token() -> str:
    now = int(time.time())
    payload = {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 900, "aud": "appstoreconnect-v1"}
    return jwt.encode(payload, os.environ["ASC_PRIVATE_KEY"], algorithm="ES256", headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"})


def api(method: str, path: str, params: dict | None = None, body: dict | None = None) -> dict:
    url = BASE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Authorization": "Bearer " + token(), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            text = r.read()
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        print(f"App Store Connect API {method} {path}: HTTP {e.code}: {e.read().decode(errors='replace')[:800]}")
        raise


def main() -> int:
    bundle = os.environ["BUNDLE_ID"]
    want = os.environ.get("BUILD_NUMBER") or None
    wait = int(os.environ.get("WAIT_MINUTES", "0"))

    apps = api("GET", "/apps", {"filter[bundleId]": bundle, "fields[apps]": "name,bundleId"})["data"]
    if not apps:
        print(f"::error::No App Store Connect app record for bundle ID {bundle}.")
        return 1
    app_id = apps[0]["id"]
    print(f"App: {apps[0]['attributes']['name']} ({bundle}), id {app_id}")

    deadline = time.time() + wait * 60
    while True:
        builds = api("GET", "/builds", {
            "filter[app]": app_id, "sort": "-uploadedDate", "limit": "10",
            "fields[builds]": "version,processingState,uploadedDate,usesNonExemptEncryption,expired",
        })["data"]
        target = next((b for b in builds if b["attributes"]["version"] == want), None) if want else (builds[0] if builds else None)
        state = target["attributes"]["processingState"] if target else "NOT_YET_VISIBLE"
        print(f"Build {want or (target and target['attributes']['version'])}: {state}")
        if state in ("VALID", "FAILED", "INVALID") or time.time() >= deadline:
            break
        time.sleep(60)

    print("Latest builds:")
    for b in builds[:5]:
        a = b["attributes"]
        print(f"  build {a['version']}: {a['processingState']}, uploaded {a.get('uploadedDate')}, "
              f"encryption answered={a.get('usesNonExemptEncryption') is not None}, expired={a.get('expired')}")

    if not target:
        print("::warning::The build is not visible in App Store Connect yet (Apple can take a few minutes after upload).")
        return 1 if wait else 0
    if state != "VALID":
        level = "error" if state in ("FAILED", "INVALID") else "warning"
        print(f"::{level}::Build {target['attributes']['version']} is {state}. Check the email from App Store Connect for details.")
        return 1 if (wait or level == "error") else 0

    build_id = target["id"]
    if target["attributes"].get("usesNonExemptEncryption") is None:
        api("PATCH", f"/builds/{build_id}", body={"data": {"type": "builds", "id": build_id, "attributes": {"usesNonExemptEncryption": False}}})
        print("Answered export compliance: standard HTTPS only (no non-exempt encryption).")

    groups = api("GET", f"/apps/{app_id}/betaGroups", {"fields[betaGroups]": "name,isInternalGroup,hasAccessToAllBuilds", "limit": "50"})["data"]
    internal = [g for g in groups if g["attributes"].get("isInternalGroup")]
    if not internal:
        print("::warning::No internal TestFlight group yet. In App Store Connect → TestFlight → Internal Testing, create a group and add yourself.")
    for g in internal:
        name = g["attributes"]["name"]
        if g["attributes"].get("hasAccessToAllBuilds"):
            print(f"Internal group '{name}' already gets every build automatically.")
            continue
        try:
            api("POST", f"/betaGroups/{g['id']}/relationships/builds", body={"data": [{"type": "builds", "id": build_id}]})
            print(f"Added build {target['attributes']['version']} to internal group '{name}'.")
        except urllib.error.HTTPError:
            print(f"::warning::Could not add the build to '{name}'; add it in App Store Connect → TestFlight.")

    detail = api("GET", f"/builds/{build_id}/buildBetaDetail")["data"]["attributes"]
    print(f"TestFlight state: internal={detail.get('internalBuildState')}, external={detail.get('externalBuildState')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
