/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Defence.Facts;
import com.chunkworks.serfdom.domain.Defence.Hand;
import com.chunkworks.serfdom.domain.Defence.Step;
import com.chunkworks.serfdom.domain.Defence.Who;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>who: a hired or captive adult; a free villager, a child, one in chains, one on its way home;</li>
 * <li>next, a raid on and mustering: unarmed and untried (arm); tried and empty-handed (hide); a ranged
 * weapon that can fire, or only a melee one, with a raider in reach (fight) or none (stand); a ranged
 * weapon out of ammo with a melee one (fight on) or without (hide);</li>
 * <li>next, no raid or not mustering: carrying what it took (put back) or nothing (none);</li>
 * <li>the hand: ranged ready, with or without melee; melee only; nothing;</li>
 * <li>reach: in sight and home's reach; past sight; past home's reach; at the edges; refused below 0;</li>
 * <li>a reload: empty with plenty, part full, full, nothing carried, less than room; its ticks; refused
 * out of range;</li>
 * <li>a melee cooldown: a sword's, an axe's, very fast (at least a tick); refused at 0.</li>
 * </ul> */
final class DefenceTest {
    @Test void ownedGrownVillagersFreeToMoveMuster() {
        assertTrue(Defence.musters(new Who(true, true, false, false)));
        assertFalse(Defence.musters(new Who(false, true, false, false)), "free");
        assertFalse(Defence.musters(new Who(true, false, false, false)), "a child");
        assertFalse(Defence.musters(new Who(true, true, true, false)), "in chains");
        assertFalse(Defence.musters(new Who(true, true, false, true)), "on its way home");
    }

    @Test void inARaidItArmsThenFightsOrStandsByOrHides() {
        assertEquals(Step.ARM, Defence.next(new Facts(true, true, false, false, false, false, true)), "unarmed, untried: arm, raider or no");
        assertEquals(Step.HIDE, Defence.next(new Facts(true, true, false, true, false, false, true)), "tried and found nothing");
        assertEquals(Step.FIGHT, Defence.next(new Facts(true, true, true, true, true, false, true)), "a bow with arrows, a raider in reach");
        assertEquals(Step.STAND, Defence.next(new Facts(true, true, true, true, true, false, false)), "and none in reach");
        assertEquals(Step.FIGHT, Defence.next(new Facts(true, true, true, true, false, true, true)), "a sword only");
        assertEquals(Step.FIGHT, Defence.next(new Facts(true, true, true, true, false, true, true)), "out of arrows, its sword");
        assertEquals(Step.HIDE, Defence.next(new Facts(true, true, true, true, false, false, true)), "out of arrows, no sword: it hides, still carrying the bow");
        assertEquals(Step.STAND, Defence.next(new Facts(true, true, true, false, true, false, false)), "armed by another way, untried");
    }

    @Test void withNoRaidWhatItTookGoesBack() {
        assertEquals(Step.PUT_BACK, Defence.next(new Facts(false, true, true, true, true, true, true)));
        assertEquals(Step.NONE, Defence.next(new Facts(false, true, false, true, false, false, false)));
        assertEquals(Step.PUT_BACK, Defence.next(new Facts(true, false, true, true, true, true, true)), "no longer mustering (in chains, say): back it goes");
        assertEquals(Step.NONE, Defence.next(new Facts(true, false, false, false, false, false, true)), "a free villager in a raid: nothing of ours");
    }

    @Test void itHoldsItsRangedWeaponWhileItCanFire() {
        assertEquals(Hand.RANGED, Defence.hand(true, true));
        assertEquals(Hand.RANGED, Defence.hand(true, false));
        assertEquals(Hand.MELEE, Defence.hand(false, true));
        assertEquals(Hand.NONE, Defence.hand(false, false));
    }

    @Test void aRaiderIsInReachWithinSightAndHomesReach() {
        assertTrue(Defence.inReach(10, 20, 32, 48));
        assertTrue(Defence.inReach(32, 48, 32, 48), "at both edges");
        assertFalse(Defence.inReach(32.01, 20, 32, 48), "out of sight");
        assertFalse(Defence.inReach(10, 48.01, 32, 48), "too far from home");
        assertThrows(IllegalArgumentException.class, () -> Defence.inReach(-1, 0, 32, 48));
        assertThrows(IllegalArgumentException.class, () -> Defence.inReach(0, Double.NaN, 32, 48));
    }

    @Test void aGunReloadsLooseRoundsFromWhatItCarries() {
        assertEquals(new Defence.Reload(15, 30), Defence.reload(15, 0, 40, 2));
        assertEquals(new Defence.Reload(5, 10), Defence.reload(15, 10, 40, 2));
        assertEquals(new Defence.Reload(0, 0), Defence.reload(15, 15, 40, 2), "full");
        assertEquals(new Defence.Reload(0, 0), Defence.reload(15, 0, 0, 2), "nothing to load");
        assertEquals(new Defence.Reload(3, 24), Defence.reload(6, 0, 3, 8), "less than room");
        assertThrows(IllegalArgumentException.class, () -> Defence.reload(0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Defence.reload(5, 6, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Defence.reload(5, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Defence.reload(5, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> Defence.reload(5, 0, 1, -1));
    }

    @Test void aBlowsCooldownIsTheWeaponsSpeed() {
        assertEquals(13, Defence.cooldown(1.6), "a sword: 12.5, up");
        assertEquals(20, Defence.cooldown(1.0), "a diamond axe: a second");
        assertEquals(1, Defence.cooldown(100), "at least a tick");
        assertThrows(IllegalArgumentException.class, () -> Defence.cooldown(0));
    }
}
