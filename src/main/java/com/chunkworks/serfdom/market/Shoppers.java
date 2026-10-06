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

/** Who shops, when, and where (D-0006), read off a villager for {@link Shopping}: a stall, or (4b) a
 * villager selling; the plan a villager's brain turns to the shop on, waiting for its trip to take it
 * up. */
public final class Shoppers {
    private Shoppers() {}

    /** Where a trip goes: a stall, or a villager selling an item. */
    public sealed interface Place {}
    public record AtStall(BlockPos pos) implements Place {}
    public record AtSeller(UUID seller, net.minecraft.world.item.Item item) implements Place {}

    /** A trip planned: where, and the need it is for; none for a look (window shopping, 4b). */
    public record Plan(Place place, Optional<Shopping.Want> need) {
        /** effects: the stall it goes to, if it goes to one. */
        public Optional<BlockPos> stall() { return place instanceof AtStall s ? Optional.of(s.pos()) : Optional.empty(); }
    }
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
     * index in {@code stalls}, each standing where its block does. */
    public static List<Shopping.Offer> offers(ServerLevel level, BlockPos bed, List<ForSaleBlockEntity> stalls) {
        var out = new ArrayList<Shopping.Offer>();
        for (int i = 0; i < stalls.size(); i++) {
            var st = stalls.get(i);
            var s = st.stall();
            boolean allowed = st.owner().map(o -> DeedCompat.allows(level, bed, o)).orElse(true);
            out.add(new Shopping.Offer(i, s.item(), s.quantity(), s.price(), Math.sqrt(st.getBlockPos().distSqr(bed)), s.open(), allowed, true, st.getBlockPos().asLong()));
        }
        return out;
    }

    /** effects: the stall to go to for one of {@code wants} and the want, among the stalls near the
     * villager's bed; empty when none it may use sells any. A hungry worker's meal buys here (stalls
     * only, as D-0006 has it). */
    public static Optional<Plan> choose(ServerLevel level, Villager villager, List<Shopping.Want> wants) {
        var bed = bed(level, villager);
        if (bed.isEmpty() || wants.isEmpty()) return Optional.empty();
        var stalls = Stalls.near(level, bed.get(), reach());
        if (stalls.isEmpty()) return Optional.empty();
        return Shopping.choose(wants, offers(level, bed.get(), stalls), Purses.emeralds(villager))
                .map(p -> new Plan(new AtStall(stalls.get(p.offer()).getBlockPos()), Optional.of(p.want())));
    }

    /** effects: whether the villager goes shopping now, and if so its plan, waiting for its trip, as
     * {@link Shopping#decide} says: for a need, once a social time, at the stall or villager in reach of
     * its bed that sells it cheapest (the trip counts as made whether one is found or not); else a look
     * at the nearest stall it has not seen today. */
    public static boolean plan(ServerLevel level, Villager villager) {
        var saved = Purses.of(villager);
        long now = level.getDayTime();
        var who = who(villager);
        int emeralds = saved.purse().emeralds();
        // The cheap checks first: most villagers most of the day stop here.
        if (!Shopping.shopper(who) || !Shopping.social(now, who.hired()) || emeralds < 1 || Shopping.salesLeft(saved.day(), now, perDay()) < 1) return false;
        var bed = bed(level, villager);
        if (bed.isEmpty()) return false;
        boolean needTrip = saved.day().tripDay() != com.chunkworks.serfdom.domain.Purse.day(now);
        var needs = needTrip ? Needs.wants(level, villager) : List.<Shopping.Want>of();
        var stalls = Stalls.near(level, bed.get(), reach());
        var wares = needs.isEmpty() ? List.<Peddlers.Ware>of() : Peddlers.near(level, bed.get(), reach(), villager);
        var offers = new ArrayList<>(offers(level, bed.get(), stalls));
        for (int i = 0; i < wares.size(); i++) offers.add(wares.get(i).offer(stalls.size() + i, bed.get()));
        var decision = Shopping.decide(who, now, saved.day(), emeralds, perDay(), needs, offers);
        if (!decision.day().equals(saved.day())) Purses.set(villager, saved.with(decision.day()));
        if (decision.trip().isEmpty()) return false;
        var trip = decision.trip().get();
        Place place = trip.offer() < stalls.size() ? new AtStall(stalls.get(trip.offer()).getBlockPos())
                : new AtSeller(wares.get(trip.offer() - stalls.size()).seller().getUUID(), wares.get(trip.offer() - stalls.size()).item());
        PLANS.put(villager.getUUID(), new Plan(place, trip.need()));
        return true;
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
