/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A For Sale block's ledger (D-0006): a line for each visit, the last {@link #LINES}, in the order
 * written; and each of the last {@link #DAYS} days' totals: sales, emeralds taken, and the visits that
 * came to nothing, by why. A clock turned back (a command) drops the days after the new one.
 * Immutable.
 *
 * <p>Rep invariant: at most LINES lines; at most DAYS days, in order, each a different day, none
 * more than DAYS - 1 days before the latest. */
public record Ledger(List<Line> lines, List<Totals> days) {
    public static final int LINES = 64, DAYS = 7;
    public static final Ledger EMPTY = new Ledger(List.of(), List.of());

    /** One visit: when, the villager's profession, what it did, and for a purchase the sales, the
     * items and the emeralds paid. RI: sales, items, paid &ge; 0; they are all 0 unless it bought. */
    public record Line(long dayTime, String profession, Verdict.Reaction reaction, int sales, int items, int paid) {
        public Line {
            Objects.requireNonNull(profession);
            Objects.requireNonNull(reaction);
            if (sales < 0 || items < 0 || paid < 0) throw new IllegalArgumentException("sales " + sales + ", items " + items + ", paid " + paid);
            if (!reaction.bought() && (sales > 0 || items > 0 || paid > 0)) throw new IllegalArgumentException(reaction + " paid nothing");
        }
    }

    /** One day's totals. RI: every count &ge; 0. */
    public record Totals(long day, int sold, int earned, int tooPricey, int cantAfford, int notInterested) {
        public Totals {
            if (sold < 0 || earned < 0 || tooPricey < 0 || cantAfford < 0 || notInterested < 0) throw new IllegalArgumentException("a count below 0");
        }
        static Totals none(long day) { return new Totals(day, 0, 0, 0, 0, 0); }
        Totals with(Line l) {
            return switch (l.reaction()) {
                case BARGAIN, BOUGHT -> new Totals(day, sold + l.sales(), earned + l.paid(), tooPricey, cantAfford, notInterested);
                case TOO_PRICEY -> new Totals(day, sold, earned, tooPricey + 1, cantAfford, notInterested);
                case CANT_AFFORD -> new Totals(day, sold, earned, tooPricey, cantAfford + 1, notInterested);
                case NOT_INTERESTED -> new Totals(day, sold, earned, tooPricey, cantAfford, notInterested + 1);
            };
        }
    }

    public Ledger {
        lines = List.copyOf(lines);
        days = List.copyOf(days);
        if (lines.size() > LINES || days.size() > DAYS) throw new IllegalArgumentException(lines.size() + " lines, " + days.size() + " days");
        for (int i = 1; i < days.size(); i++) if (days.get(i).day() <= days.get(i - 1).day()) throw new IllegalArgumentException("days out of order");
        if (!days.isEmpty() && days.getLast().day() - days.getFirst().day() >= DAYS) throw new IllegalArgumentException("a day too old");
    }

    /** effects: the ledger with the visit written: the oldest line dropped past {@link #LINES}, its
     * day's totals counted, days more than {@link #DAYS} - 1 before it, and any after it, dropped. */
    public Ledger write(Line line) {
        var l = new ArrayList<>(lines);
        l.add(line);
        while (l.size() > LINES) l.removeFirst();
        long day = Purse.day(line.dayTime());
        var d = new ArrayList<Totals>();
        for (var t : days) if (t.day() < day && day - t.day() < DAYS) d.add(t);
        var today = days.stream().filter(t -> t.day() == day).findFirst().orElse(Totals.none(day));
        d.add(today.with(line));
        return new Ledger(l, d);
    }
}
