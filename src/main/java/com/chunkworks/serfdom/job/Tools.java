/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.domain.Pace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/** A worker's action time with its tool ({@link Pace}). */
final class Tools {
    private Tools() {}

    /** effects: the ticks breaking {@code state} at {@code pos} takes a worker holding {@code tool}
     * at {@code speed}: a player's time with the same tool and its Efficiency, never under the
     * configured floor. */
    static int ticks(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, double speed) {
        float hardness = Math.max(0.0F, state.getDestroySpeed(level, pos));
        var efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        float toolSpeed = Pace.withEfficiency(tool.getDestroySpeed(state), EnchantmentHelper.getItemEnchantmentLevel(efficiency, tool));
        boolean drops = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        return Pace.ticks(hardness, Math.max(toolSpeed, 0.01F), drops, SerfdomConfig.ACTION_FLOOR_TICKS.get(), speed);
    }

    /** effects: the ticks a quick action (a harvest) takes at {@code speed}: the configured floor. */
    static int quick(double speed) {
        return Pace.ticks(0.0F, 1.0F, true, SerfdomConfig.ACTION_FLOOR_TICKS.get(), speed);
    }
}
