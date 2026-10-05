# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom 0.6.0's mutation pass (D-0006, phase 4a): each mutation breaks one rule, and the run must
fail in the test that guards it. The domain mutations run alone, one JUnit run each; the GameTest ones
run in batches, each mutation in a batch aimed at tests no other mutation in it touches, and a catch
is read off the failed tests' names. Files are restored from memory whatever happens.

    uv run --no-project python devtools/verification/mutate-0.6.0.py domain-each | a | b | c | d | e
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
        ("D1 the purse has no cap", DOM + "Purse.java", "return new Purse(Math.min(emeralds + n, Math.max(emeralds, r.cap())), lastMorning);",
         "return new Purse(emeralds + n, lastMorning);", ["PurseTest", "TillTest"]),
        ("D2 a morning seen again", DOM + "Purse.java", "return morning(dayTime) && day(dayTime) != lastMorning;", "return morning(dayTime);", ["PurseTest"]),
        ("D3 the deposit to the rich", DOM + "Purse.java", "eligible && emeralds < r.below()", "eligible", ["PurseTest"]),
        ("D4 found starts at the line", DOM + "Purse.java", "return new Purse(found ? r.cap() : Math.min(r.below(), r.cap()), NEVER);",
         "return new Purse(Math.min(r.below(), r.cap()), NEVER);", ["PurseTest"]),
        ("D5 every trade open", DOM + "Till.java", "public boolean open(Purse p) { return p.emeralds() + in >= out; }", "public boolean open(Purse p) { return true; }", ["TillTest"]),
        ("D6 a trade's takings ignored", DOM + "Till.java", "int in = (EMERALD.equals(costA) ? countA : 0) + (EMERALD.equals(costB) ? countB : 0);", "int in = 0;", ["TillTest"]),
        ("D7 a price list over data", DOM + "Values.java", "if (!out.containsKey(item) && !refused.contains(item)) median(seen)", "median(seen)", ["ValuesTest"]),
        ("D8 the mean, not the median", DOM + "Values.java", "return OptionalDouble.of(n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2);",
         "return OptionalDouble.of(s.stream().mapToDouble(Double::doubleValue).average().orElseThrow());", ["ValuesTest"]),
        ("D9 a household uses nothing", DOM + "Household.java", "double worn = w.getOrDefault(need.name(), 0.0) + need.perDay();",
         "double worn = w.getOrDefault(need.name(), 0.0);", ["HouseholdTest"]),
        ("D10 food not first", DOM + "Household.java", "if (need.food()) out.addFirst(want); else out.add(want);", "out.add(want);", ["HouseholdTest"]),
        ("D11 any price is paid", DOM + "Verdict.java", "if (each <= willing) return 1.0;", "if (true) return 1.0;", ["VerdictTest"]),
        ("D12 every purchase a bargain", DOM + "Verdict.java", "s.each() <= f.base() ? Reaction.BARGAIN", "true ? Reaction.BARGAIN", ["VerdictTest"]),
        ("D13 a short purse buys", DOM + "Verdict.java", "        if (f.emeralds() < s.price()) return new Outcome(Reaction.CANT_AFFORD, 0);\n", "", ["VerdictTest"]),
        ("D14 the dearest stall", DOM + "Shopping.java", "filter(o -> o.price() <= emeralds).min(cheapest)", "filter(o -> o.price() <= emeralds).max(cheapest)", ["ShoppingTest"]),
        ("D15 any stall in a bought village", DOM + "Shopping.java", "return villageOwner.isEmpty() || villageOwner.get().equals(stallOwner) || trusted;", "return true;", ["ShoppingTest"]),
        ("D16 two trips a social time", DOM + "Shopping.java", "d.tripDay() != Purse.day(dayTime) && ", "", ["ShoppingTest"]),
        ("D17 the ledger keeps every line", DOM + "Ledger.java", "        while (l.size() > LINES) l.removeFirst();\n", "", ["LedgerTest"]),
        ("D18 a full worker goes to buy", DOM + "Menu.java", "f.mayBuy() && f.hunger().points() < Hunger.MAX && noFood(f)", "f.mayBuy() && noFood(f)", ["MenuTest"]),
    ],
    "a": [
        ("G1 the purse closes nothing", MAIN + "market/Purses.java", "serfdom$close(!till(offer).open(purse));", "serfdom$close(false);",
         ["apurseofthreepaysforthreesalesandthefourthissoldout", "aplayerspurchasefillsthepurseandopensatradeandthecapholds"]),
        ("G6 the hire fee vanishes", MAIN + "Hire.java", "        if (com.chunkworks.serfdom.market.Purses.on()) com.chunkworks.serfdom.market.Purses.receive(villager, offer.fee());\n", "",
         ["thehirefeegoesintothepurse"]),
        ("G7 no morning", MAIN + "market/Purses.java", "        if (!on()) return;\n        var s = of(villager);", "        if (true) return;\n        var s = of(villager);",
         ["themorningdepositpaysthepooronceamorningandthehouseholdeats"]),
        ("G10 any stall in a bought village", MAIN + "market/Shoppers.java", "boolean allowed = st.owner().map(o -> DeedCompat.allows(level, bed, o)).orElse(true);", "boolean allowed = true;",
         ["inaboughtvillageatownsmanshopsonlyattheownersstall"]),
        ("G16 a hopper puts anything in", MAIN + "market/ForSaleBlockEntity.java", "if (slot >= STOCK || s.isEmpty() || !sells(s)) return s;", "if (slot >= STOCK || s.isEmpty()) return s;",
         ["hoppersservethestallandonlyitsowneropensorbreaksit"]),
    ],
    "b": [
        ("G2 the close lost in the packet's copy", MAIN + "mixin/MerchantOfferMixin.java", "        serfdom$closed = ((ClosedOffer) other).serfdom$closed();\n", "",
         ["apurseofthreepaysforthreesalesandthefourthissoldout"]),
        ("G4 a villager saved before purses starts at the line", MAIN + "market/Purses.java", "Purse.start(true, rules())", "Purse.start(false, rules())",
         ["avillagermadenewstartsatfourandonefoundintheworldfull"]),
        ("G9 goods never put away", MAIN + "behavior/GoShopping.java", "                Baskets.putAway(level, v);\n", "",
         ["atownsmanbuysthebreadheneedsandcarriesithome"]),
        ("G13 a meal never buys", MAIN + "behavior/HaveMeal.java", "var c = Menu.choose(facts.buying(mayBuy(level, worker)));", "var c = Menu.choose(facts);",
         ["ahungryworkerbuysitsdinnerandacaptivecannot"]),
        ("G17 a stranger breaks a stall", MAIN + "market/Stalls.java", "                e.setCanceled(true);\n", "",
         ["hoppersservethestallandonlyitsowneropensorbreaksit"]),
    ],
    "c": [
        ("G3 trades never move the purse", MAIN + "market/Purses.java", "        if (till.open(s.purse())) set(villager, s.with(till.after(s.purse(), rules())));\n", "",
         ["apurseofthreepaysforthreesalesandthefourthissoldout", "aplayerspurchasefillsthepurseandopensatradeandthecapholds"]),
        ("G5 a structure's villager is new", MAIN + "market/Purses.java", "boolean found = event.getSpawnType() == MobSpawnType.STRUCTURE;", "boolean found = false;",
         ["avillagermadenewstartsatfourandonefoundintheworldfull"]),
        ("G8 nobody goes shopping", MAIN + "behavior/ShopTime.java", "if (Baskets.of(villager).isPresent() || Shoppers.plan(level, villager)) brain.setActiveActivityIfPossible(Serfdom.SHOP.get());",
         "if (false) brain.setActiveActivityIfPossible(Serfdom.SHOP.get());",
         ["atownsmanbuysthebreadheneedsandcarriesithome", "atownsmanshakeshisheadattoodearaprice", "twotownsmenatonestallareservedinturn"]),
        ("G14 a captive buys", MAIN + "behavior/HaveMeal.java", "if (!com.chunkworks.serfdom.market.Purses.on() || Workers.of(worker).captive()) return false;",
         "if (!com.chunkworks.serfdom.market.Purses.on()) return false;", ["ahungryworkerbuysitsdinnerandacaptivecannot"]),
        ("G18 an explosion takes a stall", MAIN + "market/Stalls.java",
         "        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) -> e.getAffectedBlocks().removeIf(p -> e.getLevel().getBlockState(p).is(Serfdom.FOR_SALE.get())));\n", "",
         ["hoppersservethestallandonlyitsowneropensorbreaksit"]),
    ],
    "d": [
        ("G11 any reach", MAIN + "market/Shoppers.java", "static int reach() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.SHOP_REACH.get() : 64; }",
         "static int reach() { return 128; }", ["astallbeyondreachofthebedisnevervisited"]),
        ("G12 no lock", MAIN + "market/ForSaleBlockEntity.java", "        if (customer != null && !customer.equals(who) && now - customerSince < SERVE_FOR) return false;\n", "",
         ["atownsmangoestothecheaperstallandastallservesoneatatime"]),
        ("G15 the post's needs never shopped for", MAIN + "market/Needs.java", "        out.addAll(forThePost(level, villager));\n", "",
         ["aworkerwithoutatoolbuysoneforitspost"]),
    ],
    # G16 again after the hoppers test was tightened: the hopper had not yet tried its dirt.
    "a2": [
        ("G16 a hopper puts anything in", MAIN + "market/ForSaleBlockEntity.java", "if (slot >= STOCK || s.isEmpty() || !sells(s)) return s;", "if (slot >= STOCK || s.isEmpty()) return s;",
         ["hoppersservethestallandonlyitsowneropensorbreaksit"]),
    ],
    # G11 again after the reach test checks the decision, not only that nobody arrived.
    "d2": [
        ("G11 any reach", MAIN + "market/Shoppers.java", "static int reach() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.SHOP_REACH.get() : 64; }",
         "static int reach() { return 128; }", ["astallbeyondreachofthebedisnevervisited"]),
    ],
    "e": [
        ("G19 the economy switch ignored", MAIN + "market/Purses.java", "public static boolean on() { return !SerfdomConfig.SPEC.isLoaded() || SerfdomConfig.ECONOMY.get(); }",
         "public static boolean on() { return true; }", ["withtheeconomyoffvillagerstradeasvanillas"]),
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
                if "<failure" in body or "<error" in body:
                    failed.add(re.search(r'testsuite name="[^"]*\.([A-Za-z]+)"', body).group(1))
            if out.returncode != 0 and not failed:
                print(out.stdout[-3000:], out.stderr[-3000:])
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
