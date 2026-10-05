/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>emeralds: 0, between, at the cap, over it (a cap lowered); below 0 refused; rules with a cap
 * under 1 or a negative deposit or line refused;</li>
 * <li>the start: found in the world, born; a line above the cap;</li>
 * <li>paying: short, exact, under; a negative amount refused;</li>
 * <li>taking in: under the cap, to it, past it (the rest lost), into a purse already over it;</li>
 * <li>the morning: before 2000 and at it; a new morning, the same morning again, a later one, a
 * morning missed; under the line, at it, over it; eligible or not; a deposit that passes the cap;
 * negative times.</li>
 * </ul> */
final class PurseTest {
    private static final Purse.Rules R = Purse.Rules.DEFAULT;
    private static Purse of(int n) { return new Purse(n, Purse.NEVER); }

    @Test void aPurseHoldsNoFewerThanNothingAndItsRulesAreSane() {
        assertDoesNotThrow(() -> of(0));
        assertDoesNotThrow(() -> of(200));
        assertThrows(IllegalArgumentException.class, () -> of(-1));
        assertThrows(IllegalArgumentException.class, () -> new Purse.Rules(0, 2, 4));
        assertThrows(IllegalArgumentException.class, () -> new Purse.Rules(64, -1, 4));
        assertThrows(IllegalArgumentException.class, () -> new Purse.Rules(64, 2, -1));
        assertEquals(new Purse.Rules(64, 2, 4), R);
    }

    @Test void aVillagerFoundInTheWorldStartsFullAndANewOneAtTheLine() {
        assertEquals(64, Purse.start(true, R).emeralds());
        assertEquals(4, Purse.start(false, R).emeralds());
        assertEquals(Purse.NEVER, Purse.start(true, R).lastMorning());
        assertEquals(10, Purse.start(false, new Purse.Rules(10, 2, 20)).emeralds(), "never above the cap");
    }

    @Test void payingTakesWhatItHoldsAndNoMore() {
        assertTrue(of(3).canPay(3));
        assertFalse(of(3).canPay(4));
        assertEquals(0, of(3).pay(3).emeralds());
        assertEquals(1, of(3).pay(2).emeralds());
        assertThrows(IllegalArgumentException.class, () -> of(3).pay(4));
        assertThrows(IllegalArgumentException.class, () -> of(3).pay(-1));
        assertEquals(7L, new Purse(5, 7L).pay(1).lastMorning(), "paying keeps the morning");
    }

    @Test void takingInStopsAtTheCapAndTheRestIsLost() {
        assertEquals(30, of(10).takeIn(20, R).emeralds());
        assertEquals(64, of(60).takeIn(4, R).emeralds());
        assertEquals(64, of(62).takeIn(5, R).emeralds());
        assertEquals(3, of(62).lost(5, R));
        assertEquals(0, of(10).lost(20, R));
        assertEquals(80, of(80).takeIn(5, R).emeralds(), "a purse over a lowered cap takes nothing more and keeps what it has");
        assertEquals(5, of(80).lost(5, R));
        assertThrows(IllegalArgumentException.class, () -> of(1).takeIn(-1, R));
    }

    @Test void aMorningIsBeforeWorkAndEachIsSeenOnce() {
        assertTrue(Purse.morning(0));
        assertTrue(Purse.morning(1999));
        assertFalse(Purse.morning(2000));
        assertTrue(Purse.morning(24000 * 3 + 5));
        assertTrue(Purse.morning(-23000), "the last day before the first, an hour in");
        assertEquals(3, Purse.day(24000 * 3 + 5));
        assertEquals(-1, Purse.day(-1));
        var seen = of(10).morning(24000 * 3 + 100, true, R);
        assertEquals(3, seen.lastMorning());
        assertFalse(seen.newMorning(24000 * 3 + 1500), "the same morning");
        assertTrue(seen.newMorning(24000 * 4 + 10), "the next");
        assertFalse(seen.newMorning(24000 * 4 + 2000), "the next day, after its morning");
    }

    @Test void theDepositGoesOnceAMorningToAnEligiblePurseUnderTheLine() {
        long monday = 24000 * 10 + 50, laterMonday = 24000 * 10 + 1900, tuesday = 24000 * 11 + 50;
        var p = of(1).morning(monday, true, R);
        assertEquals(3, p.emeralds());
        assertEquals(3, p.morning(laterMonday, true, R).emeralds(), "once a morning");
        assertEquals(5, p.morning(tuesday, true, R).emeralds(), "3 is under 4: the next morning pays");
        assertEquals(5, p.morning(tuesday, true, R).morning(24000 * 12 + 50, true, R).emeralds(), "5 is over the line");
        assertEquals(4, of(4).morning(monday, true, R).emeralds(), "at the line: nothing");
        assertEquals(1, of(1).morning(monday, false, R).emeralds(), "a captive or a child: nothing");
        assertEquals(10, of(1).morning(monday, false, R).lastMorning(), "but the morning is seen");
        assertEquals(1, of(1).morning(24000 * 10 + 2000, true, R).emeralds(), "seen first after its morning: missed, never paid");
        assertEquals(Purse.NEVER, of(1).morning(24000 * 10 + 2000, true, R).lastMorning());
        assertEquals(6, of(3).morning(monday, true, new Purse.Rules(6, 5, 4)).emeralds(), "a deposit that passes the cap stops at it");
    }
}
