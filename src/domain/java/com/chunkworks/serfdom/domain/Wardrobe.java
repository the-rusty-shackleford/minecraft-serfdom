/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The Worker Screen's four wearing slots (D-0004): who may use them, what each takes, what may
 * come off, and where a shift-click sends a stack. A slot is one of the villager's own armour
 * slots, so what it wears protects as vanilla's armour does.
 *
 * <ul>
 * <li>Only the owner uses them, while the villager is alive, still theirs and within
 * {@link #REACH} blocks. Asked on every click and every tick, so a screen left open when the
 * villager dies, goes free or is led off is shut and nothing can be taken through it.</li>
 * <li>A slot takes one piece, one that may be worn there (the item's own say: armour, heads, a
 * carved pumpkin, the elytra, clothes).</li>
 * <li>A piece with Curse of Binding stays on, unless the player is in creative, as on a player.</li>
 * </ul> */
public final class Wardrobe {
    /** How near the owner must stand, as for the rest of the Worker Screen. */
    public static final double REACH = 8.0;

    private Wardrobe() {}

    /** The four slots, top to bottom as the screen shows them. */
    public enum Slot { HEAD, CHEST, LEGS, FEET }

    /** Whether a player may use a villager's slots now, or the first reason not. */
    public enum Access { OK, GONE, NOT_YOURS, TOO_FAR }

    /** requires: distance &gt;= 0.
     * effects: OK iff the villager is alive, the viewer owns it and stands within {@link #REACH};
     * otherwise the first that fails of gone, not yours, too far. */
    public static Access access(boolean alive, boolean viewerOwns, double distance) {
        if (!(distance >= 0)) throw new IllegalArgumentException("a distance is not negative: " + distance);
        if (!alive) return Access.GONE;
        if (!viewerOwns) return Access.NOT_YOURS;
        return distance <= REACH ? Access.OK : Access.TOO_FAR;
    }

    /** A stack as the wardrobe sees it: the slots it may be worn in, and its two curses.
     * <p>Rep invariant: nothing fits no slot and carries no curse. */
    public record Piece(boolean empty, Set<Slot> fits, boolean bound, boolean vanishing) {
        /** An empty stack. */
        public static final Piece NOTHING = new Piece(true, Set.of(), false, false);
        public Piece {
            fits = fits.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(fits));
            if (empty && (!fits.isEmpty() || bound || vanishing)) throw new IllegalArgumentException("nothing fits no slot and carries no curse");
        }
        /** effects: a piece worn only in {@code slot}, without curses. */
        public static Piece wornIn(Slot slot) { return new Piece(false, Set.of(slot), false, false); }
        /** effects: a stack worn nowhere. */
        public static final Piece UNWEARABLE = new Piece(false, Set.of(), false, false);
    }

    /** effects: whether {@code slot} takes the piece: it is something, and may be worn there. */
    public static boolean takes(Slot slot, Piece piece) {
        Objects.requireNonNull(slot);
        return !piece.empty() && piece.fits().contains(slot);
    }

    /** effects: whether a player may take the worn piece off: not one with Curse of Binding, unless
     * in creative. Nothing cannot be taken. */
    public static boolean mayTakeOff(Piece worn, boolean creative) {
        return !worn.empty() && (creative || !worn.bound());
    }

    /** The screen's three regions of slots. */
    public enum Region { WEARING, INVENTORY, HOTBAR }

    /** Where a shift-click sends a stack: into a wearing slot, or into regions tried in order, or
     * nowhere.
     * <p>Rep invariant: not both a slot and regions. */
    public record Route(Optional<Slot> wear, List<Region> regions) {
        public static final Route NOWHERE = new Route(Optional.empty(), List.of());
        public Route {
            regions = List.copyOf(regions);
            if (wear.isPresent() && !regions.isEmpty()) throw new IllegalArgumentException("into a slot or into regions, not both");
        }
        static Route into(Slot slot) { return new Route(Optional.of(slot), List.of()); }
        static Route through(Region... regions) { return new Route(Optional.empty(), List.of(regions)); }
    }

    /** requires: {@code worn} names the slots that hold something now.
     * effects: where a shift-click on the piece in {@code from} sends it, as the player's own
     * inventory does: a worn piece to the inventory, then the hotbar, when it may come off;
     * otherwise the first slot, top to bottom, that takes it and is empty; failing that, from the
     * inventory to the hotbar and from the hotbar to the inventory. Nothing goes nowhere. */
    public static Route route(Region from, Piece piece, Set<Slot> worn, boolean creative) {
        Objects.requireNonNull(from);
        if (piece.empty()) return Route.NOWHERE;
        if (from == Region.WEARING) return mayTakeOff(piece, creative) ? Route.through(Region.INVENTORY, Region.HOTBAR) : Route.NOWHERE;
        for (var slot : Slot.values()) if (takes(slot, piece) && !worn.contains(slot)) return Route.into(slot);
        return Route.through(from == Region.INVENTORY ? Region.HOTBAR : Region.INVENTORY);
    }
}
