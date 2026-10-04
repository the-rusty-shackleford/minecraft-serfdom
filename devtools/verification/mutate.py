# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom's mutation pass: each mutation is applied, the GameTests run, the file restored.
A mutation is caught when the named test fails."""
import os, re, subprocess, sys, shutil, json
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
M = "src/main/java/com/chunkworks/serfdom/"
MUTATIONS = [
    ("M1 owned villagers reset a novice's profession", M + "WorkerBrain.java",
     "                Pair.of(10, new KeepBed()));", "                Pair.of(10, new KeepBed()), Pair.of(10, ResetProfession.create()));",
     "anovicehiredawaykeepsitsprofession"),
    ("M2 a tree needs no crown of leaves", "src/domain/java/com/chunkworks/serfdom/domain/Felling.java",
     "        if (!crowned) return Optional.empty();", "", "awoodcutterfellstheoakandleavesbuildsalone"),
    ("M3 the record of placed logs is ignored", M + "job/Woodcutting.java",
     "PlacedLogs.placed(chunk, pos) ? Felling.Kind.PLACED_LOG : Felling.Kind.LOG", "Felling.Kind.LOG",
     "awoodcutterfellstheoakandleavesbuildsalone"),
    ("M4 deposits go to the last choice", M + "job/Storage.java",
     "            if (!order.isEmpty()) return Optional.of(order.getFirst());", "            if (!order.isEmpty()) return Optional.of(order.getLast());",
     "afarmerharvestsripecropsandsortsthem"),
    ("M5 the fee ignores the level", M + "Hire.java",
     "Hiring.fee(villager.getVillagerData().getLevel(), SerfdomConfig.HIRE_PER_LEVEL.get())", "Hiring.fee(1, SerfdomConfig.HIRE_PER_LEVEL.get())",
     "hiringtakesthefeeandmakesafollower"),
    ("M6 a broken chain drops a lead", "src/main/resources/serfdom.mixins.json",
     '"mixins": ["EntityMixin", "MobAccessor", "VillagerMixin"]', '"mixins": ["MobAccessor", "VillagerMixin"]',
     "thechainleadsaworkeranddropsasitself"),
    ("M7 workers do not open gates", M + "WorkerBrain.java",
     "                Pair.of(0, new OpenGates()),\n", "", "aworkeropensthegateandshutsitbehindit"),
    ("M8 sneak-use on a worker reaches Village Deed", M + "Workers.java",
     "NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Workers::interact);", "NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Workers::interact);",
     "ownedvillagersarenotofferedorgifted"),
    ("M9 no restock at the post", M + "behavior/WorkShift.java",
     "        if (worker.shouldRestock()) worker.restock();", "", "aworkerrestocksatitspost"),
    ("M10 the shift never winds down", "src/domain/java/com/chunkworks/serfdom/domain/Shift.java",
     "        if (f.shiftLeft() <= WIND_DOWN) {", "        if (false) {", "afarmerharvestsripecropsandsortsthem"),
]

env = dict(os.environ, JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64")
results = []
for name, rel, old, new, test in MUTATIONS:
    path = REPO / rel
    original = path.read_text()
    if old not in original:
        results.append((name, "NOT APPLIED (text not found)", test))
        print(name, "NOT APPLIED", flush=True)
        continue
    path.write_text(original.replace(old, new, 1))
    try:
        subprocess.run(["./gradlew", "runGameTestServer", "--console=plain", "-q"], cwd=REPO, env=env,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=900)
        log = (REPO / "run/logs/latest.log").read_text(errors="replace")
        failed = sorted(set(re.findall(r"LogTestReporter/\]: (\w+) failed", log)))
        loading = "Mod loading has failed" in log or "Crash" in log and "failed" not in log
        caught = test in failed
        verdict = "CAUGHT" if caught else ("LOAD FAILURE" if loading and not failed else "MISSED")
        results.append((name, verdict + " " + json.dumps(failed), test))
        print(name, verdict, failed, flush=True)
    finally:
        path.write_text(original)
print("\nSUMMARY")
for r in results:
    print(" | ".join(r))
