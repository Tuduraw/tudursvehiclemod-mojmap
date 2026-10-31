#!/usr/bin/env python3
"""Minimal RCON client: rcon.py <command>... (each argument is one command)."""
import socket
import struct
import sys
import time


def packet(rid, ptype, body):
    data = struct.pack("<ii", rid, ptype) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def recv(sock):
    raw = b""
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    n = struct.unpack("<i", raw)[0]
    data = b""
    while len(data) < n:
        data += sock.recv(n - len(data))
    rid, ptype = struct.unpack("<ii", data[:8])
    return rid, data[8:-2].decode("utf-8", "replace")


def run(cmds, host="127.0.0.1", port=25575, password="tvmtest"):
    for attempt in range(120):
        try:
            s = socket.create_connection((host, port), timeout=600)
            break
        except OSError:
            time.sleep(2)
    else:
        raise SystemExit("rcon: could not connect")
    s.sendall(packet(1, 3, password))
    if recv(s)[0] == -1:
        raise SystemExit("rcon: auth failed")
    out = []
    for i, c in enumerate(cmds, start=2):
        s.sendall(packet(i, 2, c))
        out.append(recv(s)[1])
    s.close()
    return out


if __name__ == "__main__":
    args = sys.argv[1:]
    port = 25575
    if args and args[0] == "--port":
        port = int(args[1])
        args = args[2:]
    for line in run(args, port=port):
        print(line)
