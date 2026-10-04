/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** A block position within one dimension. Immutable. */
public record Cell(int x, int y, int z) {
    /** effects: this cell moved by (dx, dy, dz). */
    public Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    /** effects: the cell below this one. */
    public Cell below() { return offset(0, -1, 0); }
    /** effects: the squared straight-line distance between the two cells' corners. */
    public long distanceSq(Cell other) {
        long dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
    /** effects: the largest of the three axis distances (the cube a radius describes). */
    public int chebyshev(Cell other) { return Math.max(Math.abs(x - other.x), Math.max(Math.abs(y - other.y), Math.abs(z - other.z))); }

    /** The six face neighbours, in a fixed order. */
    public static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}};
    /** The 26 neighbours sharing a face, an edge or a corner, in a fixed order. */
    public static final int[][] AROUND;
    static {
        AROUND = new int[26][];
        int i = 0;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dy != 0 || dz != 0) AROUND[i++] = new int[]{dx, dy, dz};
    }
}
