/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.LineOfFire.Box;
import com.chunkworks.serfdom.domain.LineOfFire.Point;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: no friends; a friend on the line; beside it (inside the margin, outside it); behind the
 * target; behind the shooter; above; a line along an axis (a zero component) inside and outside a
 * box; a margin of 0; refused margins and boxes. */
final class LineOfFireTest {
    private static final Point MUZZLE = new Point(0, 1.6, 0), TARGET = new Point(10, 1.2, 0);

    /** A villager-sized box standing at (x, z). */
    private static Box at(double x, double z) { return new Box(x - 0.3, 0, z - 0.3, x + 0.3, 1.95, z + 0.3); }

    @Test void aShotIsClearWithNobodyInTheWay() {
        assertTrue(LineOfFire.clear(MUZZLE, TARGET, List.of(), 1));
        assertFalse(LineOfFire.clear(MUZZLE, TARGET, List.of(at(5, 0)), 1), "a friend halfway");
        assertFalse(LineOfFire.clear(MUZZLE, TARGET, List.of(at(5, 1.2)), 1), "within the margin");
        assertTrue(LineOfFire.clear(MUZZLE, TARGET, List.of(at(5, 1.4)), 1), "past it");
        assertFalse(LineOfFire.clear(MUZZLE, TARGET, List.of(at(5, 1.2)), 1.0), "a block either side");
        assertTrue(LineOfFire.clear(MUZZLE, TARGET, List.of(at(5, 1.2)), 0), "no margin: only the box itself");
        assertTrue(LineOfFire.clear(MUZZLE, TARGET, List.of(at(13, 0)), 1), "behind the target");
        assertTrue(LineOfFire.clear(MUZZLE, TARGET, List.of(at(-3, 0)), 1), "behind the shooter");
        assertTrue(LineOfFire.clear(MUZZLE, TARGET, List.of(new Box(4, 5, -1, 6, 6, 1)), 1), "a friend overhead");
    }

    @Test void aLineAlongAnAxis() {
        var a = new Point(0, 1, 0);
        var b = new Point(0, 1, 10);
        assertFalse(LineOfFire.clear(a, b, List.of(at(0, 5)), 0));
        assertTrue(LineOfFire.clear(a, b, List.of(at(2, 5)), 0.5));
        assertFalse(LineOfFire.clear(a, b, List.of(at(2, 5)), 2));
    }

    @Test void refused() {
        assertThrows(IllegalArgumentException.class, () -> LineOfFire.clear(MUZZLE, TARGET, List.of(), -0.1));
        assertThrows(IllegalArgumentException.class, () -> new Box(1, 0, 0, 0, 1, 1));
    }
}
