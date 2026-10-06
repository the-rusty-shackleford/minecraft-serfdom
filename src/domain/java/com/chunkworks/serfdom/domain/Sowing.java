/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** What a farmer sows (D-0008). Rusty: "copy what grows near, so long as their copying does not
 * interfere with what you decided is to be grown." Only bare farmland is sown, no crop is ever
 * replaced, and no ground is tilled:
 * <ol>
 * <li>a spot that has grown a crop is sown only with that crop again;</li>
 * <li>a spot that has never grown anything takes its neighbours' crop only when they agree: every
 * crop within {@link #NEAR} blocks of it, growing or remembered, is of one kind, and not a stem's.
 * A stem is never copied, so a never-planted spot within NEAR of a stem stays bare, and the fruit
 * keeps the ground beside its stem;</li>
 * <li>no spot is sown until it has been bare for the wait, so a player's own planting comes first.</li>
 * </ol>
 * A crop's kind is named by what plants it (its seed's id). */
public final class Sowing {
    /** How far around a never-planted spot its neighbours are read: a 9 by 9 square. */
    public static final int NEAR = 4;
    private Sowing() {}

    /** One height of a field as a sweep saw it: a rectangle of columns, each a crop of some kind,
     * bare farmland (with the kind it last grew, if it is remembered, and since when it has been
     * bare), or anything else. Filled once, then read. Mutable.
     *
     * <p>Rep invariant: width, depth &ge; 1; the arrays have width * depth cells; a cell is not
     * both a crop and bare; remembered and since are set only on bare cells.
     *
     * <p>Safety from rep exposure: the arrays are private and never handed out. */
    public static final class Layer {
        private final int minX, minZ, width, depth;
        private final String[] crop, remembered;
        private final boolean[] bare;
        private final long[] since;

        /** requires: width, depth &ge; 1. effects: a layer of {@code width} by {@code depth} columns
         * from ({@code minX}, {@code minZ}), with nothing in it. */
        public Layer(int minX, int minZ, int width, int depth) {
            if (width < 1 || depth < 1) throw new IllegalArgumentException(width + " by " + depth);
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            crop = new String[width * depth];
            remembered = new String[width * depth];
            bare = new boolean[width * depth];
            since = new long[width * depth];
        }

        private int index(int x, int z) {
            int i = x - minX, j = z - minZ;
            if (i < 0 || j < 0 || i >= width || j >= depth) throw new IllegalArgumentException("outside the layer: " + x + ", " + z);
            return j * width + i;
        }

        /** requires: ({@code x}, {@code z}) in the layer and not yet bare. effects: a crop of
         * {@code kind} grows there. */
        public void crop(int x, int z, String kind) {
            int i = index(x, z);
            if (bare[i]) throw new IllegalArgumentException("already bare: " + x + ", " + z);
            crop[i] = Objects.requireNonNull(kind);
        }

        /** requires: ({@code x}, {@code z}) in the layer and not a crop. effects: bare farmland
         * there, bare since {@code since}, which last grew {@code remembered} (empty when it never
         * did or nobody remembers). */
        public void bare(int x, int z, Optional<String> remembered, long since) {
            int i = index(x, z);
            if (crop[i] != null) throw new IllegalArgumentException("a crop grows there: " + x + ", " + z);
            bare[i] = true;
            this.remembered[i] = remembered.orElse(null);
            this.since[i] = since;
        }

        /** effects: the kind growing at cell {@code i}, or remembered there; null for none. */
        private String kind(int i) { return crop[i] != null ? crop[i] : remembered[i]; }
    }

    /** A spot to sow and the kind to sow there. */
    public record Sow(int x, int z, String kind) {
        public Sow { Objects.requireNonNull(kind); }
    }

    /** requires: {@code wait} &ge; 0.
     * effects: the bare spots of {@code layer} to sow at {@code now}, each with the kind the rules
     * give it, in the layer's order (by z, then x). {@code stems} names the kinds that are stems.
     * A spot within {@link #NEAR} of the layer's edge is judged by what the layer holds, so a
     * caller reads {@code NEAR} beyond the spots it means to sow. Takes time in proportion to the
     * layer's size times the number of kinds in it. */
    public static List<Sow> choose(Layer layer, Set<String> stems, long now, long wait) {
        if (wait < 0) throw new IllegalArgumentException("wait " + wait);
        int w = layer.width, d = layer.depth;
        // Each kind in the layer, and the count of its cells in every rectangle from the corner.
        var kinds = new HashMap<String, Integer>();
        for (int i = 0; i < w * d; i++) { var k = layer.kind(i); if (k != null) kinds.putIfAbsent(k, kinds.size()); }
        var names = new String[kinds.size()];
        kinds.forEach((k, n) -> names[n] = k);
        var sums = new int[kinds.size()][(w + 1) * (d + 1)];
        for (int k = 0; k < names.length; k++) {
            var s = sums[k];
            for (int j = 0; j < d; j++) for (int i = 0; i < w; i++) {
                int here = names[k].equals(layer.kind(j * w + i)) ? 1 : 0;
                s[(j + 1) * (w + 1) + i + 1] = here + s[j * (w + 1) + i + 1] + s[(j + 1) * (w + 1) + i] - s[j * (w + 1) + i];
            }
        }
        var out = new ArrayList<Sow>();
        for (int j = 0; j < d; j++) for (int i = 0; i < w; i++) {
            int cell = j * w + i;
            if (!layer.bare[cell] || now - layer.since[cell] < wait) continue;
            int x = layer.minX + i, z = layer.minZ + j;
            if (layer.remembered[cell] != null) { out.add(new Sow(x, z, layer.remembered[cell])); continue; }
            int i0 = Math.max(0, i - NEAR), j0 = Math.max(0, j - NEAR), i1 = Math.min(w, i + NEAR + 1), j1 = Math.min(d, j + NEAR + 1);
            String only = null;
            int found = 0;
            for (int k = 0; k < names.length && found < 2; k++) {
                var s = sums[k];
                int n = s[j1 * (w + 1) + i1] - s[j0 * (w + 1) + i1] - s[j1 * (w + 1) + i0] + s[j0 * (w + 1) + i0];
                if (n > 0) { found++; only = names[k]; }
            }
            if (found == 1 && !stems.contains(only)) out.add(new Sow(x, z, only));
        }
        return out;
    }
}
