# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.7.0's mutation pass (D-0006, phase 4b): each mutation breaks one rule, and the run must
fail in the test that guards it. The domain mutations run alone, one JUnit run each; the GameTest ones
run in batches, each mutation in a batch aimed at tests no other mutation in it touches, and a catch
is read off the failed tests' names. A mutation is one or more edits. Files are restored from memory
whatever happens.

    uv run --no-project python devtools/verification/mutate-0.7.0.py domain-each | a | b | c | d
"""
import os
import re
import subprocess
import sys

REPO = str(__import__("pathlib").Path(__file__).resolve().parents[2])
MAIN = REPO + "/src/main/java/com/chunkworks/serfdom/"
DOM = REPO + "/src/domain/java/com/chunkworks/serfdom/domain/"

WINDOW = "awindowshopperbuyswhatitlikesandglancesatwhatitdoesnot"
WORKER = "aworkerbuyswhatitlikesforitshomechest"
CLIMATE = "avillagesclimateisitsbedsbiomeandgoodsfromelsewhereareworthmore"
TASTE = "atasteisthevillagersownandleansbyitstrade"
PEDDLER = "atownsmanbuysafreefarmerssparebreadratherthanadearerstalls"
STORAGE = "astallisneitherahomechestnorapostsstorage"
PATH = "aVillagerPlansItsWayAroundAStallNeverOverIt".lower()

# (id, [(file, old, new), ...], the tests of which one must fail)
BATCHES = {
    "domain": [
        ("D1 nothing is foreign", [(DOM + "Climate.java", "return !origins.isEmpty() && !origins.contains(this) ? bonus : 1.0;", "return 1.0;")], ["ClimateTest"]),
        ("D2 its own climate's goods foreign", [(DOM + "Climate.java", "return !origins.isEmpty() && !origins.contains(this) ? bonus : 1.0;",
                                                 "return !origins.isEmpty() ? bonus : 1.0;")], ["ClimateTest"]),
        ("D3 the lean pulls the wrong way", [(DOM + "Taste.java", "Math.pow(u, Math.pow(2, -lean))", "Math.pow(u, Math.pow(2, lean))")], ["TasteTest"]),
        ("D4 the favourite is the lowest", [(DOM + "Taste.java", "if (of(c) > of(best)) best = c;", "if (of(c) < of(best)) best = c;")], ["TasteTest"]),
        ("D5 it likes what it rates 1", [(DOM + "Taste.java", "public boolean likes(Category c) { return of(c) > 1; }", "public boolean likes(Category c) { return of(c) >= 1; }")], ["TasteTest"]),
        ("D6 an item by the lower of its kinds", [(DOM + "Taste.java", "best = Math.max(best, of(Objects.requireNonNull(c)));", "best = Math.min(best, of(Objects.requireNonNull(c)));")], ["TasteTest"]),
        ("D7 it wants what it doesn't like", [(DOM + "Taste.java", "if (!valued || categories.stream().noneMatch(this::likes)) return 0;", "if (!valued) return 0;")], ["TasteTest"]),
        ("D8 no most to a want", [(DOM + "Taste.java", "return Math.max(0, most - has);", "return most;")], ["TasteTest"]),
        ("D9 a lot of one, whatever the value", [(DOM + "Peddler.java", "return Optional.of(new Lot((int) Math.clamp(Math.round(1 / value), 1, Stall.MOST), 1));",
                                                  "return Optional.of(new Lot(1, 1));")], ["PeddlerTest"]),
        ("D10 it sells what it keeps", [(DOM + "Peddler.java", "return Math.max(0, has - keep);", "return has;")], ["PeddlerTest"]),
        ("D11 a stall seen is looked at again", [(DOM + "Shopping.java", " && !seen.contains(o.at()))", ")")], ["ShoppingTest"]),
        ("D12 it goes to look at a seller", [(DOM + "Shopping.java", "filter(o -> o.stall() && o.open()", "filter(o -> o.open()")], ["ShoppingTest"]),
        ("D13 never a need, only looks", [(DOM + "Shopping.java", "if (due(w, dayTime, d, emeralds, !needs.isEmpty(), perDay)) {", "if (false) {")], ["ShoppingTest"]),
        ("D14 a need nothing sells isn't counted", [(DOM + "Shopping.java", "            day = tripped(day, dayTime);\n", "")], ["ShoppingTest"]),
        ("D15 yesterday's stalls never forgotten", [(DOM + "Shopping.java", "return d.seenDay() == Purse.day(dayTime) ? d.seen() : Set.of();", "return d.seen();")], ["ShoppingTest"]),
    ],
    "a": [
        ("G1 no want for taste", [(MAIN + "market/Counter.java", "if (n > 0) want = Optional.of(", "if (false) want = Optional.of(")], [WINDOW, WORKER]),
        ("G4 the trade's lean unread", [(MAIN + "market/Needs.java", "for (var k : List.of(EVERYONE, id)) leans", "for (var k : List.<String>of()) leans")], [TASTE]),
        ("G7 the seller's purse takes nothing", [(MAIN + "market/Counter.java", "            Purses.receive(seller, paid);\n", "")], [PEDDLER]),
        ("G9 a stall is storage", [(MAIN + "job/Storage.java", "        if (level.getBlockEntity(pos) instanceof com.chunkworks.serfdom.market.ForSaleBlockEntity) return false;\n", "")], [STORAGE]),
    ],
    "b": [
        ("G2 climate ignored", [(MAIN + "market/Tastes.java", "return climate(level, villager).factor(origins(stack), climateBonus()) * of(villager)", "return 1.0 * of(villager)")], [CLIMATE]),
        ("G5 a hired worker sells", [(MAIN + "market/Peddlers.java", "!v.isBaby() && !Workers.owned(v) && ", "!v.isBaby() && ")], [PEDDLER]),
        ("G8 no celebration", [(MAIN + "market/Counter.java", "        if (favourite && reaction.bought()) {", "        if (false) {")], [WINDOW]),
    ],
    "c": [
        ("G3 taste ignored", [(MAIN + "market/Tastes.java", " * of(villager).factor(categories(stack)) * ", " * 1.0 * ")], [CLIMATE]),
        ("G6 villagers never sell", [(MAIN + "market/Shoppers.java", "var wares = needs.isEmpty() ? List.<Peddlers.Ware>of() : Peddlers.near(level, bed.get(), reach(), villager);",
                                      "var wares = List.<Peddlers.Ware>of();")], [PEDDLER]),
        ("G11 a worker's want blind to its chest", [(MAIN + "market/Tastes.java", "has = Kitchen.counts(level, home.get()).getOrDefault(key, 0);", "has = 0;")], [WORKER]),
    ],
    # Both writes of the stalls seen: the plan's, as it decides, and the counter's, as it is served.
    "d": [
        ("G10 the stalls seen never kept", [
            (MAIN + "market/Shoppers.java", "        if (!decision.day().equals(saved.day())) Purses.set(villager, saved.with(decision.day()));\n", ""),
            (MAIN + "market/Counter.java", "        Purses.set(villager, seen.with(Shopping.saw(seen.day(), now, stall.getBlockPos().asLong())));\n", "")], [WINDOW]),
        ("G12 a mob may climb a stall (the old code)", [(MAIN + "market/ForSaleBlock.java",
            "        return net.minecraft.world.level.pathfinder.PathType.FENCE;", "        return null;")], [PATH]),
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
        out = subprocess.run(["./gradlew", task, "--continue"], cwd=REPO, env=env, capture_output=True, text=True)
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
            print(("CAUGHT " if hit else "MISSED ") + mid + " -> " + (", ".join(hit) if hit else " or ".join(tests)))
        print("failed:", sorted(failed))
    finally:
        for path, text in saved.items():
            open(path, "w").write(text)
        print("restored", len(saved), "files")


if __name__ == "__main__":
    if sys.argv[1] == "domain-each":
        for m in BATCHES["domain"]:
            BATCHES["one"] = [m]
            run("one")
    else:
        run(sys.argv[1])
