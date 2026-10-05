/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A smithing upgrade's three ingredients, which vanilla keeps to itself, for the blacksmith's rule
 * book (D-0002). */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingAccess {
    @Accessor("template") Ingredient serfdom$template();
    @Accessor("base") Ingredient serfdom$base();
    @Accessor("addition") Ingredient serfdom$addition();
}
