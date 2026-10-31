#!/usr/bin/env python3
"""compare_reports.py a.json b.json [tol]  - list every differing leaf (numbers: |a-b| > tol)."""
import json
import sys


def walk(a, b, path, out, tol):
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a or k not in b:
                out.append((path + "/" + k, a.get(k, "<missing>"), b.get(k, "<missing>")))
            else:
                walk(a[k], b[k], path + "/" + k, out, tol)
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            out.append((path + "#len", len(a), len(b)))
        for i, (x, y) in enumerate(zip(a, b)):
            walk(x, y, f"{path}[{i}]", out, tol)
    elif isinstance(a, (int, float)) and isinstance(b, (int, float)) and not isinstance(a, bool):
        if abs(a - b) > tol:
            out.append((path, a, b))
    elif a != b:
        out.append((path, a, b))


if __name__ == "__main__":
    a, b = (json.load(open(p)) for p in sys.argv[1:3])
    tol = float(sys.argv[3]) if len(sys.argv) > 3 else 0.0
    out = []
    walk(a, b, "", out, tol)
    for p, x, y in out:
        print(f"{p}: {x}  |  {y}")
    print(f"-- {len(out)} differences")
