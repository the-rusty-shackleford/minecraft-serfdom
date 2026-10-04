/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets a mob's navigation after it is made: a villager's becomes the worker's. */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("navigation") void serfdom$setNavigation(PathNavigation navigation);
}
