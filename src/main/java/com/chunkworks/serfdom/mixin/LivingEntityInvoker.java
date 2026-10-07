/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Advances a living entity's swing, which vanilla does only for monsters and players (D-0011). */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("updateSwingTime") void serfdom$updateSwingTime();
}
