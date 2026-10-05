# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.2.0's mutation pass (D-0002): each mutation is applied alone, the suite named runs
(the JUnit domain tests or the GameTests), the file is restored. A mutation is caught when the
suite fails. Name mutations to run only those: `python3 mutate-0.2.0.py M4 M6`."""
import os, pathlib, subprocess, sys

REPO = pathlib.Path(__file__).resolve().parents[2]
D = "src/domain/java/com/chunkworks/serfdom/domain/"
J = "src/main/java/com/chunkworks/serfdom/job/"
ENV = dict(os.environ, JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64")

MUTATIONS = [
    ("M1 no finished load is collected", D + "Workshop.java",
     "        for (var s : f.stations()) if (s.state() == State.READY) return new Collect(s.at());\n", "", "test"),
    ("M2 a row may spend another row's keep", D + "Stock.java",
     "            out.computeIfPresent(r.item(), (k, n) -> Math.max(0, n - r.keep()));\n        }\n        return out;", "        }\n        return out;", "test"),
    ("M3 stations not ordered within a yield", D + "Recipes.java",
     ".thenComparing(Rule::station)", "", "test"),
    ("M4 cooking rules may take gear", J + "RecipeBook.java",
     "if (cooking && (s.isDamageableItem() || s.getMaxStackSize() == 1)) continue;", "if (false) continue;", "gametest"),
    ("M5 one fuel too many", D + "Fuel.java",
     "(need + burnTicks - 1) / burnTicks);", "(need + burnTicks - 1) / burnTicks + 1);", "test"),
    ("M6 a player's furnace counts as the post's", J + "Stations.java",
     "if (mine == null || (!out.isEmpty()", "if (mine != null && (!out.isEmpty()", "gametest"),
    ("M7 other workers' claims not counted as coming", J + "WorkshopJob.java",
     "            if (c.count() > 0) coming.merge(c.item(), c.count(), Integer::sum);\n", "", "gametest"),
    ("M8 a second charcoal bootstrap while one cooks", D + "Workshop.java",
     "boolean waiting = f.fuel() == 0 && f.fuelComing() > 0;", "boolean waiting = false;", "test"),
    ("M9 repairs round a quarter up", D + "Repair.java",
     "return quarter == 0 ? 0 : damage / quarter;", "return quarter == 0 ? 0 : (damage + quarter - 1) / quarter;", "test"),
    ("M10 a workshop keeps what it carries", D + "Shift.java",
     "if (f.full() || (f.tidy() && f.carrying()))", "if (f.full())", "test"),
    ("M11 what is coming is not counted", D + "Stock.java",
     "row.keep() - stored.getOrDefault(row.item(), 0) - coming.getOrDefault(row.item(), 0)", "row.keep() - stored.getOrDefault(row.item(), 0)", "gametest"),
    ("M12 a load is not recorded as the post's", J + "WorkshopTask.java",
     "            post.loadedAt(at, rule.result(), times * rule.yield());\n", "", "gametest"),
    ("M13 no station shows after no fuel", D + "Need.java",
     "NO_BED, NO_TOOL, NO_STATION, NO_FUEL,", "NO_BED, NO_TOOL, NO_FUEL, NO_STATION,", "test"),
    ("M14 a campfire's finished food is never collected", J + "Stations.java",
     "yield new Workshop.Site(at, f.kind(), empty ? Workshop.State.READY : Workshop.State.BUSY, 0);",
     "yield new Workshop.Site(at, f.kind(), Workshop.State.BUSY, 0);", "gametest"),
]

def run(suite):
    task = ["test"] if suite == "test" else ["runGameTestServer"]
    r = subprocess.run(["./gradlew", *task, "--offline", "-q"], cwd=REPO, env=ENV, capture_output=True, text=True)
    log = (REPO / "run/logs/latest.log").read_text(errors="replace") if suite == "gametest" else r.stdout + r.stderr
    failed = [l for l in log.splitlines() if "failed at" in l or "FAILED" in l]
    return r.returncode, failed

results = []
only = set(sys.argv[1:])
for name, rel, old, new, suite in MUTATIONS:
    if only and name.split()[0] not in only:
        continue
    path = REPO / rel
    original = path.read_text()
    if original.count(old) < 1:
        results.append((name, "NOT APPLIED: text not found"))
        continue
    path.write_text(original.replace(old, new, 1))
    try:
        code, failed = run(suite)
    finally:
        path.write_text(original)
    verdict = "caught" if code != 0 else "SURVIVED"
    detail = failed[0][-160:] if failed else ""
    results.append((name, f"{verdict} ({suite}) {detail}"))
    print(name, "->", results[-1][1], flush=True)

print("\nSUMMARY")
for name, verdict in results:
    print(f"{name}: {verdict}")
