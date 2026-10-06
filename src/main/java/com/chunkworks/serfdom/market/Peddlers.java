/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Peddler;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.domain.Stall;
import com.chunkworks.serfdom.job.RecipeBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Free villagers selling to other villagers (D-0006, 4b): what a free, grown villager carries in its
 * own inventory of what its profession sells ({@link Needs#sells}), beyond what it keeps, in lots at
 * the item's base value ({@link Peddler}); in practice a town farmer's bread and harvest. Hired workers
 * and captives never sell: what they handle is their owner's. A seller serves one buyer at a time. */
public final class Peddlers {
    /** Ticks a buyer may hold a seller before it is taken for one that never let go. */
    static final long SERVE_FOR = 400;
    private record Customer(UUID who, long since) {}
    private static final Map<UUID, Customer> SERVING = new ConcurrentHashMap<>();
    private Peddlers() {}

    /** One thing a villager sells now: the seller, the item, its lot and how many it may sell. */
    public record Ware(Villager seller, Item item, Peddler.Lot lot, int spare) {
        public Stall stall() { return Peddler.stall(RecipeBook.key(item), lot, spare); }
        /** effects: the ware as a shopper whose bed is at {@code bed} sees it, as offer {@code id}. */
        public Shopping.Offer offer(int id, BlockPos bed) {
            var s = stall();
            return new Shopping.Offer(id, s.item(), s.quantity(), s.price(), seller.position().distanceTo(Vec3.atBottomCenterOf(bed)), s.open(), true, false, 0);
        }
    }

    /** effects: true iff the villager sells now: free, grown, alive, awake, not trading with a player. */
    public static boolean selling(Villager v) {
        return v.isAlive() && !v.isBaby() && !Workers.owned(v) && !v.isSleeping() && !v.isTrading();
    }

    /** effects: what the villager sells now, item by item, those it has a lot of spare first in the
     * data's order; empty for one that doesn't sell. */
    public static List<Ware> wares(ServerLevel level, Villager v) {
        if (!selling(v)) return List.of();
        var out = new ArrayList<Ware>();
        Needs.sells(v.getVillagerData().getProfession()).forEach((item, keep) -> {
            int spare = Peddler.spare(v.getInventory().countItem(item), keep);
            if (spare < 1) return;
            Prices.value(level, new ItemStack(item)).stream().boxed().findFirst().flatMap(Peddler::lot)
                    .ifPresent(lot -> out.add(new Ware(v, item, lot, spare)));
        });
        return out;
    }

    /** effects: the ware {@code item} of the villager, if it sells it now. */
    public static Optional<Ware> ware(ServerLevel level, Villager v, Item item) {
        return wares(level, v).stream().filter(w -> w.item() == item).findFirst();
    }

    /** effects: everything sold by villagers within {@code reach} blocks of {@code bed}, but
     * {@code buyer}'s own, with a lot spare, nearest the bed first. */
    public static List<Ware> near(ServerLevel level, BlockPos bed, int reach, Villager buyer) {
        var centre = Vec3.atBottomCenterOf(bed);
        var out = new ArrayList<Ware>();
        for (var v : level.getEntitiesOfClass(Villager.class, new AABB(bed).inflate(reach), v -> v != buyer && v.position().distanceToSqr(centre) <= (double) reach * reach))
            for (var w : wares(level, v)) if (w.stall().open()) out.add(w);
        out.sort(java.util.Comparator.comparingDouble(w -> w.seller().position().distanceToSqr(centre)));
        return out;
    }

    /** requires: the villager carries {@code n} of {@code item}. effects: takes them out of its
     * inventory. */
    public static List<ItemStack> take(Villager seller, Item item, int n) {
        var inv = seller.getInventory();
        if (inv.countItem(item) < n) throw new IllegalStateException("short of " + n + " " + item);
        var out = new ArrayList<ItemStack>();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            var s = inv.getItem(i);
            if (!s.is(item)) continue;
            var taken = inv.removeItem(i, Math.min(n, s.getCount()));
            n -= taken.getCount();
            out.add(taken);
        }
        return out;
    }

    /** effects: true iff the seller serves {@code buyer} now: nobody else is being served (or the one
     * who was has held it past {@link #SERVE_FOR}); it is then {@code buyer}'s until let go. */
    public static boolean serve(Villager seller, UUID buyer, long now) {
        var c = SERVING.get(seller.getUUID());
        if (c != null && !c.who().equals(buyer) && now - c.since() < SERVE_FOR) return false;
        if (c == null || !c.who().equals(buyer)) SERVING.put(seller.getUUID(), new Customer(buyer, now));
        return true;
    }

    /** effects: {@code buyer} is served by the seller no more. */
    public static void letGo(UUID seller, UUID buyer) { SERVING.computeIfPresent(seller, (k, c) -> c.who().equals(buyer) ? null : c); }
}
