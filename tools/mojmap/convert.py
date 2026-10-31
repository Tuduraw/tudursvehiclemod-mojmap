#!/usr/bin/env python3
"""Regenerate this Mojang-mapped development copy from the Yarn release project.

The Yarn project is only READ: it is copied to a scratch folder first and every
Gradle run happens on that copy or on this project.

    python tools/mojmap/convert.py --yarn <path to the Yarn tudursvehiclemod project>
    python tools/mojmap/convert.py --yarn <...> --verify   # also compare the built jars

Steps
  1. Copy the Yarn project to build/mojmap-convert/yarn-copy and run Loom's
     migrateClientMappings on main+client sources together (the client source set
     needs the main classes visible to resolve names).
  2. Rewrite this project's build files from the Yarn ones (official Mojang
     mappings, separate jar/artifact name) and replace src/ with the migrated
     sources and the Yarn project's resources. tools/ and MOJMAP.md are kept.
  3. Apply manual_renames.json.
  4. Compile; feed javac's errors to fix_leftovers.py; repeat until it compiles.
  5. (--verify) Build both jars and compare their bytecode. Both are remapped to
     intermediary names, so they must match except for the known, harmless
     differences listed in compare_jars.py.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent.parent
sys.path.insert(0, str(HERE))
import fix_leftovers  # noqa: E402
import compare_jars  # noqa: E402

MC_VERSION = "1.21.11"
SKIP = {".git", ".gradle", "build", "run", "out", ".idea", "remappedSrc"}
GRADLEW = "gradlew.bat" if os.name == "nt" else "./gradlew"


def log(msg):
    print(f"[convert] {msg}", flush=True)


def gradle(project, *args, logfile=None):
    cmd = [GRADLEW, "--no-daemon", "--max-workers=1", *args]
    if os.name != "nt":
        (Path(project) / "gradlew").chmod(0o755)
    with open(logfile, "w", encoding="utf-8") if logfile else open(os.devnull, "w") as out:
        proc = subprocess.run(cmd, cwd=project, stdout=out, stderr=subprocess.STDOUT, shell=(os.name == "nt"))
    return proc.returncode


def copy_tree(src, dst):
    shutil.copytree(src, dst, ignore=lambda d, names: [n for n in names if n in SKIP])


# ------------------------------------------------------------------ step 1
def migrate(yarn, work):
    copy = work / "yarn-copy"
    if copy.exists():
        shutil.rmtree(copy)
    log(f"copying Yarn project to {copy}")
    copy_tree(yarn, copy)
    merged, out = work / "merged-src", work / "migrated-src"
    for d in (merged, out):
        if d.exists():
            shutil.rmtree(d)
    for ss in ("main", "client"):
        src = copy / "src" / ss / "java"
        if src.exists():
            shutil.copytree(src, merged, dirs_exist_ok=True)
    log("running migrateClientMappings (main + client sources together)")
    rc = gradle(copy, "migrateClientMappings", "--mappings", f"net.minecraft:mappings:{MC_VERSION}",
                "--input", str(merged), "--output", str(out), logfile=work / "migrate.log")
    if rc != 0:
        sys.exit(f"migrateClientMappings failed - see {work / 'migrate.log'}")
    return copy, out


# ------------------------------------------------------------------ step 2
def rewrite_build_files(yarn):
    for name in ("gradle", "gradlew", "gradlew.bat", "LICENSE", ".gitignore"):
        src, dst = yarn / name, PROJECT / name
        if not src.exists():
            continue
        if src.is_dir():
            shutil.rmtree(dst, ignore_errors=True)
            shutil.copytree(src, dst)
        else:
            shutil.copy2(src, dst)

    props = (yarn / "gradle.properties").read_text(encoding="utf-8")
    props = re.sub(r"^yarn_mappings=.*\n", "", props, flags=re.M)
    props = re.sub(r"^# NOTE: 1\.21\.11 is the LAST version.*?\n(#.*\n)*", "", props, flags=re.M)
    props = props.replace("minecraft_version=", "# Development copy using Mojang's official mappings, generated from the Yarn\n"
                          "# project by tools/mojmap/convert.py - see MOJMAP.md. Do not edit src/ by hand.\nminecraft_version=", 1)
    props = re.sub(r"^archives_base_name=(\S+)", r"archives_base_name=\1-mojmap", props, flags=re.M)
    (PROJECT / "gradle.properties").write_text(props, encoding="utf-8")

    build = (yarn / "build.gradle").read_text(encoding="utf-8")
    build, n = re.subn(r'mappings\s+"net\.fabricmc:yarn:\$\{project\.yarn_mappings\}:v2"', "mappings loom.officialMojangMappings()", build)
    if n != 1:
        sys.exit("build.gradle: Yarn mappings line not found - update rewrite_build_files()")
    (PROJECT / "build.gradle").write_text(build, encoding="utf-8")

    settings = (yarn / "settings.gradle").read_text(encoding="utf-8")
    settings = re.sub(r"rootProject\.name\s*=\s*'([^']+)'", r"rootProject.name = '\1-mojmap'", settings)
    (PROJECT / "settings.gradle").write_text(settings, encoding="utf-8")


def replace_sources(yarn, migrated):
    src = PROJECT / "src"
    if src.exists():
        shutil.rmtree(src)
    for ss_dir in sorted((yarn / "src").iterdir()):
        if not ss_dir.is_dir():
            continue
        for part in ss_dir.iterdir():
            dst = src / ss_dir.name / part.name
            if part.name != "java":
                shutil.copytree(part, dst)
                continue
            # Place each migrated file into the source set it came from.
            for f in part.rglob("*.java"):
                rel = f.relative_to(part)
                (dst / rel).parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(migrated / rel, dst / rel)


# ------------------------------------------------------------------ step 3
def apply_manual_renames():
    data = json.loads((HERE / "manual_renames.json").read_text(encoding="utf-8"))
    for entry in data["renames"]:
        path = PROJECT / entry["file"]
        if not path.exists():
            log(f"manual rename skipped (file gone): {entry['file']}")
            continue
        text = path.read_text(encoding="utf-8")
        for old, new in entry["pairs"]:
            if old in text:
                text = text.replace(old, new)
            elif new not in text:
                log(f"manual rename not applied (text not found): {entry['file']}: {old}")
        path.write_text(text, encoding="utf-8")


# ------------------------------------------------------------------ step 4
def loom_files():
    gh = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    base = gh / "caches" / "fabric-loom" / MC_VERSION
    yarn_tiny = sorted(base.glob("net.fabricmc.yarn.*/mappings.tiny"))
    moj_tiny = sorted(base.glob("loom.mappings.*layered*/mappings.tiny"))
    mc = PROJECT / ".gradle" / "loom-cache" / "minecraftMaven" / "net" / "minecraft"
    jars = [j for pat in ("minecraft-common-*/*/*.jar", "minecraft-clientOnly-*/*/*.jar")
            for j in sorted(mc.glob(pat)) if "sources" not in j.name and "layered" in str(j)]
    if not (yarn_tiny and moj_tiny and len(jars) >= 2):
        sys.exit("could not find Loom mapping files / Mojang-named Minecraft jars")
    return yarn_tiny[-1], moj_tiny[-1], jars


def compile_and_fix(yarn, work, rounds=8):
    init = work / "maxerrs.gradle"
    init.write_text("allprojects { tasks.withType(JavaCompile).configureEach { options.compilerArgs += ['-Xmaxerrs', '100000'] } }\n")
    for i in range(1, rounds + 1):
        logfile = work / f"compile-{i}.log"
        rc = gradle(PROJECT, "-I", str(init), "compileJava", "compileClientJava", logfile=logfile)
        if rc == 0:
            log(f"compiles cleanly (round {i})")
            return True
        yarn_tiny, moj_tiny, jars = loom_files()
        args = ["--yarn", str(yarn_tiny), "--mojmap", str(moj_tiny), "--yarn-root", str(yarn),
                "--project-root", str(PROJECT), "--log", str(logfile)]
        for j in jars:
            args += ["--jar", str(j)]
        changed = fix_leftovers.main(args)
        if not changed:
            log(f"no automatic fix left; see {logfile} and add entries to manual_renames.json")
            return False
    return False


# ------------------------------------------------------------------ main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--yarn", required=True, help="the Yarn tudursvehiclemod project (read only)")
    ap.add_argument("--verify", action="store_true", help="build both jars and compare their bytecode")
    a = ap.parse_args()

    yarn = Path(a.yarn).resolve()
    if not (yarn / "build.gradle").exists() or yarn == PROJECT:
        sys.exit(f"not a Yarn project: {yarn}")
    work = PROJECT / "build" / "mojmap-convert"
    work.mkdir(parents=True, exist_ok=True)

    copy, migrated = migrate(yarn, work)
    rewrite_build_files(yarn)
    replace_sources(yarn, migrated)
    apply_manual_renames()
    if not compile_and_fix(yarn, work):
        sys.exit(1)

    if a.verify:
        log("building the Yarn jar (on the copy) and the Mojang jar")
        if gradle(copy, "remapJar", logfile=work / "build-yarn.log") != 0:
            sys.exit(f"Yarn build failed - see {work / 'build-yarn.log'}")
        if gradle(PROJECT, "remapJar", logfile=work / "build-mojmap.log") != 0:
            sys.exit(f"Mojang build failed - see {work / 'build-mojmap.log'}")
        # newest jar: build/libs keeps jars of earlier versions, and glob order is arbitrary
        yjar = max((j for j in (copy / "build" / "libs").glob("*.jar") if "sources" not in j.name), key=lambda j: j.stat().st_mtime)
        mjar = max((j for j in (PROJECT / "build" / "libs").glob("*.jar") if "sources" not in j.name), key=lambda j: j.stat().st_mtime)
        yarn_tiny, moj_tiny, _ = loom_files()
        if not compare_jars.compare(yjar, mjar, work / "compare", yarn_tiny, moj_tiny):
            sys.exit(1)
    log("done")


if __name__ == "__main__":
    main()
