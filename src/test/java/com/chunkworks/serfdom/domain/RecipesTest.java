/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Recipes.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Warehouse Manager's partitions for its planner (its ExpansionTest), carried over with a station
 * on every rule, and Serfdom's own (D-0002).
 *
 * <p>plan: covered with nothing to make; zero crafts; one level, refused at depth 0; a made item's
 * surplus serving a later cell, in either cell order; several crafts scaling the need with times
 * rounded up; two levels (the rifle) refused at depth 1, covered at depth 2 with the shared iron
 * counted once and the steps leaves first; an alternative only makeable later in its list; a failed
 * rule backed out cleanly; yield-ascending order; the ingot-nugget-block cycle; the chain of what is
 * being made (Rusty's report of 2026-09-27); a rule spending its own result; a shared material never
 * counted twice; one kind per cell; shortages merged per option list; bad input; immutability.
 *
 * <p>making: nothing needed; no rule at all; depth 0; one rule covered and short; the first covered
 * rule chosen over a short one, the first rule's shortages named when none covers; a pickaxe from
 * raw iron through a furnace, the sticks from planks, leaves first; a netherite sword four
 * stations deep; the station order within one yield (blast furnace, smoker, furnace, campfire);
 * the item never spent below itself. */
final class RecipesTest {
    private static final String LOG = "minecraft:oak_log", OAK = "minecraft:oak_planks", BIRCH = "minecraft:birch_planks", STICK = "minecraft:stick";
    private static final String COAL = "minecraft:coal", CHARCOAL = "minecraft:charcoal", IRON = "minecraft:iron_ingot", REDSTONE = "minecraft:redstone";
    private static final String STEEL = "mm:steel_ingot", NUGGET = "mm:steel_nugget", BLOCK = "mm:steel_block";
    private static final String UPPER = "rwm:upper_receiver", LOWER = "rwm:lower_receiver", BARREL = "rwm:barrel", STOCK = "rwm:stock";
    private static final List<String> PLANKS = List.of(OAK, BIRCH), COALS = List.of(COAL, CHARCOAL);
    static Rule table(String id, List<List<String>> cells, String result, int yield) { return new Rule(id, Station.TABLE, cells, result, yield); }
    private static final Rule R_PLANKS = table(OAK, List.of(List.of(LOG)), OAK, 4);
    private static final Rule R_STICK = table(STICK, List.of(PLANKS, PLANKS), STICK, 4);
    private static final Rule R_STEEL = table(STEEL, List.of(List.of(IRON), List.of(IRON), List.of(IRON), COALS), STEEL, 3);
    private static final Rule R_STEEL_BLOCK = table("mm:steel_ingot_from_steel_block", List.of(List.of(BLOCK)), STEEL, 9);
    private static final Rule R_STEEL_NUGGETS = table("mm:steel_ingot_from_nuggets", nine(NUGGET), STEEL, 1);
    private static final Rule R_NUGGET = table(NUGGET, List.of(List.of(STEEL)), NUGGET, 9);
    private static final Rule R_BLOCK = table(BLOCK, nine(STEEL), BLOCK, 1);
    private static final Rule R_UPPER = new Rule(UPPER, Station.WORKBENCH, List.of(List.of(STEEL), List.of(STEEL), List.of(STEEL), List.of(STEEL)), UPPER, 1);
    private static final Rule R_LOWER = new Rule(LOWER, Station.WORKBENCH, List.of(List.of(STEEL), List.of(STEEL), List.of(STEEL), List.of(REDSTONE)), LOWER, 1);
    private static final Rule R_BARREL = new Rule(BARREL, Station.WORKBENCH, List.of(List.of(IRON), List.of(IRON), List.of(IRON)), BARREL, 1);
    private static final Rule R_STOCK = new Rule(STOCK, Station.WORKBENCH, List.of(PLANKS, PLANKS, PLANKS, List.of(STICK)), STOCK, 1);
    private static final List<List<String>> TORCH = List.of(COALS, List.of(STICK));
    private static final List<List<String>> RIFLE = List.of(List.of(UPPER), List.of(STOCK), List.of(LOWER), List.of(BARREL));
    private static final Rules WOOD = Rules.of(List.of(R_PLANKS, R_STICK));
    private static final Rules GUNS = Rules.of(List.of(R_STEEL, R_UPPER, R_LOWER, R_BARREL, R_STOCK));
    private static final Rules METALS = Rules.of(List.of(R_STEEL_NUGGETS, R_STEEL, R_STEEL_BLOCK, R_NUGGET, R_BLOCK));
    private static List<List<String>> nine(String item) { return java.util.Collections.nCopies(9, List.of(item)); }
    private static final String MADE = "test:made";
    private static Plan plan(List<List<String>> cells, int crafts, Map<String, Integer> available, Rules rules, int depth) { return Recipes.plan(MADE, cells, crafts, available, rules, depth); }

    // ---- plan: Warehouse Manager's partitions ----------------------------------------------------

    @Test void coveredWithNothingToMakeHasNoSteps() {
        assertEquals(new Plan(List.of(), List.of(COAL, STICK), List.of()), plan(TORCH, 1, Map.of(COAL, 1, STICK, 1), WOOD, 8));
        assertEquals(List.of(CHARCOAL, STICK), plan(TORCH, 1, Map.of(CHARCOAL, 2, STICK, 1), WOOD, 8).picks(), "the first option on hand is picked");
        assertEquals(new Plan(List.of(), List.of(COAL, STICK), List.of()), plan(TORCH, 0, Map.of(), WOOD, 8), "zero crafts need nothing");
    }
    @Test void aMissingIngredientIsMadeFromWhatIsOnHand() {
        var p = plan(TORCH, 1, Map.of(COAL, 1, OAK, 2), WOOD, 8);
        assertEquals(List.of(new Step(R_STICK, 1, List.of(OAK, OAK))), p.steps());
        assertTrue(p.covered());
        var flat = plan(TORCH, 1, Map.of(COAL, 1, OAK, 2), WOOD, 0);
        assertEquals(List.of(new Shortage(List.of(STICK), 1)), flat.shortages(), "at depth 0 nothing is made");
    }
    @Test void aMadeItemsSurplusServesALaterCell() {
        var steps = List.of(new Step(R_PLANKS, 1, List.of(LOG)), new Step(R_STICK, 1, List.of(OAK, OAK)));
        assertEquals(steps, plan(List.of(List.of(OAK), List.of(STICK)), 1, Map.of(LOG, 1), WOOD, 8).steps());
        assertEquals(steps, plan(List.of(List.of(STICK), List.of(OAK)), 1, Map.of(LOG, 1), WOOD, 8).steps(), "the planks made for the sticks cover the plank cell too");
    }
    @Test void severalCraftsScaleTheNeedAndRoundTimesUp() {
        assertEquals(List.of(new Step(R_PLANKS, 1, List.of(LOG)), new Step(R_STICK, 2, List.of(OAK, OAK))), plan(TORCH, 5, Map.of(COAL, 5, LOG, 2), WOOD, 8).steps());
        assertEquals(List.of(new Shortage(COALS, 1)), plan(TORCH, 5, Map.of(COAL, 4, LOG, 2), WOOD, 8).shortages(), "short by what the best option cannot cover");
    }
    @Test void theRifleNeedsTwoLevelsAndSharesItsIron() {
        var stock = Map.of(IRON, 12, COAL, 3, REDSTONE, 1, OAK, 3, STICK, 1);
        assertEquals(List.of(new Shortage(List.of(UPPER), 1), new Shortage(List.of(LOWER), 1)), plan(RIFLE, 1, stock, GUNS, 1).shortages());
        var p = plan(RIFLE, 1, stock, GUNS, 2);
        assertTrue(p.covered(), p.shortages().toString());
        assertEquals(List.of(new Step(R_STEEL, 2, List.of(IRON, IRON, IRON, COAL)), new Step(R_UPPER, 1, List.of(STEEL, STEEL, STEEL, STEEL)),
                new Step(R_STOCK, 1, List.of(OAK, OAK, OAK, STICK)), new Step(R_STEEL, 1, List.of(IRON, IRON, IRON, COAL)),
                new Step(R_LOWER, 1, List.of(STEEL, STEEL, STEEL, REDSTONE)), new Step(R_BARREL, 1, List.of(IRON, IRON, IRON))), p.steps());
        assertEquals(List.of(new Shortage(List.of(BARREL), 1)), plan(RIFLE, 1, Map.of(IRON, 11, COAL, 3, REDSTONE, 1, OAK, 3, STICK, 1), GUNS, 8).shortages());
    }
    @Test void aLaterAlternativeIsMadeWhenTheEarlierCannotBe() {
        var p = plan(List.of(List.of(BIRCH, OAK)), 1, Map.of(LOG, 1), WOOD, 8);
        assertEquals(List.of(OAK), p.picks());
        assertEquals(List.of(new Step(R_PLANKS, 1, List.of(LOG))), p.steps());
    }
    @Test void aRuleThatFailsIsBackedOutBeforeTheNextIsTried() {
        var a = table("t:a", List.of(List.of("t:y"), List.of("t:z")), "t:x", 1);
        var b = table("t:b", List.of(List.of("t:y")), "t:x", 1);
        var p = plan(List.of(List.of("t:x")), 1, Map.of("t:y", 1), Rules.of(List.of(a, b)), 8);
        assertEquals(List.of(new Step(b, 1, List.of("t:y"))), p.steps(), "a took the y and failed on z; b finds the y again");
    }
    @Test void rulesAreTriedInYieldOrderSoTheBlockIsNotBrokenForThree() {
        var cells = List.of(List.of(STEEL), List.of(STEEL), List.of(STEEL));
        assertEquals(List.of(new Step(R_STEEL, 1, List.of(IRON, IRON, IRON, COAL))), plan(cells, 1, Map.of(IRON, 3, COAL, 1, BLOCK, 1), METALS, 8).steps());
        assertEquals(List.of(new Step(R_STEEL_BLOCK, 1, List.of(BLOCK))), plan(cells, 1, Map.of(BLOCK, 1), METALS, 8).steps());
        assertEquals(List.of(R_STEEL_NUGGETS, R_STEEL, R_STEEL_BLOCK), METALS.making(STEEL), "yield ascending");
        assertEquals(List.of(R_STEEL), METALS.only(r -> r.id().equals(STEEL)).making(STEEL));
        assertEquals(List.of(), METALS.making("nothing:makes_this"));
    }
    @Test void theIngotNuggetBlockCycleTerminates() {
        var cell = List.of(List.of(STEEL));
        assertEquals(List.of(new Shortage(List.of(STEEL), 1)), plan(cell, 1, Map.of(), METALS, 8).shortages());
        assertEquals(List.of(new Step(R_STEEL_BLOCK, 1, List.of(BLOCK))), plan(cell, 1, Map.of(BLOCK, 1), METALS, 8).steps());
        assertEquals(List.of(new Shortage(List.of(NUGGET), 1)), plan(List.of(List.of(NUGGET)), 1, Map.of(), METALS, 8).shortages(), "nor a nugget from an ingot from nuggets");
    }
    @Test void anIngotIsNeverMadeFromNuggetsBrokenFromIngots() {
        var p = Recipes.plan(STEEL, nine(NUGGET), 1, Map.of(STEEL, 5, BLOCK, 1), METALS, 8);
        assertEquals(List.of(), p.steps(), "no ingot is broken, nor a block, to make the ingot");
        assertEquals(List.of(new Shortage(List.of(NUGGET), 9)), p.shortages());
        assertTrue(Recipes.plan(STEEL, nine(NUGGET), 1, Map.of(NUGGET, 9), METALS, 8).covered());
    }
    @Test void nothingBeingMadeIsSpentOnItsOwnIngredientsHoweverDeep() {
        var r = "t:r"; var a = "t:a"; var b = "t:b";
        var rules = Rules.of(List.of(table("t:make_a", List.of(List.of(b)), a, 1), table("t:make_b", List.of(List.of(r)), b, 1)));
        assertEquals(List.of(new Shortage(List.of(a), 1)), Recipes.plan(r, List.of(List.of(a)), 1, Map.of(r, 1), rules, 8).shortages());
        assertTrue(Recipes.plan(MADE, List.of(List.of(a)), 1, Map.of(r, 1), rules, 8).covered(), "for anything else, r into b into a is a fine plan");
    }
    @Test void aRuleMaySpendItsOwnResultFromWhatIsOnHand() {
        var x = "t:x"; var y = "t:y"; var z = "t:z";
        var grow = table("t:grow", List.of(List.of(x), List.of(y)), x, 2);
        var rules = Rules.of(List.of(grow));
        assertEquals(new Plan(List.of(), List.of(x, y), List.of()), Recipes.plan(x, grow.cells(), 1, Map.of(x, 1, y, 1), rules, 8));
        var p = Recipes.plan(z, List.of(List.of(x)), 2, Map.of(x, 1, y, 1), rules, 8);
        assertEquals(List.of(new Step(grow, 1, List.of(x, y))), p.steps());
    }
    @Test void aSharedMaterialIsNeverCountedTwice() {
        assertEquals(List.of(new Shortage(List.of(STICK), 1)), plan(List.of(List.of(OAK), List.of(STICK)), 1, Map.of(OAK, 2), WOOD, 8).shortages());
        assertEquals(List.of(new Shortage(List.of(OAK), 1)), plan(List.of(List.of(STICK), List.of(OAK)), 1, Map.of(OAK, 2), WOOD, 8).shortages());
        assertTrue(plan(List.of(List.of(OAK), List.of(STICK)), 1, Map.of(OAK, 3), WOOD, 8).covered());
    }
    @Test void eachCellTakesOneKindForEveryCraft() {
        var cells = List.of(COALS, COALS);
        assertEquals(List.of(COAL, CHARCOAL), plan(cells, 1, Map.of(COAL, 1, CHARCOAL, 1), Rules.NONE, 8).picks());
        assertEquals(List.of(new Shortage(COALS, 2)), plan(cells, 2, Map.of(COAL, 1, CHARCOAL, 1), Rules.NONE, 8).shortages());
        assertTrue(plan(cells, 2, Map.of(COAL, 2, CHARCOAL, 2), Rules.NONE, 8).covered());
    }
    @Test void shortagesAreMergedPerOptionListInOrder() {
        assertEquals(List.of(new Shortage(List.of(UPPER), 1), new Shortage(List.of(STOCK), 1), new Shortage(List.of(LOWER), 1), new Shortage(List.of(BARREL), 1)),
                plan(RIFLE, 1, Map.of(), Rules.NONE, 8).shortages());
        assertEquals(List.of(new Shortage(List.of(STEEL), 2)), plan(R_LOWER.cells(), 1, Map.of(STEEL, 1, REDSTONE, 1), Rules.NONE, 8).shortages());
    }
    @Test void badInputIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> plan(TORCH, -1, Map.of(), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> plan(TORCH, 1, Map.of(), WOOD, -1));
        assertThrows(IllegalArgumentException.class, () -> plan(TORCH, 1, Map.of(COAL, -1), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(List.of()), 1, Map.of(), WOOD, 8));
        assertThrows(NullPointerException.class, () -> Recipes.plan(null, TORCH, 1, Map.of(), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> Recipes.making(STICK, -1, Map.of(), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> Recipes.making(STICK, 1, Map.of(), WOOD, -1));
        assertThrows(IllegalArgumentException.class, () -> table(STICK, List.of(PLANKS), STICK, 0));
        assertThrows(IllegalArgumentException.class, () -> table(STICK, List.of(List.of()), STICK, 1));
        assertThrows(NullPointerException.class, () -> new Rule(STICK, null, List.of(PLANKS), STICK, 1));
        assertThrows(IllegalArgumentException.class, () -> new Step(R_STICK, 1, List.of(OAK, LOG)));
        assertThrows(IllegalArgumentException.class, () -> new Shortage(List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new Shortage(List.of(OAK), 0));
    }
    @Test void everythingIsImmutable() {
        var p = plan(TORCH, 1, Map.of(COAL, 1, OAK, 2), WOOD, 8);
        assertThrows(UnsupportedOperationException.class, () -> p.steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.steps().get(0).picks().clear());
        assertThrows(UnsupportedOperationException.class, () -> R_STICK.cells().get(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> WOOD.all().clear());
        var available = new java.util.HashMap<>(Map.of(COAL, 1, OAK, 2));
        plan(TORCH, 1, available, WOOD, 8);
        assertEquals(Map.of(COAL, 1, OAK, 2), available, "the counts handed in are not modified");
    }

    // ---- making: Serfdom's -----------------------------------------------------------------------

    private static final String RAW = "minecraft:raw_iron", PICK = "minecraft:iron_pickaxe";
    private static final String DEBRIS = "minecraft:ancient_debris", SCRAP = "minecraft:netherite_scrap", GOLD = "minecraft:gold_ingot";
    private static final String NETHERITE = "minecraft:netherite_ingot", TEMPLATE = "minecraft:netherite_upgrade_smithing_template";
    private static final String DIAMOND_SWORD = "minecraft:diamond_sword", NETHERITE_SWORD = "minecraft:netherite_sword";
    private static final String BEEF = "minecraft:beef", STEAK = "minecraft:cooked_beef";
    private static final Rule SMELT_RAW = new Rule("minecraft:iron_ingot_from_smelting_raw_iron", Station.FURNACE, List.of(List.of(RAW)), IRON, 1);
    private static final Rule BLAST_RAW = new Rule("minecraft:iron_ingot_from_blasting_raw_iron", Station.BLAST, List.of(List.of(RAW)), IRON, 1);
    private static final Rule R_PICK = table(PICK, List.of(List.of(IRON), List.of(IRON), List.of(IRON), List.of(STICK), List.of(STICK)), PICK, 1);
    private static final Rule SCRAP_BLAST = new Rule("minecraft:netherite_scrap_from_blasting", Station.BLAST, List.of(List.of(DEBRIS)), SCRAP, 1);
    private static final Rule R_NETHERITE = table(NETHERITE, List.of(List.of(SCRAP), List.of(SCRAP), List.of(SCRAP), List.of(SCRAP), List.of(GOLD), List.of(GOLD), List.of(GOLD), List.of(GOLD)), NETHERITE, 1);
    private static final Rule UPGRADE = new Rule("minecraft:netherite_sword_smithing", Station.SMITHING, List.of(List.of(TEMPLATE), List.of(DIAMOND_SWORD), List.of(NETHERITE)), NETHERITE_SWORD, 1);
    private static final Rules SMITHY = Rules.of(List.of(R_PLANKS, R_STICK, SMELT_RAW, BLAST_RAW, R_PICK, SCRAP_BLAST, R_NETHERITE, UPGRADE));

    @Test void nothingNeededIsCoveredWithNoSteps() {
        assertEquals(new Plan(List.of(), List.of(), List.of()), Recipes.making(PICK, 0, Map.of(), SMITHY, 8));
    }
    @Test void anItemNoRuleMakesIsShortOfItself() {
        assertEquals(List.of(new Shortage(List.of("t:nothing"), 3)), Recipes.making("t:nothing", 3, Map.of(), SMITHY, 8).shortages());
        assertEquals(List.of(new Shortage(List.of(PICK), 1)), Recipes.making(PICK, 1, Map.of(IRON, 3, STICK, 2), SMITHY, 0).shortages(), "at depth 0 nothing is made");
    }
    @Test void twoPickaxesComeFromRawIronInAFurnaceAndSticksFromPlanks() {
        var p = Recipes.making(PICK, 2, Map.of(RAW, 6, OAK, 2), SMITHY, Workshop.DEPTH);
        assertTrue(p.covered(), p.shortages().toString());
        assertEquals(List.of(new Step(BLAST_RAW, 6, List.of(RAW)), new Step(R_STICK, 1, List.of(OAK, OAK)), new Step(R_PICK, 2, List.of(IRON, IRON, IRON, STICK, STICK))), p.steps(),
                "the blast furnace before the furnace, the six ingots made in one step, the four sticks in one craft, the pickaxes last");
        assertEquals(List.of(new Shortage(List.of(IRON), 2)), Recipes.making(PICK, 2, Map.of(RAW, 5, OAK, 2), SMITHY, Workshop.DEPTH).shortages(),
                "five raw iron: the third ingot cell of two crafts is short, named as the ingot it takes");
    }
    @Test void ingotsOnHandAreSpentBeforeAnyAreSmelted() {
        var p = Recipes.making(PICK, 1, Map.of(IRON, 2, RAW, 4, STICK, 2), SMITHY, Workshop.DEPTH);
        assertEquals(List.of(new Step(BLAST_RAW, 1, List.of(RAW)), new Step(R_PICK, 1, List.of(IRON, IRON, IRON, STICK, STICK))), p.steps(), "one smelted for the third");
    }
    @Test void aNetheriteSwordIsFourStationsDeep() {
        var p = Recipes.making(NETHERITE_SWORD, 1, Map.of(DEBRIS, 4, GOLD, 4, DIAMOND_SWORD, 1, TEMPLATE, 1), SMITHY, Workshop.DEPTH);
        assertTrue(p.covered(), p.shortages().toString());
        assertEquals(List.of(new Step(SCRAP_BLAST, 4, List.of(DEBRIS)), new Step(R_NETHERITE, 1, List.of(SCRAP, SCRAP, SCRAP, SCRAP, GOLD, GOLD, GOLD, GOLD)),
                new Step(UPGRADE, 1, List.of(TEMPLATE, DIAMOND_SWORD, NETHERITE))), p.steps());
        assertEquals(List.of(new Shortage(List.of(TEMPLATE), 1)), Recipes.making(NETHERITE_SWORD, 1, Map.of(DEBRIS, 4, GOLD, 4, DIAMOND_SWORD, 1), SMITHY, Workshop.DEPTH).shortages());
    }
    @Test void theFirstCoveredRuleWinsAndTheFirstRulesShortagesAreNamed() {
        var cheap = table("t:cheap", List.of(List.of("t:a")), "t:x", 1);
        var dear = table("t:dear", List.of(List.of("t:b")), "t:x", 1);
        var rules = Rules.of(List.of(cheap, dear));
        assertEquals(List.of(new Step(dear, 2, List.of("t:b"))), Recipes.making("t:x", 2, Map.of("t:b", 2), rules, 8).steps(), "cheap is short, dear covers");
        assertEquals(List.of(new Shortage(List.of("t:a"), 2)), Recipes.making("t:x", 2, Map.of(), rules, 8).shortages(), "neither covers: the first rule's shortages");
    }
    @Test void withinOneYieldStationsGoInTheirOrder() {
        var smoke = new Rule("smoke", Station.SMOKER, List.of(List.of(BEEF)), STEAK, 1);
        var bake = new Rule("bake", Station.FURNACE, List.of(List.of(BEEF)), STEAK, 1);
        var fire = new Rule("fire", Station.CAMPFIRE, List.of(List.of(BEEF)), STEAK, 1);
        assertEquals(List.of(smoke, bake, fire), Rules.of(List.of(fire, bake, smoke)).making(STEAK), "smoker, furnace, campfire, whatever the ids");
        assertEquals(List.of(BLAST_RAW, SMELT_RAW), SMITHY.making(IRON));
    }
    @Test void theItemBeingMadeIsNeverSpentOrMadeBelowItself() {
        var nuggets = table("t:ingot_from_nuggets", nine(NUGGET), IRON, 1);
        var nugget = table("t:nugget", List.of(List.of(IRON)), NUGGET, 9);
        var rules = Rules.of(List.of(nuggets, nugget));
        assertFalse(Recipes.making(IRON, 1, Map.of(IRON, 5), rules, 8).covered(), "an ingot is not broken into nuggets to make an ingot");
    }
}
