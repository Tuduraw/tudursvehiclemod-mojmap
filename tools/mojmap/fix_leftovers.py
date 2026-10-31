#!/usr/bin/env python3
"""Fix Yarn names that Loom's migrateMappings left behind, using javac's errors.

migrateMappings (Mercury) skips code it cannot fully resolve - mostly lambda
bodies and methods whose generic signature does not match the vanilla
descriptor. javac then reports those leftovers as "cannot find symbol" /
"package does not exist". This script reads the compile log, looks each
leftover name up in the Yarn and Mojang mappings (through intermediary, along
the Minecraft class hierarchy) and rewrites it in place. Run it repeatedly
(compile -> fix) until nothing changes; whatever remains is fixed by hand.

Usage:
  fix_leftovers.py --yarn <yarn mappings.tiny> --mojmap <mojmap mappings.tiny>
                   --jar <mojmap minecraft jar> [--jar ...]
                   --yarn-root <Yarn project> --project-root <Mojang project>
                   --log <gradle log>
"""
import argparse
import re
import struct
import sys
import zipfile
from collections import defaultdict
from pathlib import Path


# ---------------------------------------------------------------- mappings
def load_tiny(path):
    classes = {}                    # inter -> named
    members = defaultdict(list)     # inter class -> [(kind, inter, desc, named)]
    with open(path, encoding="utf-8") as f:
        ns = f.readline().rstrip("\n").split("\t")[3:]
        ii, ni = ns.index("intermediary"), ns.index("named")
        cur = None
        for line in f:
            p = line.rstrip("\n").split("\t")
            if p[0] == "c":
                cur = p[1:][ii]
                classes[cur] = p[1:][ni]
            elif len(p) > 3 and p[0] == "" and p[1] in ("m", "f"):
                names = p[3:]
                members[cur].append((p[1], names[ii], p[2], names[ni]))
    return classes, members


# ---------------------------------------------------------------- class files
def read_class_header(data):
    """Return (this, super, [interfaces]) internal names of a class file."""
    count = struct.unpack(">H", data[8:10])[0]
    pool = [None] * count
    i, pos = 1, 10
    while i < count:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            pool[i] = data[pos + 3:pos + 3 + ln].decode("utf-8", "replace")
            pos += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = ("ref", struct.unpack(">H", data[pos + 1:pos + 3])[0])
            pos += 3
        elif tag == 15:
            pos += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        else:
            raise ValueError(f"bad constant tag {tag}")
        i += 1

    def cls(idx):
        return pool[pool[idx][1]] if idx else None

    this_c, super_c, icount = struct.unpack(">HHH", data[pos + 2:pos + 8])
    ifaces = [cls(struct.unpack(">H", data[pos + 8 + 2 * k:pos + 10 + 2 * k])[0]) for k in range(icount)]
    return cls(this_c), cls(super_c), ifaces


def load_hierarchy(jars):
    parents = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.endswith(".class") and n.startswith(("net/minecraft/", "com/mojang/")):
                    t, s, ifs = read_class_header(z.read(n))
                    parents[t] = [x for x in [s] + ifs if x]
    return parents


# ---------------------------------------------------------------- resolver
class Resolver:
    def __init__(self, yarn, moj, parents):
        self.yc, self.ym = yarn
        self.mc, self.mm = moj
        self.parents = parents
        self.moj_to_inter = {v: k for k, v in self.mc.items()}
        self.yarn_to_moj_class = {self.yc[k]: self.mc.get(k) for k in self.yc}
        self.simple = defaultdict(set)      # mojmap simple name -> full names
        for full in self.mc.values():
            self.simple[full.rsplit("/", 1)[-1].replace("$", ".")].add(full)
            self.simple[full.rsplit("/", 1)[-1].split("$")[-1]].add(full)

    def ancestors(self, cls):
        seen, todo = [], [cls]
        while todo:
            c = todo.pop(0)
            if c in seen:
                continue
            seen.append(c)
            todo.extend(self.parents.get(c, []))
        return seen

    def member(self, type_name, kind, yarn_name, argc):
        """Mojang name of the Yarn member `yarn_name` reachable from `type_name`."""
        results = set()
        starts = self.simple.get(type_name, set()) or {type_name.replace(".", "/")}
        for start in starts:
            for c in self.ancestors(start):
                inter = self.moj_to_inter.get(c)
                if inter is None:
                    continue
                ymembers = {(k, i, d): n for k, i, d, n in self.ym.get(inter, [])}
                for k, i, d, n in self.mm.get(inter, []):
                    if k != kind or ymembers.get((k, i, d)) != yarn_name:
                        continue
                    if kind == "m" and argc is not None and count_args(d) != argc:
                        continue
                    results.add(n)
        return results


def count_args(desc):
    args = desc[1:desc.index(")")]
    n, i = 0, 0
    while i < len(args):
        while args[i] == "[":
            i += 1
        i = args.index(";", i) + 1 if args[i] == "L" else i + 1
        n += 1
    return n


# ---------------------------------------------------------------- javac log
ERR = re.compile(r"^(?!\s)(.+?\.java):(\d+): error: (.*)$")


def parse_log(text):
    lines = text.splitlines()
    out, seen = [], set()
    for idx, line in enumerate(lines):
        m = ERR.match(line)
        if not m:
            continue
        path, lineno, msg = m.group(1), int(m.group(2)), m.group(3)
        caret_line = lines[idx + 2] if idx + 2 < len(lines) else ""
        col = caret_line.find("^")
        symbol = location = None
        for extra in lines[idx + 3:idx + 6]:
            s = extra.strip()
            if s.startswith("symbol:"):
                symbol = s[len("symbol:"):].strip()
            elif s.startswith("location:"):
                location = s[len("location:"):].strip()
        key = (path, lineno, col, msg)
        if key in seen:
            continue
        seen.add(key)
        out.append(dict(path=path, line=lineno, col=col, msg=msg, symbol=symbol, location=location))
    return out


def location_type(location):
    if not location:
        return None
    m = re.search(r"of type ([\w.$]+)", location)
    if m:
        return m.group(1)
    m = re.match(r"(?:class|interface|enum) ([\w.$]+)", location)
    return m.group(1).rsplit(".", 1)[-1] if m else None


# ---------------------------------------------------------------- main
def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--yarn", required=True)
    ap.add_argument("--mojmap", required=True)
    ap.add_argument("--jar", action="append", required=True)
    ap.add_argument("--yarn-root", required=True)
    ap.add_argument("--project-root", required=True)
    ap.add_argument("--log", required=True)
    a = ap.parse_args(argv)

    r = Resolver(load_tiny(a.yarn), load_tiny(a.mojmap), load_hierarchy(a.jar))
    errors = parse_log(Path(a.log).read_text(encoding="utf-8", errors="replace"))
    edits = defaultdict(list)    # path -> [(line, col, old, new)]
    imports = defaultdict(set)   # path -> {fqcn}
    unresolved = []

    for e in errors:
        sym, msg = e["symbol"], e["msg"]
        m = re.match(r"package ([\w.]+) does not exist", msg)
        if m:
            # Fully qualified Yarn name in code: rewrite package + class at the caret.
            edits[e["path"]].append((e["line"], None, "FQN", None))
            continue
        if msg == "cannot find symbol" and sym:
            kind_word, _, name = sym.partition(" ")
            name = name.strip()
            if kind_word == "class":
                if (e["location"] or "").startswith("package "):
                    # Fully qualified Yarn class name used inline (net.minecraft.util.Hand).
                    edits[e["path"]].append((e["line"], None, "FQN", None))
                else:
                    imports[e["path"]].add(name)
                continue
            argc = None
            if kind_word == "method":
                mname, _, args = name.partition("(")
                args = args.rstrip(")")
                argc = 0 if not args else args.count(",") + 1
                name = mname
            t = location_type(e["location"])
            kind = "m" if kind_word == "method" else "f"
            cands = r.member(t, kind, name, argc) if t else set()
            if len(cands) == 1:
                edits[e["path"]].append((e["line"], e["col"], name, cands.pop()))
            else:
                unresolved.append((e, t, sorted(cands)))
            continue
        unresolved.append((e, None, []))

    changed = 0
    for path, items in edits.items():
        src = Path(path).read_text(encoding="utf-8").split("\n")
        # Apply right-to-left on each line so earlier columns stay valid.
        for lineno, col, old, new in sorted(items, key=lambda x: (x[0], -(x[1] or 0))):
            text = src[lineno - 1]
            if old == "FQN":
                def repl(m):
                    full = m.group(0).replace(".", "/")
                    mo = r.yarn_to_moj_class.get(full)
                    return mo.replace("/", ".").replace("$", ".") if mo else m.group(0)
                new_text = re.sub(r"net\.minecraft(?:\.[a-z0-9_]+)+\.[A-Z]\w*", repl, text)
            else:
                # The caret is under the first character of the identifier.
                tabs_expanded = col
                if text[col:col + len(old)] == old:
                    new_text = text[:col] + new + text[col + len(old):]
                else:
                    idx = text.find(old, max(0, tabs_expanded - 8))
                    new_text = text if idx < 0 else text[:idx] + new + text[idx + len(old):]
            if new_text != text:
                src[lineno - 1] = new_text
                changed += 1
        Path(path).write_text("\n".join(src), encoding="utf-8")

    # Imports dropped by the migration: copy the Yarn import from the original file and map it.
    for path, names in imports.items():
        rel = Path(path).resolve().relative_to(Path(a.project_root).resolve())
        yarn_file = Path(a.yarn_root) / rel
        if not yarn_file.exists():
            continue
        yarn_imports = re.findall(r"^import (net\.minecraft\.[\w.]+);", yarn_file.read_text(encoding="utf-8"), re.M)
        text = Path(path).read_text(encoding="utf-8")
        added = []
        for name in names:
            for imp in yarn_imports:
                if imp.rsplit(".", 1)[-1] != name:
                    continue
                mo = r.yarn_to_moj_class.get(imp.replace(".", "/"))
                if mo:
                    line = "import " + mo.replace("/", ".").replace("$", ".") + ";"
                    if line not in text:
                        added.append(line)
        if added:
            text = re.sub(r"^(package [\w.]+;\n)", lambda m: m.group(1) + "\n" + "\n".join(sorted(added)) + "\n", text, count=1, flags=re.M)
            Path(path).write_text(text, encoding="utf-8")
            changed += len(added)

    print(f"edits applied: {changed}")
    for e, t, cands in unresolved:
        print(f"UNRESOLVED {Path(e['path']).name}:{e['line']} {e['msg']} | {e['symbol']} | type={t} | candidates={cands}")
    return changed


if __name__ == "__main__":
    main()
