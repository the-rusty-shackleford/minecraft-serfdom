/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.domain.Fit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.npc.Villager;

/** An elytra on a villager (D-0004): vanilla's own layer, hung {@link Fit#ELYTRA_BACK} farther back
 * to clear the robe, and never shrunk a second time for a child (the renderer has already). */
public final class VillagerElytraLayer extends ElytraLayer<Villager, VillagerModel<Villager>> {
    public VillagerElytraLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, EntityModelSet models) { super(parent, models); }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
                                 float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        var model = getParentModel();
        boolean young = model.young;
        model.young = false;
        pose.pushPose();
        pose.translate(0.0F, 0.0F, (float) (Fit.ELYTRA_BACK / 16.0));
        super.render(pose, buffers, light, villager, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        pose.popPose();
        model.young = young;
    }
}
