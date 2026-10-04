/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** A block position in a named dimension ("minecraft:overworld"). Immutable. */
public record Spot(String dimension, Cell cell) {
    public Spot { Objects.requireNonNull(dimension); Objects.requireNonNull(cell); }
    public Spot(String dimension, int x, int y, int z) { this(dimension, new Cell(x, y, z)); }

    /** effects: true iff both spots are in the same dimension and no more than {@code blocks}
     * apart in a straight line. */
    public boolean within(Spot other, int blocks) {
        return dimension.equals(other.dimension) && cell.distanceSq(other.cell) <= (long) blocks * blocks;
    }
}
