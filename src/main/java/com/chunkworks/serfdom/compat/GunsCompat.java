/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.compat;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Guns in a worker's hands (D-0007), through the Ranged Weapons protocol (its mod id
 * {@code rangedweapons}, which Ranged Weapons Mod nests): what a gun is and does, its loose rounds,
 * its reload, and its shot. Without the protocol nothing is a gun. Every call but {@link #gun} asks
 * for a stack {@link #gun} said was one. The protocol's classes are named only in {@link Inner}, so
 * nothing loads them when it is absent.
 *
 * <p>A worker reloads only loose rounds the gun accepts ({@code WeaponProfile.acceptsAmmo}): Ranged
 * Weapons Mod's magazines are its own, not the protocol's ammo stores, so they stay in the chest. */
public final class GunsCompat {
    private GunsCompat() {}

    /** A gun's facts: whether it is a launcher (never taken), its damage a second's numbers, its
     * capacity, a round's reload and its trigger's ticks, and the range it shoots from. */
    public record Gun(boolean launcher, float damage, int projectiles, int fireTicks, int capacity, int reloadPerRound, double range) {}

    private static final boolean PRESENT = ModList.get() != null && ModList.get().isLoaded("rangedweapons");

    /** effects: the gun {@code stack} is, if the protocol knows it as one. */
    public static Optional<Gun> gun(ItemStack stack) { return PRESENT && !stack.isEmpty() ? Inner.gun(stack) : Optional.empty(); }

    /** effects: true iff {@code round} loads into the gun {@code gun}. */
    public static boolean accepts(ItemStack gun, ItemStack round) { return PRESENT && Inner.accepts(gun, round); }

    /** effects: the rounds loaded in the gun. */
    public static int rounds(ItemStack gun) { return PRESENT ? Inner.rounds(gun) : 0; }

    /** requires: rounds(gun) + n &le; its capacity; {@code round} accepted. effects: {@code n} more
     * rounds of {@code round} in it, written on the stack itself. */
    public static void load(ItemStack gun, int n, ItemStack round) { if (PRESENT) Inner.load(gun, n, round); }

    /** requires: rounds(gun) &ge; 1; the gun is in the shooter's main hand. effects: one trigger pull at
     * the target's middle, from the shooter's eye: the shot, the round spent, a point of wear, and the
     * report heard about it. */
    public static void fire(ServerLevel level, Villager shooter, ItemStack gun, LivingEntity target) { if (PRESENT) Inner.fire(level, shooter, gun, target); }

    private static final class Inner {
        static Optional<Gun> gun(ItemStack stack) {
            var weapon = com.nfx.rangedweapons.api.RangedWeapons.resolve(stack);
            if (weapon == null) return Optional.empty();
            var s = weapon.stats(stack);
            return Optional.of(new Gun(weapon.profile().weaponClass() == com.nfx.rangedweapons.api.WeaponClass.LAUNCHER, s.damage(), s.projectilesPerShot(),
                    s.fireRateTicks(), s.capacity(), s.reloadTicksPerRound(), s.engagementRange()));
        }

        static boolean accepts(ItemStack gun, ItemStack round) {
            var weapon = com.nfx.rangedweapons.api.RangedWeapons.resolve(gun);
            return weapon != null && weapon.profile().acceptsAmmo(round);
        }

        static int rounds(ItemStack gun) {
            var weapon = com.nfx.rangedweapons.api.RangedWeapons.resolve(gun);
            return weapon == null ? 0 : weapon.rounds(gun);
        }

        static void load(ItemStack gun, int n, ItemStack round) {
            var weapon = com.nfx.rangedweapons.api.RangedWeapons.resolve(gun);
            if (weapon == null || n <= 0) return;
            weapon.load(gun, weapon.rounds(gun) + n, round.getItem());
        }

        static void fire(ServerLevel level, Villager shooter, ItemStack gun, LivingEntity target) {
            var weapon = com.nfx.rangedweapons.api.RangedWeapons.resolve(gun);
            if (weapon == null || weapon.isEmpty(gun)) return;
            var muzzle = shooter.getEyePosition();
            // Eye to the middle of the target, an explicit vector: a mob's look lags its head.
            var aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(muzzle);
            if (aim.lengthSqr() < 1e-6) return;
            var shot = com.nfx.rangedweapons.api.Shot.of(weapon.stats(gun), muzzle, aim);
            weapon.fire(level, shooter, gun, shot);
            weapon.consumeRound(gun);
            gun.hurtAndBreak(1, shooter, EquipmentSlot.MAINHAND);
            com.nfx.rangedweapons.api.ShotReport.play(level, shooter, weapon.profile(), shot.origin(), SoundSource.NEUTRAL, 0.95F + shooter.getRandom().nextFloat() * 0.1F);
        }
    }

    /** effects: the middle of {@code e}, where a defender aims. */
    public static Vec3 middle(LivingEntity e) { return e.position().add(0, e.getBbHeight() * 0.5, 0); }
}
