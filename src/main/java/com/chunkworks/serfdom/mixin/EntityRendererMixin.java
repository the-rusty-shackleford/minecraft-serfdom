/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import com.chunkworks.serfdom.client.ChainLook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** A villager's leash is the chain lead, drawn in iron instead of rope: vanilla's leash with its
 * two alternating shades turned into dark and light links. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "renderLeash", at = @At("HEAD"))
    private void serfdom$chainOn(Entity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, Entity holder, CallbackInfo ci) {
        ChainLook.drawing(entity instanceof Villager);
    }

    @Inject(method = "renderLeash", at = @At("RETURN"))
    private void serfdom$chainOff(Entity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, Entity holder, CallbackInfo ci) {
        ChainLook.drawing(false);
    }

    @ModifyArgs(method = "addVertexPair", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static void serfdom$chainColour(Args args) {
        if (!ChainLook.drawing()) return;
        float[] c = ChainLook.colour(args.get(0));
        args.set(0, c[0]);
        args.set(1, c[1]);
        args.set(2, c[2]);
    }
}
