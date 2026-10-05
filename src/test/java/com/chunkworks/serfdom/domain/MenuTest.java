/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Menu.Choice;
import com.chunkworks.serfdom.domain.Menu.Facts;
import com.chunkworks.serfdom.domain.Menu.Food;
import com.chunkworks.serfdom.domain.Menu.Kind;
import com.chunkworks.serfdom.domain.Recipes.Rule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>food: what fills and is not refused; refused by a harmful effect or the tag; nothing that
 * does not fill; raw (a heat recipe makes it better) or ready;</li>
 * <li>where: home holds ready food; only a post does (the first, a later one); home and posts both;
 * only raw food at home, with a free station that cooks it and with none; nothing;</li>
 * <li>which: the most plentiful; a tie, to what fills more; one the worker would overshoot on;</li>
 * <li>the dish: the one that fills most; a table meal and a table recipe that is not one; a pot's
 * meal; as many crafts as fill, capped at four and at what home holds; ingredients only from
 * home;</li>
 * <li>a choice's rep invariant.</li>
 * </ul> */
final class MenuTest {
    private static final Map<String, Food> FOODS = Map.ofEntries(
            Map.entry("bread", food(5)), Map.entry("cooked_beef", food(8)), Map.entry("beef", food(3)), Map.entry("potato", food(1)),
            Map.entry("baked_potato", food(5)), Map.entry("carrot", food(3)), Map.entry("pie", food(8)),
            Map.entry("golden_apple", new Food(4, true, Optional.empty())), Map.entry("rotten_flesh", new Food(4, true, Optional.empty())),
            Map.entry("stew", new Food(10, false, Optional.of("bowl"))), Map.entry("air_snack", food(0)));
    private static final Rule SMOKE_BEEF = rule("smoke_beef", Station.SMOKER, List.of(List.of("beef")), "cooked_beef");
    private static final Rule FURNACE_BEEF = rule("furnace_beef", Station.FURNACE, List.of(List.of("beef")), "cooked_beef");
    private static final Rule BAKE_POTATO = rule("bake_potato", Station.FURNACE, List.of(List.of("potato")), "baked_potato");
    private static final Rule BREAD = rule("bread", Station.TABLE, List.of(List.of("wheat"), List.of("wheat"), List.of("wheat")), "bread");
    private static final Rule PIE = rule("pie", Station.TABLE, List.of(List.of("pumpkin"), List.of("sugar")), "pie");
    private static final Rule STEW = rule("stew", Station.POT, List.of(List.of("beef"), List.of("potato"), List.of("bowl")), "stew");
    private static final Recipes.Rules HEAT = Recipes.Rules.of(List.of(SMOKE_BEEF, FURNACE_BEEF, BAKE_POTATO));

    private static Food food(int n) { return new Food(n, false, Optional.empty()); }
    private static Rule rule(String id, Station s, List<List<String>> cells, String result) { return new Rule(id, s, cells, result, 1); }
    private static Facts facts(double hunger, Map<String, Integer> home, List<Map<String, Integer>> posts, List<Rule> kitchen) {
        return new Facts(new Hunger(hunger), home, posts, FOODS, HEAT, Recipes.Rules.of(kitchen), Set.of("bread"));
    }
    private static Choice choose(double hunger, Map<String, Integer> home, List<Map<String, Integer>> posts, List<Rule> kitchen) {
        return Menu.choose(facts(hunger, home, posts, kitchen));
    }

    @Test void foodFillsIsNotRefusedAndIsRawOnlyWhenHeatMakesItBetter() {
        var f = facts(5, Map.of(), List.of(), List.of());
        assertTrue(Menu.edible("bread", FOODS));
        assertFalse(Menu.edible("golden_apple", FOODS), "refused");
        assertFalse(Menu.edible("air_snack", FOODS), "fills nothing");
        assertFalse(Menu.edible("wheat", FOODS), "not food");
        assertTrue(Menu.raw("beef", f));
        assertTrue(Menu.raw("potato", f));
        assertFalse(Menu.raw("carrot", f), "no heat recipe takes it");
        assertFalse(Menu.raw("cooked_beef", f));
        assertTrue(Menu.ready("cooked_beef", f));
        assertFalse(Menu.ready("beef", f));
    }

    @Test void readyFoodAtHomeComesFirstTheMostPlentiful() {
        assertEquals(Choice.eat(Kind.EAT_HOME, "bread", -1), choose(9, Map.of("bread", 3, "cooked_beef", 1), List.of(Map.of("bread", 10)), List.of(SMOKE_BEEF)));
        assertEquals("cooked_beef", choose(9, Map.of("bread", 2, "cooked_beef", 5), List.of(), List.of()).item());
        assertEquals("cooked_beef", choose(9, Map.of("bread", 2, "cooked_beef", 2), List.of(), List.of()).item(), "a tie goes to what fills more");
        assertEquals("bread", choose(9, Map.of("golden_apple", 10, "rotten_flesh", 10, "bread", 1), List.of(), List.of()).item(), "never what is refused");
        assertEquals(Choice.eat(Kind.EAT_HOME, "bread", -1), choose(9, Map.of("bread", 1, "beef", 30), List.of(), List.of(SMOKE_BEEF)), "ready before cooking");
    }

    @Test void thenTheNearestPostThatHoldsReadyFood() {
        assertEquals(Choice.eat(Kind.EAT_POST, "bread", 1), choose(9, Map.of(), List.of(Map.of("beef", 5), Map.of("bread", 1), Map.of("cooked_beef", 9)), List.of()));
        assertEquals(Choice.eat(Kind.EAT_POST, "cooked_beef", 0), choose(9, Map.of("beef", 3), List.of(Map.of("cooked_beef", 2)), List.of(SMOKE_BEEF)), "the canteen before cooking");
    }

    @Test void thenADishCookedFromHomeThatFillsMost() {
        var c = choose(4, Map.of("beef", 10), List.of(), List.of(SMOKE_BEEF));
        assertEquals(Kind.COOK, c.kind());
        assertEquals(SMOKE_BEEF, c.rule().orElseThrow());
        assertEquals(2, c.times(), "16 short, steaks of 8: two");
        assertEquals(1, choose(9, Map.of("beef", 10), List.of(), List.of(SMOKE_BEEF)).times(), "11 short: a second steak would overshoot by 5");
        assertEquals(List.of("beef"), c.picks());
        assertEquals(FURNACE_BEEF, choose(9, Map.of("beef", 2, "potato", 2), List.of(), List.of(FURNACE_BEEF, BAKE_POTATO)).rule().orElseThrow(), "steak fills more than a baked potato");
        assertEquals(STEW, choose(9, Map.of("beef", 1, "potato", 1, "bowl", 1), List.of(), List.of(SMOKE_BEEF, STEW)).rule().orElseThrow(), "a pot's stew fills most");
    }

    @Test void asManyCraftsAsFillCappedAtFourAndAtWhatHomeHolds() {
        assertEquals(2, choose(0, Map.of("beef", 2), List.of(), List.of(SMOKE_BEEF)).times(), "three would fill, home has two");
        assertEquals(4, choose(0, Map.of("potato", 10), List.of(), List.of(BAKE_POTATO)).times(), "four at most");
        assertEquals(1, choose(14, Map.of("potato", 10), List.of(), List.of(BAKE_POTATO)).times(), "6 short, potatoes of 5: a second would overshoot by 4");
    }

    @Test void atATableOnlyTableMeals() {
        assertEquals(BREAD, choose(9, Map.of("wheat", 3, "pumpkin", 1, "sugar", 1), List.of(), List.of(BREAD, PIE)).rule().orElseThrow(), "the pie fills more but is no table meal");
        var withPie = new Facts(new Hunger(9), Map.of("wheat", 3, "pumpkin", 1, "sugar", 1), List.of(), FOODS, HEAT, Recipes.Rules.of(List.of(BREAD, PIE)), Set.of("bread", "pie"));
        assertEquals(PIE, Menu.choose(withPie).rule().orElseThrow());
    }

    @Test void ingredientsComeOnlyFromHome() {
        assertEquals(Choice.NOTHING, choose(9, Map.of(), List.of(Map.of("beef", 10)), List.of(SMOKE_BEEF)));
    }

    @Test void rawFoodIsEatenRawOnlyWhenNoFreeStationCooksIt() {
        assertEquals(Choice.eat(Kind.EAT_RAW, "beef", -1), choose(9, Map.of("beef", 4), List.of(), List.of()));
        assertEquals(Kind.COOK, choose(9, Map.of("beef", 4), List.of(), List.of(SMOKE_BEEF)).kind());
        assertEquals(Choice.eat(Kind.EAT_RAW, "beef", -1), choose(9, Map.of("beef", 4), List.of(), List.of(BAKE_POTATO)), "only a free station's recipes that take beef count");
    }

    @Test void nothingTheWorkerWantsIsNothing() {
        assertEquals(Choice.NOTHING, choose(9, Map.of(), List.of(), List.of()));
        assertEquals(Choice.NOTHING, choose(17, Map.of("cooked_beef", 5), List.of(Map.of("cooked_beef", 5)), List.of()), "a steak would overshoot by 5");
        assertEquals(Choice.NOTHING, choose(20, Map.of("bread", 5), List.of(), List.of()), "full");
    }

    @Test void aChoiceSaysOnlyWhatItsKindNeeds() {
        assertThrows(IllegalArgumentException.class, () -> new Choice(Kind.EAT_HOME, "", -1, Optional.empty(), 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Choice(Kind.EAT_POST, "bread", -1, Optional.empty(), 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Choice(Kind.COOK, "", -1, Optional.of(SMOKE_BEEF), 0, List.of("beef")));
        assertThrows(IllegalArgumentException.class, () -> new Choice(Kind.COOK, "", -1, Optional.of(SMOKE_BEEF), 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Choice(Kind.NOTHING, "", -1, Optional.empty(), 2, List.of()));
    }
}
