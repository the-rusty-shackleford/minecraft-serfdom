# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.5.0's mutation pass (D-0005): each mutation breaks one rule, and the run must fail in
the test that guards it. The domain mutations run alone, one JUnit run each; the GameTest ones run
in three batches, each mutation in a batch aimed at tests no other mutation in it touches, and a
catch is read off the failed tests' names. Files are restored from memory whatever happens.

    uv run --no-project python devtools/verification/mutate-0.5.0.py domain-each | a | b | c | d
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
        ("D1 asleep drains as fast as awake", DOM + "Hunger.java", "(asleep ? 0.5 : 1.0)", "1.0", ["HungerTest"]),
        ("D2 any bite however it overshoots", DOM + "Hunger.java", "return nutrition > 0 && points < MAX && points + nutrition - MAX <= nutrition / 2.0;", "return nutrition > 0 && points < MAX;", ["HungerTest", "MenuTest"]),
        ("D3 hunger never slows work", DOM + "Hunger.java", "if (points >= HALF) return 1.0;", "if (points > 0) return 1.0;", ["HungerTest"]),
        ("D4 a window eaten twice", DOM + "Meals.java", "w.getAsLong() != f.times().ateWindow() && ", "", ["MealsTest"]),
        ("D5 a sleeper eats", DOM + "Meals.java", "if (!f.awake() || f.cuffed()", "if (f.cuffed()", ["MealsTest"]),
        ("D6 no wait between meals", DOM + "Meals.java", "        if (f.gameTime() < f.times().retryAt()) return false;\n", "", ["MealsTest"]),
        ("D7 home's food passed over", DOM + "Menu.java", "        if (home.isPresent()) return Choice.eat(Kind.EAT_HOME, home.get(), -1);\n", "", ["MenuTest"]),
        ("D8 refused food eaten", DOM + "Menu.java", "return f != null && f.nutrition() > 0 && !f.refused();", "return f != null && f.nutrition() > 0;", ["MenuTest"]),
        ("D9 any table recipe a meal", DOM + "Menu.java", "            if (rule.station() == Station.TABLE && !f.tableMeals().contains(rule.result())) continue;\n", "", ["MenuTest"]),
        ("D10 hungry shown after a tool", DOM + "Need.java", "NO_BED, HUNGRY, NO_TOOL,", "NO_BED, NO_TOOL, HUNGRY,", ["HungerTest"]),
    ],
    "a": [
        ("G1 no meal is ever due", MAIN + "behavior/MealTime.java", "        if (Appetite.due(level, villager)) brain.setActiveActivityIfPossible(Serfdom.MEAL.get());\n", "",
         ["ahungryworkereatsbreadfromitshomechesttofull", "thecanteenfeedsaworkerwhosehomechestisempty", "aworkercooksitsbreakfastatafreesmoker"]),
    ],
    "b": [
        ("G2 no canteen", MAIN + "job/Kitchen.java", "return Posts.near(level, w.bed().get().pos(), Assignment.MAX_BED_TO_POST, w.owner().get());", "return List.of();",
         ["thecanteenfeedsaworkerwhosehomechestisempty"]),
        ("G3 a smoker emptied before it cooks", MAIN + "behavior/HaveMeal.java", "            if (!done && !stalled && !late) return;\n", "",
         ["aworkercooksitsbreakfastatafreesmoker"]),
        ("G4 the bowls kept", MAIN + "behavior/HaveMeal.java", "        leaves.ifPresent(l -> hands.addItem(new ItemStack(RecipeBook.item(l))));\n", "",
         ["aworkercooksapotmealandputsthebowlsback"]),
        ("G5 hunger never slows work", MAIN + "job/Jobs.java", "com.chunkworks.serfdom.Appetite.fed(worker)", "1.0",
         ["ahungryworkerworksslower"]),
        ("G6 a starved worker works on", MAIN + "behavior/WorkShift.java", "        if (com.chunkworks.serfdom.Appetite.starved(worker)) {", "        if (false) {",
         ["astarvedworkerstopsuntilithaseaten"]),
        ("G9 the meal keeps the day", MAIN + "behavior/HaveMeal.java", "        if (brain.isActive(Serfdom.MEAL.get())) brain.setActiveActivityIfPossible(brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L)));\n", "",
         ["ahungryworkereatsbreadfromitshomechesttofull", "ahungryworkerbreaksoffitsshifttoeatandgoesback"]),
    ],
    # G6 and G7 again, together, after their tests were tightened: a starved woodcutter that works on
    # had fetched its axe but not yet brought logs in, and a worker in chains was refused a second
    # time by the meal itself, so only its untouched meal times show it never sat down.
    "d": [
        ("G6 a starved worker works on", MAIN + "behavior/WorkShift.java", "        if (com.chunkworks.serfdom.Appetite.starved(worker)) {", "        if (false) {",
         ["astarvedworkerstopsuntilithaseaten"]),
        ("G7 chains stop no meal", MAIN + "Appetite.java", "                worker.cuffed(), worker.escaping(), worker.bed().isPresent()));", "                false, worker.escaping(), worker.bed().isPresent()));",
         ["acaptiveeatsandoneinchainsdoesnot"]),
    ],
    "c": [
        ("G7 chains stop no meal", MAIN + "Appetite.java", "                worker.cuffed(), worker.escaping(), worker.bed().isPresent()));", "                false, worker.escaping(), worker.bed().isPresent()));",
         ["acaptiveeatsandoneinchainsdoesnot"]),
        ("G8 children hunger", MAIN + "Appetite.java", "return on() && !villager.isBaby() && Workers.owned(villager);", "return on() && Workers.owned(villager);",
         ["anownedchildneverhungersandthescreenshowshunger"]),
        ("G10 the hungry icon never shows", MAIN + "Workers.java", "        if (Appetite.of(villager).hunger().hungry()) needs.add(Need.HUNGRY);\n", "",
         ["ahungryworkereatsbreadfromitshomechesttofull", "astarvedworkerstopsuntilithaseaten"]),
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
