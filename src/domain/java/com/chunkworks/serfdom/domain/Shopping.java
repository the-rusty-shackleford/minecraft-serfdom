/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A villager's shopping (D-0006): who goes, when, for what, and to which stall.
 * <ul>
 * <li><b>Who:</b> free and hired adults with a bed; never captives, villagers in chains or on their
 * way home.</li>
 * <li><b>When:</b> once a social time (a free villager's is vanilla's meeting, 9000 to 11000; a hired
 * worker's 8000 to 10000), when it wants something, holds an emerald, and has a purchase left today
 * of the {@code perDay} it may make. A trip that came to nothing still counts.</li>
 * <li><b>Which stall:</b> for its first want some open stall it may use sells: the one with the
 * lowest price an item among those it can afford a sale at; if it can afford none, the cheapest (it
 * finds out there). Ties to the nearer, then the lower id.</li>
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

    /** A villager's shopping day: the last day it went out, and how many sales it made on the last day
     * it bought. Immutable. RI: sales &ge; 0. */
    public record Day(long tripDay, long salesDay, int sales) {
        public static final Day NONE = new Day(NEVER, NEVER, 0);
        public Day {
            if (sales < 0) throw new IllegalArgumentException("sales " + sales);
        }
    }

    /** Who a villager is, as shopping asks. */
    public record Who(boolean hired, boolean grown, boolean captive, boolean cuffed, boolean escaping, boolean hasBed) {}

    /** A stall as a shopper sees it: an id, the item, how many a sale, a sale's price, how far it is
     * from the shopper's bed, whether it sells now, and whether the shopper may use it. */
    public record Offer(int id, String item, int quantity, int price, double distance, boolean open, boolean allowed) {
        public double each() { return price / (double) quantity; }
    }

    /** The stall chosen and the want it is for. */
    public record Pick(int offer, Want want) {}

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

    /** effects: true iff the villager goes shopping now, as the class describes. */
    public static boolean due(Who w, long dayTime, Day d, int emeralds, boolean wants, int perDay) {
        return shopper(w) && social(dayTime, w.hired()) && d.tripDay() != Purse.day(dayTime) && emeralds >= 1 && wants && salesLeft(d, dayTime, perDay) > 0;
    }

    /** effects: the day after a trip begun at {@code dayTime}. */
    public static Day tripped(Day d, long dayTime) { return new Day(Purse.day(dayTime), d.salesDay(), d.sales()); }

    /** requires: n &ge; 0. effects: the day after {@code n} sales at {@code dayTime}. */
    public static Day sold(Day d, long dayTime, int n) {
        if (n < 0) throw new IllegalArgumentException("n " + n);
        long day = Purse.day(dayTime);
        return new Day(d.tripDay(), day, (d.salesDay() == day ? d.sales() : 0) + n);
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
