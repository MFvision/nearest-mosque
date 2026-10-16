#!/usr/bin/env python3
"""Google Play store listing: the text and graphics of every listing language (CI, Google Play Developer API v3).

    play_listing.py check                 show what Play has now (languages, text lengths, image counts); changes nothing
    play_listing.py apply <assets dir>    replace the listed languages' text and graphics with ours, then commit

Text comes from shared/store/play/listing.json (tools/store/play_listing_build.py). Graphics come from <assets dir>
as built by tools/store/play_graphics.py (.github/workflows/store-play-assets.yml):
    icon.png, <app language>/feature.png, <app language>/phone/NN-*.png
A language with no graphics of its own uses the English ones. Store contact details (email, website) and the
default language (en-US) are set too. The edit is committed only after every call succeeded, so a failure leaves
the store as it was. Nothing here touches releases or tracks.

Environment: PLAY_SERVICE_ACCOUNT_JSON, PACKAGE_NAME, optional LOCALES (space-separated Play locale codes to limit
apply to), NOT_FOR_REVIEW ("true": commit with changesNotSentForReview, for apps where Play asks for it).
Logs never show the service account's email or key.
"""
import json
import os
import pathlib
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from play_upload import API, UPLOAD, Play, access_token  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
LISTING = ROOT / "shared/store/play/listing.json"
CONTACT = {"defaultLanguage": "en-US", "contactEmail": "app@vision.bz", "contactWebsite": "https://nearmosque.net/"}
IMAGE_TYPES = ("icon", "featureGraphic", "phoneScreenshots")


def upload_image(play: Play, edit: str, lang: str, kind: str, path: pathlib.Path) -> None:
    play.call("POST", f"{UPLOAD}/{play.package}/edits/{edit}/listings/{lang}/{kind}?uploadType=media",
              data=path.read_bytes(), content_type="image/png")


def graphics(assets: pathlib.Path, app_lang: str) -> dict:
    def pick(lang: str) -> dict:
        d = assets / lang
        return {"featureGraphic": [d / "feature.png"] if (d / "feature.png").exists() else [],
                "phoneScreenshots": sorted((d / "phone").glob("*.png")) if (d / "phone").is_dir() else []}
    own, en = pick(app_lang), pick("en")
    out = {k: own[k] or en[k] for k in own}
    out["icon"] = [assets / "icon.png"] if (assets / "icon.png").exists() else []
    return out


def check(play: Play, edit: str, ours: dict) -> None:
    have = {l["language"]: l for l in play.call("GET", play.app(f"/edits/{edit}/listings")).get("listings", [])}
    details = play.call("GET", play.app(f"/edits/{edit}/details"))
    print(f"Default language: {details.get('defaultLanguage', '?')}; contact website set: {bool(details.get('contactWebsite'))}")
    print(f"Play has {len(have)} listing language(s); ours has {len(ours)}.")
    for lang in sorted(set(have) | set(ours)):
        l = have.get(lang)
        counts = []
        if l:
            for kind in IMAGE_TYPES:
                n = len(play.call("GET", play.app(f"/edits/{edit}/listings/{lang}/{kind}")).get("images", []))
                counts.append(f"{kind} {n}")
        state = "on Play" if l else "missing on Play"
        extra = "" if lang in ours else "  (not in ours)"
        title = f"title {len(l.get('title', ''))} ch" if l else ""
        print(f"  {lang:7s} {state:16s} {title:12s} {', '.join(counts)}{extra}")


def apply(play: Play, edit: str, ours: dict, assets: pathlib.Path) -> None:
    limit = os.environ.get("LOCALES", "").split()
    locales = {k: v for k, v in ours.items() if not limit or k in limit}
    missing = [k for k in limit if k not in ours]
    if missing:
        raise SystemExit(f"Not in listing.json: {' '.join(missing)}")
    play.call("PATCH", play.app(f"/edits/{edit}/details"), body=CONTACT)
    print("Contact details and default language set.", flush=True)
    for lang, text in sorted(locales.items(), key=lambda kv: kv[0] != "en-US"):
        play.call("PUT", play.app(f"/edits/{edit}/listings/{lang}"), body={
            "language": lang, "title": text["title"], "shortDescription": text["shortDescription"],
            "fullDescription": text["fullDescription"]})
        g = graphics(assets, text["appLanguage"])
        for kind in IMAGE_TYPES:
            if not g[kind]:
                continue
            play.call("DELETE", play.app(f"/edits/{edit}/listings/{lang}/{kind}"))
            for path in g[kind][:8]:
                upload_image(play, edit, lang, kind, path)
        print(f"  {lang:7s} text, icon {len(g['icon'])}, feature graphic {len(g['featureGraphic'])}, "
              f"phone screenshots {len(g['phoneScreenshots'][:8])}", flush=True)


def main() -> int:
    if len(sys.argv) < 2 or sys.argv[1] not in ("check", "apply") or (sys.argv[1] == "apply" and len(sys.argv) < 3):
        print(__doc__)
        return 2
    mode = sys.argv[1]
    ours = json.loads(LISTING.read_text(encoding="utf-8"))["locales"]
    try:
        key = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
        key["client_email"], key["private_key"]
    except (KeyError, ValueError):
        print("PLAY_SERVICE_ACCOUNT_JSON is not a service account JSON key (it needs client_email and private_key).")
        return 1
    play = Play(access_token(key), os.environ["PACKAGE_NAME"])
    edit = play.call("POST", play.app("/edits"), body={})["id"]
    if mode == "check":
        check(play, edit, ours)
        play.call("DELETE", play.app(f"/edits/{edit}"))
        print("Check only: nothing was changed.")
        return 0
    assets = pathlib.Path(sys.argv[2])
    if not (assets / "en").is_dir():
        print(f"{assets} has no en/ graphics; build them with .github/workflows/store-play-assets.yml.")
        return 1
    apply(play, edit, ours, assets)
    q = "?changesNotSentForReview=true" if os.environ.get("NOT_FOR_REVIEW", "").lower() == "true" else ""
    play.call("POST", play.app(f"/edits/{edit}:commit{q}"))
    print("Committed: the store listing is updated in Play Console.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
