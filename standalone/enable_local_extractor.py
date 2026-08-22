#!/usr/bin/env python3
from pathlib import Path

p = Path("upstream/settings.gradle.kts")
text = p.read_text(encoding="utf-8")
old = '''//    includeBuild("../NewPipeExtractor") {
//        dependencySubstitution {
//            substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
//                .using(project(":extractor"))
//        }
//    }
'''
new = '''includeBuild("../NewPipeExtractor") {
    dependencySubstitution {
        substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
            .using(project(":extractor"))
    }
}
'''
if old not in text:
    raise SystemExit("Local NewPipeExtractor substitution block not found")
p.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Enabled local NewPipeExtractor composite build")
