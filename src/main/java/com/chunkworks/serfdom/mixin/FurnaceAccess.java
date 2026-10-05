/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What a worker loading a furnace, blast furnace or smoker needs to know of it (D-0002): the ticks
 * its fire still burns, and how long a fuel burns in it (a blast furnace and a smoker halve it). */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccess {
    @Accessor("litTime") int serfdom$litTime();
    @Invoker("getBurnDuration") int serfdom$burnDuration(ItemStack fuel);
}
