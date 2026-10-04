/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import org.junit.jupiter.api.Test;
import static com.chunkworks.serfdom.domain.Hiring.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: verdict over every combination of the four facts (each reason's precedence); fee at
 * each level, below novice and above master, a zero rate, a negative rate. */
final class HiringTest {
    @Test void onlyAnEmployedUnownedAdultInAnOpenVillageIsHired() {
        assertEquals(HIRE, Hiring.verdict(true, false, true, true));
        int hires = 0;
        for (int i = 0; i < 16; i++) {
            boolean adult = (i & 1) != 0, owned = (i & 2) != 0, employed = (i & 4) != 0, allows = (i & 8) != 0;
            if (Hiring.verdict(adult, owned, employed, allows) == HIRE) hires++;
        }
        assertEquals(1, hires);
    }
    @Test void reasonsComeInOrder() {
        assertEquals(BABY, Hiring.verdict(false, true, false, false), "a baby is a baby before anything else");
        assertEquals(OWNED, Hiring.verdict(true, true, false, false));
        assertEquals(NO_PROFESSION, Hiring.verdict(true, false, false, false));
        assertEquals(NOT_YOURS, Hiring.verdict(true, false, true, false));
    }
    @Test void feeIsTheRateTimesTheLevel() {
        assertEquals(8, Hiring.fee(1, 8));
        assertEquals(16, Hiring.fee(2, 8));
        assertEquals(24, Hiring.fee(3, 8));
        assertEquals(32, Hiring.fee(4, 8));
        assertEquals(40, Hiring.fee(5, 8));
    }
    @Test void levelIsHeldToNoviceThroughMaster() {
        assertEquals(8, Hiring.fee(0, 8));
        assertEquals(8, Hiring.fee(-3, 8));
        assertEquals(40, Hiring.fee(6, 8));
        assertEquals(40, Hiring.fee(Integer.MAX_VALUE, 8));
    }
    @Test void aZeroRateHiresForNothingAndANegativeOneIsRefused() {
        assertEquals(0, Hiring.fee(3, 0));
        assertThrows(IllegalArgumentException.class, () -> Hiring.fee(3, -1));
    }
}
