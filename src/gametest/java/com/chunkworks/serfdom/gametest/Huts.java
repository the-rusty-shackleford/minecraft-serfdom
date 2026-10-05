/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.villagedeed.village.StructureVillages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/** A village for the capture's tests, as Village Law's tests have it from Village Deed's: the
 * {@code serfdom_gametest:hut} structure, an 8x5x8 box the gametest datapack tags as a village and
 * as Thief-protected, generated and registered inside the test's arena the way worldgen registers a
 * structure; and Guard Villagers' guards, Village Law's officers. */
final class Huts {
    static final ResourceKey<Structure> HUT = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("serfdom_gametest", "hut"));
    /** The planted hut: the structure's box, in world coordinates. */
    record Hut(StructureStart start, BoundingBox box) {
        /** effects: the world position at an offset from the box's minimum corner. */
        BlockPos at(int dx, int dy, int dz) { return new BlockPos(box.minX() + dx, box.minY() + dy, box.minZ() + dz); }
        BlockPos centre() { return box.getCenter(); }
    }
    private Huts() {}

    /** effects: generates the hut in the chunk {@code dx} chunks east of the arena's middle one and
     * records its start and references in the chunks it covers, as the chunk generator would, so the
     * structure manager (and through it Thief and Village Deed) finds it; then lays its stone floor
     * and a wall three high round it, which a guard cannot step over: the arena around is open air,
     * and a guard that wandered out fell out of sight of the crime it was to see. Asserts it lies
     * inside the arena. */
    static Hut plant(GameTestHelper h, int dx) {
        var level = h.getLevel();
        var bounds = h.getBounds();
        int minCx = (int) Math.ceil(bounds.minX / 16.0), maxCx = (int) Math.floor((bounds.maxX + 1) / 16.0) - 1;
        int minCz = (int) Math.ceil(bounds.minZ / 16.0), maxCz = (int) Math.floor((bounds.maxZ + 1) / 16.0) - 1;
        h.assertTrue(minCx + 1 <= maxCx - 1 && minCz + 1 <= maxCz - 1, "the arena holds a chunk with whole chunks around it: " + bounds);
        int cx = (minCx + maxCx) / 2 + dx, cz = (minCz + maxCz) / 2;
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(HUT);
        var generator = level.getChunkSource().getGenerator();
        var start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), new ChunkPos(cx, cz), 0, level, biome -> true);
        h.assertTrue(start.isValid(), "the hut structure generates");
        var box = start.getBoundingBox();
        h.assertTrue(bounds.contains(box.minX(), box.minY(), box.minZ()) && bounds.contains(box.maxX(), box.maxY(), box.maxZ()),
                "the hut lies inside the arena: " + box + " in " + bounds);
        var home = level.getChunk(cx, cz);
        level.structureManager().setStartForStructure(SectionPos.bottomOf(home), structure, start, home);
        long reference = new ChunkPos(cx, cz).toLong();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
            var chunk = level.getChunk(x, z);
            level.structureManager().addReferenceForStructure(SectionPos.bottomOf(chunk), structure, reference, chunk);
        }
        h.assertTrue(level.structureManager().getStructureWithPieceAt(box.getCenter(), StructureVillages.VILLAGES).isValid(), "the hut is a village");
        for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
            level.setBlock(new BlockPos(x, box.minY(), z), Blocks.STONE.defaultBlockState(), 3);
            boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
            if (edge) for (int y = 1; y <= 3; y++) level.setBlock(new BlockPos(x, box.minY() + y, z), Blocks.STONE.defaultBlockState(), 3);
        }
        return new Hut(start, box);
    }

    /** effects: one of Guard Villagers' guards at the position, its AI running, joined the way a
     * spawned guard joins, so Village Law makes it an officer. */
    static Guard guard(GameTestHelper h, BlockPos at) {
        var guard = GuardEntityType.GUARD.get().create(h.getLevel());
        guard.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        guard.setPersistenceRequired();
        h.getLevel().addFreshEntity(guard);
        return guard;
    }
}
