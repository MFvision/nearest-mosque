#!/usr/bin/env python3
"""Google Play store listing: the text and graphics of every listing language (CI, Google Play Developer API v3).

    play_listing.py check                 show what Play has now (languages, text lengths, image counts); changes nothing
    play_listing.py apply <assets dir>    replace the listed languages' text and graphics with ours, then commit

Text comes from shared/store/play/listing.json (tools/store/play_listing_build.py). Graphics come from <assets dir>
as built by tools/store/play_graphics.py (.github/workflows/store-play-assets.yml):
    icon.png, <app language>/feature.png, <app language>/{phone,seven,ten}/NN-*.png
(<app language>/desktop and xr, the Chromebook and Android XR screenshots, are not uploaded: the API has no image
type for them, so they go up by hand in Play Console.)
A language with no graphics of its own uses the English ones. Store contact details (email, website) are set too; the
default language stays as chosen in Play Console unless DEFAULT_LANGUAGE is given. The edit is committed only after every call succeeded, so a failure leaves
the store as it was. Nothing here touches releases or tracks.

Environment: PLAY_SERVICE_ACCOUNT_JSON, PACKAGE_NAME, optional LOCALES (space-separated Play locale codes to limit
apply to), NOT_FOR_REVIEW ("true": commit with changesNotSentForReview, for apps where Play asks for it).
Logs never show the service account's email or key.
"""
import json
import os
import pathlib
import sys
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from play_upload import API, UPLOAD, Play, access_token  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
LISTING = ROOT / "shared/store/play/listing.json"
CONTACT = {"contactEmail": "app@vision.bz", "contactWebsite": "https://nearmosque.net/"}
IMAGE_TYPES = ("icon", "featureGraphic", "phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots")
# Folder under <assets>/<app language>/ for each kind of screenshot (7-inch: an unfolded foldable such as the
# Galaxy Z Fold's inner screen; 10-inch: a tablet).
SHOT_DIRS = {"phoneScreenshots": "phone", "sevenInchScreenshots": "seven", "tenInchScreenshots": "ten"}


def upload_image(play: Play, edit: str, lang: str, kind: str, path: pathlib.Path) -> None:
    play.call("POST", f"{UPLOAD}/{play.package}/edits/{edit}/listings/{lang}/{kind}?uploadType=media",
              data=path.read_bytes(), content_type="image/png")


def graphics(assets: pathlib.Path, app_lang: str) -> dict:
    def pick(lang: str) -> dict:
        d = assets / lang
        out = {"featureGraphic": [d / "feature.png"] if (d / "feature.png").exists() else []}
        for kind, sub in SHOT_DIRS.items():
            out[kind] = sorted((d / sub).glob("*.png")) if (d / sub).is_dir() else []
        return out
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
    details = dict(CONTACT)
    if os.environ.get("DEFAULT_LANGUAGE"):
        details["defaultLanguage"] = os.environ["DEFAULT_LANGUAGE"]
    play.call("PATCH", play.app(f"/edits/{edit}/details"), body=details)
    print("Contact details set" + (f", default language {details['defaultLanguage']}." if "defaultLanguage" in details else
                                   " (the default language stays as set in Play Console)."), flush=True)
    def one(lang: str, text: dict) -> str:
        play.call("PUT", play.app(f"/edits/{edit}/listings/{lang}"), body={
            "language": lang, "title": text["title"], "shortDescription": text["shortDescription"],
            "fullDescription": text["fullDescription"]})
        g = graphics(assets, text["appLanguage"])
        for kind in IMAGE_TYPES:
            if not g[kind]:
                continue
            play.call("DELETE", play.app(f"/edits/{edit}/listings/{lang}/{kind}"))
            # One after another within a slot, so the screenshots keep their order on the store page.
            for path in g[kind][:8]:
                upload_image(play, edit, lang, kind, path)
        return (f"  {lang:7s} text, icon {len(g['icon'])}, feature graphic {len(g['featureGraphic'])}, "
                f"phone {len(g['phoneScreenshots'][:8])}, 7-inch {len(g['sevenInchScreenshots'][:8])}, "
                f"10-inch {len(g['tenInchScreenshots'][:8])} screenshots")

    # Several languages at once: each language's calls stay in order, and a failure stops the run before the commit.
    with ThreadPoolExecutor(max_workers=int(os.environ.get("WORKERS", "6"))) as pool:
        for line in pool.map(lambda kv: one(*kv), sorted(locales.items())):
            print(line, flush=True)


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
    play = Play(access_token(key), os.environ["PACKAGE_NAME"], key)
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
