# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.4.0's mutation pass (D-0004): each mutation breaks one rule, and the run must fail in
the test that guards it. The domain mutations run alone, one JUnit run each; the GameTest ones run
in three batches, each mutation in a batch aimed at tests no other mutation in it touches, and a
catch is read off the failed tests' names. Files are restored from memory whatever happens. The
layer's drawing rules (the robe, the hat) are judged by the booth's photographs, not here.

    uv run --no-project python devtools/verification/mutate-0.4.0.py domain-each | a | a2 | b | c
"""
import os
import re
import subprocess
import sys

REPO = str(__import__("pathlib").Path(__file__).resolve().parents[2])
MAIN = REPO + "/src/main/java/com/chunkworks/serfdom/"
DOM = REPO + "/src/domain/java/com/chunkworks/serfdom/domain/"

# (id, file, old, new, the tests of which one must fail)
BATCHES = {
    "domain": [
        ("D1 a stranger uses the slots", DOM + "Wardrobe.java", "        if (!viewerOwns) return Access.NOT_YOURS;\n", "", ["WardrobeTest"]),
        ("D2 binding ignored", DOM + "Wardrobe.java", "return !worn.empty() && (creative || !worn.bound());", "return !worn.empty();", ["WardrobeTest"]),
        ("D3 a slot takes anything", DOM + "Wardrobe.java", "return !piece.empty() && piece.fits().contains(slot);", "return !piece.empty();", ["WardrobeTest"]),
        ("D4 an escapee drops its gear", DOM + "Parting.java", "case ESCAPES, FREED_BY_LAW -> Fate.STAYS_ON;", "case ESCAPES, FREED_BY_LAW -> Fate.DROPS;", ["PartingTest"]),
        ("D5 a vanishing piece drops", DOM + "Parting.java", "case DIES, CONVERTS -> vanishing ? Fate.VANISHES : Fate.DROPS;", "case DIES, CONVERTS -> Fate.DROPS;", ["PartingTest"]),
        ("D6 the chest not deepened", DOM + "Fit.java", "BODY_DEPTH = 1.32;", "BODY_DEPTH = 1.0;", ["FitTest"]),
        ("D7 the helmet's lift not turned", DOM + "Fit.java", "rotate(xRot, yRot, zRot, new Vec(0, -HEAD_LIFT, 0).times(scale))", "new Vec(0, -HEAD_LIFT, 0).times(scale)", ["FitTest"]),
        ("D8 the panel too tall for GUI scale 5", DOM + "WorkerLayout.java", "HEIGHT = 228,", "HEIGHT = 300,", ["WorkerLayoutTest"]),
    ],
    "a": [
        ("G1 armour never wears", MAIN + "mixin/VillagerWearMixin.java",
         "doHurtEquipment(source, damage, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);", "",
         ["armouronavillagerwearsandbreaksasonaplayer"]),
        ("G2 a dead worker's gear left on it", MAIN + "Workers.java", "        out.addAll(takeOff(villager, way));\n", "",
         ["adeadworkerdropswhatitworebutthevanishing", "aworkerturnedzombiedropsitsthings"]),
        ("G4 a slot takes anything", MAIN + "WorkerMenu.java", "return villager == null ? !stack.isEmpty() : Wardrobe.takes(which, piece(stack, villager));", "return !stack.isEmpty();",
         ["onlytheownerwithinreachchangeswhataworkerwears"]),
    ],
    # G4 again, alone, after its test was tightened (its first run went uncaught: the click it
    # counted on landed on bound boots, so binding refused it before the slot's own rule was asked).
    "a2": [
        ("G4 a slot takes anything", MAIN + "WorkerMenu.java", "return villager == null ? !stack.isEmpty() : Wardrobe.takes(which, piece(stack, villager));", "return !stack.isEmpty();",
         ["onlytheownerwithinreachchangeswhataworkerwears"]),
    ],
    "b": [
        ("G3 the screen never shuts", MAIN + "WorkerMenu.java", "        return access(player, villager) == Wardrobe.Access.OK;", "        return true;",
         ["ascreenleftopenonaworkerthatdiesorgoesfreetakesnothing", "onlytheownerwithinreachchangeswhataworkerwears"]),
        ("G5 set free keeps its gear on", MAIN + "Workers.java", "        for (var piece : takeOff(villager, way)) villager.spawnAtLocation(piece);\n", "",
         ["settingacaptivefreeletsitgoandgivestheownerschainback"]),
        ("G6 no mark to drop whole", MAIN + "WorkerMenu.java", "            if (!stack.isEmpty()) villager.setDropChance(WORN[index], Parting.GUARANTEED);\n", "",
         ["apiececlickedontoaworkeriswornandprotectsasarmourdoes", "acaptiveslipshomeatnightandachainonthewayendsit", "acaptureaguardsawisowedtothecaseandpayingfreesitandkeepsthechain"]),
    ],
    "c": [
        ("G7 a zombie keeps nothing of what it wore", MAIN + "Workers.java", "belongings(villager, worker, Parting.Way.CONVERTS)", "belongings(villager, worker, Parting.Way.ESCAPES)",
         ["aworkerturnedzombiedropsitsthings"]),
        ("G8 a stranger's menu is valid", MAIN + "WorkerMenu.java", "Workers.of(villager).ownedBy(player.getUUID())", "true",
         ["onlytheownerwithinreachchangeswhataworkerwears"]),
        ("G9 an escapee drops its gear", MAIN + "behavior/RunHome.java", "Parting.Way.ESCAPES", "Parting.Way.SET_FREE",
         ["acaptiveslipshomeatnightandachainonthewayendsit"]),
        ("G10 the law's freed captive drops its gear", MAIN + "Remedies.java", "Workers.free((ServerLevel) loaded.level(), loaded, com.chunkworks.serfdom.domain.Parting.Way.FREED_BY_LAW);",
         "Workers.free((ServerLevel) loaded.level(), loaded, com.chunkworks.serfdom.domain.Parting.Way.SET_FREE);",
         ["acaptureaguardsawisowedtothecaseandpayingfreesitandkeepsthechain"]),
        ("G11 shift-clicks put nothing on", MAIN + "WorkerMenu.java", "            moved = moveItemStackTo(stack, to, to + 1, false);", "            moved = false;",
         ["ashiftclicksendseachpiecetoitsslotandback", "apiececlickedontoaworkeriswornandprotectsasarmourdoes"]),
    ],
}


def run(batch):
    mutations = BATCHES[batch]
    saved = {}
    try:
        for mid, path, old, new, _ in mutations:
            text = saved.setdefault(path, open(path).read()) if path not in saved else open(path).read()
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
                if "<failure" in body:
                    failed.add(re.search(r'testsuite name="[^"]*\.([A-Za-z]+)"', body).group(1))
        else:
            log = open(REPO + "/run/logs/latest.log").read()
            failed = set(re.findall(r"\]: (\w+) failed at", log))
            print(re.findall(r"(?:All \d+ required tests passed|\d+ required tests failed)", log))
        for mid, _, _, _, tests in mutations:
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
