/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.compat.DeedCompat;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.job.RecipeBook;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** Who shops, when, and at which stall (D-0006), read off a villager for {@link Shopping}; the plan a
 * villager's brain turns to the shop on, waiting for its trip to take it up. */
public final class Shoppers {
    private Shoppers() {}

    /** A trip planned: the stall and the want it is for. */
    public record Plan(BlockPos stall, Shopping.Want want) {}
    private static final Map<UUID, Plan> PLANS = new ConcurrentHashMap<>();

    /** effects: the villager as shopping sees it. */
    public static Shopping.Who who(Villager villager) {
        var w = Workers.of(villager);
        boolean bed = w.owned() ? w.bed().isPresent() : villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent();
        return new Shopping.Who(w.owned(), !villager.isBaby(), w.captive(), w.cuffed(), w.escaping(), bed);
    }

    /** effects: the villager's bed in this level, if it has one there. */
    public static Optional<BlockPos> bed(ServerLevel level, Villager villager) {
        var w = Workers.of(villager);
        var bed = w.owned() ? w.bed() : villager.getBrain().getMemory(MemoryModuleType.HOME);
        return bed.filter(b -> b.dimension() == level.dimension()).map(b -> b.pos());
    }

    static int perDay() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.SALES_PER_DAY.get() : 3; }
    static int reach() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.SHOP_REACH.get() : 64; }

    /** effects: the stalls the villager may use within reach of its bed, as shopping's offers, by their
     * index in {@code stalls}. */
    public static List<Shopping.Offer> offers(ServerLevel level, BlockPos bed, List<ForSaleBlockEntity> stalls) {
        var out = new ArrayList<Shopping.Offer>();
        for (int i = 0; i < stalls.size(); i++) {
            var st = stalls.get(i);
            var s = st.stall();
            boolean allowed = st.owner().map(o -> DeedCompat.allows(level, bed, o)).orElse(true);
            out.add(new Shopping.Offer(i, s.item(), s.quantity(), s.price(), Math.sqrt(st.getBlockPos().distSqr(bed)), s.open(), allowed));
        }
        return out;
    }

    /** effects: the stall to go to for one of {@code wants} and the want, among the stalls near the
     * villager's bed; empty when none it may use sells any. */
    public static Optional<Plan> choose(ServerLevel level, Villager villager, List<Shopping.Want> wants) {
        var bed = bed(level, villager);
        if (bed.isEmpty() || wants.isEmpty()) return Optional.empty();
        var stalls = Stalls.near(level, bed.get(), reach());
        if (stalls.isEmpty()) return Optional.empty();
        return Shopping.choose(wants, offers(level, bed.get(), stalls), Purses.emeralds(villager))
                .map(p -> new Plan(stalls.get(p.offer()).getBlockPos(), p.want()));
    }

    /** effects: whether the villager goes shopping now, and if so its plan, waiting for its trip; the
     * trip counts as made today whether a stall was found or not. */
    public static boolean plan(ServerLevel level, Villager villager) {
        var saved = Purses.of(villager);
        long now = level.getDayTime();
        var who = who(villager);
        // The cheap checks first: most villagers most of the day stop here.
        if (!Shopping.shopper(who) || !Shopping.social(now, who.hired()) || saved.day().tripDay() == com.chunkworks.serfdom.domain.Purse.day(now)) return false;
        var wants = Needs.wants(level, villager);
        if (!Shopping.due(who, now, saved.day(), saved.purse().emeralds(), !wants.isEmpty(), perDay())) return false;
        Purses.set(villager, saved.with(Shopping.tripped(saved.day(), now)));
        var plan = choose(level, villager, wants);
        plan.ifPresent(p -> PLANS.put(villager.getUUID(), p));
        return plan.isPresent();
    }

    /** effects: the villager's waiting plan, taken. */
    public static Optional<Plan> take(Villager villager) { return Optional.ofNullable(PLANS.remove(villager.getUUID())); }

    /** effects: a free adult's vanilla brain shops too: its shopping time in the core, and the shop. */
    public static void addTo(Villager villager, Brain<Villager> brain) {
        if (villager.isBaby()) return;
        brain.addActivity(Activity.CORE, ImmutableList.of(Pair.of(10, new com.chunkworks.serfdom.behavior.ShopTime())));
        brain.addActivity(Serfdom.SHOP.get(), shop());
    }

    /** effects: the shop's behaviours: the trip, and a look about. */
    public static ImmutableList<Pair<Integer, ? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Villager>>> shop() {
        return ImmutableList.of(Pair.of(5, new com.chunkworks.serfdom.behavior.GoShopping()));
    }

    /** effects: the item's id, as shopping names it. */
    static String key(net.minecraft.world.item.ItemStack stack) { return RecipeBook.key(stack.getItem()); }
}
