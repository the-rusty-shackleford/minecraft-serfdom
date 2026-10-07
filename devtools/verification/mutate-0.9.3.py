# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.9.3's mutation pass (D-0011, arms out of the fold): each mutation breaks one of the server's
rules the arms answer to, and the GameTest server's run must fail the test that guards it. The look itself
(the arms, the hands, the item in the hand, the sleeves and the manacles) is the client's, judged by the
booth's photographs, and the poses are the domain's, by GripTest. Files are restored from memory whatever
happens.

    uv run --no-project python devtools/verification/mutate-0.9.3.py a | b | c
"""
import os
import re
import subprocess
import sys

REPO = str(__import__("pathlib").Path(__file__).resolve().parents[2])
MAIN = REPO + "/src/main/java/com/chunkworks/serfdom/"
SWING = "avillagersswingplaysandends"
WOOD = "awoodcutterfellstheoakandleavesbuildsalone"
FARM = "afarmerharvestsripecropsandsortsthem"
SWORD = "aswordsmanarmsatitschestkillsaraiderandputstheswordback"
CHAINED = "adefenderchainedmidfightisnolongeraggressive"

BATCHES = {
    # Alone: every swing test leans on it.
    "a": [("M1 a villager's swing never moves", [(MAIN + "mixin/VillagerMixin.java", "        ((LivingEntityInvoker) this).serfdom$updateSwingTime();\n", "")], [SWING, WOOD])],
    "b": [
        ("M2 no blow while chopping", [(MAIN + "job/WoodTask.java", "                if ((total - left) % Job.SWING == 1) worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);\n", ""),
                                      (MAIN + "job/WoodTask.java", "            level.destroyBlock(pos, false, worker);\n            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);\n",
                                       "            level.destroyBlock(pos, false, worker);\n")], [WOOD]),
        ("M3 no blow at a harvest", [(MAIN + "job/CropTask.java", "            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);\n            done.accept(pos);", "            done.accept(pos);")], [FARM]),
        ("M4 a defender is never aggressive", [(MAIN + "behavior/Defend.java", "        v.setAggressive(step == Defence.Step.FIGHT && target.filter(LivingEntity::isAlive).isPresent());\n", "")], [SWORD]),
    ],
    # Alone, and only where a defence is stopped from outside mid-fight: a defence that ends by itself
    # clears it in its own tick first. (Its first pass, against the swordsman alone, got past.)
    "c": [("M5 aggression kept when the defence stops", [(MAIN + "behavior/Defend.java", "        v.setAggressive(false);\n        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);", "        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);")], [CHAINED])],
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
