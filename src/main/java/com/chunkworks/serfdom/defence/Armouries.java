/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.defence;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.compat.GunsCompat;
import com.chunkworks.serfdom.domain.Armoury;
import com.chunkworks.serfdom.job.Kitchen;
import com.chunkworks.serfdom.job.Storage;
import com.chunkworks.serfdom.post.Posts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** A worker's armoury in a raid (D-0007): the chests it arms from (its home chest, then its post's
 * storage), what in them is a weapon and how good ({@link Armoury}), what feeds each, and taking and
 * putting back. Melee weapons are the item tag {@code #serfdom:defence/melee} (swords, axes, maces,
 * tridents); a bow, a crossbow, or any gun the Ranged Weapons protocol knows is ranged. */
public final class Armouries {
    public static final TagKey<Item> MELEE = TagKey.create(Registries.ITEM, Serfdom.id("defence/melee"));
    private Armouries() {}

    /** A weapon in a chest: the chest, the slot, a copy of the stack, its kind and damage a second. */
    public record Spot(BlockPos chest, int slot, ItemStack stack, Armoury.Kind kind, double dps) {}

    /** What it means to take: the ranged weapon and the melee one, each where it lies. */
    public record Plan(Optional<Spot> ranged, Optional<Spot> melee) {
        public boolean empty() { return ranged.isEmpty() && melee.isEmpty(); }
    }

    /** effects: the chests the worker arms from: its home chest, then its post's storage. */
    public static List<BlockPos> chests(ServerLevel level, Villager v) {
        var out = new LinkedHashSet<BlockPos>();
        Kitchen.home(level, v).ifPresent(out::add);
        Workers.of(v).post().filter(p -> p.dimension() == level.dimension()).flatMap(p -> Posts.loaded(level.getServer(), p))
                .ifPresent(post -> out.addAll(post.storage(level)));
        return List.copyOf(out);
    }

    /** effects: the kind of weapon {@code stack} is; empty for anything else. */
    public static Optional<Armoury.Kind> kind(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.getItem() instanceof CrossbowItem) return Optional.of(Armoury.Kind.CROSSBOW);
        if (stack.getItem() instanceof BowItem) return Optional.of(Armoury.Kind.BOW);
        var gun = GunsCompat.gun(stack);
        if (gun.isPresent()) return Optional.of(gun.get().launcher() ? Armoury.Kind.LAUNCHER : Armoury.Kind.GUN);
        if (stack.is(MELEE)) return Optional.of(Armoury.Kind.MELEE);
        return Optional.empty();
    }

    /** effects: a melee weapon's damage a blow, as a player's (a fist's 1 and the weapon's), and its
     * blows a second (a player's 4 and the weapon's). */
    public static double[] blow(ItemStack stack) {
        double[] dmg = {1.0}, speed = {4.0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (mod.operation() != AttributeModifier.Operation.ADD_VALUE) return;
            if (attr.is(Attributes.ATTACK_DAMAGE)) dmg[0] += mod.amount();
            if (attr.is(Attributes.ATTACK_SPEED)) speed[0] += mod.amount();
        });
        return new double[]{Math.max(0, dmg[0]), Math.max(0.1, speed[0])};
    }

    /** requires: {@code kind} is {@code stack}'s. effects: its damage a second. */
    public static double dps(ItemStack stack, Armoury.Kind kind) {
        return switch (kind) {
            case MELEE -> { var b = blow(stack); yield Armoury.melee(b[0], b[1]); }
            case BOW -> Armoury.bow(false);
            case CROSSBOW -> Armoury.bow(true);
            case GUN, LAUNCHER -> GunsCompat.gun(stack).map(g -> Armoury.gun(g.damage(), Math.max(1, g.projectiles()), Math.max(1, g.fireTicks()),
                    Math.max(1, g.capacity()), Math.max(0, g.reloadPerRound()))).orElse(0.0);
        };
    }

    /** effects: true iff {@code round} feeds the ranged weapon {@code weapon}: an arrow for a bow or a
     * crossbow, a loose round the gun accepts. */
    public static boolean feeds(ItemStack weapon, ItemStack round) {
        var kind = kind(weapon);
        if (kind.isEmpty() || round.isEmpty()) return false;
        return switch (kind.get()) {
            case BOW, CROSSBOW -> round.is(ItemTags.ARROWS);
            case GUN, LAUNCHER -> GunsCompat.accepts(weapon, round);
            case MELEE -> false;
        };
    }

    /** effects: how many of what feeds {@code weapon} the chests hold. */
    static int fodder(ServerLevel level, ItemStack weapon, List<BlockPos> chests) {
        int n = 0;
        for (var pos : chests) {
            var h = Storage.handler(level, pos).orElse(null);
            if (h == null) continue;
            for (int i = 0; i < h.getSlots(); i++) if (feeds(weapon, h.getStackInSlot(i))) n += h.getStackInSlot(i).getCount();
        }
        return n;
    }

    /** effects: what the worker means to take: every weapon in its chests weighed, a ranged weapon
     * counted with what it could fire (a gun's loaded rounds too), and the best of each kind chosen. */
    public static Plan plan(ServerLevel level, Villager v) {
        var chests = chests(level, v);
        var spots = new ArrayList<Spot>();
        var found = new ArrayList<Armoury.Found>();
        for (var pos : chests) {
            var h = Storage.handler(level, pos).orElse(null);
            if (h == null) continue;
            for (int i = 0; i < h.getSlots(); i++) {
                var s = h.getStackInSlot(i);
                var kind = kind(s);
                if (kind.isEmpty()) continue;
                int ammo = kind.get().ranged() ? fodder(level, s, chests) + (kind.get() == Armoury.Kind.GUN ? GunsCompat.rounds(s) : 0) : 0;
                double dps = dps(s, kind.get());
                found.add(new Armoury.Found(spots.size(), kind.get(), dps, ammo));
                spots.add(new Spot(pos, i, s.copyWithCount(1), kind.get(), dps));
            }
        }
        var choice = Armoury.choose(found);
        return new Plan(choice.ranged().map(spots::get), choice.melee().map(spots::get));
    }

    /** effects: the weapon {@code spot} names, taken out of its chest: from its slot if it is still
     * there, else one like it elsewhere in that chest; empty when another took it first. */
    public static ItemStack take(ServerLevel level, Spot spot) {
        var h = Storage.handler(level, spot.chest()).orElse(null);
        if (h == null) return ItemStack.EMPTY;
        if (spot.slot() < h.getSlots() && ItemStack.isSameItemSameComponents(h.getStackInSlot(spot.slot()), spot.stack())) return h.extractItem(spot.slot(), 1, false);
        for (int i = 0; i < h.getSlots(); i++) if (ItemStack.isSameItemSameComponents(h.getStackInSlot(i), spot.stack())) return h.extractItem(i, 1, false);
        return ItemStack.EMPTY;
    }

    /** effects: up to {@code want} of what {@code is} accepts taken out of the chest at {@code pos}, as
     * whole stacks. */
    public static List<ItemStack> takeAll(ServerLevel level, BlockPos pos, Predicate<ItemStack> is, int want) {
        var out = new ArrayList<ItemStack>();
        var h = Storage.handler(level, pos).orElse(null);
        if (h == null) return out;
        for (int i = 0; i < h.getSlots() && want > 0; i++) {
            var s = h.getStackInSlot(i);
            if (s.isEmpty() || !is.test(s)) continue;
            var got = h.extractItem(i, Math.min(want, s.getCount()), false);
            if (got.isEmpty()) continue;
            want -= got.getCount();
            out.add(got);
        }
        return out;
    }

    /** effects: {@code stack} put into the chest at {@code pos}, if it is still storage; what does not
     * fit is returned. */
    public static ItemStack put(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!level.isLoaded(pos) || !Storage.storage(level, pos)) return stack;
        var h = Storage.handler(level, pos).orElse(null);
        return h == null ? stack : ItemHandlerHelper.insertItemStacked(h, stack, false);
    }
}
