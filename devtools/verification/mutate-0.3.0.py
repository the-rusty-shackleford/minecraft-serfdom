# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.3.0's mutation pass (D-0003): each mutation breaks one rule, and the run must fail in
the test that guards it. The domain mutations run alone, one JUnit run each; the GameTest ones run
in two batches, each mutation in a batch aimed at a different test, and a catch is read off the
failed test's name. Files are restored from memory whatever happens.

    uv run --no-project python devtools/verification/mutate-0.3.0.py domain-each | a | b
"""
import os
import re
import subprocess
import sys

REPO = str(__import__("pathlib").Path(__file__).resolve().parents[2])
MAIN = REPO + "/src/main/java/com/chunkworks/serfdom/"
DOM = REPO + "/src/domain/java/com/chunkworks/serfdom/domain/"

# (id, file, old, new, the test that must fail)
BATCHES = {
    "domain": [
        ("D1 non-owner uncuffs", DOM + "Chain.java", "case ACTOR -> f.actorOwns() ? Act.UNCUFF : Act.LET_GO;", "case ACTOR -> Act.UNCUFF;", "ChainTest"),
        ("D2 looking away ignored", DOM + "Capture.java", "if (!looking) return Step.LOOKED_AWAY;", "", "CaptureTest"),
        ("D3 fleeing keeps the debt", DOM + "Remedy.java", "if (!owed.containsKey(c)) return this;\n        var next = mutable(this);\n        next.remove(c);\n        return new Remedy(next);", "return this;", "RemedyTest"),
        ("D4 a night rolled twice", DOM + "Escape.java", "day(dayTime) > lastRolledDay", "day(dayTime) >= lastRolledDay", "EscapeTest"),
        ("D5 captive meets", DOM + "WorkDay.java", "public static final int CAPTIVE_WORK_END = MEET_END;", "public static final int CAPTIVE_WORK_END = WORK_END;", "WorkDayTest"),
        ("D6 captive works at full pace", DOM + "Pace.java", "(captive ? 1.0 - captiveSlowdown : 1.0)", "1.0", "CaptiveRulesTest"),
        ("D7 a child of two owners is owned", DOM + "Birth.java", "mother.isPresent() && mother.equals(father) ? mother : Optional.empty()", "mother.isPresent() ? mother : father", "CaptiveRulesTest"),
    ],
    "a": [
        ("G1 a snapped chain drops", MAIN + "mixin/EntityMixin.java", "if (com.chunkworks.serfdom.Workers.cuffed(villager)) cir.setReturnValue(null);\n        else cir.setReturnValue", "cir.setReturnValue", "thechainsstayonuntiltheownertakesthemoff"),
        ("G2 captives want golems", MAIN + "mixin/VillagerMixin.java", "if (!self.level().isClientSide() && com.chunkworks.serfdom.Workers.of(self).captive()) cir.setReturnValue(false);", "", "captiveswantnogolemandtheirbedsbringnocat"),
        ("G3 the capture owed to no case", MAIN + "Workers.java", "owedTo.ifPresent(v -> Remedies.owe(player, v, villager.getUUID()));", "", "acaptureaguardsawisowedtothecaseandpayingfreesitandkeepsthechain"),
        ("G4 nobody gets up", MAIN + "behavior/CaptiveNight.java", "Workers.set(level, villager, worker.withNight(new Worker.Night(night.rolled(), Optional.empty(), true)));", "", "acaptiveslipshomeatnightandachainonthewayendsit"),
        ("G5 reach ignored", MAIN + "Captures.java", "double distance = present ? player.distanceTo(villager) : 0.0;", "double distance = 0.0;", "onlyagrowntradedvillageristakenandsteppingofforlookingawayendsthehold"),
        ("G6 hired workers hum", MAIN + "Humming.java", "if (!SerfdomConfig.HUMMING.get() || !Workers.of(worker).captive()) return;", "if (!SerfdomConfig.HUMMING.get()) return;", "acaptiveatworkhumstheworksonganda hiredworkerdoesnot".replace(" ", "")),
        ("G7 a converted worker drops nothing", MAIN + "Workers.java", "for (var stack : belongings(villager, worker)) level.addFreshEntity", "for (var stack : java.util.List.<ItemStack>of()) level.addFreshEntity", "aworkerturnedzombiedropsitsthings"),
    ],
    "b": [
        ("G8 captives' beds bring cats", MAIN + "mixin/CatSpawnerMixin.java", "return beds - Workers.captiveBedsNear(level, pos, 48);", "return beds;", "captiveswantnogolemandtheirbedsbringnocat"),
        ("G9 paying frees nobody", MAIN + "Remedies.java", "            Workers.free((ServerLevel) loaded.level(), loaded);\n", "", "acaptureaguardsawisowedtothecaseandpayingfreesitandkeepsthechain"),
        ("G10 captives are no cargo", MAIN + "compat/WheelsCompat.java", "return entity instanceof Villager v && Workers.cuffed(v); }", "return false; }", "fourcaptivesrideinthetrailerandachainletsthemout"),
        ("G11 a stranger may take a bought village's people", MAIN + "Captures.java", "DeedCompat.allows(level, villager.blockPosition(), player.getUUID()));", "true);", "aboughtvillagesownertakesitspeoplefreelyandastrangernotatall"),
        ("G12 no child is owned", MAIN + "mixin/VillagerMixin.java", "com.chunkworks.serfdom.Workers.born(child, (Villager) (Object) this, partner);", "", "achildofoneownersworkersistheirsandtakesnopost"),
        ("G13 set free keeps the owner's chain", MAIN + "Screens.java", "if (Workers.of(worker).cuffed() && !player.getAbilities().instabuild)", "if (false)", "settingacaptivefreeletsitgoandgivestheownerschainback"),
        ("G14 a captive works through no meeting", MAIN + "WorkerBrain.java", ".changeActivityAt(WorkDay.CAPTIVE_WORK_END, Serfdom.STAY.get())", ".changeActivityAt(WorkDay.WORK_END, Serfdom.STAY.get())", "acaptiveworksthroughthemeetingandworksslower"),
    ],
    "c": [
        ("G15 a held key's repeat takes the cuffs off", MAIN + "Workers.java", "        if (Captures.settling(player, villager)) {", "        if (false) {", "holdingthechaintwosecondstakesafreevillagerandlettinggoearlydoesnot"),
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
        for mid, _, _, _, test in mutations:
            print(("CAUGHT " if test in failed else "MISSED ") + mid + " -> " + test)
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
