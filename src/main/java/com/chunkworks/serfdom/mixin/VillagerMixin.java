/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.WorkerBrain;
import com.chunkworks.serfdom.behavior.WorkerNavigation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An owned villager's brain is the worker's (D-0001). Vanilla builds a villager's brain here on
 * spawn, on load and on every profession change; a worker's state is loaded before its brain.
 * Every villager walks with {@link WorkerNavigation}, which is vanilla's but for a worker. */
@Mixin(Villager.class)
abstract class VillagerMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/npc/VillagerType;)V", at = @At("RETURN"))
    private void serfdom$navigation(EntityType<? extends Villager> type, Level level, VillagerType villagerType, CallbackInfo ci) {
        var self = (Villager) (Object) this;
        ((MobAccessor) self).serfdom$setNavigation(new WorkerNavigation(self, level));
    }

    @Inject(method = "registerBrainGoals", at = @At("HEAD"), cancellable = true)
    private void serfdom$workerBrain(Brain<Villager> brain, CallbackInfo ci) {
        if (WorkerBrain.register((Villager) (Object) this, brain)) ci.cancel();
    }
}
