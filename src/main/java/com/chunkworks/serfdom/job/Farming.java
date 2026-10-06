/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import com.chunkworks.serfdom.domain.Farm;
import com.chunkworks.serfdom.domain.Harvest;
import com.chunkworks.serfdom.post.Farms;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The farmer (D-0001, D-0008): every ripe crop on farmland in its farm, worked a plot at a time,
 * each replanted with a seed from its own harvest; pumpkins and melons beside their stems; Farmer's
 * Delight's tomatoes and rice by their own rules; and bare farmland sown as {@link
 * com.chunkworks.serfdom.domain.Sowing} says, from seeds it carries or the farm's chests. Never
 * tills. Its farm is its post's ({@link Farms}); the work is what each post's field found
 * ({@link Field}). A farmer takes the plot its own post's area offers first, else the nearest
 * anywhere on the farm, never one another worker holds. */
public final class Farming implements Job {
    public static final Farming INSTANCE = new Farming();
    /** The most crops one round of work takes on before looking again. */
    static final int ROUND = 64;
    private Farming() {}

    /** effects: true iff the block state could be worth a farmer's look. */
    static boolean crop(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof AttachedStemBlock || FarmersDelightCompat.crop(state);
    }

    @Override public Search find(ServerLevel level, Villager worker, WorkPostBlockEntity post, Predicate<BlockPos> skip) {
        var farm = Farms.of(level, post);
        var at = worker.blockPosition();
        var carried = worker.getInventory();
        // The farm's work by plot, each piece once, and the plots with work in the worker's own area.
        var plots = new LinkedHashMap<Farm.Plot, LinkedHashMap<BlockPos, Field.Work>>();
        var own = new HashSet<Farm.Plot>();
        for (var p : farm)
            p.field().work(level, p).forEach((plot, list) -> {
                for (var w : list) {
                    if (skip.test(w.pos())) continue;
                    plots.computeIfAbsent(plot, k -> new LinkedHashMap<>()).putIfAbsent(w.pos(), w);
                    if (p == post) own.add(plot);
                }
            });
        // Sowing needs the seed: carried, or in a chest on the farm.
        var stored = new HashMap<Item, Optional<BlockPos>>();
        boolean seedless = false;
        var offers = new ArrayList<Farm.Offer>();
        var usable = new HashMap<Farm.Plot, List<Field.Work>>();
        for (var e : plots.entrySet()) {
            var list = new ArrayList<Field.Work>();
            long nearest = Long.MAX_VALUE;
            for (var w : e.getValue().values()) {
                if (w.sow().isPresent() && Storage.count(carried, w.sow().get()) == 0
                        && stored.computeIfAbsent(w.sow().get(), seed -> Storage.nearestHolding(level, farm, at, seed)).isEmpty()) {
                    seedless = true;
                    continue;
                }
                list.add(w);
                nearest = Math.min(nearest, (long) w.pos().distSqr(at));
            }
            if (list.isEmpty()) continue;
            usable.put(e.getKey(), list);
            offers.add(new Farm.Offer(e.getKey(), nearest, own.contains(e.getKey())));
        }
        var id = worker.getUUID();
        var next = Farm.next(offers, plot -> Holding.heldByAnother(level, Job.Place.plot(plot), id));
        if (next.isEmpty())
            return offers.isEmpty() && seedless ? new Search(Optional.empty(), Optional.of(com.chunkworks.serfdom.domain.Need.NO_MATERIALS)) : Search.none();
        var byPos = new HashMap<BlockPos, Field.Work>();
        for (var w : usable.get(next.get())) byPos.put(w.pos(), w);
        var work = route(at, usable.get(next.get()).stream().map(Field.Work::pos).toList()).stream().map(byPos::get).toList();
        // What the round sows and the farmer lacks is fetched first, from the nearest chest holding it.
        var sows = new LinkedHashMap<Item, Integer>();
        for (var w : work) w.sow().ifPresent(seed -> sows.merge(seed, 1, Integer::sum));
        var fetches = new ArrayList<CropTask.Fetch>();
        sows.forEach((seed, n) -> {
            int lack = Math.min(n - Storage.count(carried, seed), seed.getDefaultMaxStackSize());
            if (lack > 0) stored.computeIfAbsent(seed, s -> Storage.nearestHolding(level, farm, at, s))
                    .ifPresent(chest -> fetches.add(new CropTask.Fetch(chest, seed, lack)));
        });
        return Search.of(Optional.of(new CropTask(Job.Place.plot(next.get()), fetches, work, pos -> { for (var p : farm) p.field().taken(pos); })));
    }

    /** effects: what to take for the crop block at {@code pos}: the crop itself when it is ripe on
     * farmland, the fruit beside an attached stem, or what Farmer's Delight's rules say. */
    static Optional<BlockPos> target(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof AttachedStemBlock) {
            var fruit = pos.relative(state.getValue(AttachedStemBlock.FACING));
            return level.getBlockState(fruit).isAir() ? Optional.empty() : Optional.of(fruit);
        }
        if (FarmersDelightCompat.crop(state)) return FarmersDelightCompat.ripe(level, pos, state) ? Optional.of(pos) : Optional.empty();
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && level.getBlockState(pos.below()).getBlock() instanceof FarmBlock)
            return Optional.of(pos);
        return Optional.empty();
    }

    /** effects: true iff the block at {@code pos} is a fruit an attached stem beside it grew. */
    static boolean fruit(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) return false;
        for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var stem = level.getBlockState(pos.relative(side));
            if (stem.getBlock() instanceof AttachedStemBlock && stem.getValue(AttachedStemBlock.FACING) == side.getOpposite()) return true;
        }
        return false;
    }

    /** effects: up to {@link #ROUND} of {@code targets} in walking order: each the nearest to the
     * one before, from {@code start}. */
    static List<BlockPos> route(BlockPos start, List<BlockPos> targets) {
        var left = new ArrayList<>(targets);
        var out = new ArrayList<BlockPos>();
        var at = start;
        while (!left.isEmpty() && out.size() < ROUND) {
            int best = 0;
            for (int i = 1; i < left.size(); i++) if (left.get(i).distSqr(at) < left.get(best).distSqr(at)) best = i;
            at = left.remove(best);
            out.add(at);
        }
        return out;
    }

    /** effects: the harvest's drops less one seed for replanting ({@link Harvest#replantFrom}),
     * and whether there was one. */
    static Harvest.Split split(List<net.minecraft.world.item.ItemStack> drops, net.minecraft.world.item.Item seed) {
        var reg = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var stacks = drops.stream().filter(s -> !s.isEmpty()).map(s -> new Harvest.Stack(reg.getKey(s.getItem()).toString(), s.getCount())).toList();
        return Harvest.replantFrom(stacks, reg.getKey(seed).toString());
    }
}
