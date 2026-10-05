/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Wardrobe.Access;
import com.chunkworks.serfdom.domain.Wardrobe.Piece;
import com.chunkworks.serfdom.domain.Wardrobe.Region;
import com.chunkworks.serfdom.domain.Wardrobe.Route;
import com.chunkworks.serfdom.domain.Wardrobe.Slot;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>access: the villager alive or not × the viewer its owner or not × the distance 0, at the
 * reach, just past it, far; a negative or NaN distance refused;</li>
 * <li>a piece: nothing, worn in one slot, in two, in none; bound, vanishing; nothing with a slot or
 * a curse refused;</li>
 * <li>a slot taking a piece: each slot × nothing, its own piece, another slot's, one that fits two,
 * an unwearable stack, a bound piece;</li>
 * <li>taking off: nothing, a plain piece, a bound one × creative or not;</li>
 * <li>a shift-click: from the wearing slots (plain, bound in survival, bound in creative, nothing);
 * from the inventory or the hotbar (its slot empty, its slot worn, a piece for two slots with the
 * first worn, an unwearable stack, nothing).</li>
 * </ul> */
final class WardrobeTest {
    private static final Piece HELMET = Piece.wornIn(Slot.HEAD), BOOTS = Piece.wornIn(Slot.FEET);
    private static final Piece BOUND = new Piece(false, Set.of(Slot.HEAD), true, false);
    private static final Piece SKIRT = new Piece(false, EnumSet.of(Slot.CHEST, Slot.LEGS), false, false);

    @Test void onlyTheOwnerOfALivingVillagerWithinReachUsesItsSlots() {
        assertEquals(Access.OK, Wardrobe.access(true, true, 0));
        assertEquals(Access.OK, Wardrobe.access(true, true, Wardrobe.REACH), "at the reach");
        assertEquals(Access.TOO_FAR, Wardrobe.access(true, true, Wardrobe.REACH + 0.01));
        assertEquals(Access.TOO_FAR, Wardrobe.access(true, true, 100));
        assertEquals(Access.NOT_YOURS, Wardrobe.access(true, false, 0));
        assertEquals(Access.GONE, Wardrobe.access(false, true, 0));
    }

    @Test void theReasonsComeInOrderGoneThenNotYoursThenTooFar() {
        assertEquals(Access.GONE, Wardrobe.access(false, false, 100));
        assertEquals(Access.GONE, Wardrobe.access(false, true, 100));
        assertEquals(Access.NOT_YOURS, Wardrobe.access(true, false, 100));
    }

    @Test void aDistanceIsNeverNegativeOrUndefined() {
        assertThrows(IllegalArgumentException.class, () -> Wardrobe.access(true, true, -0.5));
        assertThrows(IllegalArgumentException.class, () -> Wardrobe.access(true, true, Double.NaN));
    }

    @Test void nothingFitsNoSlotAndCarriesNoCurse() {
        assertThrows(IllegalArgumentException.class, () -> new Piece(true, Set.of(Slot.HEAD), false, false));
        assertThrows(IllegalArgumentException.class, () -> new Piece(true, Set.of(), true, false));
        assertThrows(IllegalArgumentException.class, () -> new Piece(true, Set.of(), false, true));
        var slots = EnumSet.of(Slot.LEGS);
        var piece = new Piece(false, slots, false, false);
        slots.add(Slot.HEAD);
        assertEquals(Set.of(Slot.LEGS), piece.fits(), "the piece keeps its own copy");
    }

    @Test void aSlotTakesOnlyAPieceWornThere() {
        for (var slot : Slot.values()) {
            assertFalse(Wardrobe.takes(slot, Piece.NOTHING));
            assertFalse(Wardrobe.takes(slot, Piece.UNWEARABLE));
            assertTrue(Wardrobe.takes(slot, Piece.wornIn(slot)));
            for (var other : Slot.values()) if (other != slot) assertFalse(Wardrobe.takes(slot, Piece.wornIn(other)), other + " in " + slot);
        }
        assertTrue(Wardrobe.takes(Slot.CHEST, SKIRT));
        assertTrue(Wardrobe.takes(Slot.LEGS, SKIRT));
        assertFalse(Wardrobe.takes(Slot.HEAD, SKIRT));
        assertTrue(Wardrobe.takes(Slot.HEAD, BOUND), "binding stops a piece coming off, not going on");
    }

    @Test void bindingKeepsAPieceOnOutsideCreative() {
        for (boolean creative : new boolean[]{false, true}) {
            assertFalse(Wardrobe.mayTakeOff(Piece.NOTHING, creative));
            assertTrue(Wardrobe.mayTakeOff(HELMET, creative));
        }
        assertFalse(Wardrobe.mayTakeOff(BOUND, false));
        assertTrue(Wardrobe.mayTakeOff(BOUND, true));
    }

    @Test void aWornPieceShiftClicksToTheInventoryThenTheHotbarUnlessItIsBound() {
        var out = new Route(Optional.empty(), List.of(Region.INVENTORY, Region.HOTBAR));
        assertEquals(out, Wardrobe.route(Region.WEARING, HELMET, Set.of(Slot.HEAD), false));
        assertEquals(Route.NOWHERE, Wardrobe.route(Region.WEARING, BOUND, Set.of(Slot.HEAD), false));
        assertEquals(out, Wardrobe.route(Region.WEARING, BOUND, Set.of(Slot.HEAD), true));
        assertEquals(Route.NOWHERE, Wardrobe.route(Region.WEARING, Piece.NOTHING, Set.of(), false));
    }

    @Test void aCarriedPieceShiftClicksIntoItsEmptySlot() {
        for (var from : new Region[]{Region.INVENTORY, Region.HOTBAR}) {
            assertEquals(Optional.of(Slot.HEAD), Wardrobe.route(from, HELMET, Set.of(), false).wear());
            assertEquals(Optional.of(Slot.FEET), Wardrobe.route(from, BOOTS, Set.of(Slot.HEAD, Slot.CHEST, Slot.LEGS), false).wear());
            assertEquals(Optional.of(Slot.LEGS), Wardrobe.route(from, SKIRT, Set.of(Slot.CHEST), false).wear(), "the first empty slot it fits");
            assertEquals(Optional.of(Slot.CHEST), Wardrobe.route(from, SKIRT, Set.of(), false).wear(), "top to bottom");
            assertEquals(Route.NOWHERE, Wardrobe.route(from, Piece.NOTHING, Set.of(), false));
        }
    }

    @Test void whatHasNoEmptySlotMovesBetweenTheInventoryAndTheHotbar() {
        assertEquals(List.of(Region.HOTBAR), Wardrobe.route(Region.INVENTORY, HELMET, Set.of(Slot.HEAD), false).regions());
        assertEquals(List.of(Region.INVENTORY), Wardrobe.route(Region.HOTBAR, HELMET, Set.of(Slot.HEAD), false).regions());
        assertEquals(List.of(Region.HOTBAR), Wardrobe.route(Region.INVENTORY, Piece.UNWEARABLE, Set.of(), false).regions());
        assertEquals(List.of(Region.INVENTORY), Wardrobe.route(Region.HOTBAR, Piece.UNWEARABLE, Set.of(), false).regions());
        assertEquals(List.of(Region.HOTBAR), Wardrobe.route(Region.INVENTORY, SKIRT, Set.of(Slot.CHEST, Slot.LEGS), false).regions());
    }

    @Test void aRouteIsIntoASlotOrThroughRegionsNotBoth() {
        assertThrows(IllegalArgumentException.class, () -> new Route(Optional.of(Slot.HEAD), List.of(Region.INVENTORY)));
    }
}
