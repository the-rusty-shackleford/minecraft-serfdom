/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.WorkerLayout.Rect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the screens it must fit (Rusty's 3440×1440 at GUI scale 5, the booth's 1280×720 at
 * scale 2) and one it cannot; every part inside the panel; every pair of parts apart; the wearing
 * slots stacked; the inventory centred; a rectangle with no size refused. */
final class WorkerLayoutTest {
    @Test void itFitsRustysScreenAtGuiScaleFiveAndTheBooths() {
        assertTrue(WorkerLayout.fits(3440 / 5, 1440 / 5), WorkerLayout.WIDTH + "×" + WorkerLayout.HEIGHT + " in 688×288");
        assertTrue(WorkerLayout.fits(1280 / 2, 720 / 2));
        assertFalse(WorkerLayout.fits(300, 200));
    }

    @Test void everyPartLiesInsideThePanelAndApartFromTheOthers() {
        var parts = WorkerLayout.parts();
        for (int i = 0; i < parts.size(); i++) {
            assertTrue(parts.get(i).inside(WorkerLayout.PANEL), "inside: " + parts.get(i));
            for (int j = i + 1; j < parts.size(); j++) assertFalse(parts.get(i).overlaps(parts.get(j)), parts.get(i) + " and " + parts.get(j));
        }
    }

    @Test void theWearingSlotsStandOneUnderAnother() {
        for (int i = 1; i < 4; i++) {
            assertEquals(WorkerLayout.wearing(i - 1).x(), WorkerLayout.wearing(i).x());
            assertEquals(WorkerLayout.wearing(i - 1).y() + WorkerLayout.SLOT, WorkerLayout.wearing(i).y());
        }
        assertThrows(IllegalArgumentException.class, () -> WorkerLayout.wearing(4));
    }

    @Test void theInventoryIsCentred() {
        var inv = WorkerLayout.inventory();
        int left = inv.x(), right = WorkerLayout.WIDTH - (inv.x() + inv.w());
        assertTrue(Math.abs(left - right) <= 1, left + " and " + right);
        assertEquals(inv.x(), WorkerLayout.hotbar().x());
    }

    @Test void aRectangleHasASize() {
        assertThrows(IllegalArgumentException.class, () -> new Rect(0, 0, 0, 5));
        assertTrue(new Rect(0, 0, 10, 10).overlaps(new Rect(9, 9, 5, 5)));
        assertFalse(new Rect(0, 0, 10, 10).overlaps(new Rect(10, 0, 5, 5)), "touching is not overlapping");
    }
}
