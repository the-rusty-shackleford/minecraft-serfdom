/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>what an offer shows: a villager buying, selling; a second cost; emeralds both ways; nothing
 * counted; an item for itself;</li>
 * <li>the median: none, one, odd, even, unsorted;</li>
 * <li>the order: an item's own entry over a tag's, the first tag over a later one, data over the
 * price lists, the price lists over food, food at bread's rate; data of 0 refusing an item a price
 * list or food would value; food without bread's value; an item in none;</li>
 * <li>a blank tag refused.</li>
 * </ul> */
final class ValuesTest {
    private static final String E = Till.EMERALD;

    @Test void anOfferShowsAPriceWhenItIsEmeraldsForOneKindOfItem() {
        assertEquals(new Values.Seen("minecraft:wheat", 0.05), Values.seen("minecraft:wheat", 20, "", 0, E, 1).orElseThrow());
        assertEquals(new Values.Seen("minecraft:bread", 1 / 6.0), Values.seen(E, 1, "", 0, "minecraft:bread", 6).orElseThrow());
        assertTrue(Values.seen("minecraft:book", 1, E, 5, "minecraft:enchanted_book", 1).isEmpty(), "a second cost");
        assertTrue(Values.seen(E, 1, "", 0, E, 2).isEmpty(), "emeralds both ways");
        assertTrue(Values.seen("minecraft:gravel", 10, "", 0, "minecraft:flint", 10).isEmpty(), "no emeralds");
        assertTrue(Values.seen("minecraft:wheat", 0, "", 0, E, 1).isEmpty(), "nothing counted");
        assertTrue(Values.seen(E, 1, null, 0, "minecraft:bread", 6).isPresent(), "no second cost at all");
    }

    @Test void theMedianOfThePricesSeen() {
        assertEquals(OptionalDouble.empty(), Values.median(List.of()));
        assertEquals(OptionalDouble.of(3), Values.median(List.of(3.0)));
        assertEquals(OptionalDouble.of(2), Values.median(List.of(9.0, 1.0, 2.0)));
        assertEquals(OptionalDouble.of(2.5), Values.median(List.of(4.0, 1.0, 2.0, 3.0)));
    }

    @Test void dataComesFirstItsItemsBeforeItsTagsThenThePriceListsThenFood() {
        var facts = new Values.Facts(
                Map.of("minecraft:diamond", 5.0, "minecraft:bread", 0.2),
                List.of(new Values.Tagged("minecraft:gems", Set.of("minecraft:diamond", "minecraft:emerald_ore", "minecraft:amethyst_shard"), 3.0),
                        new Values.Tagged("c:shiny", Set.of("minecraft:amethyst_shard", "minecraft:gold_ingot"), 9.0)),
                Map.of("minecraft:amethyst_shard", List.of(1.0), "minecraft:wheat", List.of(0.05, 0.04, 0.06), "minecraft:bread", List.of(1 / 6.0),
                        "minecraft:cooked_beef", List.of(0.25)),
                Map.of("minecraft:bread", 5, "minecraft:cooked_beef", 8, "minecraft:carrot", 3, "minecraft:cake", 0));
        var t = Values.table(facts);
        assertEquals(5.0, t.get("minecraft:diamond"), "its own entry over a tag's");
        assertEquals(3.0, t.get("minecraft:emerald_ore"), "a tag");
        assertEquals(3.0, t.get("minecraft:amethyst_shard"), "the first tag over a later one, and over a price list");
        assertEquals(9.0, t.get("minecraft:gold_ingot"));
        assertEquals(0.05, t.get("minecraft:wheat"), 1e-12, "the median price");
        assertEquals(0.2, t.get("minecraft:bread"), "data over the price list");
        assertEquals(0.25, t.get("minecraft:cooked_beef"), "a price over food's rate");
        assertEquals(0.12, t.get("minecraft:carrot"), 1e-12, "food at bread's 0.04 a point");
        assertFalse(t.containsKey("minecraft:cake"), "fills nothing");
        assertFalse(t.containsKey("minecraft:dirt"), "in nothing: no value");
    }

    @Test void dataOfNothingRefusesAnItemAndFoodNeedsBreadsValue() {
        var t = Values.table(new Values.Facts(Map.of("minecraft:rotten_flesh", 0.0),
                List.of(new Values.Tagged("c:junk", Set.of("minecraft:poisonous_potato"), -1.0)),
                Map.of("minecraft:rotten_flesh", List.of(0.1), "minecraft:bread", List.of(0.2)),
                Map.of("minecraft:bread", 5, "minecraft:rotten_flesh", 4, "minecraft:poisonous_potato", 2, "minecraft:apple", 4)));
        assertFalse(t.containsKey("minecraft:rotten_flesh"), "refused though a price list values it");
        assertFalse(t.containsKey("minecraft:poisonous_potato"), "refused though it fills");
        assertEquals(0.16, t.get("minecraft:apple"), 1e-12);
        var noBread = Values.table(new Values.Facts(Map.of(), List.of(), Map.of(), Map.of("minecraft:apple", 4)));
        assertTrue(noBread.isEmpty(), "food is valued only at bread's rate");
        assertThrows(IllegalArgumentException.class, () -> new Values.Tagged(" ", Set.of(), 1));
    }
}
