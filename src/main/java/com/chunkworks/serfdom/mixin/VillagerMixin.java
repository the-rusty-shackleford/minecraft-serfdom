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

    /** A free adult shops too (D-0006): its shopping time and the shop are added to vanilla's brain.
     * Reached only when the worker's brain did not replace vanilla's. */
    @Inject(method = "registerBrainGoals", at = @At("RETURN"))
    private void serfdom$shopping(Brain<Villager> brain, CallbackInfo ci) {
        com.chunkworks.serfdom.market.Shoppers.addTo((Villager) (Object) this, brain);
    }

    /** As a player starts trading, the trades its purse can't pay are closed (D-0006); after the
     * special prices are set, so a discount counts, and before the offers are sent. */
    @Inject(method = "startTrading", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/Villager;openTradingScreen(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;I)V"))
    private void serfdom$purse(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        com.chunkworks.serfdom.market.Purses.open((Villager) (Object) this, player);
    }

    /** Nobody trading: every trade open again. */
    @Inject(method = "stopTrading", at = @At("HEAD"))
    private void serfdom$purseShut(CallbackInfo ci) {
        com.chunkworks.serfdom.market.Purses.close((Villager) (Object) this);
    }

    /** A captive never wants a golem (D-0003, Rusty's call): it neither summons one, by gossip or in
     * a panic, nor counts toward another villager's summons. Every summons asks this. */
    @Inject(method = "wantsToSpawnGolem", at = @At("HEAD"), cancellable = true)
    private void serfdom$noGolemForCaptives(long gameTime, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        var self = (Villager) (Object) this;
        if (!self.level().isClientSide() && com.chunkworks.serfdom.Workers.of(self).captive()) cir.setReturnValue(false);
    }

    /** A child of two villagers with the same owner is that owner's, hired (D-0003); any other is
     * free. Its bed is given to it as it is born ({@code VillagerMakeLoveMixin}). */
    @Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/Villager;", at = @At("RETURN"))
    private void serfdom$ownedChild(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.AgeableMob other,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Villager> cir) {
        var child = cir.getReturnValue();
        if (child == null || !(other instanceof Villager partner)) return;
        com.chunkworks.serfdom.Workers.born(child, (Villager) (Object) this, partner);
    }
}
