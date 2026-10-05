/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Stock, Fuel and Repair (D-0002).
 *
 * <p>Stock: keep at 0, 1, 999, 1000; nine rows and a tenth; a repeated item; setting, adding,
 * removing; short with nothing, some stored, some coming, both, more than kept; what a row may
 * spend with another row keeping more, less and none of an item; spare.
 *
 * <p>Fuel: nothing to cook; a coal for one, eight, nine items in a furnace; a blast furnace's halved
 * burn and cook; burning left and fuel in the slot, covering all or part; the slot's room; charcoal
 * with fuel (fewer logs than a load, more) and without (enough logs for the bootstrap, one short).
 *
 * <p>Repair: lost nothing, a quarter less one, a quarter, three quarters and a bit, all; a quarter
 * of nothing; the damage left; the first by share lost, a tie, none with material, none worth it. */
final class StockTest {
    private static final String BREAD = "minecraft:bread", WHEAT = "minecraft:wheat", CAKE = "minecraft:cake";

    @Test void keepIsOneToNineHundredNinetyNine() {
        assertThrows(IllegalArgumentException.class, () -> new Stock.Row(BREAD, 0));
        assertEquals(1, new Stock.Row(BREAD, 1).keep());
        assertEquals(999, new Stock.Row(BREAD, 999).keep());
        assertThrows(IllegalArgumentException.class, () -> new Stock.Row(BREAD, 1000));
        assertThrows(IllegalArgumentException.class, () -> new Stock.Row(" ", 1));
    }
    @Test void nineRowsOfDistinctItems() {
        var rows = new java.util.ArrayList<Stock.Row>();
        for (int i = 0; i < 9; i++) rows.add(new Stock.Row("t:" + i, 1));
        var full = new Stock(rows);
        assertEquals(full, full.with(9, new Stock.Row("t:9", 1)), "a tenth row is not added");
        rows.add(new Stock.Row("t:9", 1));
        assertThrows(IllegalArgumentException.class, () -> new Stock(rows));
        assertThrows(IllegalArgumentException.class, () -> new Stock(List.of(new Stock.Row(BREAD, 1), new Stock.Row(BREAD, 2))));
    }
    @Test void rowsAreSetAddedAndRemoved() {
        var s = Stock.EMPTY.with(0, new Stock.Row(BREAD, 8)).with(1, new Stock.Row(CAKE, 1));
        assertEquals(List.of(new Stock.Row(BREAD, 8), new Stock.Row(CAKE, 1)), s.rows());
        assertEquals(List.of(new Stock.Row(BREAD, 16), new Stock.Row(CAKE, 1)), s.with(0, new Stock.Row(BREAD, 16)).rows());
        assertEquals(s, s.with(1, new Stock.Row(BREAD, 2)), "a second row of bread is refused");
        assertEquals(List.of(new Stock.Row(CAKE, 1)), s.without(0).rows());
        assertThrows(IndexOutOfBoundsException.class, () -> s.with(3, new Stock.Row(WHEAT, 1)));
    }
    @Test void aRowIsShortByItsKeepLessStoredAndComing() {
        var row = new Stock.Row(BREAD, 8);
        assertEquals(8, Stock.shortBy(row, Map.of(), Map.of()));
        assertEquals(5, Stock.shortBy(row, Map.of(BREAD, 3), Map.of()));
        assertEquals(6, Stock.shortBy(row, Map.of(), Map.of(BREAD, 2)));
        assertEquals(1, Stock.shortBy(row, Map.of(BREAD, 3), Map.of(BREAD, 4)));
        assertEquals(0, Stock.shortBy(row, Map.of(BREAD, 30), Map.of()), "never below 0");
    }
    @Test void aRowNeverSpendsAnotherRowsKeep() {
        var s = new Stock(List.of(new Stock.Row(BREAD, 4), new Stock.Row(WHEAT, 10)));
        var counts = Map.of(WHEAT, 16, BREAD, 2, "minecraft:egg", 1);
        assertEquals(Map.of(WHEAT, 6, BREAD, 2, "minecraft:egg", 1), s.spendable(0, counts), "bread may take the six wheat over the ten kept");
        assertEquals(Map.of(WHEAT, 16, BREAD, 0, "minecraft:egg", 1), s.spendable(1, counts), "wheat's own row spends wheat; bread is kept");
        assertEquals(Map.of(WHEAT, 0, BREAD, 2), s.spendable(0, Map.of(WHEAT, 4, BREAD, 2)), "less than kept: nothing");
        assertEquals(Map.of(WHEAT, 6, BREAD, 0, "minecraft:egg", 1), s.spare(counts));
    }

    // ---- Fuel ------------------------------------------------------------------------------------

    @Test void fuelCoversTheLoadAndNoMore() {
        assertEquals(0, Fuel.units(0, 200, 1600, 0, 0, 64));
        assertEquals(1, Fuel.units(1, 200, 1600, 0, 0, 64));
        assertEquals(1, Fuel.units(8, 200, 1600, 0, 0, 64), "a coal does eight");
        assertEquals(2, Fuel.units(9, 200, 1600, 0, 0, 64));
        assertEquals(1, Fuel.units(8, 100, 800, 0, 0, 64), "a blast furnace burns a coal in half the time and cooks in half: still eight");
        assertEquals(0, Fuel.units(3, 200, 1600, 600, 0, 64), "what is burning covers three");
        assertEquals(1, Fuel.units(4, 200, 1600, 600, 0, 64), "but not four");
        assertEquals(0, Fuel.units(8, 200, 1600, 0, 1, 64), "a coal waiting in the slot");
        assertEquals(1, Fuel.units(16, 200, 1600, 0, 1, 64));
        assertEquals(3, Fuel.units(64, 200, 1600, 0, 0, 3), "never more than the slot takes");
        assertEquals(2, Fuel.units(3, 200, 300, 0, 0, 64), "two logs burn three items");
        assertThrows(IllegalArgumentException.class, () -> Fuel.units(1, 0, 1600, 0, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> Fuel.units(-1, 200, 1600, 0, 0, 64));
    }
    @Test void charcoalIsAFullLoadWithFuelAndABootstrapWithout() {
        assertEquals(5, Fuel.charcoalLoad(5, 1, 2));
        assertEquals(Fuel.CHARCOAL_LOAD, Fuel.charcoalLoad(30, 1, 2));
        assertEquals(Fuel.BOOTSTRAP, Fuel.charcoalLoad(5, 0, 2), "three in, two under");
        assertEquals(0, Fuel.charcoalLoad(4, 0, 2), "four logs cannot do both");
        assertEquals(0, Fuel.charcoalLoad(0, 3, 2));
    }

    // ---- Repair ----------------------------------------------------------------------------------

    @Test void aRepairSpendsOnlyWholeQuarters() {
        assertEquals(0, Repair.units(250, 0));
        assertEquals(0, Repair.units(250, 61), "a quarter of 250 is 62");
        assertEquals(1, Repair.units(250, 62));
        assertEquals(3, Repair.units(250, 200));
        assertEquals(4, Repair.units(250, 250));
        assertEquals(0, Repair.units(3, 3), "a quarter of nothing mends nothing");
        assertEquals(14, Repair.after(250, 200, 3));
        assertEquals(2, Repair.after(250, 250, 4), "four quarters of 62 mend 248: a fifth ingot for the last two would be wasted");
        assertThrows(IllegalArgumentException.class, () -> Repair.units(250, 251));
    }
    @Test void theMostWornWithMaterialGoesFirst() {
        var axe = new Repair.Worn("minecraft:iron_axe", 250, 150, 5);
        var sword = new Repair.Worn("minecraft:iron_sword", 250, 200, 5);
        var dry = new Repair.Worn("minecraft:diamond_pickaxe", 1561, 1500, 0);
        var fresh = new Repair.Worn("minecraft:iron_hoe", 250, 10, 5);
        assertEquals(java.util.Optional.of(1), Repair.first(List.of(axe, sword, dry, fresh)), "the sword has lost the most of what is mendable");
        assertEquals(java.util.Optional.of(0), Repair.first(List.of(axe, new Repair.Worn("minecraft:iron_axe", 250, 150, 5))), "a tie: the earlier");
        assertEquals(java.util.Optional.empty(), Repair.first(List.of(dry, fresh)), "no material, or not worth it");
        assertEquals(2, new Repair.Worn("minecraft:iron_axe", 250, 200, 2).spend(), "three quarters lost, two ingots stored");
    }
}
