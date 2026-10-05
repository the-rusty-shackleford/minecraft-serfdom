/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** A box of blocks in a named dimension, its corners included: a village's bounds as a captive
 * remembers them (D-0003). Immutable.
 *
 * <p>Rep invariant: min &le; max on each axis. */
public record Area(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Area {
        Objects.requireNonNull(dimension);
        if (minX > maxX || minY > maxY || minZ > maxZ)
            throw new IllegalArgumentException("min above max: " + minX + "," + minY + "," + minZ + " .. " + maxX + "," + maxY + "," + maxZ);
    }

    /** effects: true iff {@code spot} is in this dimension and inside the box seen from above: its x
     * and z within the bounds, at any height. A village's bounds hold its buildings, and a villager
     * walking home over the ground around them may stand above or below. */
    public boolean coversColumn(Spot spot) {
        var c = spot.cell();
        return dimension.equals(spot.dimension()) && c.x() >= minX && c.x() <= maxX && c.z() >= minZ && c.z() <= maxZ;
    }

    /** effects: the block at the middle of the box. */
    public Spot centre() { return new Spot(dimension, Math.floorDiv(minX + maxX, 2), Math.floorDiv(minY + maxY, 2), Math.floorDiv(minZ + maxZ, 2)); }
}
