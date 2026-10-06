# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.9.1's mutation pass (D-0009, no structure search while sampling the price lists): each
mutation breaks the guard one way, and the GameTest server's run must fail
`theBaseValuesSearchForNoStructure`. All four aim at that one test, so each runs alone. Files are
restored from memory whatever happens.

    uv run --no-project python devtools/verification/mutate-0.9.1.py a | b | c | d
"""
import os
import re
import subprocess
import sys

REPO = str(__import__("pathlib").Path(__file__).resolve().parents[2])
MAIN = REPO + "/src/main/java/com/chunkworks/serfdom/"
TEST = "thebasevaluessearchfornostructure"

BATCHES = {
    "a": [("M1 the guard never refuses", [(MAIN + "mixin/ServerLevelMixin.java", "if (Prices.refusesSearch()) cir.setReturnValue(null);",
                                           "if (false && Prices.refusesSearch()) cir.setReturnValue(null);")], [TEST])],
    "b": [("M2 sampling never marks its thread", [(MAIN + "market/Prices.java", "        sampler = Thread.currentThread();\n", "")], [TEST])],
    "c": [("M3 the mark outlives the sampling", [(MAIN + "market/Prices.java", "        } finally {\n            sampler = null;\n        }", "        } finally {\n        }")], [TEST])],
    "d": [("M4 the mixin is not applied", [(REPO + "/src/main/resources/serfdom.mixins.json", '    "ServerLevelMixin",\n', "")], [TEST])],
}


def run(batch):
    mutations = BATCHES[batch]
    saved = {}
    try:
        for mid, edits, _ in mutations:
            for path, old, new in edits:
                if path not in saved:
                    saved[path] = open(path).read()
                text = open(path).read()
                if text.count(old) != 1:
                    raise SystemExit(f"{mid}: the text to mutate appears {text.count(old)} times in {path}")
                open(path, "w").write(text.replace(old, new))
        env = dict(os.environ, JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64")
        # A build that fails to compile runs no tests: the old log must not be read as this run's.
        if os.path.exists(REPO + "/run/logs/latest.log"):
            os.remove(REPO + "/run/logs/latest.log")
        out = subprocess.run(["./gradlew", "runGameTestServer", "--continue"], cwd=REPO, env=env, capture_output=True, text=True)
        if "Compilation failed" in out.stdout + out.stderr:
            raise SystemExit(f"{batch}: the build did not compile\n" + out.stdout[-3000:])
        print(f"runGameTestServer exit {out.returncode}")
        log = open(REPO + "/run/logs/latest.log").read()
        failed = set(re.findall(r"\]: (\w+) failed at", log))
        print(re.findall(r"(?:All \d+ required tests passed|\d+ required tests failed)", log))
        for mid, _, tests in mutations:
            hit = [t for t in tests if t in failed]
            print(("CAUGHT " if hit else "MISSED ") + mid + " -> " + (", ".join(hit) if hit else " or ".join(tests)), flush=True)
        print("failed:", sorted(failed), flush=True)
    finally:
        for path, text in saved.items():
            open(path, "w").write(text)
        print("restored", len(saved), "files", flush=True)


if __name__ == "__main__":
    for b in sys.argv[1:]:
        run(b)
