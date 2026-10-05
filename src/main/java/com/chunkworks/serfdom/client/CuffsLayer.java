/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Workers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;

/** The cuffs (D-0003): a villager in chains wears a short length of vanilla's chain across the front
 * of its crossed forearms, so anyone can see it is cuffed whether or not someone holds the chain.
 * The chain block's own model and texture, laid on its side; no art of ours. */
public final class CuffsLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    /** The chain's length across the forearms, as a share of a block: eight of the model's pixels. */
    private static final float SCALE = 0.5F;
    /** Where the chain lies, in the arms' own frame (pixels): the middle of the forearms' front face. */
    private static final float Y = 4.0F, Z = -2.4F;

    public CuffsLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) { super(parent); }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
                                 float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!Workers.cuffed(villager) || villager.isInvisible()) return;
        pose.pushPose();
        getParentModel().root().getChild("arms").translateAndRotate(pose);
        pose.translate(0.0F, Y / 16.0F, Z / 16.0F);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X),
                pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
