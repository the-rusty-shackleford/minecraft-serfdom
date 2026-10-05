/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.client.Dress;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A villager wearing something on its head shows no hat under it (D-0004): the profession layer
 * sets the hat's visibility every draw, so the choice is made after it, from {@link Dress}. */
@Mixin(VillagerModel.class)
abstract class VillagerModelMixin {
    @Shadow @Final private ModelPart hat;
    @Shadow @Final private ModelPart hatRim;

    @Inject(method = "hatVisible", at = @At("TAIL"))
    private void serfdom$noHatUnderAHelmet(boolean visible, CallbackInfo ci) {
        if (Dress.headCovered()) {
            hat.visible = false;
            hatRim.visible = false;
        }
    }
}
