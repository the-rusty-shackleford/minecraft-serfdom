/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import org.junit.jupiter.api.Test;
import static com.chunkworks.serfdom.domain.Capture.Step.*;
import static com.chunkworks.serfdom.domain.Capture.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the verdict over every combination of its four facts (each reason's precedence);
 * the hold before, at and after its time; each failure alone and each pair of them (precedence);
 * the reach at, just inside and just past its bound; bad arguments. */
final class CaptureTest {
    @Test void onlyAnEmployedFreeAdultInAnOpenVillageIsTaken() {
        int takes = 0;
        for (int i = 0; i < 16; i++) {
            boolean adult = (i & 1) != 0, owned = (i & 2) != 0, employed = (i & 4) != 0, allows = (i & 8) != 0;
            if (Capture.verdict(adult, owned, employed, allows) == TAKE) takes++;
        }
        assertEquals(1, takes);
        assertEquals(TAKE, Capture.verdict(true, false, true, true));
    }
    @Test void reasonsComeInOrder() {
        assertEquals(BABY, Capture.verdict(false, true, false, false));
        assertEquals(OWNED, Capture.verdict(true, true, false, false), "an owned villager is cuffed, whatever else");
        assertEquals(NO_PROFESSION, Capture.verdict(true, false, false, false));
        assertEquals(NOT_YOURS, Capture.verdict(true, false, true, false));
    }

    private static Capture.Step at(int held) { return Capture.tick(held, Capture.HOLD, true, true, 1.0, Capture.REACH, true); }

    @Test void theHoldTakesTwoSeconds() {
        assertEquals(HOLDING, at(0));
        assertEquals(HOLDING, at(Capture.HOLD - 1));
        assertEquals(DONE, at(Capture.HOLD));
        assertEquals(DONE, at(Capture.HOLD + 5));
        assertEquals(40, Capture.HOLD);
    }
    @Test void eachFailureEndsTheHoldEvenAtItsLastTick() {
        int last = Capture.HOLD;
        assertEquals(GONE, Capture.tick(last, Capture.HOLD, false, true, 1.0, 2.0, true));
        assertEquals(LET_GO, Capture.tick(last, Capture.HOLD, true, false, 1.0, 2.0, true));
        assertEquals(TOO_FAR, Capture.tick(last, Capture.HOLD, true, true, 2.01, 2.0, true));
        assertEquals(LOOKED_AWAY, Capture.tick(last, Capture.HOLD, true, true, 1.0, 2.0, false));
    }
    @Test void failuresComeInOrder() {
        assertEquals(GONE, Capture.tick(3, 40, false, false, 9.0, 2.0, false));
        assertEquals(LET_GO, Capture.tick(3, 40, true, false, 9.0, 2.0, false));
        assertEquals(TOO_FAR, Capture.tick(3, 40, true, true, 9.0, 2.0, false));
    }
    @Test void theReachIncludesItsBound() {
        assertEquals(HOLDING, Capture.tick(3, 40, true, true, 2.0, 2.0, true));
        assertEquals(HOLDING, Capture.tick(3, 40, true, true, 0.0, 2.0, true));
        assertEquals(TOO_FAR, Capture.tick(3, 40, true, true, 2.0001, 2.0, true));
    }
    @Test void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Capture.tick(-1, 40, true, true, 1, 2, true));
        assertThrows(IllegalArgumentException.class, () -> Capture.tick(0, 0, true, true, 1, 2, true));
        assertThrows(IllegalArgumentException.class, () -> Capture.tick(0, 40, true, true, -0.1, 2, true));
        assertThrows(IllegalArgumentException.class, () -> Capture.tick(0, 40, true, true, 1, 0, true));
    }
}
