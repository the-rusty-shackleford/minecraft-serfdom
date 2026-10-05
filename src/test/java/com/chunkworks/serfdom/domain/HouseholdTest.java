/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>a household: empty; counts of 0 or below, and wear outside [0, 1), refused; a need with a
 * blank name, a negative keep or use refused;</li>
 * <li>what it has of a need: none, one item, several items that meet it; short of what it keeps, at
 * it, over it;</li>
 * <li>adding: a new item, more of one; nothing; a negative refused;</li>
 * <li>a morning's use: a whole one a day, two, half (over two mornings), more than it holds; the
 * most plentiful first, a tie by id; a need with nothing (no wear owed); a use of 0;</li>
 * <li>what it wants: food first whatever the list's order; nothing short; a need no item meets.</li>
 * </ul> */
final class HouseholdTest {
    private static final Household.Need FOOD = new Household.Need(Household.FOOD, Set.of("bread", "carrot", "potato"), 4, 2);
    private static final Household.Need HOE = new Household.Need("hoe", Set.of("wooden_hoe", "iron_hoe"), 1, 0.125);
    private static final Household.Need BONE_MEAL = new Household.Need("bone_meal", Set.of("bone_meal"), 8, 0.5);

    @Test void aHouseholdHoldsCountsAndItsNeedsAreSane() {
        assertTrue(Household.EMPTY.goods().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Household(Map.of("bread", 0), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Household(Map.of(), Map.of("food", 1.0)));
        assertThrows(IllegalArgumentException.class, () -> new Household(Map.of(), Map.of("food", -0.1)));
        assertThrows(IllegalArgumentException.class, () -> new Household.Need(" ", Set.of(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Household.Need("x", Set.of(), -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Household.Need("x", Set.of(), 1, -1));
        assertTrue(FOOD.food());
        assertFalse(HOE.food());
    }

    @Test void itHasWhatEveryItemThatMeetsANeedAddsUpTo() {
        var h = Household.EMPTY.add("bread", 2).add("carrot", 1).add("iron_hoe", 1).add("bread", 1);
        assertEquals(4, h.has(FOOD));
        assertEquals(0, h.shortOf(FOOD), "at what it keeps");
        assertEquals(0, h.shortOf(HOE));
        assertEquals(8, h.shortOf(BONE_MEAL), "none");
        assertEquals(3, h.goods().get("bread"));
        assertEquals(0, Household.EMPTY.add("bread", 9).shortOf(FOOD), "over");
        assertSame(h, h.add("bread", 0));
        assertThrows(IllegalArgumentException.class, () -> h.add("bread", -1));
    }

    @Test void eachMorningUsesUpWholeItemsTheMostPlentifulFirst() {
        var h = Household.EMPTY.add("bread", 3).add("carrot", 3).add("potato", 1);
        var after = h.morning(List.of(FOOD));
        assertEquals(5, after.has(FOOD), "two a day");
        assertEquals(Map.of("bread", 2, "carrot", 2, "potato", 1), after.goods(), "the most plentiful first, a tie to the first by id: bread, then carrot");
        assertFalse(after.wear().containsKey(Household.FOOD), "nothing left over");
    }

    @Test void aFractionalUseAddsUpOverMornings() {
        var h = Household.EMPTY.add("bone_meal", 3);
        var one = h.morning(List.of(BONE_MEAL));
        assertEquals(3, one.has(BONE_MEAL));
        assertEquals(0.5, one.wear().get("bone_meal"), 1e-12);
        var two = one.morning(List.of(BONE_MEAL));
        assertEquals(2, two.has(BONE_MEAL));
        assertFalse(two.wear().containsKey("bone_meal"));
        var hoe = Household.EMPTY.add("iron_hoe", 1);
        for (int day = 0; day < 7; day++) hoe = hoe.morning(List.of(HOE));
        assertEquals(1, hoe.has(HOE), "a hoe lasts eight mornings");
        assertEquals(0, hoe.morning(List.of(HOE)).has(HOE));
    }

    @Test void aNeedWithNothingOwesNothing() {
        var h = Household.EMPTY.add("bread", 1).morning(List.of(FOOD));
        assertEquals(0, h.has(FOOD), "two a day with one: it uses the one");
        assertFalse(h.wear().containsKey(Household.FOOD), "and owes nothing for the other");
        var bought = h.add("bread", 4).morning(List.of(FOOD));
        assertEquals(2, bought.has(FOOD), "four bought the next day: two used, not three");
        var none = new Household.Need("idle", Set.of("stick"), 1, 0);
        assertEquals(Household.EMPTY.add("stick", 1), Household.EMPTY.add("stick", 1).morning(List.of(none)), "a use of 0");
    }

    @Test void itWantsWhatItIsShortOfFoodFirst() {
        var h = Household.EMPTY.add("bread", 1).add("iron_hoe", 1);
        var wants = h.wants(List.of(BONE_MEAL, HOE, FOOD));
        assertEquals(2, wants.size());
        assertEquals(new Shopping.Want(Household.FOOD, FOOD.items(), 3, Shopping.Unit.ITEMS, Shopping.Dest.HOUSEHOLD), wants.get(0), "food first");
        assertEquals("bone_meal", wants.get(1).name());
        assertEquals(8, wants.get(1).count());
        assertTrue(Household.EMPTY.add("bread", 4).wants(List.of(FOOD)).isEmpty(), "nothing short");
        assertTrue(Household.EMPTY.wants(List.of(new Household.Need("ghost", Set.of(), 2, 1))).isEmpty(), "no item meets it");
    }
}
