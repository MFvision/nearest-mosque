#!/usr/bin/env python3
"""Uploads an Android App Bundle to Google Play as a draft release (CI, Google Play Developer API v3).

    play_upload.py <app.aab>

Environment: PLAY_SERVICE_ACCOUNT_JSON (the service account's JSON key, the whole file), PACKAGE_NAME
(e.g. sa.zood.nearmosque), TRACK (internal | closed | production; "closed" is Play's default closed track,
"alpha"), RELEASE_NAME (optional, shown in Play Console), DRY_RUN ("true": sign in and open an edit, then
discard it without uploading).

It uses the edits flow: open an edit, upload the bundle, put its version code on the track as a release with
status "draft", commit. The R8 mapping travels inside the bundle (BUNDLE-METADATA), so Play deobfuscates crash
stacks without a separate upload. A draft release
is never sent to testers or users; someone presses "Review release" / "Start rollout" in Play Console. The
first release of a new app must be uploaded by hand in Play Console (docs/12-google-play.md); the API cannot
create it. Logs show the track, the version code and HTTP status only, never the service account's email or
key. Needs PyJWT and cryptography.
"""
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

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
TRACKS = {"internal": "internal", "closed": "alpha", "production": "production"}
EMAIL = re.compile(r"[\w.+-]+@[\w-]+(\.[\w-]+)+")


def access_token(key: dict) -> str:
    now = int(time.time())
    token_uri = key.get("token_uri") or "https://oauth2.googleapis.com/token"
    assertion = jwt.encode(
        {"iss": key["client_email"], "scope": SCOPE, "aud": token_uri, "iat": now, "exp": now + 3600},
        key["private_key"], algorithm="RS256", headers={"kid": key.get("private_key_id", "")},
    )
    body = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion}).encode()
    req = urllib.request.Request(token_uri, data=body, method="POST", headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read())["access_token"]
    except urllib.error.HTTPError as e:
        # Only the OAuth error code (the description can name the account).
        try:
            code = json.loads(e.read() or b"{}").get("error", "")
        except ValueError:
            code = ""
        print(f"Google sign-in with the service account failed: HTTP {e.code} {code}", flush=True)
        raise SystemExit(1)


class Play:
    def __init__(self, token: str, package: str):
        self.token = token
        self.package = package

    def call(self, method: str, url: str, body: dict | None = None, data: bytes | None = None, timeout: int = 120,
             content_type: str = "application/octet-stream") -> dict:
        headers = {"Authorization": "Bearer " + self.token}
        if data is not None:
            headers["Content-Type"] = content_type
        elif body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        label = url.split("/applications/")[-1].split("?")[0].replace(self.package, "<app>")
        for attempt in range(4):
            req = urllib.request.Request(url, data=data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=timeout) as r:
                    text = r.read()
                    return json.loads(text) if text else {}
            except urllib.error.HTTPError as e:
                if e.code in (429, 500, 502, 503) and attempt < 3:
                    time.sleep(10 * (attempt + 1))
                    continue
                try:
                    err = json.loads(e.read() or b"{}").get("error", {})
                    summary = f"{err.get('status', '')}: {EMAIL.sub('<email>', str(err.get('message', '')))[:300]}"
                except ValueError:
                    summary = ""
                print(f"Google Play API {method} {label}: HTTP {e.code} {summary}", flush=True)
                raise SystemExit(1)
        raise RuntimeError("unreachable")

    def app(self, path: str = "") -> str:
        return f"{API}/{self.package}{path}"


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    aab = pathlib.Path(sys.argv[1])
    package = os.environ["PACKAGE_NAME"]
    track_input = os.environ.get("TRACK", "internal")
    if track_input not in TRACKS:
        print(f"Unknown track {track_input!r}; use one of {', '.join(TRACKS)}.")
        return 2
    track = TRACKS[track_input]
    dry = os.environ.get("DRY_RUN", "").lower() == "true"
    try:
        key = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
        key["client_email"], key["private_key"]
    except (KeyError, ValueError):
        print("PLAY_SERVICE_ACCOUNT_JSON is not a service account JSON key (it needs client_email and private_key).")
        return 1

    play = Play(access_token(key), package)
    edit = play.call("POST", play.app("/edits"), body={})["id"]
    print(f"Opened an edit for track {track_input} ({track}).", flush=True)
    if dry:
        play.call("DELETE", play.app(f"/edits/{edit}"))
        print("Dry run: the service account can open an edit; nothing was uploaded and the edit was discarded.")
        return 0

    size = aab.stat().st_size
    print(f"Uploading the bundle ({size / 1e6:.1f} MB)...", flush=True)
    bundle = play.call("POST", f"{UPLOAD}/{package}/edits/{edit}/bundles?uploadType=media", data=aab.read_bytes(), timeout=1800)
    version = int(bundle["versionCode"])
    print(f"Bundle uploaded: version code {version}.", flush=True)

    release = {"versionCodes": [str(version)], "status": "draft"}
    name = os.environ.get("RELEASE_NAME", "").strip()
    if name:
        release["name"] = name[:50]
    play.call("PUT", play.app(f"/edits/{edit}/tracks/{track}"), body={"track": track, "releases": [release]})
    play.call("POST", play.app(f"/edits/{edit}:commit"))
    print(f"Committed: version code {version} is a draft release on the {track_input} track. "
          "Review and roll it out in Play Console.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
