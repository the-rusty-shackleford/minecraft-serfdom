/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>points: 0, between, half, MAX; below 0, above MAX and NaN refused;</li>
 * <li>draining: awake and asleep, an hour and a day, held at 0; zero ticks; negative ticks or rate
 * refused;</li>
 * <li>eating: from empty, to the cap; negative refused; wanting: nothing that fills, full, a bite
 * that fits, one that overshoots by half its worth and by more;</li>
 * <li>thresholds: just under half, at half; 0 and just above it;</li>
 * <li>work speed: at and above half, between, just above 0, at 0, against the floor; a floor of 0
 * or above 1 refused; the pace it stacks into;</li>
 * <li>the drumsticks shown; the hungry icon's place among the needs;</li>
 * <li>the day the defaults make: dinner to breakfast to dinner.</li>
 * </ul> */
final class HungerTest {
    private static Hunger at(double p) { return new Hunger(p); }

    @Test void pointsLieBetweenZeroAndTwenty() {
        assertEquals(20.0, Hunger.FULL.points());
        assertDoesNotThrow(() -> at(0));
        assertDoesNotThrow(() -> at(20));
        assertThrows(IllegalArgumentException.class, () -> at(-0.01));
        assertThrows(IllegalArgumentException.class, () -> at(20.01));
        assertThrows(IllegalArgumentException.class, () -> at(Double.NaN));
    }

    @Test void aWakingHourCostsAPointAndASleepingHourHalf() {
        assertEquals(19.0, Hunger.FULL.drain(Hunger.HOUR, false, 1.0).points(), 1e-9);
        assertEquals(19.5, Hunger.FULL.drain(Hunger.HOUR, true, 1.0).points(), 1e-9);
        assertEquals(18.0, Hunger.FULL.drain(Hunger.HOUR, false, 2.0).points(), 1e-9, "the rate scales");
        assertEquals(Hunger.FULL, Hunger.FULL.drain(0, false, 1.0));
        assertEquals(0.0, at(3).drain(24000, false, 1.0).points(), "never below 0");
        assertThrows(IllegalArgumentException.class, () -> Hunger.FULL.drain(-1, false, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Hunger.FULL.drain(1, false, -1.0));
    }

    @Test void eatingFillsUpToTwenty() {
        assertEquals(5.0, at(0).eat(5).points());
        assertEquals(20.0, at(18).eat(8).points(), "capped");
        assertThrows(IllegalArgumentException.class, () -> at(5).eat(-1));
    }

    @Test void aWorkerStartsOnABiteUnlessFullOrItWouldOvershootByMoreThanHalf() {
        assertFalse(at(5).wants(0), "nothing that fills");
        assertFalse(Hunger.FULL.wants(1), "full");
        assertTrue(at(9).wants(8));
        assertTrue(at(16).wants(8), "overshoots by 4, half its worth");
        assertFalse(at(16.5).wants(8), "overshoots by more than half");
        assertTrue(at(19.5).wants(1), "half a point over a 1");
    }

    @Test void belowHalfIsHungryAndZeroIsStarved() {
        assertTrue(at(9.99).hungry());
        assertFalse(at(10).hungry());
        assertTrue(at(0).starved());
        assertFalse(at(0.01).starved());
        assertTrue(at(0.01).hungry());
    }

    @Test void workSlowsStraightDownFromHalfToTheFloorAndStopsAtZero() {
        assertEquals(1.0, Hunger.FULL.workSpeed(0.5));
        assertEquals(1.0, at(10).workSpeed(0.5));
        assertEquals(0.75, at(5).workSpeed(0.5), 1e-9);
        assertEquals(0.5005, at(0.01).workSpeed(0.5), 1e-9, "just above the floor");
        assertEquals(0.0, at(0).workSpeed(0.5));
        assertEquals(0.9, at(5).workSpeed(0.8), 1e-9);
        assertEquals(1.0, at(5).workSpeed(1.0), "a floor of 1 never slows");
        assertThrows(IllegalArgumentException.class, () -> at(5).workSpeed(0));
        assertThrows(IllegalArgumentException.class, () -> at(5).workSpeed(1.01));
    }

    @Test void theHungerFactorStacksIntoThePace() {
        assertEquals(1.25 * 0.9 * 0.75, Pace.speed(true, true, 0.1, at(5).workSpeed(0.5)), 1e-9);
        assertEquals(Pace.speed(true, false, 0.1), Pace.speed(true, false, 0.1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Pace.speed(true, false, 0.1, 0.0), "a starved worker has no pace: it does not work");
    }

    @Test void theDrumsticksRoundUp() {
        assertEquals(20, Hunger.FULL.halves());
        assertEquals(1, at(0.2).halves(), "not starved yet shows half a drumstick");
        assertEquals(0, at(0).halves());
        assertEquals(14, at(13.75).halves());
    }

    @Test void hungryComesAfterNoBedAndBeforeTheRest() {
        assertEquals(Optional.of(Need.NO_BED), Need.shown(Set.of(Need.HUNGRY, Need.NO_BED)));
        for (var n : List.of(Need.NO_TOOL, Need.NO_STATION, Need.NO_FUEL, Need.NO_MATERIALS, Need.CHEST_FULL))
            assertEquals(Optional.of(Need.HUNGRY), Need.shown(Set.of(n, Need.HUNGRY)), n.name());
    }

    @Test void theDefaultsMakeADayOfTwoMealsWithoutGoingHungry() {
        // Full at dinner's end (10500), awake to 12000, asleep to the day's turn: about 13.75 at waking.
        var morning = Hunger.FULL.drain(1500, false, 1.0).drain(12000, true, 1.0);
        assertEquals(13.75, morning.points(), 1e-9);
        assertFalse(morning.hungry(), "a worker that ate dinner wakes fed");
        // Full at breakfast (500), awake to dinner (10500).
        var evening = Hunger.FULL.drain(10000, false, 1.0);
        assertFalse(evening.hungry(), "a worker that ate breakfast reaches dinner fed: " + evening.points());
        // Missing dinner, it wakes hungry and eats first thing.
        assertTrue(evening.drain(1500, false, 1.0).drain(12000, true, 1.0).hungry());
    }
}
