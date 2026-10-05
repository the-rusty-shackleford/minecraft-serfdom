/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** What a worker's worn gear does as the worker stops being its owner's (D-0004, Rusty's call):
 *
 * <ul>
 * <li>it dies, or turns into a zombie villager: every piece drops with the rest of its things, but
 * a piece with Curse of Vanishing vanishes, as from any death;</li>
 * <li>its owner sets it free: it takes every piece off and drops it where it stands, with its tool
 * (Vanishing is a curse of death only);</li>
 * <li>it escapes, or the law frees it: it leaves wearing them.</li>
 * </ul>
 *
 * A piece put on through the Worker Screen is marked to drop whole whenever the villager dies,
 * whoever it belongs to by then, so a villager that leaves wearing gear gives it up when caught
 * again or killed. */
public final class Parting {
    /** The drop chance a piece put on through the screen is given: above 1, which is vanilla's mark
     * for a piece that always drops, undamaged (its dispenser gives armour the same). */
    public static final float GUARANTEED = 2.0F;

    private Parting() {}

    /** How a worker stops being its owner's. */
    public enum Way { DIES, CONVERTS, SET_FREE, ESCAPES, FREED_BY_LAW }

    /** What one worn piece does. */
    public enum Fate { DROPS, VANISHES, STAYS_ON }

    /** effects: what a worn piece does as the worker goes {@code way}; {@code vanishing} is whether
     * the piece carries Curse of Vanishing. */
    public static Fate fate(Way way, boolean vanishing) {
        return switch (Objects.requireNonNull(way)) {
            case DIES, CONVERTS -> vanishing ? Fate.VANISHES : Fate.DROPS;
            case SET_FREE -> Fate.DROPS;
            case ESCAPES, FREED_BY_LAW -> Fate.STAYS_ON;
        };
    }
}
