/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.market.Prices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No structure search while the base values sample the price lists (D-0009). A map listing asked
 * for its offer searches the world for its structure, marks the one it finds as taken, and saves a
 * new map: vanilla's cartographer and Backport's explorer maps alike. Answered "none", every such
 * listing gives no offer, before it searches or draws anything. Every other search is vanilla's. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void serfdom$noSearchWhileSampling(TagKey<Structure> tag, BlockPos pos, int radius, boolean skipKnown, CallbackInfoReturnable<BlockPos> cir) {
        if (Prices.refusesSearch()) cir.setReturnValue(null);
    }
}
