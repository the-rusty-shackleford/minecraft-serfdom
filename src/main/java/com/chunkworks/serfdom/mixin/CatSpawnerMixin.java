/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.Workers;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.CatSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A captive's bed does not count toward a village's cats (D-0003, Rusty's call). Vanilla spawns a
 * cat where more than four occupied beds lie within 48 blocks; the captives' beds there are taken
 * off that count. */
@Mixin(CatSpawner.class)
abstract class CatSpawnerMixin {
    @ModifyExpressionValue(method = "spawnInVillage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getCountInRange(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)J"))
    private long serfdom$withoutCaptivesBeds(long beds, ServerLevel level, BlockPos pos) {
        return beds - Workers.captiveBedsNear(level, pos, 48);
    }
}
