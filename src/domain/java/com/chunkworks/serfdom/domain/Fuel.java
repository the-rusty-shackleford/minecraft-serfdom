/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** How much a worker burns (D-0002): only as much fuel as a load needs, after what is burning and
 * what already sits in the fuel slot; and how charcoal is made when fuel runs low. */
public final class Fuel {
    /** Fewer fuel items than this in the chests, and logs are made into charcoal. */
    public static final int LOW = 8;
    /** The most logs put in for charcoal at once. */
    public static final int CHARCOAL_LOAD = 8;
    /** With no fuel at all, this many logs go in for charcoal, burning logs under them. */
    public static final int BOOTSTRAP = 3;
    private Fuel() {}

    /** requires: items, burnLeft, inSlot, room &ge; 0; cookTicks, burnTicks &ge; 1.
     * effects: the fuel to add so a station that cooks one item in {@code cookTicks} cooks
     * {@code items} more: the ticks they take, less the {@code burnLeft} ticks still burning and
     * the {@code inSlot} fuel already waiting at {@code burnTicks} each, in whole fuel of
     * {@code burnTicks}, rounded up and never more than {@code room}. */
    public static int units(int items, int cookTicks, int burnTicks, int burnLeft, int inSlot, int room) {
        if (items < 0 || burnLeft < 0 || inSlot < 0 || room < 0 || cookTicks < 1 || burnTicks < 1)
            throw new IllegalArgumentException(items + ", " + cookTicks + ", " + burnTicks + ", " + burnLeft + ", " + inSlot + ", " + room);
        long need = (long) items * cookTicks - burnLeft - (long) inSlot * burnTicks;
        if (need <= 0) return 0;
        return (int) Math.min(room, (need + burnTicks - 1) / burnTicks);
    }

    /** requires: logs &ge; 0, fuel &ge; 0. effects: how many logs to load for charcoal: with fuel
     * stored, up to {@link #CHARCOAL_LOAD}; with none, {@link #BOOTSTRAP} (burning logs under
     * them), or 0 when the logs cannot cover both. */
    public static int charcoalLoad(int logs, int fuel, int bootstrapFuel) {
        if (logs < 0 || fuel < 0 || bootstrapFuel < 0) throw new IllegalArgumentException(logs + ", " + fuel + ", " + bootstrapFuel);
        if (fuel > 0) return Math.min(CHARCOAL_LOAD, logs);
        return logs >= BOOTSTRAP + bootstrapFuel ? BOOTSTRAP : 0;
    }
}
