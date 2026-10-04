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
}
