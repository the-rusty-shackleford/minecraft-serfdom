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
 * <li>a worker's food: short, exactly a day's, more; a want or day that breaks its invariant;</li>
 * <li>4b, the stalls seen: one, two, yesterday's, kept through a trip and a sale;</li>
 * <li>4b, a look: the nearest; closed, barred and sellers passed; the nearest seen, the next; all
 * seen; a tie;</li>
 * <li>4b, what it does now: a need first, though a stall nearer sells a want; then a look; all seen,
 * nothing; a new day; needs nobody sells, a look; nothing in reach; a seller cheaper than a stall for
 * a need, never for a look; out of hours, no emerald, no sale left, a captive; a worker.</li>
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

    // ---- 4b: the stalls seen today, window shopping, and what it does now ----------------------

    private static Offer seller(int id, String item, int quantity, int price, double distance) {
        return new Offer(id, item, quantity, price, distance, true, true, false, 0);
    }

    @Test void theStallsSeenTodayAreForgottenTomorrow() {
        var d = Shopping.saw(Shopping.Day.NONE, DAY + 9100, 77L);
        assertEquals(Set.of(77L), Shopping.seen(d, DAY + 9500));
        assertEquals(Set.of(77L, 12L), Shopping.seen(Shopping.saw(d, DAY + 9600, 12L), DAY + 9700));
        assertEquals(Set.of(), Shopping.seen(d, DAY + 24000 + 9100), "a new day");
        assertEquals(Set.of(5L), Shopping.seen(Shopping.saw(d, DAY + 24000 + 9100, 5L), DAY + 24000 + 9200), "yesterday's forgotten as one is seen");
        var kept = Shopping.sold(Shopping.tripped(d, DAY + 9200), DAY + 9300, 2);
        assertEquals(Set.of(77L), Shopping.seen(kept, DAY + 9400), "a trip and a sale keep what it has seen");
        assertEquals(new Shopping.Day(5, Shopping.NEVER, 0), new Shopping.Day(5, Shopping.NEVER, 0, Shopping.NEVER, Set.of()));
    }

    @Test void aWindowShopperLooksAtTheNearestStallItHasNotSeenToday() {
        var a = new Offer(1, "carpet", 4, 1, 10, true, true, true, 100);
        var b = new Offer(2, "paper", 4, 1, 6, true, true, true, 200);
        var closed = new Offer(3, "lantern", 1, 1, 2, false, true, true, 300);
        var barred = new Offer(4, "lantern", 1, 1, 2, true, false, true, 400);
        var peddler = seller(5, "bread", 6, 1, 1);
        assertEquals(2, Shopping.browse(List.of(a, b, closed, barred, peddler), Set.of()).orElseThrow().id(), "the nearest open stall it may use; never a seller");
        assertEquals(1, Shopping.browse(List.of(a, b), Set.of(200L)).orElseThrow().id(), "the nearest it has seen: the next");
        assertEquals(Optional.empty(), Shopping.browse(List.of(a, b), Set.of(100L, 200L)), "every one seen");
        var twin = new Offer(0, "paper", 4, 1, 6, true, true, true, 500);
        assertEquals(0, Shopping.browse(List.of(b, twin), Set.of()).orElseThrow().id(), "a tie to the lower id");
    }

    @Test void itGoesForANeedFirstOnceASocialTimeThenLooksAtStallsItHasNotSeen() {
        var bread = new Offer(1, "bread", 6, 1, 20, true, true, true, 100);
        var carpet = new Offer(2, "carpet", 4, 1, 5, true, true, true, 200);
        long now = DAY + 9300;
        var first = Shopping.decide(FREE, now, Shopping.Day.NONE, 5, 3, List.of(FOOD), List.of(bread, carpet));
        assertEquals(new Shopping.Trip(1, Optional.of(FOOD)), first.trip().orElseThrow(), "the need, though the carpets are nearer");
        assertEquals(5, first.day().tripDay(), "its trip for a need is made");
        assertEquals(Set.of(100L), Shopping.seen(first.day(), now), "and the bread stall seen");
        var second = Shopping.decide(FREE, now + 400, first.day(), 4, 3, List.of(FOOD), List.of(bread, carpet));
        assertEquals(new Shopping.Trip(2, Optional.empty()), second.trip().orElseThrow(), "a need already tried today: a look at the carpets");
        assertEquals(Set.of(100L, 200L), Shopping.seen(second.day(), now + 400));
        var third = Shopping.decide(FREE, now + 800, second.day(), 4, 3, List.of(), List.of(bread, carpet));
        assertEquals(Optional.empty(), third.trip(), "every stall seen: nothing more today");
        assertEquals(second.day(), third.day());
        var tomorrow = Shopping.decide(FREE, now + 24000, third.day(), 4, 3, List.of(), List.of(bread, carpet));
        assertEquals(new Shopping.Trip(2, Optional.empty()), tomorrow.trip().orElseThrow(), "a new day, the nearest again");
    }

    @Test void aNeedNothingHereSellsLeavesItFreeToWindowShop() {
        var carpet = new Offer(2, "carpet", 4, 1, 5, true, true, true, 200);
        var d = Shopping.decide(FREE, DAY + 9300, Shopping.Day.NONE, 5, 3, List.of(FOOD, HOE), List.of(carpet));
        assertEquals(new Shopping.Trip(2, Optional.empty()), d.trip().orElseThrow(), "needs food and a hoe, nobody sells either: browses");
        assertEquals(5, d.day().tripDay(), "the trip for its needs counts all the same");
        var none = Shopping.decide(FREE, DAY + 9300, Shopping.Day.NONE, 5, 3, List.of(FOOD), List.of());
        assertEquals(Optional.empty(), none.trip(), "nothing in reach");
        assertEquals(5, none.day().tripDay(), "a trip that came to nothing still counts");
    }

    @Test void aVillagerSellingIsAnOfferForANeedNeverForALook() {
        var stall = new Offer(1, "bread", 4, 1, 3, true, true, true, 100);
        var farmer = seller(2, "bread", 6, 1, 30);
        var d = Shopping.decide(FREE, DAY + 9300, Shopping.Day.NONE, 5, 3, List.of(FOOD), List.of(stall, farmer));
        assertEquals(new Shopping.Trip(2, Optional.of(FOOD)), d.trip().orElseThrow(), "six for one at the farmer beats four at the stall");
        assertEquals(Set.of(), Shopping.seen(d.day(), DAY + 9300), "a villager is not a stall seen");
        var look = Shopping.decide(FREE, DAY + 9300, Shopping.Day.NONE, 5, 3, List.of(), List.of(farmer));
        assertEquals(Optional.empty(), look.trip(), "no need: it doesn't go to look at a farmer");
    }

    @Test void nobodyGoesOutOfHoursWithoutAnEmeraldOrASaleLeftOrWhoNeverShops() {
        var carpet = new Offer(2, "carpet", 4, 1, 5, true, true, true, 200);
        var d = Shopping.Day.NONE;
        assertEquals(Optional.empty(), Shopping.decide(FREE, DAY + 8000, d, 5, 3, List.of(), List.of(carpet)).trip(), "not its hours");
        assertEquals(Optional.empty(), Shopping.decide(FREE, DAY + 9300, d, 0, 3, List.of(), List.of(carpet)).trip(), "no emerald");
        assertEquals(Optional.empty(), Shopping.decide(FREE, DAY + 9300, Shopping.sold(d, DAY + 9000, 3), 5, 3, List.of(), List.of(carpet)).trip(), "the day's sales used");
        assertEquals(Optional.empty(), Shopping.decide(new Who(false, true, true, false, false, true), DAY + 9300, d, 5, 3, List.of(), List.of(carpet)).trip(), "a captive");
        assertEquals(d, Shopping.decide(FREE, DAY + 8000, d, 5, 3, List.of(FOOD), List.of(carpet)).day(), "nothing decided, nothing counted");
        assertEquals(new Shopping.Trip(2, Optional.empty()), Shopping.decide(HIRED, DAY + 8500, d, 5, 3, List.of(), List.of(carpet)).trip().orElseThrow(), "a worker in its meeting hours");
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
