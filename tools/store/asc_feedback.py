#!/usr/bin/env python3
"""TestFlight feedback: the crash reports and screenshot comments testers sent with "Share" (App Store
Connect API), newest first.

  asc_feedback.py <out dir>

For each crash it prints the build, device, iOS version, the process that crashed, the exception and the
crashing thread's frames, and saves the whole crash log to <out dir>. Testers' names and emails are never
printed or saved (the logs of a run can be read by everyone with access to the repository).

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY, BUNDLE_ID, optional LIMIT (default 10).
"""
import json
import os
import pathlib
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_upload import all_pages, api  # noqa: E402

# Only these fields of a submission are kept: nothing that names the tester.
KEEP = ["createdDate", "comment", "deviceModel", "osVersion", "appPlatform", "devicePlatform", "deviceFamily", "locale",
        "appUptimeInMilliseconds", "batteryPercentage", "diskBytesAvailable", "connectionType", "architecture", "buildBundleId"]


def build_of(sub: dict) -> str:
    try:
        b = api("GET", f"/{sub['type']}/{sub['id']}/build")["data"]
        return b["attributes"].get("version", "?")
    except Exception:  # noqa: BLE001
        return "?"


def summarize(text: str) -> list[str]:
    """The lines that say what crashed and where: from a JSON .ips (iOS 15 and later) or a text crash report."""
    lines = text.splitlines()
    if lines and lines[0].lstrip().startswith("{"):
        try:
            head = json.loads(lines[0])
            body = json.loads("\n".join(lines[1:]))
        except ValueError:
            body = None
        if body:
            out = [f"process: {body.get('procName', head.get('app_name', '?'))}  bundle: {head.get('bundleID', '?')}  "
                   f"version: {head.get('app_version', '?')} ({head.get('build_version', '?')})  os: {head.get('os_version', '?')}"]
            ex = body.get("exception", {})
            out.append(f"exception: {ex.get('type', '?')} {ex.get('signal', '')} {ex.get('codes', '')} {ex.get('subtype', '')}".strip())
            term = body.get("termination", {})
            if term:
                out.append(f"termination: {term.get('namespace', '')} {term.get('code', '')} {term.get('indicator', '')} "
                           f"{' '.join(term.get('reasons', []) or [])}".strip())
            if body.get("asi"):
                out.append("app-specific: " + json.dumps(body["asi"])[:600])
            images = body.get("usedImages", [])
            threads = body.get("threads", [])
            fault = body.get("faultingThread", 0)
            if 0 <= fault < len(threads):
                out.append(f"crashed thread {fault}:")
                for i, f in enumerate(threads[fault].get("frames", [])[:45]):
                    img = images[f["imageIndex"]].get("name", "?") if f.get("imageIndex", -1) < len(images) else "?"
                    sym = f.get("symbol", "") + (f" + {f['symbolLocation']}" if "symbolLocation" in f else "")
                    src = f" ({f['sourceFile']}:{f.get('sourceLine', '')})" if f.get("sourceFile") else ""
                    out.append(f"  {i:2d} {img:28s} {sym or hex(f.get('imageOffset', 0))}{src}")
            return out
    # A text report: its header and the crashed thread.
    out, in_thread = [], False
    for line in lines:
        if any(line.startswith(k) for k in ("Process:", "Identifier:", "Version:", "OS Version:", "Hardware Model:", "Exception Type:",
                                           "Exception Codes:", "Exception Subtype:", "Termination Reason:", "Triggered by Thread:")):
            out.append(line)
        if "Crashed:" in line:
            in_thread = True
            out.append(line)
            continue
        if in_thread:
            if not line.strip():
                break
            out.append(line)
    return out[:80]


def main(out_dir: str) -> None:
    out = pathlib.Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    limit = int(os.environ.get("LIMIT", "10"))
    app = api("GET", "/apps", {"filter[bundleId]": os.environ["BUNDLE_ID"]})["data"][0]
    crashes = all_pages(f"/apps/{app['id']}/betaFeedbackCrashSubmissions", {"limit": "50", "sort": "-createdDate"})[:limit]
    print(f"{len(crashes)} crash report(s)", flush=True)
    for n, c in enumerate(crashes, 1):
        a = c["attributes"]
        meta = {k: a.get(k) for k in KEEP if a.get(k) is not None}
        meta["build"] = build_of(c)
        print(f"\n=== crash {n}: {meta.get('createdDate')}  build {meta['build']}  {meta.get('deviceModel')}  "
              f"{meta.get('devicePlatform', '')} {meta.get('osVersion')}  up {int(meta.get('appUptimeInMilliseconds', 0) or 0) // 1000} s", flush=True)
        if meta.get("comment"):
            print("comment: " + meta["comment"][:300])
        (out / f"crash-{n}.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False))
        try:
            log = api("GET", f"/betaFeedbackCrashSubmissions/{c['id']}/crashLog")["data"]["attributes"].get("logText") or ""
        except Exception:  # noqa: BLE001
            log = ""
        if not log:
            print("(no crash log attached)")
            continue
        (out / f"crash-{n}.ips").write_text(log)
        for line in summarize(log):
            print(line)
    shots = all_pages(f"/apps/{app['id']}/betaFeedbackScreenshotSubmissions", {"limit": "50", "sort": "-createdDate"})[:limit]
    print(f"\n{len(shots)} screenshot feedback item(s)")
    for s in shots:
        a = s["attributes"]
        print(f"- {a.get('createdDate')}  build {build_of(s)}  {a.get('deviceModel')} {a.get('osVersion')}: {(a.get('comment') or '')[:300]}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "feedback")
