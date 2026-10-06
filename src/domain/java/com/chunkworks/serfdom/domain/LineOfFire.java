/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;

/** Whether a shot from a muzzle to a target would pass near a friend (D-0007): a defender holds fire
 * while any friend's box, grown by a margin on every side, meets the straight line between, short of
 * the target. Bullets and arrows hit whatever is in their way. */
public final class LineOfFire {
    /** A friend's box. RI: each min &le; its max. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            if (!(minX <= maxX && minY <= maxY && minZ <= maxZ)) throw new IllegalArgumentException("an inside-out box");
        }
        Box grown(double m) { return new Box(minX - m, minY - m, minZ - m, maxX + m, maxY + m, maxZ + m); }
    }

    /** A point. */
    public record Point(double x, double y, double z) {}

    private LineOfFire() {}

    /** requires: margin &ge; 0. effects: true iff no friend's box, grown by {@code margin}, meets the
     * segment from {@code muzzle} to {@code target}. */
    public static boolean clear(Point muzzle, Point target, List<Box> friends, double margin) {
        if (!(margin >= 0)) throw new IllegalArgumentException("margin " + margin);
        for (var f : friends) if (meets(muzzle, target, f.grown(margin))) return false;
        return true;
    }

    /** effects: true iff the segment from {@code a} to {@code b} meets {@code box}, by the slab method:
     * the segment's spans between each pair of faces must overlap. */
    static boolean meets(Point a, Point b, Box box) {
        double t0 = 0, t1 = 1;
        double[] from = {a.x(), a.y(), a.z()}, d = {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()};
        double[] lo = {box.minX(), box.minY(), box.minZ()}, hi = {box.maxX(), box.maxY(), box.maxZ()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-12) {
                if (from[i] < lo[i] || from[i] > hi[i]) return false;
                continue;
            }
            double u = (lo[i] - from[i]) / d[i], v = (hi[i] - from[i]) / d[i];
            t0 = Math.max(t0, Math.min(u, v));
            t1 = Math.min(t1, Math.max(u, v));
            if (t0 > t1) return false;
        }
        return true;
    }
}
