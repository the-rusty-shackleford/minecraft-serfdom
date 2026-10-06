/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.defence;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.compat.GunsCompat;
import com.chunkworks.serfdom.domain.Armoury;
import com.chunkworks.serfdom.domain.Defence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The defence module (D-0007): a worker's raid, its arms ({@code serfdom:arms}), the facts the
 * domain's {@link Defence} decides on, the raiders within its reach, and its listeners: villagers get
 * a fist's attack damage; a defender that dies drops what it took, and one that turns drops it too; and
 * nobody trades with a defender, since a trade empties a villager's hand. */
public final class Defenders {
    /** What vanilla drops a mob's held item at; a defender's weapon always drops. */
    static final float HAND_DROP = 0.085F, ALWAYS = 2.0F;
    private Defenders() {}

    public static boolean on() { return !SerfdomConfig.SPEC.isLoaded() || SerfdomConfig.DEFENCE.get(); }
    static int reach() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.DEFENCE_REACH.get() : 48; }
    static int ammoCarried() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.AMMO_CARRIED.get() : 64; }

    public static Arms arms(Villager v) { return v.getExistingData(Serfdom.ARMS).orElse(Arms.NONE); }

    public static void set(Villager v, Arms arms) {
        if (arms.blank()) v.removeData(Serfdom.ARMS);
        else v.setData(Serfdom.ARMS, arms);
    }

    /** effects: the raid where the villager stands, while it is on (not won, lost or stopped). */
    public static Optional<Raid> raid(ServerLevel level, Villager v) {
        var raid = level.getRaidAt(v.blockPosition());
        return raid == null || raid.isOver() || raid.isStopped() ? Optional.empty() : Optional.of(raid);
    }

    /** effects: true iff the villager arms in a raid. */
    public static boolean musters(Villager v) {
        var w = Workers.of(v);
        return on() && Defence.musters(new Defence.Who(w.owned(), !v.isBaby(), w.cuffed(), w.escaping()));
    }

    /** effects: true iff the villager is out defending: its brain on the defence. */
    public static boolean defending(Villager v) { return v.getBrain().isActive(Serfdom.DEFEND.get()); }

    /** effects: where the villager belongs: its post and its bed, in this level. */
    static java.util.List<BlockPos> homes(ServerLevel level, Villager v) {
        var w = Workers.of(v);
        var out = new ArrayList<BlockPos>();
        w.post().filter(p -> p.dimension() == level.dimension()).ifPresent(p -> out.add(p.pos()));
        w.bed().filter(p -> p.dimension() == level.dimension()).ifPresent(p -> out.add(p.pos()));
        return out;
    }

    /** effects: how far {@code at} is from where the villager belongs (its post or its bed, the nearer);
     * where it stands when it has neither. */
    static double fromHome(ServerLevel level, Villager v, Vec3 at) {
        var homes = homes(level, v);
        if (homes.isEmpty()) return at.distanceTo(v.position());
        return homes.stream().mapToDouble(h -> at.distanceTo(Vec3.atBottomCenterOf(h))).min().orElseThrow();
    }

    /** effects: the nearest raider of {@code raid} the villager can see within its reach. */
    public static Optional<Raider> target(ServerLevel level, Villager v, Raid raid) {
        var r = reach();
        return level.getEntitiesOfClass(Raider.class, v.getBoundingBox().inflate(Defence.SIGHT), e -> e.isAlive() && e.getCurrentRaid() == raid
                        && Defence.inReach(e.distanceTo(v), fromHome(level, v, e.position()), Defence.SIGHT, r) && v.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(v)));
    }

    // ---- what it carries --------------------------------------------------------------------

    /** effects: its ranged weapon, in its hand or carried; empty when it has none. */
    public static ItemStack ranged(Villager v, Arms arms) {
        var hand = v.getMainHandItem();
        if (arms.holds() && Armouries.kind(hand).map(Armoury.Kind::ranged).orElse(false)) return hand;
        return arms.kept().stream().map(Arms.Kept::stack).filter(s -> Armouries.kind(s).map(Armoury.Kind::ranged).orElse(false)).findFirst().orElse(ItemStack.EMPTY);
    }

    /** effects: true iff it carries a melee weapon, in its hand or not. */
    public static boolean melee(Villager v, Arms arms) {
        if (arms.holds() && Armouries.kind(v.getMainHandItem()).filter(k -> k == Armoury.Kind.MELEE).isPresent()) return true;
        return arms.kept().stream().anyMatch(k -> Armouries.kind(k.stack()).filter(x -> x == Armoury.Kind.MELEE).isPresent());
    }

    /** effects: how much of what feeds {@code weapon} it carries. */
    public static int fodder(Arms arms, ItemStack weapon) {
        return arms.kept().stream().map(Arms.Kept::stack).filter(s -> Armouries.feeds(weapon, s)).mapToInt(ItemStack::getCount).sum();
    }

    /** effects: how much of what feeds its ranged weapon it carries; 0 with no ranged weapon. */
    public static int fodderOf(Villager v) {
        var arms = arms(v);
        var weapon = ranged(v, arms);
        return weapon.isEmpty() ? 0 : fodder(arms, weapon);
    }

    /** effects: true iff its ranged weapon can fire: a gun loaded, or what feeds it carried. */
    public static boolean rangedReady(Villager v, Arms arms) {
        var weapon = ranged(v, arms);
        if (weapon.isEmpty()) return false;
        return GunsCompat.rounds(weapon) > 0 || fodder(arms, weapon) > 0;
    }

    /** effects: the facts the defence decides on; with {@code look} false, as if no raider were in
     * reach (no search: the core asks only whether to take the brain over). */
    public static Defence.Facts facts(ServerLevel level, Villager v, boolean look) {
        var raid = raid(level, v);
        var arms = arms(v);
        boolean target = look && raid.isPresent() && target(level, v, raid.get()).isPresent();
        return new Defence.Facts(raid.isPresent(), musters(v), arms.carries(), raid.map(r -> arms.raid() == r.getId()).orElse(false),
                rangedReady(v, arms), melee(v, arms), target);
    }

    // ---- listeners --------------------------------------------------------------------------

    public static void listen(IEventBus modBus) {
        // Villagers have no attack damage; a fist's, as a player's, so a sword hits as in a player's hand.
        modBus.addListener((EntityAttributeModificationEvent e) -> {
            if (!e.has(EntityType.VILLAGER, Attributes.ATTACK_DAMAGE)) e.add(EntityType.VILLAGER, Attributes.ATTACK_DAMAGE, 1.0);
        });
        NeoForge.EVENT_BUS.addListener((LivingDropsEvent e) -> {
            if (!(e.getEntity() instanceof Villager v) || v.level().isClientSide()) return;
            for (var k : arms(v).kept()) e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(v.level(), v.getX(), v.getY(), v.getZ(), k.stack().copy()));
            v.removeData(Serfdom.ARMS);
        });
        NeoForge.EVENT_BUS.addListener((LivingConversionEvent.Post e) -> {
            if (e.getEntity() instanceof Villager v && !v.level().isClientSide()) drop(v);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (PlayerInteractEvent.EntityInteract e) -> {
            if (e.getTarget() instanceof Villager v && !v.level().isClientSide() && !e.getEntity().isSecondaryUseActive() && defending(v)) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                v.setUnhappyCounter(40);
            }
        });
    }

    /** effects: everything the defender took dropped where it stands, the weapon in its hand too, and
     * nothing of ours carried after: it is leaving its owner (set free, freed by the law, escaping) or
     * turning (a zombie), and nothing will put them back. */
    public static void drop(Villager v) {
        var arms = arms(v);
        for (var k : arms.kept()) v.spawnAtLocation(k.stack().copy());
        if (arms.holds()) {
            v.spawnAtLocation(v.getMainHandItem().copy());
            v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            handDrops(v, false);
        }
        v.removeData(Serfdom.ARMS);
    }

    /** effects: the weapon in its hand always drops, or drops as vanilla's does. */
    public static void handDrops(Villager v, boolean always) { v.setDropChance(EquipmentSlot.MAINHAND, always ? ALWAYS : HAND_DROP); }
}
