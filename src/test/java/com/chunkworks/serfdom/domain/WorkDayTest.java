/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import org.junit.jupiter.api.Test;
import static com.chunkworks.serfdom.domain.WorkDay.Part.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: each part's first and last tick; the day's turn (23999, 24000); times past the
 * first day; negative times. */
final class WorkDayTest {
    @Test void eachPartStartsAndEndsOnItsTick() {
        assertEquals(IDLE, WorkDay.at(0));
        assertEquals(IDLE, WorkDay.at(1999));
        assertEquals(WORK, WorkDay.at(2000));
        assertEquals(WORK, WorkDay.at(7999));
        assertEquals(MEET, WorkDay.at(8000));
        assertEquals(MEET, WorkDay.at(9999));
        assertEquals(IDLE, WorkDay.at(10000));
        assertEquals(IDLE, WorkDay.at(11999));
        assertEquals(SLEEP, WorkDay.at(12000));
        assertEquals(SLEEP, WorkDay.at(23999));
    }
    @Test void laterDaysRepeat() {
        assertEquals(IDLE, WorkDay.at(24000));
        assertEquals(WORK, WorkDay.at(24000L * 1000 + 5000));
        assertEquals(SLEEP, WorkDay.at(24000L * 7 + 18000));
    }
    @Test void negativeTimesCountBackFromTheTurn() {
        assertEquals(23999, WorkDay.tickOfDay(-1));
        assertEquals(SLEEP, WorkDay.at(-1));
        assertEquals(WORK, WorkDay.at(-24000 + 2000));
    }
    @Test void shiftLeftCountsDownInsideWorkOnly() {
        assertEquals(6000, WorkDay.shiftLeft(2000));
        assertEquals(1, WorkDay.shiftLeft(7999));
        assertEquals(0, WorkDay.shiftLeft(8000));
        assertEquals(0, WorkDay.shiftLeft(1999));
        assertEquals(0, WorkDay.shiftLeft(18000));
        assertEquals(3000, WorkDay.shiftLeft(24000 * 3 + 5000));
    }

    // The captive's day (D-0003). Partitions: each part's first and last tick; the hired worker's
    // meeting hours, which a captive works through; a later day.
    @Test void aCaptiveWorksThroughTheMeetingAndNeverMeets() {
        assertEquals(IDLE, WorkDay.atCaptive(1999));
        assertEquals(WORK, WorkDay.atCaptive(2000));
        assertEquals(WORK, WorkDay.atCaptive(8000), "a hired worker meets at 8000; a captive works");
        assertEquals(WORK, WorkDay.atCaptive(9999));
        assertEquals(IDLE, WorkDay.atCaptive(10000));
        assertEquals(IDLE, WorkDay.atCaptive(11999));
        assertEquals(SLEEP, WorkDay.atCaptive(12000));
        assertEquals(SLEEP, WorkDay.atCaptive(23999));
        assertEquals(WORK, WorkDay.atCaptive(24000L * 5 + 9000));
        for (long t = 0; t < WorkDay.DAY; t += 250) assertNotEquals(MEET, WorkDay.atCaptive(t), "never meets: " + t);
    }
    @Test void aCaptivesShiftRunsToTenThousand() {
        assertEquals(8000, WorkDay.shiftLeft(2000, true));
        assertEquals(2000, WorkDay.shiftLeft(8000, true), "still at work when a hired worker's shift is over");
        assertEquals(1, WorkDay.shiftLeft(9999, true));
        assertEquals(0, WorkDay.shiftLeft(10000, true));
        assertEquals(0, WorkDay.shiftLeft(1999, true));
        assertEquals(WorkDay.shiftLeft(5000), WorkDay.shiftLeft(5000, false), "a hired worker's is unchanged");
        assertEquals(0, WorkDay.shiftLeft(8000, false));
    }
}
