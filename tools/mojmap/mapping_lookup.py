#!/usr/bin/env python3
"""Look up Yarn <-> Mojang names through intermediary (Minecraft 1.21.11).

Usage:
  mapping_lookup.py <yarn_tiny> <mojmap_tiny> <query>...
  query = a class name (dots or slashes), or Class#member (e.g. net.minecraft.entity.Entity#getPitch)
Prints every matching entry as: yarn  ->  mojmap  (intermediary)
"""
import sys


def load(path):
    classes = {}  # inter -> named
    members = {}  # (inter_class, kind, inter_name, inter_desc) -> named
    with open(path, encoding="utf-8") as f:
        header = f.readline().rstrip("\n").split("\t")
        ns = header[3:]
        ii, ni = ns.index("intermediary"), ns.index("named")
        cur = None
        for line in f:
            p = line.rstrip("\n").split("\t")
            if p[0] == "c":
                names = p[1:]
                cur = names[ii]
                classes[cur] = names[ni]
            elif len(p) > 2 and p[0] == "" and p[1] in ("m", "f"):
                desc = p[2]
                names = p[3:]
                members[(cur, p[1], names[ii], desc)] = names[ni]
    return classes, members


def main():
    yarn, moj = load(sys.argv[1]), load(sys.argv[2])
    for q in sys.argv[3:]:
        q = q.replace(".", "/")
        cls, _, mem = q.partition("#")
        for inter, yname in yarn[0].items():
            mname = moj[0].get(inter, "?")
            if cls not in (yname, mname) and not yname.endswith("/" + cls) and not mname.endswith("/" + cls):
                continue
            if not mem:
                print(f"{yname}  ->  {mname}  ({inter})")
                continue
            for (c, kind, iname, desc), yn in yarn[1].items():
                if c != inter:
                    continue
                mn = moj[1].get((c, kind, iname, desc), "?")
                if mem in (yn, mn):
                    print(f"{yname}#{yn}{desc}  ->  {mname}#{mn}  ({iname})")


if __name__ == "__main__":
    main()
