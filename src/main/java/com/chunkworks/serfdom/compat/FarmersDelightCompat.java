/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/** Farmer's Delight's crops that are not plain crops (D-0001), checked against 1.3.3: a tomato
 * (the bottom on farmland and every vine up its rope) is picked at its greatest age as its
 * right-click picks it, one or two tomatoes and now and then a rotten one, and set back to age
 * 0; ripe rice panicles are cut above the water, and the rice below grows new ones. Nothing here
 * loads a Farmer's Delight class unless the mod is present. */
public final class FarmersDelightCompat {
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("farmersdelight");
    private FarmersDelightCompat() {}

    /** What a farmer takes from one of these crops, and the change to the world that takes it. */
    public record Picked(List<ItemStack> drops, Runnable apply) {}

    /** effects: true iff the state is a Farmer's Delight crop with its own rule. */
    public static boolean crop(BlockState state) { return LOADED && Inner.crop(state); }

    /** requires: {@link #crop}(state). effects: true iff it is ready to take. */
    public static boolean ripe(ServerLevel level, BlockPos pos, BlockState state) { return Inner.ripe(state); }

    /** requires: {@link #crop}(state) and {@link #ripe}. effects: what taking it yields, given what
     * breaking it would drop, and how to take it. */
    public static Picked pick(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) { return Inner.pick(level, pos, state, drops); }

    private static final class Inner {
        static boolean crop(BlockState state) {
            var block = state.getBlock();
            return block instanceof vectorwing.farmersdelight.common.block.TomatoBlock || block instanceof vectorwing.farmersdelight.common.block.RicePaniclesBlock;
        }
        static boolean ripe(BlockState state) {
            return state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state);
        }
        static Picked pick(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
            if (state.getBlock() instanceof vectorwing.farmersdelight.common.block.TomatoBlock tomato) {
                var out = new ArrayList<ItemStack>();
                out.add(new ItemStack(vectorwing.farmersdelight.common.registry.ModItems.TOMATO.get(), 1 + level.random.nextInt(2)));
                if (level.random.nextFloat() < 0.05F) out.add(new ItemStack(vectorwing.farmersdelight.common.registry.ModItems.ROTTEN_TOMATO.get()));
                return new Picked(out, () -> {
                    level.playSound(null, pos, vectorwing.farmersdelight.common.registry.ModSounds.BLOCK_TOMATOES_PICK_TOMATOES.get(), SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
                    level.setBlock(pos, state.setValue(tomato.getAgeProperty(), 0), 2);
                });
            }
            return new Picked(drops, () -> level.destroyBlock(pos, false));
        }
    }
}
