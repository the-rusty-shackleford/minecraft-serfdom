/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: due at the window's first and last tick and just outside it; a day already rolled, a
 * later day, awake; negative times. The roll: chance 0 and 1, the same seed and day, the rate over
 * many days at the default, the get-up tick inside the window. Home: inside, on the edge and out of
 * the village seen from above, at another height, in another dimension; with no village, within
 * and past 16 blocks of where it was taken. */
final class EscapeTest {
    private static final long D = WorkDay.DAY;

    @Test void aRollIsDueOnlyAsleepInsideTheWindowOnANewDay() {
        assertTrue(Escape.due(18000, -1, true));
        assertTrue(Escape.due(19999, -1, true));
        assertFalse(Escape.due(17999, -1, true), "before midnight");
        assertFalse(Escape.due(20000, -1, true), "after two");
        assertFalse(Escape.due(18000, -1, false), "awake");
        assertFalse(Escape.due(18000, 0, true), "today is rolled");
        assertTrue(Escape.due(D + 18000, 0, true), "tomorrow is not");
        assertFalse(Escape.due(D * 3 + 18500, 3, true));
        assertEquals(-1, Escape.day(-1));
        assertTrue(Escape.due(-D + 18000, -2, true), "a day before the first counts back");
    }
    @Test void aCertainRollGetsUpInsideTheWindowAndAnImpossibleOneNever() {
        for (long day = 0; day < 200; day++) {
            var t = Escape.roll(7, day, 1.0);
            assertTrue(t.isPresent());
            assertTrue(t.getAsInt() >= Escape.WINDOW_START && t.getAsInt() < Escape.WINDOW_END, "inside: " + t);
            assertTrue(Escape.roll(7, day, 0.0).isEmpty());
        }
    }
    @Test void theRollIsTheSameForTheSameCaptiveAndNight() {
        for (long day = 0; day < 50; day++) assertEquals(Escape.roll(123456789L, day, 0.5), Escape.roll(123456789L, day, 0.5));
    }
    @Test void aboutOneNightInTwentySucceedsAtTheDefault() {
        int nights = 40000, out = 0;
        for (long day = 0; day < nights; day++) if (Escape.roll(-42L, day, Escape.CHANCE).isPresent()) out++;
        // 5% of 40000 is 2000; its standard deviation is about 44, so a band of ±200 is over 4.5 sigma.
        assertTrue(Math.abs(out - 2000) < 200, "escapes: " + out);
        int other = 0;
        for (long day = 0; day < nights; day++) if (Escape.roll(99L, day, Escape.CHANCE).isPresent()) other++;
        assertTrue(Math.abs(other - 2000) < 200, "another captive's nights too: " + other);
    }
    @Test void aBadChanceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Escape.roll(1, 1, -0.01));
        assertThrows(IllegalArgumentException.class, () -> Escape.roll(1, 1, 1.01));
        assertThrows(IllegalArgumentException.class, () -> Escape.roll(1, 1, Double.NaN));
    }

    private static final Area OAK = new Area("minecraft:overworld", 100, 60, 200, 140, 80, 240);
    private static Spot at(int x, int y, int z) { return new Spot("minecraft:overworld", x, y, z); }

    @Test void homeIsTheVillageSeenFromAbove() {
        var taken = at(120, 64, 220);
        assertTrue(Escape.home(at(120, 64, 220), Optional.of(OAK), taken));
        assertTrue(Escape.home(at(100, 64, 240), Optional.of(OAK), taken), "on its corner");
        assertTrue(Escape.home(at(140, 200, 200), Optional.of(OAK), taken), "high above its bounds still counts");
        assertTrue(Escape.home(at(130, 10, 230), Optional.of(OAK), taken), "below them too");
        assertFalse(Escape.home(at(99, 64, 220), Optional.of(OAK), taken), "a block outside");
        assertFalse(Escape.home(at(120, 64, 241), Optional.of(OAK), taken));
        assertFalse(Escape.home(new Spot("minecraft:the_nether", 120, 64, 220), Optional.of(OAK), taken), "another dimension");
    }
    @Test void withNoVillageHomeIsNearWhereItWasTaken() {
        var taken = at(0, 64, 0);
        assertTrue(Escape.home(at(16, 90, 0), Optional.empty(), taken), "16 blocks off, at any height");
        assertTrue(Escape.home(at(11, 64, 11), Optional.empty(), taken), "15.6 diagonally");
        assertFalse(Escape.home(at(12, 64, 12), Optional.empty(), taken), "17 diagonally");
        assertFalse(Escape.home(at(17, 64, 0), Optional.empty(), taken));
        assertFalse(Escape.home(new Spot("minecraft:the_nether", 0, 64, 0), Optional.empty(), taken));
    }
    @Test void theGoalIsTheVillagesMiddleElseWhereItWasTaken() {
        assertEquals(at(120, 70, 220), Escape.goal(Optional.of(OAK), at(0, 0, 0)));
        assertEquals(at(5, 6, 7), Escape.goal(Optional.empty(), at(5, 6, 7)));
    }
    @Test void anAreaIsRefusedInsideOut() {
        assertThrows(IllegalArgumentException.class, () -> new Area("d", 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Area("d", 0, 1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Area("d", 0, 0, 1, 0, 0, 0));
        assertEquals(new Spot("d", -2, 0, -2), new Area("d", -2, 0, -2, -1, 0, -1).centre(), "the middle, -1.5, rounds down");
        assertEquals(new Spot("d", 1, 0, 1), new Area("d", 1, 0, 1, 2, 0, 2).centre(), "1.5 rounds down too");
    }
}
