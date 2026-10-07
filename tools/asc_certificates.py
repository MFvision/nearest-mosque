#!/usr/bin/env python3
"""Development certificates left by CI builds (App Store Connect API).

Each TestFlight build runs on a fresh macOS machine, where Xcode's automatic signing creates a new
"Apple Development" certificate through the API key; its private key is thrown away with the machine, so
the certificate can never be used again, and Apple caps how many an account may hold. This lists them and
revokes them. Only development certificates whose name says they were created through the API are
touched; certificates made in Xcode on a person's own Mac, and distribution certificates, are left alone.
Names are never printed (the logs must not show people's names).

  python tools/asc_certificates.py list
  python tools/asc_certificates.py revoke-ci       # every development certificate created through the API
  python tools/asc_certificates.py revoke-recent   # only those created in the last few hours (end of a build)

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY. Needs PyJWT and cryptography.
"""
import datetime
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_testflight import api  # noqa: E402

DEVELOPMENT = {"DEVELOPMENT", "IOS_DEVELOPMENT"}
MARK = "created via api"


def certificates():
    out, path, params = [], "/certificates", {"limit": "200", "fields[certificates]": "name,displayName,certificateType,expirationDate"}
    while path:
        page = api("GET", path, params)
        out += page["data"]
        nxt = page.get("links", {}).get("next")
        path, params = (nxt.replace("https://api.appstoreconnect.apple.com/v1", ""), None) if nxt else (None, None)
    return out


def made_by_ci(c):
    a = c["attributes"]
    return a.get("certificateType") in DEVELOPMENT and MARK in ((a.get("name") or "") + " " + (a.get("displayName") or "")).lower()


def expires(c):
    return datetime.datetime.fromisoformat(c["attributes"]["expirationDate"].replace("Z", "+00:00"))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "list"
    certs = certificates()
    by_type = {}
    for c in certs:
        by_type[c["attributes"].get("certificateType")] = by_type.get(c["attributes"].get("certificateType"), 0) + 1
    ci = [c for c in certs if made_by_ci(c)]
    print(f"{len(certs)} certificates by type: {by_type}")
    print(f"{len(ci)} development certificates created through the API (CI builds)")
    for c in sorted(ci, key=expires):
        print(f"  {c['id']}  expires {c['attributes']['expirationDate'][:10]}")
    if mode == "list":
        return 0
    if mode == "revoke-recent":
        # Development certificates last a year, so one made during this run expires about a year from now.
        cutoff = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=365) - datetime.timedelta(hours=6)
        targets = [c for c in ci if expires(c) >= cutoff]
    elif mode == "revoke-ci":
        if not ci:
            others = sum(n for t, n in by_type.items() if t in DEVELOPMENT)
            print(f"No development certificate is marked as created through the API ({others} other development certificates); nothing revoked.")
            return 1
        targets = ci
    else:
        print(f"unknown mode {mode}")
        return 2
    for c in targets:
        api("DELETE", f"/certificates/{c['id']}")
        print(f"  revoked {c['id']}")
    print(f"Revoked {len(targets)} development certificate(s) created by CI.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
