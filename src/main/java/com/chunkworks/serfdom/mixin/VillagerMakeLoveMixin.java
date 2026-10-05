/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.Workers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An owned child keeps the bed it is born into (D-0003): vanilla takes a vacant bed's ticket for the
 * child and gives it the bed here; an owned child records it as its own, as a given bed is. */
@Mixin(VillagerMakeLove.class)
abstract class VillagerMakeLoveMixin {
    @Inject(method = "giveBedToChild", at = @At("TAIL"))
    private void serfdom$ownedChildsBed(ServerLevel level, Villager child, BlockPos pos, CallbackInfo ci) {
        Workers.bornInto(child, GlobalPos.of(level.dimension(), pos));
    }
}
