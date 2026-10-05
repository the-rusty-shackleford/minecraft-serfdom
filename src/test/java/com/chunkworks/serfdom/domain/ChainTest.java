/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Chain.Act;
import com.chunkworks.serfdom.domain.Chain.Facts;
import com.chunkworks.serfdom.domain.Chain.Hand;
import com.chunkworks.serfdom.domain.Chain.Holder;
import org.junit.jupiter.api.Test;
import static com.chunkworks.serfdom.domain.Chain.Act.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the villager (free; owned and loose; cuffed and held by nobody, the actor or someone
 * else) × the actor (owner or not) × the hand (empty, the chain, anything else) × sneaking or not;
 * and facts that break the rep invariant. Every valid combination is checked against the table in
 * Chain's doc. */
final class ChainTest {
    private static Act act(boolean owned, boolean cuffed, Holder holder, boolean owns, Hand hand, boolean sneaking) {
        return Chain.gesture(new Facts(owned, cuffed, holder, owns, hand, sneaking));
    }

    @Test void theChainOnAFreeVillagerIsTheCaptureAndAnythingElseIsVanillas() {
        for (boolean sneak : new boolean[]{false, true}) {
            assertEquals(CAPTURE, act(false, false, Holder.NOBODY, false, Hand.CHAIN, sneak));
            assertEquals(PASS, act(false, false, Holder.NOBODY, false, Hand.EMPTY, sneak), "the hire offer is a sneaking empty hand");
            assertEquals(PASS, act(false, false, Holder.NOBODY, false, Hand.OTHER, sneak));
        }
    }

    @Test void theChainCuffsALooseOwnedVillagerForAnyone() {
        for (boolean owns : new boolean[]{false, true}) for (boolean sneak : new boolean[]{false, true})
            assertEquals(CUFF, act(true, false, Holder.NOBODY, owns, Hand.CHAIN, sneak));
    }

    @Test void aLooseOwnedVillagerTradesAndShowsItsScreenAsInPhaseOne() {
        assertEquals(PASS, act(true, false, Holder.NOBODY, true, Hand.EMPTY, false), "a plain use trades");
        assertEquals(PASS, act(true, false, Holder.NOBODY, false, Hand.OTHER, false));
        assertEquals(SCREEN, act(true, false, Holder.NOBODY, true, Hand.EMPTY, true));
        assertEquals(SCREEN, act(true, false, Holder.NOBODY, true, Hand.OTHER, true));
        assertEquals(WHOSE, act(true, false, Holder.NOBODY, false, Hand.EMPTY, true));
    }

    @Test void aCuffedVillagerNobodyHoldsIsTakenByAnyoneWithAnEmptyHandOrTheChain() {
        for (boolean owns : new boolean[]{false, true}) {
            assertEquals(TAKE, act(true, true, Holder.NOBODY, owns, Hand.EMPTY, false));
            assertEquals(TAKE, act(true, true, Holder.NOBODY, owns, Hand.CHAIN, false));
            assertEquals(TAKE, act(true, true, Holder.NOBODY, owns, Hand.CHAIN, true), "the chain ignores sneaking");
        }
    }

    @Test void onlyTheOwnerUncuffsTheOneTheyHoldAndAnyoneElseLetsGo() {
        for (var hand : new Hand[]{Hand.EMPTY, Hand.CHAIN}) {
            assertEquals(UNCUFF, act(true, true, Holder.ACTOR, true, hand, false));
            assertEquals(LET_GO, act(true, true, Holder.ACTOR, false, hand, false));
        }
    }

    @Test void someoneElsesHoldIsSaidAndNotBroken() {
        for (boolean owns : new boolean[]{false, true}) for (var hand : new Hand[]{Hand.EMPTY, Hand.CHAIN})
            assertEquals(HELD, act(true, true, Holder.SOMEONE_ELSE, owns, hand, false));
    }

    @Test void sneakingWithoutTheChainOnACuffedVillagerStillShowsTheScreenOrTheOwner() {
        for (var holder : Holder.values()) {
            assertEquals(SCREEN, act(true, true, holder, true, Hand.EMPTY, true));
            assertEquals(WHOSE, act(true, true, holder, false, Hand.OTHER, true));
        }
    }

    @Test void aVillagerInChainsDoesNotTrade() {
        for (var holder : Holder.values()) for (boolean owns : new boolean[]{false, true})
            assertEquals(REFUSE, act(true, true, holder, owns, Hand.OTHER, false));
    }

    @Test void everyValidCombinationHasOneAct() {
        int combos = 0;
        for (boolean owned : new boolean[]{false, true}) for (boolean cuffed : new boolean[]{false, true})
            for (var holder : Holder.values()) for (boolean owns : new boolean[]{false, true})
                for (var hand : Hand.values()) for (boolean sneak : new boolean[]{false, true}) {
                    if ((!owned && (cuffed || owns)) || (!cuffed && holder != Holder.NOBODY)) continue;
                    assertNotNull(act(owned, cuffed, holder, owns, hand, sneak));
                    combos++;
                }
        assertEquals(6 + 12 + 36, combos, "free 6, loose owned 12, cuffed 36");
    }

    @Test void factsThatCannotBeAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Facts(false, true, Holder.NOBODY, false, Hand.EMPTY, false), "a free villager in chains");
        assertThrows(IllegalArgumentException.class, () -> new Facts(false, false, Holder.NOBODY, true, Hand.EMPTY, false), "a free villager the actor owns");
        assertThrows(IllegalArgumentException.class, () -> new Facts(true, false, Holder.ACTOR, true, Hand.EMPTY, false), "held without the chain");
        assertThrows(NullPointerException.class, () -> new Facts(true, true, null, true, Hand.EMPTY, false));
    }
}
