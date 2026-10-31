#!/usr/bin/env python3
"""Compare the Yarn release jar with the Mojang development jar.

Both jars are remapped to intermediary names by Loom. Addon mods are compiled
against the release jar, so every class, field and method of the release jar
must exist in the Mojang jar under the same name and descriptor, with the same
bytecode. Allowed differences:
  * synthetic lambda method names (lambda$<enclosing method>$N): javac derives
    them from the enclosing method's development name; they are private.
  * extra forwarding methods in the Mojang jar: generic overrides such as
    VehicleEntityRenderer#extractRenderState(AbstractVehicleEntity, ...) that only
    call the Yarn-named typed method (updateRenderState). The Mojang name must be
    the Mojang counterpart of that Yarn name (checked against the mappings). The
    compiler-generated bridge then calls the forwarder instead of the typed method
    directly; that one extra hop is accepted.
Anything else - a missing or renamed method, or different code - is reported.

Usage: compare_jars.py <yarn jar> <mojmap jar> <work dir> --yarn <tiny> --mojmap <tiny>
"""
import argparse
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

LAMBDA = re.compile(r"lambda\$[\w$]+?\$(\d+)")
CLASS_HDR = re.compile(r"^(?:public |protected |private |abstract |final |static |sealed |non-sealed )*(?:class|interface|enum|record|@interface) ([\w.$]+)")
METHOD_HDR = re.compile(r"^  \S.*\);$|^  \S.*\) throws .*;$")
NAME_IN_HDR = re.compile(r"([\w$<>]+)\(")
INVOKE = re.compile(r"invoke\w+\s+// (?:Interface)?Method ([\w$]+):")


def norm(line):
    # Constant-pool indices differ between builds (the resolved name follows in a
    # comment); dropping them leaves padding of varying width, so collapse it.
    line = re.sub(r"#\d+(:#\d+)?", "", line)
    line = re.sub(r"(?<=\S) {2,}", " ", line)
    return LAMBDA.sub(r"lambda$_$\1", line)


def disassemble(jar, out_dir):
    """{class: {member header: [body lines]}} from javap -c -p -s."""
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)
    with zipfile.ZipFile(jar) as z:
        z.extractall(out_dir)
    classes = sorted(str(p.relative_to(out_dir)) for p in out_dir.rglob("*.class"))
    lines = []
    for i in range(0, len(classes), 200):
        r = subprocess.run(["javap", "-c", "-p", "-s", *classes[i:i + 200]], cwd=out_dir,
                           capture_output=True, text=True)
        lines.extend(r.stdout.splitlines())
    result, cls, member = {}, None, None
    for raw in lines:
        line = norm(raw)
        m = CLASS_HDR.match(line)
        if m and not line.startswith(" "):
            cls, member = m.group(1), "<class header>"
            result.setdefault(cls, {})[member] = [line]
            continue
        if cls is None:
            continue
        if line.startswith("  ") and not line.startswith("   ") and line.strip():
            member = line
            result[cls].setdefault(member, [])
            continue
        result[cls].setdefault(member, []).append(line)
    return result


def method_name_pairs(yarn_tiny, moj_tiny):
    def load(path):
        names = {}
        with open(path, encoding="utf-8") as f:
            ns = f.readline().rstrip("\n").split("\t")[3:]
            ii, ni = ns.index("intermediary"), ns.index("named")
            for line in f:
                p = line.rstrip("\n").split("\t")
                if len(p) > 3 and p[0] == "" and p[1] == "m":
                    names[p[3:][ii]] = p[3:][ni]
        return names
    y, m = load(yarn_tiny), load(moj_tiny)
    return {(y[k], m[k]) for k in y if k in m}


def is_forwarder(header, body, pairs):
    m = NAME_IN_HDR.search(header)
    if not m:
        return False
    moj_name = m.group(1)
    calls = [c for l in body for c in INVOKE.findall(l)]
    return len(calls) == 1 and (pairs is None or (calls[0], moj_name) in pairs)


def compare(yarn_jar, moj_jar, work, yarn_tiny=None, moj_tiny=None):
    work = Path(work)
    problems = []
    with zipfile.ZipFile(yarn_jar) as zy, zipfile.ZipFile(moj_jar) as zm:
        ny, nm = set(zy.namelist()), set(zm.namelist())
        if ny != nm:
            problems.append(f"file lists differ: {sorted(ny ^ nm)[:20]}")
        for n in sorted(ny & nm):
            if not n.endswith(".class") and not n.endswith("/") and zy.read(n) != zm.read(n):
                problems.append(f"resource differs: {n}")

    y = disassemble(yarn_jar, work / "yarn")
    m = disassemble(moj_jar, work / "mojmap")
    pairs = method_name_pairs(yarn_tiny, moj_tiny) if yarn_tiny and moj_tiny else None
    forwarders = 0
    for cls in sorted(set(y) | set(m)):
        if cls not in m or cls not in y:
            problems.append(f"class only in {'Yarn' if cls in y else 'Mojang'} jar: {cls}")
            continue
        ym, mm = y[cls], m[cls]
        # Calls to a forwarder count as calls to the method it forwards to (the
        # bridge method then reaches the same typed body through one extra hop).
        fwd = {}
        for header, body in mm.items():
            if header not in ym and is_forwarder(header, body, pairs):
                fwd[NAME_IN_HDR.search(header).group(1)] = [c for l in body for c in INVOKE.findall(l)][0]
        def through(body):
            out = []
            for l in body:
                for moj, yarn in fwd.items():
                    l = l.replace(f"Method {moj}:", f"Method {yarn}:")
                out.append(l)
            return out
        for header, body in ym.items():
            if header not in mm:
                problems.append(f"{cls}: missing in Mojang jar: {header.strip()}")
            elif mm[header] != body and through(mm[header]) != body:
                problems.append(f"{cls}: code differs: {header.strip()}")
        for header, body in mm.items():
            if header in ym:
                continue
            if is_forwarder(header, body, pairs):
                forwarders += 1
            else:
                problems.append(f"{cls}: only in Mojang jar: {header.strip()}")
    print(f"[compare] {len(y)} classes; forwarding methods added: {forwarders}; problems: {len(problems)}")
    for p in problems[:40]:
        print("  " + p)
    return not problems


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("yarn_jar")
    ap.add_argument("mojmap_jar")
    ap.add_argument("work")
    ap.add_argument("--yarn")
    ap.add_argument("--mojmap")
    a = ap.parse_args()
    sys.exit(0 if compare(a.yarn_jar, a.mojmap_jar, a.work, a.yarn, a.mojmap) else 1)
