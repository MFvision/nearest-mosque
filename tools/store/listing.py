#!/usr/bin/env python3
"""Builds the App Store listing text for every store locale and platform from shared/store/listing/<lang>.json.

    listing.py           # write shared/store/listing.json
    listing.py --check   # fail when a field is over App Store Connect's limit or the output is out of date

Each app language file holds the pieces (subtitle, promotional text, keywords, what's new, and the description
in sections); this assembles one description per platform, because the devices differ: the Mac and Apple
Vision Pro have no compass, Dynamic Island or Lock Screen, and the Apple TV app has no Ask, library, widgets or
weather sky.
The output, keyed like tools/store/screens.json (App Store locale → platform → field), is what an agent pastes
into App Store Connect. Next to the platforms, each locale has "APP_INFO" for the app-level text, which belongs
to the app rather than to a version: "privacyPolicyText" (App Information → Privacy Policy Text, shown on Apple
TV; API: appInfoLocalizations), from privacy_policy_text. docs/09-app-store.md explains it.
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent.parent
SRC = REPO / "shared/store/listing"
OUT = REPO / "shared/store/listing.json"
SPEC = json.loads((HERE / "screens.json").read_text())

# App Store Connect limits (characters). privacyPolicyText: Apple documents no limit for it that this repo has a
# source for, so it is held to the description's 4000.
LIMITS = {"name": 30, "subtitle": 30, "promotionalText": 170, "keywords": 100, "description": 4000, "whatsNew": 4000,
          "privacyPolicyText": 4000}
PLATFORMS = ["IOS", "MAC_OS", "VISION_OS", "TV_OS"]
# The app-level fields (appInfoLocalizations) under each locale's "APP_INFO", and the full policy they link to.
APP_INFO = "APP_INFO"
POLICY_URL = "https://nearmosque.net/privacy.html"


def bullets(items):
    return "\n".join(f"• {x}" for x in items)


def section(head, body):
    return f"{head}\n{body}"


def description(d, platform):
    if platform == "TV_OS":
        # The Apple TV app has no "Sky follows the weather", so its privacy text leaves the weather out.
        tv = d["tv"]
        return "\n\n".join([tv["intro"], bullets(tv["points"]), section(d["h_privacy"], tv["privacy"]), d["languages"]])
    phone = platform == "IOS"
    parts = [
        d["intro"],
        section(d["h_prayer"], bullets(d["prayer"])),
        section(d["h_qibla"], bullets(d["qibla_live"] if phone else d["qibla_north"])),
        section(d["h_mosques"], bullets(d["mosques"])),
        section(d["h_ask"], bullets(d["ask"])),
        section(d["h_library"], bullets(d["library"])),
    ]
    if phone:
        parts.append(section(d["h_widgets"], bullets(d["widgets_ios"])))
        # The Apple Watch app ships inside the iPhone app.
        parts.append(section(d["h_watch"], bullets(d["watch"])))
    elif platform == "MAC_OS":
        parts.append(section(d["h_widgets"], bullets(d["widgets_mac"])))
    parts.append(section(d["h_also"], d[{"IOS": "also_ios", "MAC_OS": "also_mac", "VISION_OS": "also_vision"}[platform]]))
    parts.append(section(d["h_privacy"], d["privacy"]))
    parts.append(d["languages"])
    return "\n\n".join(parts)


def build():
    out, problems = {}, []
    for lang in SPEC["languages"]:
        path = SRC / f"{lang['app']}.json"
        if not path.exists():
            problems.append(f"missing {path.relative_to(REPO)}")
            continue
        d = json.loads(path.read_text())
        per = {}
        for platform in PLATFORMS:
            tv = platform == "TV_OS"
            fields = {
                "subtitle": d["subtitle"],
                "promotionalText": d["promo_tv"] if tv else d["promo"],
                "keywords": d["tv"]["keywords"] if tv else d["keywords"],
                "description": description(d, platform),
                "whatsNew": d["whats_new"] if platform == "IOS" else d["whats_new_other"],
            }
            for k, v in fields.items():
                if len(v) > LIMITS[k]:
                    problems.append(f"{lang['app']} {platform} {k}: {len(v)} > {LIMITS[k]}")
            if any(" ," in w or w != w.strip() for w in [fields["keywords"]]):
                problems.append(f"{lang['app']} keywords: no spaces around commas")
            per[platform] = fields
        policy = d.get("privacy_policy_text")
        if not isinstance(policy, str) or not policy.strip():
            problems.append(f"{lang['app']} privacy_policy_text: missing")
        else:
            if len(policy) > LIMITS["privacyPolicyText"]:
                problems.append(f"{lang['app']} privacyPolicyText: {len(policy)} > {LIMITS['privacyPolicyText']}")
            if POLICY_URL not in policy:
                problems.append(f"{lang['app']} privacy_policy_text: does not link {POLICY_URL}")
            per[APP_INFO] = {"privacyPolicyText": policy}
        for locale in lang["asc"]:
            out[locale] = {"appLanguage": lang["app"], "sameAs": None if locale == lang["asc"][0] else lang["asc"][0], **per}
    return out, problems


def main():
    out, problems = build()
    text = json.dumps({
        "about": "App Store listing text per App Store locale and platform (IOS = iPhone and iPad). Built by "
                 "tools/store/listing.py from shared/store/listing/<language>.json; do not edit by hand. "
                 "Translations other than English and Arabic are drafts awaiting native review.",
        "appInfo": "APP_INFO in each locale is app-level text (appInfoLocalizations), not a platform: "
                   "privacyPolicyText is App Information → Privacy Policy Text, shown on Apple TV.",
        "limits": LIMITS,
        "locales": out,
    }, ensure_ascii=False, indent=1) + "\n"
    for p in problems:
        print(p)
    if "--check" in sys.argv:
        if problems or not OUT.exists() or OUT.read_text() != text:
            print("listing.json is out of date or over a limit; run tools/store/listing.py")
            sys.exit(1)
        print(f"{len(out)} locales OK")
        return
    OUT.write_text(text)
    print(f"{len(out)} locales written, {len(problems)} problems")


if __name__ == "__main__":
    main()
