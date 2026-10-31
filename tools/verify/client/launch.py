#!/usr/bin/env python3
"""Minimal production Fabric client launcher for headless tests.

launch.py prepare            download vanilla + Fabric libraries, client jar, assets (no sounds)
launch.py run <gamedir> [extra jvm args...]   start the client (needs DISPLAY)
"""
import hashlib
import json
import os
import subprocess
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BASE = Path(__file__).resolve().parent
LIB = BASE / "libraries"
ASSETS = BASE / "assets"
MC = "1.21.11"
LOADER = "0.19.3"
VJSON = BASE / "v.json"  # Mojang version json for 1.21.11 (piston-meta)


def fetch(url, dest, sha1=None):
    dest = Path(dest)
    if dest.exists() and (sha1 is None or hashlib.sha1(dest.read_bytes()).hexdigest() == sha1):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=120) as r:
        data = r.read()
    dest.write_bytes(data)
    return dest


def maven_path(name):
    parts = name.split(":")
    group, art, ver = parts[0], parts[1], parts[2]
    cls = f"-{parts[3]}" if len(parts) > 3 else ""
    return f"{group.replace('.', '/')}/{art}/{ver}/{art}-{ver}{cls}.jar"


def allowed(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    ok = False
    for r in rules:
        os_ok = "os" not in r or r["os"].get("name") == "linux"
        if os_ok:
            ok = r["action"] == "allow"
    return ok


def classpath_entries():
    v = json.loads(VJSON.read_text())
    fab = json.loads((BASE / "fabric.json").read_text())
    entries, jobs = [], []
    seen = set()
    for lib in fab["libraries"]:
        p = maven_path(lib["name"])
        key = ":".join(lib["name"].split(":")[:2])
        seen.add(key)
        jobs.append((lib["url"].rstrip("/") + "/" + p, LIB / p, lib.get("sha1")))
        entries.append(LIB / p)
    for lib in v["libraries"]:
        if not allowed(lib):
            continue
        art = lib.get("downloads", {}).get("artifact")
        if not art:
            continue
        key = ":".join(lib["name"].split(":")[:2])
        if key in seen and len(lib["name"].split(":")) == 3:
            continue
        jobs.append((art["url"], LIB / art["path"], art["sha1"]))
        entries.append(LIB / art["path"])
    client = BASE / "client.jar"
    jobs.append((v["downloads"]["client"]["url"], client, v["downloads"]["client"]["sha1"]))
    entries.append(client)
    return v, fab, entries, jobs


def prepare():
    manifest = json.loads(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", timeout=60).read())
    fetch(next(v["url"] for v in manifest["versions"] if v["id"] == MC), VJSON)
    fetch(f"https://meta.fabricmc.net/v2/versions/loader/{MC}/{LOADER}/profile/json", BASE / "fabric.json")
    v, fab, entries, jobs = classpath_entries()
    with ThreadPoolExecutor(8) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))
    idx = v["assetIndex"]
    index = fetch(idx["url"], ASSETS / "indexes" / f"{idx['id']}.json", idx["sha1"])
    objs = json.loads(index.read_text())["objects"]
    wanted = [(k, o) for k, o in objs.items() if not k.startswith("minecraft/sounds/") and not k.startswith("minecraft/music")]
    def get(item):
        k, o = item
        h = o["hash"]
        fetch(f"https://resources.download.minecraft.net/{h[:2]}/{h}", ASSETS / "objects" / h[:2] / h, h)
    with ThreadPoolExecutor(16) as ex:
        list(ex.map(get, wanted))
    print(f"prepared: {len(entries)} classpath entries, {len(wanted)} asset objects")


def run(gamedir, extra):
    v, fab, entries, _ = classpath_entries()
    gamedir = Path(gamedir).resolve()
    cp = os.pathsep.join(str(e) for e in entries)
    cmd = ["java", "-Xmx3G", *extra, "-cp", cp, fab["mainClass"],
           "--username", "Tester", "--version", MC, "--gameDir", str(gamedir),
           "--assetsDir", str(ASSETS), "--assetIndex", v["assetIndex"]["id"],
           "--uuid", "00000000000000000000000000000001", "--accessToken", "0", "--userType", "legacy",
           "--versionType", "release", "--width", "960", "--height", "540",
           "--quickPlaySingleplayer", "tvm"]
    return subprocess.call(cmd, cwd=gamedir)


if __name__ == "__main__":
    if sys.argv[1] == "prepare":
        prepare()
    else:
        sys.exit(run(sys.argv[2], sys.argv[3:]))
