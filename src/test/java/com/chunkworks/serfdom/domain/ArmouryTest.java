/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Armoury.Choice;
import com.chunkworks.serfdom.domain.Armoury.Found;
import com.chunkworks.serfdom.domain.Armoury.Kind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>the choice: nothing found; melee only (the best, a tie); ranged only (with ammo, without); both;
 * a launcher with ammo (never), the best ranged over a better launcher; the best by damage a second
 * among a gun, a crossbow and a bow; a tie to the lower id;</li>
 * <li>damage a second: melee (a sword's, an axe's; refused below 0, a speed of 0); a bow's and a
 * crossbow's full draw; a gun's over its magazine (a pistol's, a shotgun's several projectiles, no
 * reload time; refused out of range);</li>
 * <li>ammo carried: less than the most, the most, more; refused below 0;</li>
 * <li>a found weapon that breaks its invariant.</li>
 * </ul> */
final class ArmouryTest {
    private static Found f(int id, Kind kind, double dps, int ammo) { return new Found(id, kind, dps, ammo); }

    @Test void itTakesTheBestRangedWeaponItCanFireAndTheBestMeleeWeapon() {
        assertEquals(Choice.NOTHING, Armoury.choose(List.of()));
        assertTrue(Armoury.choose(List.of()).empty());
        assertEquals(new Choice(Optional.empty(), Optional.of(2)), Armoury.choose(List.of(f(1, Kind.MELEE, 6.4, 0), f(2, Kind.MELEE, 9.6, 0))));
        assertEquals(new Choice(Optional.empty(), Optional.of(1)), Armoury.choose(List.of(f(3, Kind.MELEE, 6.4, 0), f(1, Kind.MELEE, 6.4, 0))), "a tie to the lower id");
        assertEquals(new Choice(Optional.of(4), Optional.empty()), Armoury.choose(List.of(f(4, Kind.BOW, 6, 10))));
        assertEquals(Choice.NOTHING, Armoury.choose(List.of(f(4, Kind.BOW, 6, 0))), "a bow without arrows is nothing");
        assertEquals(new Choice(Optional.of(4), Optional.of(1)), Armoury.choose(List.of(f(1, Kind.MELEE, 6.4, 0), f(4, Kind.BOW, 6, 10))));
        assertEquals(new Choice(Optional.of(4), Optional.of(1)), Armoury.choose(List.of(f(1, Kind.MELEE, 30, 0), f(4, Kind.BOW, 6, 10))),
                "ranged first, whatever the sword does");
    }

    @Test void neverALauncher() {
        assertEquals(Choice.NOTHING, Armoury.choose(List.of(f(1, Kind.LAUNCHER, 40, 5))));
        assertEquals(new Choice(Optional.of(2), Optional.empty()), Armoury.choose(List.of(f(1, Kind.LAUNCHER, 40, 5), f(2, Kind.BOW, 6, 5))));
        assertFalse(Kind.MELEE.ranged());
        assertTrue(Kind.LAUNCHER.ranged());
    }

    @Test void amongRangedWeaponsTheMostDamageASecond() {
        var found = List.of(f(1, Kind.BOW, Armoury.bow(false), 20), f(2, Kind.CROSSBOW, Armoury.bow(true), 20),
                f(3, Kind.GUN, Armoury.gun(6, 1, 5, 15, 2), 30), f(4, Kind.GUN, Armoury.gun(12, 1, 6, 30, 1), 0));
        assertEquals(Optional.of(3), Armoury.choose(found).ranged(), "the pistol; the rifle has no rounds");
        assertEquals(Optional.of(1), Armoury.choose(List.of(f(2, Kind.CROSSBOW, Armoury.bow(true), 20), f(1, Kind.BOW, Armoury.bow(false), 20))).ranged(),
                "a bow's 6 a second beats a crossbow's 5.6");
    }

    @Test void damageASecond() {
        assertEquals(6.4, Armoury.melee(4, 1.6), 1e-12, "a stone sword");
        assertEquals(9.0, Armoury.melee(9, 1.0), 1e-12, "an iron axe");
        assertEquals(0.0, Armoury.melee(0, 1));
        assertThrows(IllegalArgumentException.class, () -> Armoury.melee(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> Armoury.melee(1, 0));
        assertEquals(6, Armoury.arrow(Armoury.BOW_SPEED), "a full draw: 2 times 3");
        assertEquals(7, Armoury.arrow(Armoury.CROSSBOW_SPEED), "2 times 3.15, rounded up");
        assertEquals(6.0, Armoury.bow(false), 1e-12);
        assertEquals(5.6, Armoury.bow(true), 1e-12);
        assertEquals(6 * 15 * 20.0 / (15 * 5 + 15 * 2), Armoury.gun(6, 1, 5, 15, 2), 1e-12, "a pistol: 15 rounds of 6 over 75 ticks of firing and 30 of reloading");
        assertEquals(4 * 8 * 6 * 20.0 / (6 * 15 + 6 * 8), Armoury.gun(4, 8, 15, 6, 8), 1e-12, "a shotgun: eight pellets a round");
        assertEquals(6 * 20.0 / 5, Armoury.gun(6, 1, 5, 15, 0), 1e-12, "no reload: its rate alone");
        assertThrows(IllegalArgumentException.class, () -> Armoury.gun(6, 0, 5, 15, 2));
        assertThrows(IllegalArgumentException.class, () -> Armoury.gun(6, 1, 0, 15, 2));
        assertThrows(IllegalArgumentException.class, () -> Armoury.gun(6, 1, 5, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> Armoury.gun(6, 1, 5, 15, -1));
        assertThrows(IllegalArgumentException.class, () -> Armoury.gun(-6, 1, 5, 15, 2));
    }

    @Test void itCarriesOutAsMuchAmmoAsThereIsUpToTheMost() {
        assertEquals(10, Armoury.ammo(10, 64));
        assertEquals(64, Armoury.ammo(64, 64));
        assertEquals(64, Armoury.ammo(200, 64));
        assertEquals(0, Armoury.ammo(0, 64));
        assertThrows(IllegalArgumentException.class, () -> Armoury.ammo(-1, 64));
        assertThrows(IllegalArgumentException.class, () -> Armoury.ammo(1, -1));
    }

    @Test void aFoundWeaponMustBeWhole() {
        assertThrows(IllegalArgumentException.class, () -> f(1, Kind.BOW, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> f(1, Kind.BOW, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> f(1, Kind.BOW, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> f(1, Kind.BOW, 1, -1));
        assertThrows(NullPointerException.class, () -> f(1, null, 1, 1));
    }
}
