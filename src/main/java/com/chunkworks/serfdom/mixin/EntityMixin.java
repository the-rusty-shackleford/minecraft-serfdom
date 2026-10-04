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

/** A villager's leash is always the chain lead (vanilla refuses villagers a lead), so the lead
 * vanilla drops when a villager's leash breaks, its holder dies or leaves, or its knot goes is the
 * chain. Every one of vanilla's leash drops comes through here. */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void serfdom$chainNotLead(ItemLike item, CallbackInfoReturnable<ItemEntity> cir) {
        if (item == Items.LEAD && (Object) this instanceof Villager villager)
            cir.setReturnValue(villager.spawnAtLocation(Serfdom.CHAIN_LEAD.get()));
    }
}
