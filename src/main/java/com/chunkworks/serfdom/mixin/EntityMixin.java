/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.Serfdom;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A villager's leash is always the chain lead (vanilla refuses villagers a lead). The chain is
 * the cuffs (D-0003): when vanilla drops the lead because a cuffed villager's leash broke, or its
 * holder died or left, the chain stays on the villager and nothing drops; it drops with the
 * villager if the villager dies. A leashed villager out of chains, which Serfdom never makes, drops
 * the chain rather than a lead. Every one of vanilla's leash drops comes through here. */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void serfdom$chainNotLead(ItemLike item, CallbackInfoReturnable<ItemEntity> cir) {
        if (item != Items.LEAD || !((Object) this instanceof Villager villager)) return;
        if (com.chunkworks.serfdom.Workers.cuffed(villager)) cir.setReturnValue(null);
        else cir.setReturnValue(villager.spawnAtLocation(Serfdom.CHAIN_LEAD.get()));
    }
}
