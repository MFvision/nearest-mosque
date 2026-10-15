#!/usr/bin/env python3
"""Uploads App Store media through the App Asset Library (App Store Connect API, iOS 27 and later): the creative
assets (product page header and search results videos) and screenshot sets the older API has no slot for (iPhone
Duo). Each file is uploaded once and placed on every App Store Connect locale of its language.

    asc_library.py discover                       # the placement types and groups Apple offers (changes nothing)
    asc_library.py census                         # what each locale holds, by type and group (changes nothing)
    asc_library.py fill <folder> <group>          # adds the screenshots a set lacks (by NN- number) and orders it
    asc_library.py creative <folder>              # <folder>/header|search|demo/<locale>.mp4 (demo: the app preview)
    asc_library.py screenshots <folder> <group>   # <folder>/<locale>/NN-*.png into that APP_SCREENSHOT group

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY, BUNDLE_ID, DRY_RUN ("true": report only).

Like asc_upload.py it only fills empty places: a locale that already has a placement of that type and group is
left as it is and reported; nothing is replaced or deleted. It uses the editable iOS version (Prepare for
Submission or rejected); placements are reviewed with that version. Logs show locales and counts only.
Needs PyJWT and cryptography.
"""
import json
import os
import pathlib
import struct
import sys
import time
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from asc_upload import EDITABLE, all_pages, api  # noqa: E402

SPEC = json.loads((pathlib.Path(__file__).resolve().parent / "screens.json").read_text())
# Folder in the artifact -> (placement type, asset category).
CREATIVE = {"header": ("PRODUCT_PAGE_HEADER_ASSET", "CREATIVE_ASSETS"), "search": ("APP_STORE_SEARCH_RESULTS_ASSET", "CREATIVE_ASSETS"),
            "demo": ("APP_PREVIEW", "APP_SCREENSHOTS_AND_PREVIEWS")}
DRY = os.environ.get("DRY_RUN", "").lower() == "true"
# creative: REPLACE="images" swaps image placements for the video, "all" swaps whatever is there (the asset stays in
# the library); GROUPS and LOCALES (space-separated) narrow where it places.
REPLACE = os.environ.get("REPLACE", "")
GROUPS = set(os.environ.get("GROUPS", "").split())
LOCALES = set(os.environ.get("LOCALES", "").split())


def ref_data() -> dict:
    return all_pages("/appAssetLibraryRefData")[0]["attributes"]


IOS_PLATFORMS = {"IPHONE_APP_STORE", "IPAD_APP_STORE", "ANY"}


def ios_groups(ref: dict) -> set:
    # iPhone and iPad groups, and DEFAULT_PROFILE (platform ANY), which the creative assets use.
    return {g["placementProfileGroupId"] for g in ref.get("placementProfileGroups", []) if g.get("platform") in IOS_PLATFORMS}


def groups_for(ref: dict, placement_type: str) -> dict:
    """Group id -> the specs it accepts, for one placement type."""
    for t in ref.get("placementTypes", []):
        if t.get("placementTypeId") == placement_type:
            out = {}
            for m in t.get("specMappings", []):
                out.setdefault(m["placementGroupId"], set()).update(m.get("specs", []))
            return out
    return {}


def spec_fits(ref: dict, spec_ids: set, width: int, height: int) -> bool:
    for s in ref.get("videoSpecs", []) + ref.get("imageSpecs", []):
        if s.get("specId") in spec_ids:
            d = s.get("dimensions", {})
            if d.get("minWidth", 0) <= width <= d.get("maxWidth", 10 ** 6) and d.get("minHeight", 0) <= height <= d.get("maxHeight", 10 ** 6):
                return True
    return False


def media_size(path: pathlib.Path) -> tuple[int, int]:
    if path.suffix == ".png":
        with path.open("rb") as f:
            head = f.read(24)
        return struct.unpack(">II", head[16:24])
    import subprocess
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout.strip()
    w, h = out.split(",")
    return int(w), int(h)


def target():
    app = api("GET", "/apps", {"filter[bundleId]": os.environ["BUNDLE_ID"]})["data"][0]
    versions = all_pages(f"/apps/{app['id']}/appStoreVersions", {"filter[platform]": "IOS", "limit": "50"})
    editable = [v for v in versions if (v["attributes"].get("appVersionState") or v["attributes"].get("appStoreState")) in EDITABLE]
    if not editable:
        # Say which versions there are and in what state, so it is clear what to do (create the next version, or
        # take the one in review back), and write it to the run's summary. A dry run ends as a warning: it had
        # nothing to change, so nothing failed.
        states = ", ".join(f"{v['attributes']['versionString']}: {(v['attributes'].get('appVersionState') or v['attributes'].get('appStoreState') or '?').replace('_', ' ').lower()}" for v in versions[:5]) or "none"
        note = (f"No editable iOS version to place assets on (versions: {states}). Create the next version in App Store Connect, "
                "or remove the one waiting for review from review, then run this again.")
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a") as f:
                f.write(f"### Nothing uploaded\n\n{note}\n")
        if DRY:
            print(f"::warning::{note}", flush=True)
            raise SystemExit(0)
        raise SystemExit(f"::error::{note}")
    version = editable[0]
    print(f"iOS version {version['attributes']['versionString']}", flush=True)
    locs = {l["attributes"]["locale"]: l["id"] for l in all_pages(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations", {"limit": "50"})}
    library = api("GET", f"/apps/{app['id']}/assetLibrary")["data"]["id"]
    return locs, library


def asc_locales(folder_locale: str) -> list:
    # A file named for a language's first App Store Connect locale serves all of that language's locales.
    for lang in SPEC["languages"]:
        if lang["asc"][0] == folder_locale:
            return lang["asc"]
    return [folder_locale]


def upload(library: str, path: pathlib.Path, kind: str, category: str, name: str) -> str:
    data = path.read_bytes()
    attrs = {"fileName": path.name, "fileSize": len(data), "category": category, "referenceName": name}
    if kind == "appAssetLibraryVideos":
        attrs["previewFrameTimeCode"] = "00:00:05:00"
    res = api("POST", f"/{kind}", body={"data": {"type": kind, "attributes": attrs,
                                                  "relationships": {"assetLibrary": {"data": {"type": "appAssetLibraries", "id": library}}}}})["data"]
    for op in res["attributes"]["uploadOperations"]:
        part = data[op["offset"]: op["offset"] + op["length"]]
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders", [])}
        for attempt in range(3):
            try:
                with urllib.request.urlopen(urllib.request.Request(op["url"], data=part, method=op["method"], headers=headers), timeout=600):
                    break
            except OSError:
                if attempt == 2:
                    raise
                time.sleep(5)
    api("PATCH", f"/{kind}/{res['id']}", body={"data": {"type": kind, "id": res["id"], "attributes": {"uploaded": True}}})
    return res["id"]


def wait_ready(kind: str, ids: list, minutes: int = 40) -> dict:
    deadline, states = time.time() + minutes * 60, {}
    while time.time() < deadline:
        for i in ids:
            if states.get(i) not in ("PREPARE_FOR_SUBMISSION", "FAILED"):
                states[i] = api("GET", f"/{kind}/{i}")["data"]["attributes"].get("state")
        if all(s in ("PREPARE_FOR_SUBMISSION", "FAILED") for s in states.values()):
            break
        time.sleep(15)
    return states


_placements: dict = {}


def placements(loc_id: str) -> list:
    if loc_id not in _placements:
        _placements[loc_id] = all_pages(f"/appStoreVersionLocalizations/{loc_id}/placements", {"limit": "200"})
    return _placements[loc_id]


def existing(loc_id: str, placement_type: str, group: str) -> list:
    # Checked here rather than with filter parameters, which the endpoint may not apply.
    return [p for p in placements(loc_id)
            if p["attributes"].get("placementType") == placement_type and p["attributes"].get("placementGroup") == group]


def census(locs: dict) -> None:
    # What one locale already holds, by type and group (counts only).
    loc = locs.get("en-US") or next(iter(locs.values()))
    counts: dict = {}
    for p in placements(loc):
        k = f"{p['attributes'].get('placementType')}/{p['attributes'].get('placementGroup')}"
        counts[k] = counts.get(k, 0) + 1
    print("en-US now holds: " + (", ".join(f"{k} x{v}" for k, v in sorted(counts.items())) or "no placements"), flush=True)


def place(asset_kind: str, asset_id: str, loc_id: str, placement_type: str, group: str) -> str:
    rel = "video" if asset_kind == "appAssetLibraryVideos" else "image"
    return api("POST", "/appAssetLibraryPlacements", body={"data": {
        "type": "appAssetLibraryPlacements", "attributes": {"placementType": placement_type, "placementGroup": group},
        "relationships": {rel: {"data": {"type": asset_kind, "id": asset_id}},
                          "appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": loc_id}}}}})["data"]["id"]


def discover() -> int:
    ref = ref_data()
    specs = {x["specId"]: x for x in ref.get("imageSpecs", []) + ref.get("videoSpecs", [])}
    for t in ref.get("placementTypes", []):
        print(f"{t['placementTypeId']} (accepts {', '.join(t.get('acceptsAssetCategories', []))}):")
        for g, ids in sorted(groups_for(ref, t["placementTypeId"]).items()):
            if g in ios_groups(ref):
                sizes = sorted({f"{specs[i]['dimensions']['minWidth']}~{specs[i]['dimensions']['maxWidth']}x"
                                f"{specs[i]['dimensions']['minHeight']}~{specs[i]['dimensions']['maxHeight']}" for i in ids if i in specs})
                print(f"  {g}: {', '.join(sizes)}")
    for f in ref.get("features", []):
        if f.get("featureId") == "APP_STORE_VERSIONS":
            for p in f.get("placementPolicies", []):
                print(f"limit {p['placementType']}: " + "; ".join(f"{l.get('maxCount')} per group" for l in p.get("groupLimits", [])[:1]))
    locs, library = target()
    print(f"asset library found; {len(locs)} localizations on the editable version")
    return 0


def creative(folder: pathlib.Path) -> int:
    ref = ref_data()
    locs, library = target()
    census(locs)
    placed = skipped = failed = 0
    sample = locs.get("en-US") or next(iter(locs.values()))
    with_shots = {p["attributes"].get("placementGroup") for p in placements(sample) if p["attributes"].get("placementType") == "APP_SCREENSHOT"}
    for sub, (ptype, category) in CREATIVE.items():
        files = sorted((folder / sub).glob("*.mp4"))
        if not files:
            continue
        w, h = media_size(files[0])
        groups = [g for g, specs in groups_for(ref, ptype).items() if g in ios_groups(ref) and spec_fits(ref, specs, w, h)]
        if ptype == "APP_PREVIEW":
            # Previews go with the screenshot sets the app has (not every device size the file would fit).
            groups = [g for g in groups if g in with_shots]
        if not groups:
            print(f"::error::{ptype}: no iOS placement group takes {w} x {h} videos (run discover).")
            return 1
        print(f"{ptype}: groups {', '.join(groups)}", flush=True)
        if GROUPS:
            groups = [g for g in groups if g in GROUPS]
            print(f"{ptype}: only {', '.join(groups)}", flush=True)
        for f in files:
            # Each locale and group gets the video where it is empty; with REPLACE, also where it holds what
            # REPLACE names (images, or anything), which is taken off only once the new video is ready.
            todo, old = [], {}
            for l in asc_locales(f.stem):
                if l not in locs or (LOCALES and l not in LOCALES):
                    continue
                for g in groups:
                    ex = existing(locs[l], ptype, g)
                    swap = [p for p in ex if REPLACE == "all" or (REPLACE == "images" and p["attributes"].get("mediaType") == "IMAGE")]
                    if not ex or swap:
                        todo.append((l, g))
                        old[(l, g)] = swap
                    else:
                        skipped += 1
            if not todo:
                print(f"{sub} {f.stem}: already placed; left as it is.", flush=True)
                continue
            replacing = sum(len(v) for v in old.values())
            if DRY:
                print(f"{sub} {f.stem}: would upload once and place on {len(todo)} locale group(s)"
                      + (f", taking off {replacing} existing placement(s) there" if replacing else "") + ".", flush=True)
                continue
            # Reference names are unique in the library: a short fingerprint of the file keeps a new cut apart.
            import hashlib
            tag = hashlib.md5(f.read_bytes()).hexdigest()[:8]
            vid = upload(library, f, "appAssetLibraryVideos", category, f"Near Mosque {sub} {f.stem} {tag}")
            state = wait_ready("appAssetLibraryVideos", [vid])[vid]
            if state != "PREPARE_FOR_SUBMISSION":
                print(f"::warning::{sub} {f.stem}: processing ended in {state}.", flush=True)
                failed += 1
                continue
            for l, g in todo:
                for p in old[(l, g)]:
                    # The placement only: its image or video stays in the asset library.
                    api("DELETE", f"/appAssetLibraryPlacements/{p['id']}")
                place("appAssetLibraryVideos", vid, locs[l], ptype, g)
                placed += 1
            print(f"{sub} {f.stem}: placed on {len(todo)} locale group(s)" + (f", replacing {replacing}" if replacing else "") + ".", flush=True)
    print(f"Done: {placed} placed, {skipped} left as they were, {failed} failed." + (" (dry run)" if DRY else ""))
    return 1 if failed else 0


def screenshots(folder: pathlib.Path, group: str) -> int:
    ref = ref_data()
    if group not in groups_for(ref, "APP_SCREENSHOT"):
        print(f"::error::{group} is not an APP_SCREENSHOT group Apple offers (run discover).")
        return 1
    locs, library = target()
    census(locs)
    placed = skipped = failed = 0
    for d in sorted(p for p in folder.iterdir() if p.is_dir()):
        files = sorted(d.glob("*.png"))[:10]
        targets = [l for l in asc_locales(d.name) if l in locs]
        empty = [l for l in targets if not existing(locs[l], "APP_SCREENSHOT", group)]
        skipped += len(targets) - len(empty)
        if not files or not empty:
            print(f"{d.name}: nothing to add.", flush=True)
            continue
        if DRY:
            print(f"{d.name}: would upload {len(files)} and place them on {len(empty)} locale(s).", flush=True)
            continue
        ids = [upload(library, f, "appAssetLibraryImages", "APP_SCREENSHOTS_AND_PREVIEWS", f"Near Mosque {group} {d.name} {f.stem}") for f in files]
        states = wait_ready("appAssetLibraryImages", ids, 20)
        ready = [i for i in ids if states.get(i) == "PREPARE_FOR_SUBMISSION"]
        failed += len(ids) - len(ready)
        for l in empty:
            order = [place("appAssetLibraryImages", i, locs[l], "APP_SCREENSHOT", group) for i in ready]
            api("POST", "/appAssetLibraryPlacementOrderingRequests", body={"data": {
                "type": "appAssetLibraryPlacementOrderingRequests", "attributes": {"placementGroup": group},
                "relationships": {"orderedPlacements": {"data": [{"type": "appAssetLibraryPlacements", "id": p} for p in order]},
                                  "appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": locs[l]}}}}})
            placed += len(order)
        print(f"{d.name}: {len(ready)} of {len(ids)} placed on {len(empty)} locale(s).", flush=True)
    print(f"Done: {placed} placed, {skipped} locale(s) left as they were, {failed} failed." + (" (dry run)" if DRY else ""))
    return 1 if failed else 0


def numbered(name: str) -> int:
    # The store position in a file name: "03-mosques-map.png" -> 3, "duo_03.png" -> 3. Names without one -> 99.
    import re
    stem = os.path.splitext(os.path.basename(name))[0]
    lead = re.match(r"(\d{1,2})(?:\D|$)", stem)
    if lead:
        return int(lead.group(1))
    tail = re.search(r"(?:^|\D)(\d{1,2})$", stem)
    return int(tail.group(1)) if tail else 99


def fill(folder: pathlib.Path, group: str) -> int:
    """Adds the screenshots a locale's set lacks (by number, NN-*.png) and orders the whole set by those numbers.
    What is already there stays; nothing is removed."""
    locs, library = target()
    added = 0
    for d in sorted(p for p in folder.iterdir() if p.is_dir()):
        files = {numbered(f.name): f for f in sorted(d.glob("*.png"))}
        for locale in [l for l in asc_locales(d.name) if l in locs]:
            loc = locs[locale]
            # One page holds them all (a locale has a few dozen placements at most), with their images' names.
            page = api("GET", f"/appStoreVersionLocalizations/{loc}/placements", {"include": "image", "limit": "200"})
            current = [p for p in page["data"] if p["attributes"].get("placementType") == "APP_SCREENSHOT" and p["attributes"].get("placementGroup") == group]
            images = {}
            for inc in page.get("included", []):
                images[inc["id"]] = inc["attributes"].get("fileName") or inc["attributes"].get("referenceName") or ""
            by_num = {}
            for p in current:
                img = ((p.get("relationships") or {}).get("image") or {}).get("data") or {}
                by_num[numbered(images.get(img.get("id"), ""))] = p["id"]
            if DRY:
                # What the names look like (file names of store screenshots, nothing personal).
                inc = page.get("included", [])
                mine = [images.get((((p.get("relationships") or {}).get("image") or {}).get("data") or {}).get("id"), "?") for p in current]
                print(f"{locale}: {len(current)} in this group, named {mine}", flush=True)
            if 99 in by_num or len(by_num) < len(current):
                print(f"::warning::{locale}: a screenshot there has no NN- number in its name; left as it is.", flush=True)
                continue
            missing = [n for n in sorted(files) if n not in by_num]
            room = 10 - len(current)
            missing = missing[:max(0, room)]
            if not missing:
                print(f"{locale}: {len(current)} in place, nothing to add.", flush=True)
                continue
            if DRY:
                print(f"{locale}: has {len(current)} ({sorted(by_num)}); would add {missing}.", flush=True)
                continue
            new = {n: upload(library, files[n], "appAssetLibraryImages", "APP_SCREENSHOTS_AND_PREVIEWS", files[n].name) for n in missing}
            states = wait_ready("appAssetLibraryImages", list(new.values()), 20)
            for n, i in new.items():
                if states.get(i) == "PREPARE_FOR_SUBMISSION":
                    by_num[n] = place("appAssetLibraryImages", i, loc, "APP_SCREENSHOT", group)
                    added += 1
            order = [by_num[n] for n in sorted(by_num)]
            api("POST", "/appAssetLibraryPlacementOrderingRequests", body={"data": {
                "type": "appAssetLibraryPlacementOrderingRequests", "attributes": {"placementGroup": group},
                "relationships": {"orderedPlacements": {"data": [{"type": "appAssetLibraryPlacements", "id": p} for p in order]},
                                  "appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": loc}}}}})
            print(f"{locale}: added {len(new)}, now {len(order)} in order.", flush=True)
    print(f"Done: {added} added." + (" (dry run)" if DRY else ""))
    return 0


def census_all() -> int:
    locs, _ = target()
    kinds = set()
    table = {}
    for locale, loc in sorted(locs.items()):
        row = {}
        for p in placements(loc):
            k = f"{p['attributes'].get('placementType')}/{p['attributes'].get('placementGroup')}"
            row[k] = row.get(k, 0) + 1
        table[locale] = row
        kinds |= set(row)
    for k in sorted(kinds):
        print(f"{k}: " + " ".join(f"{l}={table[l].get(k, 0)}" for l in sorted(table)), flush=True)
    return 0


def main() -> int:
    mode = sys.argv[1]
    if mode == "discover":
        return discover()
    if mode == "census":
        return census_all()
    if mode == "fill":
        return fill(pathlib.Path(sys.argv[2]), sys.argv[3])
    if mode == "creative":
        return creative(pathlib.Path(sys.argv[2]))
    if mode == "screenshots":
        return screenshots(pathlib.Path(sys.argv[2]), sys.argv[3])
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main())
