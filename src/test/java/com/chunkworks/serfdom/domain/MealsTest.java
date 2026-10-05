/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Meals.Facts;
import com.chunkworks.serfdom.domain.Meals.Times;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>the windows at each boundary: 0, 1999, 2000, 9999, 10000, 11999, 12000, 23999; a later day
 * and a negative time;</li>
 * <li>in a window: not eaten in it, eaten in it, eaten in yesterday's; full or not;</li>
 * <li>out of a window: hungry or not;</li>
 * <li>never: asleep, in chains, on its way home, without a bed (each alone, hungry, in a window);</li>
 * <li>the wait: before it ends, at its end;</li>
 * <li>after a meal: eaten in a window, eaten out of one, nothing eaten.</li>
 * </ul> */
final class MealsTest {
    private static final Hunger PECKISH = new Hunger(15), HUNGRY = new Hunger(9), FULL = Hunger.FULL;

    private static Facts at(long dayTime, Hunger h, Times t) { return new Facts(dayTime, 100_000, h, t, true, false, false, true); }

    @Test void breakfastIsZeroToTwoThousandAndDinnerTenToTwelveThousand() {
        assertEquals(OptionalLong.of(0), Meals.window(0));
        assertEquals(OptionalLong.of(0), Meals.window(1999));
        assertEquals(OptionalLong.empty(), Meals.window(2000));
        assertEquals(OptionalLong.empty(), Meals.window(9999));
        assertEquals(OptionalLong.of(1), Meals.window(10000));
        assertEquals(OptionalLong.of(1), Meals.window(11999));
        assertEquals(OptionalLong.empty(), Meals.window(12000));
        assertEquals(OptionalLong.empty(), Meals.window(23999));
        assertEquals(OptionalLong.of(6), Meals.window(3 * 24000 + 500), "day three's breakfast");
        assertEquals(OptionalLong.of(-1), Meals.window(-24000 + 10500), "yesterday's dinner");
    }

    @Test void aWindowFeedsAWorkerThatIsNotFullOnce() {
        assertTrue(Meals.due(at(500, PECKISH, Times.NONE)));
        assertFalse(Meals.due(at(500, PECKISH, new Times(0, Meals.NEVER))), "eaten in this breakfast");
        assertTrue(Meals.due(at(24500, PECKISH, new Times(1, Meals.NEVER))), "yesterday's dinner is not today's breakfast");
        assertFalse(Meals.due(at(500, FULL, Times.NONE)), "full");
        assertTrue(Meals.due(at(10500, PECKISH, new Times(0, Meals.NEVER))), "dinner after breakfast");
    }

    @Test void betweenMealsOnlyAHungryWorkerEats() {
        assertFalse(Meals.due(at(5000, PECKISH, Times.NONE)));
        assertTrue(Meals.due(at(5000, HUNGRY, Times.NONE)), "breaking off its work");
        assertTrue(Meals.due(at(500, HUNGRY, new Times(0, Meals.NEVER))), "hungry again in a window it ate in");
    }

    @Test void aWorkerAsleepInChainsOnItsWayHomeOrWithoutABedNeverEats() {
        assertFalse(Meals.due(new Facts(500, 100_000, HUNGRY, Times.NONE, false, false, false, true)), "asleep");
        assertFalse(Meals.due(new Facts(500, 100_000, HUNGRY, Times.NONE, true, true, false, true)), "in chains");
        assertFalse(Meals.due(new Facts(500, 100_000, HUNGRY, Times.NONE, true, false, true, true)), "on its way home");
        assertFalse(Meals.due(new Facts(500, 100_000, HUNGRY, Times.NONE, true, false, false, false)), "no bed");
    }

    @Test void itWaitsBetweenOneMealAndTheNext() {
        var times = new Times(Meals.NEVER, 100_000 + 1);
        assertFalse(Meals.due(at(5000, HUNGRY, times)), "a tick before the wait ends");
        assertTrue(Meals.due(new Facts(5000, 100_001, HUNGRY, times, true, false, false, true)), "at its end");
    }

    @Test void aMealCountsItsWindowOnlyWhenSomethingWasEaten() {
        assertEquals(new Times(0, 100_000 + Meals.RETRY), Meals.after(Times.NONE, 1500, 100_000, true));
        assertEquals(new Times(Meals.NEVER, 100_000 + Meals.RETRY), Meals.after(Times.NONE, 1500, 100_000, false), "nothing eaten: it looks again later");
        assertEquals(new Times(1, 100_000 + Meals.RETRY), Meals.after(new Times(1, 0), 5000, 100_000, true), "out of a window the last window stands");
    }
}
