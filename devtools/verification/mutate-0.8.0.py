# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.8.0's mutation pass (D-0007, phase 5): each mutation breaks one rule, and the run must
fail in the test that guards it. The domain mutations run alone, one JUnit run each; the GameTest ones
run in batches, each mutation in a batch aimed at tests no other mutation in it touches, and a catch
is read off the failed tests' names. A mutation is one or more edits. Files are restored from memory
whatever happens. Left out: villagers without attack damage, which crashes the server at the first
blow rather than failing a test.

    uv run --no-project python devtools/verification/mutate-0.8.0.py domain-each | a | b | c | d | e
"""
import os
import re
import subprocess
import sys

REPO = str(__import__("pathlib").Path(__file__).resolve().parents[2])
MAIN = REPO + "/src/main/java/com/chunkworks/serfdom/"
DOM = REPO + "/src/domain/java/com/chunkworks/serfdom/domain/"

SWORD = "aswordsmanarmsatitschestkillsaraiderandputstheswordback"
LIVE = "aworkerindiamondkillsalivevindicatorwithoutpanicking"
BOW = "anarcherspendsarrowsfromitschestandputstherestback"
SWITCH = "outofarrowsitdrawsitssword"
GUN = "agunnerloadsroundsfromitschestfiresandputstherestback"
NONE = "withnothingtofightwithittakesnothingandhides"
WHO = "acaptivearmsandafreevillagerdoesnot"
FRIEND = "afriendinthelineholdsitsfire"
DEATH = "adefenderkilleddropswhatittook"
FREED = "adefendersetfreemidraiddropswhatittook"
HID = "adefenderthathidputsitsbowbackaftertheraid"

# (id, [(file, old, new), ...], the tests of which one must fail)
BATCHES = {
    "domain": [
        ("D1 a launcher taken", [(DOM + "Armoury.java", " && f.kind() != Kind.LAUNCHER && f.ammo() > 0", " && f.ammo() > 0")], ["ArmouryTest"]),
        ("D2 a ranged weapon without ammo taken", [(DOM + "Armoury.java", " && f.kind() != Kind.LAUNCHER && f.ammo() > 0", " && f.kind() != Kind.LAUNCHER")], ["ArmouryTest"]),
        ("D3 the worst melee weapon", [(DOM + "Armoury.java", "filter(f -> f.kind() == Kind.MELEE).min(BEST)", "filter(f -> f.kind() == Kind.MELEE).max(BEST)")], ["ArmouryTest"]),
        ("D4 a gun's reload ignored", [(DOM + "Armoury.java", " + (double) capacity * reloadPerRound)", ")")], ["ArmouryTest"]),
        ("D5 a free villager musters", [(DOM + "Defence.java", "return w.owned() && w.grown()", "return w.grown()")], ["DefenceTest"]),
        ("D6 it arms again every look", [(DOM + "Defence.java", "if (!f.carries() && !f.tried()) return Step.ARM;", "if (!f.carries()) return Step.ARM;")], ["DefenceTest"]),
        ("D7 it never hides", [(DOM + "Defence.java", "        if (!f.rangedReady() && !f.melee()) return Step.HIDE;\n", "")], ["DefenceTest"]),
        ("D8 it keeps what it took", [(DOM + "Defence.java", "return f.carries() ? Step.PUT_BACK : Step.NONE;", "return Step.NONE;")], ["DefenceTest"]),
        ("D9 melee before a ready bow", [(DOM + "Defence.java", "if (rangedReady) return Hand.RANGED;", "if (rangedReady && !melee) return Hand.RANGED;")], ["DefenceTest"]),
        ("D10 no reach from home", [(DOM + "Defence.java", "return toTarget <= sight && fromHome <= reach;", "return toTarget <= sight;")], ["DefenceTest"]),
        ("D11 a reload past its capacity", [(DOM + "Defence.java", "int n = Math.min(capacity - loaded, carried);", "int n = carried;")], ["DefenceTest"]),
        ("D12 it shoots through friends", [(DOM + "LineOfFire.java", "        for (var f : friends) if (meets(muzzle, target, f.grown(margin))) return false;\n", "")], ["LineOfFireTest"]),
        ("D13 no margin", [(DOM + "LineOfFire.java", "if (meets(muzzle, target, f.grown(margin))) return false;", "if (meets(muzzle, target, f)) return false;")], ["LineOfFireTest"]),
    ],
    # Alone: every weapon test rests on it.
    "a": [
        ("G2 the work-tool stash takes the weapon", [(MAIN + "Workers.java", "        if (com.chunkworks.serfdom.defence.Defenders.arms(villager).holds()) return;\n", "")], [SWORD, LIVE, BOW, GUN]),
    ],
    "b": [
        ("G3 no line of fire", [(MAIN + "behavior/Defend.java", "        if (!clear(level, v, t)) return;\n        var arrow = takeOne(v, hand);", "        var arrow = takeOne(v, hand);")], [FRIEND]),
        ("G6 a launcher is a gun", [(MAIN + "defence/Armouries.java", "gun.get().launcher() ? Armoury.Kind.LAUNCHER : Armoury.Kind.GUN", "Armoury.Kind.GUN")], [NONE]),
        ("G11 the dead drop nothing they took", [(MAIN + "defence/Defenders.java",
            "            for (var k : arms(v).kept()) e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(v.level(), v.getX(), v.getY(), v.getZ(), k.stack().copy()));\n", "")], [DEATH]),
    ],
    "c": [
        ("G4 a skeleton's aim", [(MAIN + "behavior/Defend.java", "shot.shoot(dx, dy + 0.5 * ARROW_GRAVITY * flight * flight, dz,", "shot.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz,")], [BOW]),
        ("G7 nothing put back", [(MAIN + "behavior/RaidDuty.java", "            case PUT_BACK -> { if (!ShopTime.busy(villager)) brain.setActiveActivityIfPossible(Serfdom.DEFEND.get()); }",
                                  "            case PUT_BACK -> {}")], [SWORD]),
        ("G9 vanilla's panic takes the defender", [(MAIN + "WorkerBrain.java",
            "new com.chunkworks.serfdom.behavior.Unless<Villager>(new VillagerPanicTrigger(), com.chunkworks.serfdom.defence.Defenders::defending)",
            "new VillagerPanicTrigger()")], [LIVE]),
    ],
    # After the first pass: G5 there was equivalent (a free villager's brain has no defence to muster
    # it), and G7 had no test on its path (a defender that hid). G5 is aimed at a defender set free;
    # G7 again with the hiding test.
    "e": [
        ("G5 a defender set free keeps what it took", [(MAIN + "Workers.java", "        com.chunkworks.serfdom.defence.Defenders.drop(villager);\n", "")], [FREED]),
        ("G7 nothing put back", [(MAIN + "behavior/RaidDuty.java", "            case PUT_BACK -> { if (!ShopTime.busy(villager)) brain.setActiveActivityIfPossible(Serfdom.DEFEND.get()); }",
                                  "            case PUT_BACK -> {}")], [HID]),
    ],
    "d": [
        ("G8 a defender trades", [(MAIN + "defence/Defenders.java", "                e.setCanceled(true);\n                e.setCancellationResult", "                e.setCancellationResult")], [SWORD]),
        ("G10 rounds never leave what it carries", [(MAIN + "behavior/Defend.java", "        Defenders.set(v, arms.with(kept));\n        return out;", "        return out;")], [GUN]),
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
