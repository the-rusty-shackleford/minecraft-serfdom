/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.PlacedLogs;
import com.chunkworks.serfdom.domain.Cell;
import com.chunkworks.serfdom.domain.Felling;
import com.chunkworks.serfdom.domain.Radius;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.core.SectionPos;

/** The woodcutter (D-0001): the nearest natural tree whose trunk base is in the post's area,
 * felled whole, top first, its leaves cleared, replanted. A trunk base is a log standing on soil;
 * the area is read section by section, skipping every section whose palette holds no log. (Not
 * from the heightmap: a roof, an overhang or a cave ceiling over a tree would hide it.) */
public final class Woodcutting implements Job {
    public static final Woodcutting INSTANCE = new Woodcutting();
    private Woodcutting() {}

    @Override public Optional<Task> find(ServerLevel level, Villager worker, WorkPostBlockEntity post, Predicate<BlockPos> skip) {
        var forest = new LevelForest(level);
        var p = post.getBlockPos();
        var centre = Storage.cell(p);
        int r = post.radius();
        var bases = new ArrayList<Cell>();
        for (int cx = (p.getX() - r) >> 4; cx <= (p.getX() + r) >> 4; cx++)
            for (int cz = (p.getZ() - r) >> 4; cz <= (p.getZ() + r) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = Math.max(level.getMinSection(), (p.getY() - r) >> 4); sy <= Math.min(level.getMaxSection() - 1, (p.getY() + r) >> 4); sy++) {
                    var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(BlockTags.LOGS))) continue;
                    var origin = SectionPos.of(cx, sy, cz).origin();
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).is(BlockTags.LOGS)) continue;
                        var cell = new Cell(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        if (!Radius.contains(centre, r, cell) || forest.at(cell).kind() != Felling.Kind.LOG) continue;
                        if (forest.at(cell.below()).kind() == Felling.Kind.SOIL && !skip.test(pos(cell))) bases.add(cell);
                    }
                }
            }
        var near = new Cell(worker.getBlockX(), worker.getBlockY(), worker.getBlockZ());
        bases.sort(Comparator.comparingLong(b -> b.distanceSq(near)));
        var seen = new java.util.HashSet<Cell>();
        for (var base : bases) {
            if (seen.contains(base)) continue;
            var tree = Felling.tree(forest, base);
            if (tree.isPresent()) return Optional.of(new WoodTask(tree.get()));
            seen.add(base);
        }
        return Optional.empty();
    }

    static BlockPos pos(Cell c) { return new BlockPos(c.x(), c.y(), c.z()); }

    /** The level as the woodcutter reads it: a log is its block's id, a player's log is one the
     * record of placed logs holds, leaves are natural unless persistent, soil is
     * {@code #minecraft:dirt}. A chunk that is not loaded reads as nothing, so a search never
     * loads one. */
    record LevelForest(ServerLevel level) implements Felling.Forest {
        @Override public Felling.Block at(Cell c) {
            var chunk = level.getChunkSource().getChunkNow(c.x() >> 4, c.z() >> 4);
            if (chunk == null || level.isOutsideBuildHeight(c.y())) return Felling.Block.OTHER;
            var pos = pos(c);
            var state = chunk.getBlockState(pos);
            if (state.is(BlockTags.LOGS)) {
                var species = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                return new Felling.Block(PlacedLogs.placed(chunk, pos) ? Felling.Kind.PLACED_LOG : Felling.Kind.LOG, species);
            }
            if (state.is(BlockTags.LEAVES))
                return Felling.Block.of(state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT) ? Felling.Kind.LEAVES : Felling.Kind.PERSISTENT_LEAVES);
            if (state.is(BlockTags.DIRT)) return Felling.Block.of(Felling.Kind.SOIL);
            return Felling.Block.OTHER;
        }
    }
}
