/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Verdict.Reaction;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>a line: each reaction; a purchase with its sales, items and paid; a pass that says it paid
 * refused; negatives refused;</li>
 * <li>writing: the first line; lines past 64 (the oldest dropped); a day's totals by reaction; a new
 * day; a day seven back dropped, six kept; the clock turned back;</li>
 * <li>a ledger that breaks its invariant refused.</li>
 * </ul> */
final class LedgerTest {
    private static final long DAY = 24000;
    private static Ledger.Line bought(long t, int sales, int items, int paid) { return new Ledger.Line(t, "minecraft:farmer", Reaction.BOUGHT, sales, items, paid); }
    private static Ledger.Line pass(long t, Reaction r) { return new Ledger.Line(t, "minecraft:mason", r, 0, 0, 0); }

    @Test void aLineSaysWhatHappenedAndOnlyAPurchasePays() {
        assertDoesNotThrow(() -> new Ledger.Line(0, "minecraft:farmer", Reaction.BARGAIN, 1, 6, 1));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Line(0, "minecraft:farmer", Reaction.TOO_PRICEY, 1, 6, 1));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Line(0, "minecraft:farmer", Reaction.BOUGHT, -1, 6, 1));
    }

    @Test void aDaysTotalsCountEachVisitByWhatHappened() {
        var l = Ledger.EMPTY.write(bought(DAY * 3 + 9100, 2, 8, 2))
                .write(new Ledger.Line(DAY * 3 + 9200, "minecraft:cleric", Reaction.BARGAIN, 1, 4, 1))
                .write(pass(DAY * 3 + 9300, Reaction.TOO_PRICEY))
                .write(pass(DAY * 3 + 9400, Reaction.CANT_AFFORD))
                .write(pass(DAY * 3 + 9500, Reaction.CANT_AFFORD))
                .write(pass(DAY * 3 + 9600, Reaction.NOT_INTERESTED));
        assertEquals(6, l.lines().size());
        assertEquals(List.of(new Ledger.Totals(3, 3, 3, 1, 2, 1)), l.days());
        var next = l.write(bought(DAY * 4 + 100, 1, 4, 1));
        assertEquals(2, next.days().size());
        assertEquals(new Ledger.Totals(4, 1, 1, 0, 0, 0), next.days().getLast());
    }

    @Test void theLastSixtyFourLinesAreKept() {
        var l = Ledger.EMPTY;
        for (int i = 0; i < 70; i++) l = l.write(bought(DAY + i, 1, 1, 1));
        assertEquals(64, l.lines().size());
        assertEquals(DAY + 6, l.lines().getFirst().dayTime(), "the six oldest dropped");
        assertEquals(DAY + 69, l.lines().getLast().dayTime());
        assertEquals(70, l.days().getFirst().sold(), "the day's totals keep every sale");
    }

    @Test void sevenDaysAreKept() {
        var l = Ledger.EMPTY;
        for (int day = 10; day <= 16; day++) l = l.write(bought(DAY * day, 1, 1, 1));
        assertEquals(7, l.days().size());
        assertEquals(10, l.days().getFirst().day());
        l = l.write(bought(DAY * 17, 1, 1, 1));
        assertEquals(7, l.days().size());
        assertEquals(11, l.days().getFirst().day(), "a day seven back dropped");
        l = l.write(bought(DAY * 30, 1, 1, 1));
        assertEquals(List.of(new Ledger.Totals(30, 1, 1, 0, 0, 0)), l.days(), "a long gap leaves only the new day");
    }

    @Test void aClockTurnedBackDropsTheDaysAfterIt() {
        var l = Ledger.EMPTY.write(bought(DAY * 10, 1, 1, 1)).write(bought(DAY * 12, 1, 1, 1)).write(bought(DAY * 11, 1, 1, 1));
        assertEquals(List.of(10L, 11L), l.days().stream().map(Ledger.Totals::day).toList());
        assertEquals(3, l.lines().size(), "every line is kept in the order written");
    }

    @Test void aLedgerKeepsItsInvariant() {
        var t = new Ledger.Totals(1, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new Ledger(List.of(), List.of(t, t)), "a day twice");
        assertThrows(IllegalArgumentException.class, () -> new Ledger(List.of(), List.of(t, new Ledger.Totals(8, 0, 0, 0, 0, 0))), "seven days apart");
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Totals(1, -1, 0, 0, 0, 0));
    }
}
