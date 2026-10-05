/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Shopping.Dest;
import com.chunkworks.serfdom.domain.Shopping.Offer;
import com.chunkworks.serfdom.domain.Shopping.Unit;
import com.chunkworks.serfdom.domain.Shopping.Want;
import com.chunkworks.serfdom.domain.Shopping.Who;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>who: a free adult, a hired one; a child, a captive, one in chains, one on its way home, one
 * without a bed;</li>
 * <li>when: a free villager's hours and a hired worker's, each at its edges; already out today, out
 * yesterday; no emerald; nothing wanted; the day's sales used, used yesterday;</li>
 * <li>the day: a trip; sales on the same day added, on a new day restarted;</li>
 * <li>which stall: none sells it; one; the cheapest an item of several; a cheaper one it can't
 * afford; none it can afford (the cheapest); a tie, to the nearer then the lower id; closed; not
 * allowed; the first want no stall sells passed for the next;</li>
 * <li>a bought village: not bought; its owner's stall; a trusted player's; a stranger's;</li>
 * <li>sales for a want: in items (exact, a remainder); in points (an item that fills nothing);</li>
 * <li>a worker's food: short, exactly a day's, more; a want or day that breaks its invariant.</li>
 * </ul> */
final class ShoppingTest {
    private static final Who FREE = new Who(false, true, false, false, false, true);
    private static final Who HIRED = new Who(true, true, false, false, false, true);
    private static final Want FOOD = new Want(Household.FOOD, Set.of("bread", "carrot"), 4, Unit.ITEMS, Dest.HOUSEHOLD);
    private static final Want HOE = new Want("hoe", Set.of("iron_hoe"), 1, Unit.ITEMS, Dest.HOUSEHOLD);
    private static final long DAY = 24000 * 5;

    private static Offer offer(int id, String item, int quantity, int price, double distance) {
        return new Offer(id, item, quantity, price, distance, true, true);
    }

    @Test void onlyFreeAndHiredAdultsWithABedShop() {
        assertTrue(Shopping.shopper(FREE));
        assertTrue(Shopping.shopper(HIRED));
        assertFalse(Shopping.shopper(new Who(false, false, false, false, false, true)), "a child");
        assertFalse(Shopping.shopper(new Who(false, true, true, false, false, true)), "a captive");
        assertFalse(Shopping.shopper(new Who(true, true, false, true, false, true)), "in chains");
        assertFalse(Shopping.shopper(new Who(false, true, true, false, true, true)), "on its way home");
        assertFalse(Shopping.shopper(new Who(false, true, false, false, false, false)), "no bed");
    }

    @Test void eachShopsInItsOwnSocialTime() {
        assertTrue(Shopping.social(DAY + 9000, false));
        assertTrue(Shopping.social(DAY + 10999, false));
        assertFalse(Shopping.social(DAY + 8999, false));
        assertFalse(Shopping.social(DAY + 11000, false));
        assertTrue(Shopping.social(DAY + 8000, true));
        assertTrue(Shopping.social(DAY + 9999, true));
        assertFalse(Shopping.social(DAY + 7999, true));
        assertFalse(Shopping.social(DAY + 10000, true));
    }

    @Test void aTripIsDueOnceASocialTimeWithAnEmeraldAWantAndASaleLeft() {
        var d = Shopping.Day.NONE;
        assertTrue(Shopping.due(FREE, DAY + 9500, d, 1, true, 3));
        assertFalse(Shopping.due(FREE, DAY + 9500, Shopping.tripped(d, DAY + 9100), 1, true, 3), "out already today");
        assertTrue(Shopping.due(FREE, DAY + 9500, Shopping.tripped(d, DAY - 24000 + 9100), 1, true, 3), "out yesterday");
        assertFalse(Shopping.due(FREE, DAY + 9500, d, 0, true, 3), "no emerald");
        assertFalse(Shopping.due(FREE, DAY + 9500, d, 5, false, 3), "wants nothing");
        assertFalse(Shopping.due(FREE, DAY + 8500, d, 5, true, 3), "not its hours");
        assertTrue(Shopping.due(HIRED, DAY + 8500, d, 5, true, 3));
        var spent = Shopping.sold(d, DAY + 2000, 3);
        assertFalse(Shopping.due(FREE, DAY + 9500, spent, 5, true, 3), "the day's sales used");
        assertTrue(Shopping.due(FREE, DAY + 24000 + 9500, spent, 5, true, 3), "used yesterday");
        assertFalse(Shopping.due(new Who(false, true, true, false, false, true), DAY + 9500, d, 5, true, 3), "a captive");
    }

    @Test void theDayCountsTripsAndSales() {
        var d = Shopping.tripped(Shopping.Day.NONE, DAY + 9000);
        assertEquals(5, d.tripDay());
        var one = Shopping.sold(d, DAY + 9100, 1);
        assertEquals(new Shopping.Day(5, 5, 1), one);
        assertEquals(2, Shopping.salesLeft(one, DAY + 9200, 3));
        assertEquals(new Shopping.Day(5, 5, 3), Shopping.sold(one, DAY + 9300, 2));
        assertEquals(new Shopping.Day(5, 6, 1), Shopping.sold(one, DAY + 24000 + 100, 1), "a new day restarts the count");
        assertEquals(3, Shopping.salesLeft(one, DAY + 24000, 3));
        assertEquals(0, Shopping.salesLeft(new Shopping.Day(5, 5, 7), DAY, 3), "never below 0");
        assertThrows(IllegalArgumentException.class, () -> Shopping.sold(d, DAY, -1));
        assertThrows(IllegalArgumentException.class, () -> new Shopping.Day(0, 0, -1));
    }

    @Test void itGoesToTheCheapestStallItCanAffordForItsFirstWantAnyStallSells() {
        assertEquals(Optional.empty(), Shopping.choose(List.of(FOOD), List.of(offer(1, "dirt", 1, 1, 5)), 10));
        assertEquals(1, Shopping.choose(List.of(FOOD), List.of(offer(1, "bread", 4, 1, 50)), 10).orElseThrow().offer());
        var stalls = List.of(offer(1, "bread", 4, 1, 5), offer(2, "carrot", 8, 1, 60), offer(3, "bread", 32, 6, 2));
        assertEquals(2, Shopping.choose(List.of(FOOD), stalls, 10).orElseThrow().offer(), "an eighth of an emerald beats a quarter and 0.1875");
        assertEquals(1, Shopping.choose(List.of(FOOD), List.of(offer(1, "bread", 4, 1, 5), offer(3, "bread", 32, 6, 2)), 5).orElseThrow().offer(),
                "a cheaper one it can't afford a sale at");
        assertEquals(3, Shopping.choose(List.of(FOOD), List.of(offer(1, "bread", 4, 2, 5), offer(3, "bread", 32, 6, 2)), 1).orElseThrow().offer(),
                "it can afford none: the cheapest");
        assertEquals(4, Shopping.choose(List.of(FOOD), List.of(offer(5, "bread", 4, 1, 9), offer(4, "bread", 4, 1, 3)), 5).orElseThrow().offer(), "a tie to the nearer");
        assertEquals(4, Shopping.choose(List.of(FOOD), List.of(offer(5, "bread", 4, 1, 3), offer(4, "bread", 4, 1, 3)), 5).orElseThrow().offer(), "then the lower id");
    }

    @Test void closedStallsAndOnesItMayNotUseArePassedAndSoIsAWantNoneSells() {
        var closed = new Offer(1, "bread", 8, 1, 5, false, true);
        var barred = new Offer(2, "bread", 8, 1, 5, true, false);
        var hoes = offer(3, "iron_hoe", 1, 3, 30);
        assertEquals(Optional.empty(), Shopping.choose(List.of(FOOD), List.of(closed, barred), 10));
        var pick = Shopping.choose(List.of(FOOD, HOE), List.of(closed, barred, hoes), 10).orElseThrow();
        assertEquals(3, pick.offer());
        assertEquals(HOE, pick.want(), "food first, but nobody here sells it");
    }

    @Test void inABoughtVillageOnlyTheOwnersAndHisTrustedPlayersStalls() {
        assertTrue(Shopping.allowed(Optional.empty(), "anyone", false));
        assertTrue(Shopping.allowed(Optional.of("rusty"), "rusty", false));
        assertTrue(Shopping.allowed(Optional.of("rusty"), "friend", true));
        assertFalse(Shopping.allowed(Optional.of("rusty"), "stranger", false));
    }

    @Test void theSalesThatMeetAWant() {
        assertEquals(1, Shopping.salesFor(FOOD, 4, 0), "four items, four a sale");
        assertEquals(2, Shopping.salesFor(FOOD, 3, 0), "a remainder");
        var points = new Want(Household.FOOD, Set.of("bread"), 11, Unit.POINTS, Dest.HOME);
        assertEquals(3, Shopping.salesFor(points, 1, 5), "eleven points of five-point bread: three");
        assertEquals(1, Shopping.salesFor(points, 4, 5));
        assertEquals(0, Shopping.salesFor(points, 4, 0), "food that fills nothing");
        assertThrows(IllegalArgumentException.class, () -> Shopping.salesFor(FOOD, 0, 1));
    }

    @Test void aWorkerWantsFoodWhenHomeAndTheCanteenHoldLessThanADays() {
        assertEquals(Optional.of(new Want(Household.FOOD, Set.of("bread"), 15, Unit.POINTS, Dest.HOME)), Shopping.workerFood(5, Set.of("bread")));
        assertEquals(Optional.empty(), Shopping.workerFood(20, Set.of("bread")));
        assertEquals(Optional.empty(), Shopping.workerFood(40, Set.of("bread")));
        assertThrows(IllegalArgumentException.class, () -> Shopping.workerFood(-1, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Want("x", Set.of(), 0, Unit.ITEMS, Dest.HOME));
        assertThrows(IllegalArgumentException.class, () -> new Want(" ", Set.of(), 1, Unit.ITEMS, Dest.HOME));
    }
}
