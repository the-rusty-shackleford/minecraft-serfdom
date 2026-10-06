/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A villager's shopping (D-0006): who goes, when, for what, and to which stall or seller.
 * <ul>
 * <li><b>Who:</b> free and hired adults with a bed; never captives, villagers in chains or on their
 * way home.</li>
 * <li><b>When:</b> in its social time (a free villager's is vanilla's meeting, 9000 to 11000; a
 * hired worker's 8000 to 10000), holding an emerald, with a purchase left today of the {@code perDay}
 * it may make.</li>
 * <li><b>For a need</b> (4a): once a social time, when it needs something. A trip that came to
 * nothing still counts. For its first need some open offer it may use sells (a stall, or a free
 * villager selling, 4b): the one with the lowest price an item among those it can afford a sale at;
 * if it can afford none, the cheapest (it finds out there). Ties to the nearer, then the lower
 * id.</li>
 * <li><b>Window shopping</b> (4b): with no need any offer here sells, a look at the nearest open
 * stall it may use that it has not seen today; ties to the lower id. Every stall it goes to is seen
 * for the day.</li>
 * </ul> */
public final class Shopping {
    /** A free villager's social time: vanilla's meeting. */
    public static final int FREE_START = 9000, FREE_END = 11000;
    /** A hired worker's: its meeting. */
    public static final int HIRED_START = WorkDay.WORK_END, HIRED_END = WorkDay.MEET_END;
    /** A worker keeps a day's food near its bed: what fills a belly. */
    public static final int DAYS_FOOD = (int) Hunger.MAX;
    public static final long NEVER = Long.MIN_VALUE;
    private Shopping() {}

    /** How a want is counted: in items, or in the points of hunger the food must fill. */
    public enum Unit { ITEMS, POINTS }
    /** Where a purchase goes: a free villager's household, a worker's home chest, its post's chests. */
    public enum Dest { HOUSEHOLD, HOME, POST }

    /** One thing a villager wants: a name, the items that meet it, how many (in its unit), and where
     * it goes. Immutable. RI: name not blank; count &ge; 1. */
    public record Want(String name, Set<String> items, int count, Unit unit, Dest dest) {
        public Want {
            Objects.requireNonNull(name);
            if (name.isBlank()) throw new IllegalArgumentException("blank want");
            items = Set.copyOf(items);
            Objects.requireNonNull(unit);
            Objects.requireNonNull(dest);
            if (count < 1) throw new IllegalArgumentException(name + ": count " + count);
        }
    }

    /** A villager's shopping day: the last day it went out for a need, how many sales it made on the
     * last day it bought, and the stalls (by where they stand) it has seen on {@code seenDay}.
     * Immutable. RI: sales &ge; 0. */
    public record Day(long tripDay, long salesDay, int sales, long seenDay, Set<Long> seen) {
        public static final Day NONE = new Day(NEVER, NEVER, 0);
        public Day {
            if (sales < 0) throw new IllegalArgumentException("sales " + sales);
            seen = Set.copyOf(seen);
        }
        /** A day with no stall seen. */
        public Day(long tripDay, long salesDay, int sales) { this(tripDay, salesDay, sales, NEVER, Set.of()); }
    }

    /** Who a villager is, as shopping asks. */
    public record Who(boolean hired, boolean grown, boolean captive, boolean cuffed, boolean escaping, boolean hasBed) {}

    /** What a shopper may buy from, as it sees it: an id, the item, how many a sale, a sale's price, how
     * far it is from the shopper's bed, whether it sells now, whether the shopper may use it, whether
     * it is a stall (else a villager selling), and for a stall where it stands. */
    public record Offer(int id, String item, int quantity, int price, double distance, boolean open, boolean allowed, boolean stall, long at) {
        /** A stall, standing at its id. */
        public Offer(int id, String item, int quantity, int price, double distance, boolean open, boolean allowed) {
            this(id, item, quantity, price, distance, open, allowed, true, id);
        }
        public double each() { return price / (double) quantity; }
    }

    /** The offer chosen and the want it is for. */
    public record Pick(int offer, Want want) {}

    /** A trip: to an offer, for a need, or (no need) a look at a stall. */
    public record Trip(int offer, Optional<Want> need) {
        public boolean browsing() { return need.isEmpty(); }
    }

    /** What a villager does now, and its day after deciding. */
    public record Decision(Optional<Trip> trip, Day day) {}

    /** effects: true iff the villager ever shops: a free or hired adult with a bed, not a captive,
     * not in chains, not on its way home. */
    public static boolean shopper(Who w) { return w.grown() && !w.captive() && !w.cuffed() && !w.escaping() && w.hasBed(); }

    /** effects: true iff {@code dayTime} is in the villager's social time. */
    public static boolean social(long dayTime, boolean hired) {
        int t = WorkDay.tickOfDay(dayTime);
        return hired ? t >= HIRED_START && t < HIRED_END : t >= FREE_START && t < FREE_END;
    }

    /** effects: the sales the villager may still make on the day of {@code dayTime}. requires:
     * perDay &ge; 0. */
    public static int salesLeft(Day d, long dayTime, int perDay) {
        if (perDay < 0) throw new IllegalArgumentException("perDay " + perDay);
        return d.salesDay() == Purse.day(dayTime) ? Math.max(0, perDay - d.sales()) : perDay;
    }

    /** effects: true iff a trip for a need is due now, as the class describes. */
    public static boolean due(Who w, long dayTime, Day d, int emeralds, boolean wants, int perDay) {
        return shopper(w) && social(dayTime, w.hired()) && d.tripDay() != Purse.day(dayTime) && emeralds >= 1 && wants && salesLeft(d, dayTime, perDay) > 0;
    }

    /** effects: the day after a trip for a need begun at {@code dayTime}. */
    public static Day tripped(Day d, long dayTime) { return new Day(Purse.day(dayTime), d.salesDay(), d.sales(), d.seenDay(), d.seen()); }

    /** requires: n &ge; 0. effects: the day after {@code n} sales at {@code dayTime}. */
    public static Day sold(Day d, long dayTime, int n) {
        if (n < 0) throw new IllegalArgumentException("n " + n);
        long day = Purse.day(dayTime);
        return new Day(d.tripDay(), day, (d.salesDay() == day ? d.sales() : 0) + n, d.seenDay(), d.seen());
    }

    /** effects: the stalls the villager has seen on the day of {@code dayTime}. */
    public static Set<Long> seen(Day d, long dayTime) { return d.seenDay() == Purse.day(dayTime) ? d.seen() : Set.of(); }

    /** effects: the day after it has seen the stall standing at {@code at}, on the day of
     * {@code dayTime}: yesterday's stalls are forgotten. */
    public static Day saw(Day d, long dayTime, long at) {
        var s = new java.util.HashSet<>(seen(d, dayTime));
        s.add(at);
        return new Day(d.tripDay(), d.salesDay(), d.sales(), Purse.day(dayTime), s);
    }

    /** effects: the stall a window shopper looks at, as the class describes; empty when it has seen
     * every open stall it may use. */
    public static Optional<Offer> browse(List<Offer> offers, Set<Long> seen) {
        return offers.stream().filter(o -> o.stall() && o.open() && o.allowed() && !seen.contains(o.at()))
                .min(Comparator.comparingDouble(Offer::distance).thenComparingInt(Offer::id));
    }

    /** effects: what the villager does now, as the class describes: a trip for its first need an offer
     * sells, once a social time (the trip counts whether or not one does); else a look at a stall it
     * has not seen today; else nothing. The stall it goes to is seen for the day. {@code needs} are
     * its needs now, food first; {@code offers} the stalls and sellers in reach. */
    public static Decision decide(Who w, long dayTime, Day d, int emeralds, int perDay, List<Want> needs, List<Offer> offers) {
        if (!shopper(w) || !social(dayTime, w.hired()) || emeralds < 1 || salesLeft(d, dayTime, perDay) < 1) return new Decision(Optional.empty(), d);
        var day = d;
        if (due(w, dayTime, d, emeralds, !needs.isEmpty(), perDay)) {
            day = tripped(day, dayTime);
            var pick = choose(needs, offers, emeralds);
            if (pick.isPresent()) {
                var to = offers.stream().filter(o -> o.id() == pick.get().offer()).findFirst().orElseThrow();
                if (to.stall()) day = saw(day, dayTime, to.at());
                return new Decision(Optional.of(new Trip(to.id(), Optional.of(pick.get().want()))), day);
            }
        }
        var look = browse(offers, seen(day, dayTime));
        if (look.isEmpty()) return new Decision(Optional.empty(), day);
        return new Decision(Optional.of(new Trip(look.get().id(), Optional.empty())), saw(day, dayTime, look.get().at()));
    }

    /** effects: the stall to go to and the want it is for, as the class describes; empty when no
     * stall it may use sells anything it wants. */
    public static Optional<Pick> choose(List<Want> wants, List<Offer> offers, int emeralds) {
        var cheapest = Comparator.comparingDouble(Offer::each).thenComparingDouble(Offer::distance).thenComparingInt(Offer::id);
        for (var want : wants) {
            var sellers = offers.stream().filter(o -> o.open() && o.allowed() && want.items().contains(o.item())).toList();
            if (sellers.isEmpty()) continue;
            var best = sellers.stream().filter(o -> o.price() <= emeralds).min(cheapest).or(() -> sellers.stream().min(cheapest)).orElseThrow();
            return Optional.of(new Pick(best.id(), want));
        }
        return Optional.empty();
    }

    /** effects: true iff a shopper whose bed lies in a village bought by {@code villageOwner} (none:
     * not bought) may use a stall of {@code stallOwner}'s: anywhere unbought; in a bought village only
     * the owner's stalls and those of players he trusts ({@code trusted}). */
    public static boolean allowed(Optional<String> villageOwner, String stallOwner, boolean trusted) {
        return villageOwner.isEmpty() || villageOwner.get().equals(stallOwner) || trusted;
    }

    /** requires: quantity &ge; 1. effects: how many sales of {@code quantity} items meet the want: in
     * items, enough to cover it; in points, enough items that fill {@code fills} each to cover it (none
     * when they fill nothing). */
    public static int salesFor(Want w, int quantity, int fills) {
        if (quantity < 1) throw new IllegalArgumentException("quantity " + quantity);
        if (w.unit() == Unit.ITEMS) return (w.count() + quantity - 1) / quantity;
        if (fills <= 0) return 0;
        return (int) Math.ceil(w.count() / (double) (fills * quantity));
    }

    /** effects: a worker's want of food (D-0006): when the ready food at its home chest and the
     * canteen fills less than {@link #DAYS_FOOD}, the points short, met by {@code foods}, for its
     * home chest; empty otherwise. requires: fills &ge; 0. */
    public static Optional<Want> workerFood(int fills, Set<String> foods) {
        if (fills < 0) throw new IllegalArgumentException("fills " + fills);
        return fills < DAYS_FOOD ? Optional.of(new Want(Household.FOOD, foods, DAYS_FOOD - fills, Unit.POINTS, Dest.HOME)) : Optional.empty();
    }
}
