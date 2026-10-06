# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.9.0's mutation pass (D-0008, shared farms and sowing): each mutation breaks one rule, and
the run must fail in the test that guards it. The domain mutations run alone, one JUnit run each; the
GameTest ones run in batches, each mutation in a batch aimed at tests no other mutation in it touches,
and a catch is read off the failed tests' names. A mutation that changes how every farmer moves (no
holds at all, no fence rule, a farm of one) runs alone. Files are restored from memory whatever happens.

    uv run --no-project python devtools/verification/mutate-0.9.0.py domain-each | a | b | c | d | e | f | g | h | i
"""
import os
import re
import subprocess
import sys

REPO = str(__import__("pathlib").Path(__file__).resolve().parents[2])
MAIN = REPO + "/src/main/java/com/chunkworks/serfdom/"
DOM = REPO + "/src/domain/java/com/chunkworks/serfdom/domain/"

TWO = "twofarmersononepostneverchaseonecrop"
WOOD = "twowoodcuttersfelltwotrees"
HELP = "afarmerhelpsinatouchingpostsarea"
APART = "agaporanotherownerkeepsfarmsapart"
KILLED = "afarmerfillsinforonekilled"
END = "afarmerwhoseshiftendsletsgo"
LAPSED = "afarmerwhoseholdlapsedleavestheplot"
OWN = "afarmerworksitsownareafirst"
NEAREST = "theharvestgoestothenearestpostschest"
GAP = "agapinarowissownafterthewait"
CHEST = "seedistakenfromthefarmschest"
MEET = "nocopywherekindsmeetorstemsgrow"
CARROT = "aspotthatgrewacarrotgetsacarrot"

LINK_PLUS_ONE = (DOM + "Farm.java", "int reach = a.radius() + b.radius() + 1;", "int reach = a.radius() + b.radius();")
LINK_OWNER = (DOM + "Farm.java", "if (!a.owner().equals(b.owner()) || !a.job().equals(b.job())) return false;", "if (!a.job().equals(b.job())) return false;")
OWN_FIRST = (DOM + "Farm.java", "Comparator.comparing((Offer o) -> !o.own())\n            .thenComparingLong(Offer::distanceSq)",
             "Comparator.comparingLong((Offer o) -> o.distanceSq())\n            .thenComparingLong(Offer::distanceSq)")
NO_WAIT = (DOM + "Sowing.java", "if (!layer.bare[cell] || now - layer.since[cell] < wait) continue;", "if (!layer.bare[cell]) continue;")
MOST_COMMON = (DOM + "Sowing.java", "if (found == 1 && !stems.contains(only))", "if (found >= 1 && !stems.contains(only))")
STEMS_COPIED = (DOM + "Sowing.java", "if (found == 1 && !stems.contains(only))", "if (found == 1)")
NO_MEMORY = (DOM + "Sowing.java", "            if (layer.remembered[cell] != null) { out.add(new Sow(x, z, layer.remembered[cell])); continue; }\n", "")

# (id, [(file, old, new), ...], the tests of which one must fail)
BATCHES = {
    "domain": [
        ("D1 areas one block apart link", [LINK_PLUS_ONE], ["FarmTest"]),
        ("D2 another owner's post links", [LINK_OWNER], ["FarmTest"]),
        ("D3 its own area not first", [OWN_FIRST], ["FarmTest"]),
        ("D4 a hold never lapses", [(DOM + "Holds.java", "private static boolean live(Hold h, long now) { return now - h.renewed() < LAPSE; }",
                                     "private static boolean live(Hold h, long now) { return true; }")], ["HoldsTest"]),
        ("D5 another's live hold is taken", [(DOM + "Holds.java", "        if (h != null && live(h, now) && !h.worker().equals(worker)) return false;\n", "")], ["HoldsTest"]),
        ("D6 no wait before sowing", [NO_WAIT], ["SowingTest"]),
        ("D7 a spot copies where kinds meet", [MOST_COMMON], ["SowingTest"]),
        ("D8 stems are copied", [STEMS_COPIED], ["SowingTest"]),
        ("D9 what a spot grew is forgotten", [NO_MEMORY], ["SowingTest"]),
    ],
    # Alone: without holds every farmer goes for the nearest plot, and the second idles.
    "a": [
        ("G1 farmers ignore holds", [(MAIN + "job/Farming.java", "var next = Farm.next(offers, plot -> Holding.heldByAnother(level, Job.Place.plot(plot), id));",
                                      "var next = Farm.next(offers, plot -> false);")], [TWO]),
    ],
    "b": [
        ("G2 woodcutters ignore holds", [(MAIN + "job/Woodcutting.java", "\n                                && !Holding.heldByAnother(level, Job.Place.tree(pos(cell)), worker.getUUID())", "")], [WOOD]),
        ("G4 the dead keep their plot", [(MAIN + "Workers.java", "            // The plot or tree it held is free at once for the others (D-0008).\n            com.chunkworks.serfdom.job.Holding.releaseAll(v.getUUID());\n", "")], [KILLED]),
        ("G5 a shift that stops keeps its plot", [(MAIN + "behavior/WorkShift.java", "    @Override protected void stop(ServerLevel level, Villager worker, long gameTime) {\n        drop(level, worker);",
                                                   "    @Override protected void stop(ServerLevel level, Villager worker, long gameTime) {\n        task = null;")], [END]),
        ("G8 no seed fetched", [(MAIN + "job/Farming.java", "            if (lack > 0) stored.computeIfAbsent(", "            if (false) stored.computeIfAbsent(")], [CHEST]),
    ],
    "c": [
        ("G3 a lost hold is never noticed", [(MAIN + "behavior/WorkShift.java", "if (task != null && mode != Mode.WAIT && !keep(level, worker)) {", "if (false) {")], [LAPSED]),
        ("G7 the harvest only to its own post", [(MAIN + "behavior/WorkShift.java", "case DEPOSIT -> Storage.depositTarget(level, farm, at, carried)", "case DEPOSIT -> Storage.depositTarget(level, java.util.List.of(post), at, carried)")], [NEAREST]),
        ("G16 another owner's post links", [LINK_OWNER], [APART]),
    ],
    # Alone: every walk on every farm crosses posts.
    "d": [
        ("G9 a post is open ground", [(MAIN + "behavior/WorkerNavigation.java", "            if (state.getBlock() instanceof com.chunkworks.serfdom.post.WorkPostBlock && !endsAt(x, y, z)) return PathType.FENCE;\n", "")], [OWN]),
    ],
    # Alone: no farm has a second post.
    "e": [
        ("G6 a farm of one", [(MAIN + "post/Farms.java", "        if (!shares(post)) return List.of(post);", "        if (true) return List.of(post);")], [HELP]),
    ],
    "f": [
        ("G11 no wait before sowing", [NO_WAIT], [GAP]),
        ("G12 what a spot grew is forgotten", [NO_MEMORY], [CARROT]),
        ("G13 a spot copies where kinds meet", [MOST_COMMON], [MEET]),
    ],
    # After the first pass: G2 got past the woodcutters' test (without holds the second is refused the
    # tree at its first step and waits, so the two never share one), which now also asks that both hold
    # trees at once; G12 was masked by G13 in batch f (with both, the spot gets the last kind found,
    # here the carrot). Each again alone.
    "h": [
        ("G2 woodcutters ignore holds", [(MAIN + "job/Woodcutting.java", "\n                                && !Holding.heldByAnother(level, Job.Place.tree(pos(cell)), worker.getUUID())", "")], [WOOD]),
    ],
    "i": [
        ("G12 what a spot grew is forgotten", [NO_MEMORY], [CARROT]),
    ],
    "g": [
        ("G10 its own area not first", [OWN_FIRST], [OWN]),
        ("G14 stems are copied", [STEMS_COPIED], [MEET]),
        ("G15 areas one block apart link", [LINK_PLUS_ONE], [HELP]),
    ],
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
        task = "test" if batch in ("domain", "one") else "runGameTestServer"
        # A build that fails to compile runs no tests: the old log must not be read as this run's.
        if os.path.exists(REPO + "/run/logs/latest.log"):
            os.remove(REPO + "/run/logs/latest.log")
        out = subprocess.run(["./gradlew", task, "--continue"], cwd=REPO, env=env, capture_output=True, text=True)
        if "Compilation failed" in out.stdout + out.stderr:
            raise SystemExit(f"{batch}: the build did not compile\n" + out.stdout[-3000:])
        print(f"{task} exit {out.returncode}")
        if batch in ("domain", "one"):
            failed = set()
            results = REPO + "/build/test-results/test"
            for f in os.listdir(results):
                if not f.endswith(".xml"):
                    continue
                body = open(os.path.join(results, f)).read()
                if "<failure" in body or "<error" in body:
                    failed.add(re.search(r'testsuite name="[^"]*\.([A-Za-z]+)"', body).group(1))
            if out.returncode != 0 and not failed:
                print(out.stdout[-3000:], out.stderr[-3000:])
        else:
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
    if sys.argv[1] == "domain-each":
        for m in BATCHES["domain"]:
            BATCHES["one"] = [m]
            run("one")
    else:
        for b in sys.argv[1:]:
            run(b)
