/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** The radii a job allows its post: a least, a default and a most (D-0001: 4, 8, 16, or for
 * woodcutting 4, 16, 32). The area is the cube of that radius around the post. Immutable.
 *
 * <p>Rep invariant: 1 &le; min &le; standard &le; max &le; {@link #CEILING}. */
public record Radius(int min, int standard, int max) {
    /** The largest radius any job may declare: a cube 129 blocks across. */
    public static final int CEILING = 64;
    public static final Radius WORK = new Radius(4, 8, 16);

    public Radius {
        if (min < 1 || min > standard || standard > max || max > CEILING)
            throw new IllegalArgumentException("radius needs 1 <= min <= standard <= max <= " + CEILING + ": " + min + ", " + standard + ", " + max);
    }

    /** effects: {@code radius} held to [min, max]. */
    public int clamp(int radius) { return Math.clamp(radius, min, max); }

    /** effects: true iff {@code cell} is inside the cube of {@code radius} around {@code post}. */
    public static boolean contains(Cell post, int radius, Cell cell) { return post.chebyshev(cell) <= radius; }
}
