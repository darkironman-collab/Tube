#!/usr/bin/env python3
from pathlib import Path

p = Path("upstream/buildSrc/src/main/kotlin/ProjectConfig.kt")
text = p.read_text(encoding="utf-8")
old = 'const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 36'
new = 'const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 37'
if old not in text:
    raise SystemExit("Expected temporary API 36 override not found")
p.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Restored compileSdk 37.0")
