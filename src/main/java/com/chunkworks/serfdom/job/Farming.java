/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import com.chunkworks.serfdom.domain.Harvest;
import com.chunkworks.serfdom.domain.Radius;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The farmer (D-0001): every ripe crop on farmland in the post's area, worked through nearest
 * next, each replanted with a seed from its own harvest; pumpkins and melons beside their stems;
 * Farmer's Delight's tomatoes and rice by their own rules. Never tills. The area is read section by
 * section, skipping every section whose palette holds no crop. */
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
        var p = post.getBlockPos();
        int r = post.radius();
        var centre = Storage.cell(p);
        var found = new LinkedHashSet<BlockPos>();
        for (int cx = (p.getX() - r) >> 4; cx <= (p.getX() + r) >> 4; cx++)
            for (int cz = (p.getZ() - r) >> 4; cz <= (p.getZ() + r) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = Math.max(level.getMinSection(), (p.getY() - r) >> 4); sy <= Math.min(level.getMaxSection() - 1, (p.getY() + r) >> 4); sy++) {
                    var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(Farming::crop)) continue;
                    var origin = SectionPos.of(cx, sy, cz).origin();
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        var state = section.getBlockState(x, y, z);
                        if (!crop(state)) continue;
                        var pos = origin.offset(x, y, z);
                        if (!Radius.contains(centre, r, Storage.cell(pos))) continue;
                        target(level, pos, state).filter(t -> !skip.test(t)).ifPresent(found::add);
                    }
                }
            }
        if (found.isEmpty()) return Search.none();
        return Search.of(Optional.of(new CropTask(route(worker.blockPosition(), new ArrayList<>(found)))));
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
