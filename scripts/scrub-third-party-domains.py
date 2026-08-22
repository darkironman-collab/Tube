#!/usr/bin/env python3
"""Scrub selected non-Google/YouTube third-party network endpoints from an APK.

This runs between patch passes. It rewrites exact ASCII endpoint strings in DEX files
with same-length non-resolving placeholders, then recomputes each DEX SHA-1 signature
and Adler32 checksum. The second Morphe patch pass rebuilds/signs the final APK.

The goal is not to remove GPL notices or attribution; it prevents the bundled dormant
modules from contacting the listed third-party services even if accidentally invoked.
"""

from __future__ import annotations

import hashlib
import os
import shutil
import struct
import sys
import tempfile
import zlib
import zipfile
from pathlib import Path

BLOCKED = [
    # Morphe online/about/update/announcement endpoints
    b"api.morphe.software",
    b"www.morphe.software",
    b"morphe.software",
    b"api.morphi.app",
    b"github.com/MorpheApp",
    b"raw.githubusercontent.com/MorpheApp",
    b"cdn.jsdelivr.net/gh/MorpheApp",
    # SponsorBlock
    b"sponsor.ajay.app",
    b"sb.ltn.fi",
    # Return YouTube Dislike
    b"returnyoutubedislikeapi.com",
    # DeArrow
    b"dearrow-thumb.ajay.app",
    b"dearrow.ajay.app",
    # AiSList / remote lists
    b"raw.githubusercontent.com/Override92",
    b"aisloplist.com",
    # Translation / AI services
    b"api.mymemory.translated.net",
    b"openrouter.ai",
]


def replacement(value: bytes) -> bytes:
    # Same byte length is required so DEX string-data offsets remain unchanged.
    # Keep punctuation out of the replacement so a URL using it cannot resolve.
    return b"x" * len(value)


def fix_dex_header(data: bytearray) -> None:
    if len(data) < 112 or not data.startswith(b"dex\n"):
        raise ValueError("Not a DEX file")
    # DEX signature = SHA-1 of everything after the signature field.
    data[12:32] = hashlib.sha1(data[32:]).digest()
    # DEX checksum = Adler32 of everything after the checksum field.
    checksum = zlib.adler32(data[12:]) & 0xFFFFFFFF
    data[8:12] = struct.pack("<I", checksum)


def scrub_dex(path: Path) -> dict[bytes, int]:
    data = bytearray(path.read_bytes())
    counts: dict[bytes, int] = {}
    changed = False
    # Longest first avoids a shorter substring being removed before its longer form.
    for needle in sorted(BLOCKED, key=len, reverse=True):
        repl = replacement(needle)
        count = data.count(needle)
        if count:
            data = data.replace(needle, repl)
            counts[needle] = count
            changed = True
    if changed:
        fix_dex_header(data)
        path.write_bytes(data)
    return counts


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: scrub-third-party-domains.py <input.apk> <output.apk>", file=sys.stderr)
        return 2

    src = Path(sys.argv[1]).resolve()
    dst = Path(sys.argv[2]).resolve()
    if not src.is_file():
        raise SystemExit(f"Input APK not found: {src}")
    if dst.exists():
        raise SystemExit(f"Refusing to overwrite: {dst}")

    totals: dict[bytes, int] = {}
    with tempfile.TemporaryDirectory(prefix="ytube-scrub-") as td:
        root = Path(td)
        with zipfile.ZipFile(src, "r") as zin:
            zin.extractall(root)

        dex_files = sorted(root.glob("classes*.dex"))
        if not dex_files:
            raise SystemExit("No classes*.dex files found")

        for dex in dex_files:
            for key, count in scrub_dex(dex).items():
                totals[key] = totals.get(key, 0) + count

        # Remove old APK signature metadata. The next patch pass rebuilds and signs the APK.
        meta = root / "META-INF"
        if meta.exists():
            for p in list(meta.iterdir()):
                if p.suffix.upper() in {".RSA", ".DSA", ".EC", ".SF", ".MF"}:
                    p.unlink(missing_ok=True)

        with zipfile.ZipFile(dst, "w", allowZip64=True) as zout:
            for p in sorted(root.rglob("*")):
                if not p.is_file():
                    continue
                rel = p.relative_to(root).as_posix()
                compress = zipfile.ZIP_STORED if rel.endswith((".so", ".arsc")) else zipfile.ZIP_DEFLATED
                zout.write(p, rel, compress_type=compress)

    print("Scrubbed third-party endpoint occurrences:")
    for key in BLOCKED:
        print(f"  {key.decode('ascii')}: {totals.get(key, 0)}")

    # Strong invariant: final intermediate APK must not contain any blocked ASCII string.
    blob = dst.read_bytes()
    survivors = [x.decode("ascii") for x in BLOCKED if x in blob]
    if survivors:
        raise SystemExit("Blocked endpoint strings still present: " + ", ".join(survivors))

    print(f"Created scrubbed APK: {dst}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
