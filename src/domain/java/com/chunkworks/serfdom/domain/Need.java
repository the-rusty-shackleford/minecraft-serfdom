/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Collection;
import java.util.Optional;

/** What a worker lacks, shown as one icon above its head (D-0001). Declared in priority order:
 * when a worker lacks several things, the first declared is the one shown. Nothing shows when
 * nothing is lacking. Hungry (D-0005) comes after no bed, since a worker without a bed has no home
 * chest and the bed is the fix, and before everything else. */
public enum Need {
    NO_BED, HUNGRY, NO_TOOL, NO_STATION, NO_FUEL, NO_MATERIALS, CHEST_FULL;

    /** effects: the need to show of {@code needs}, the first in declaration order; empty when there
     * are none. */
    public static Optional<Need> shown(Collection<Need> needs) {
        Need best = null;
        for (Need n : needs) if (best == null || n.ordinal() < best.ordinal()) best = n;
        return Optional.ofNullable(best);
    }

    /** effects: the need's wire code, 1 + its position; 0 stands for none. */
    public static byte code(Optional<Need> need) { return (byte) need.map(n -> n.ordinal() + 1).orElse(0).intValue(); }

    /** effects: the need a wire code names; empty for 0 or a code no need has. */
    public static Optional<Need> of(byte code) {
        int i = code - 1;
        return i >= 0 && i < values().length ? Optional.of(values()[i]) : Optional.empty();
    }
}
