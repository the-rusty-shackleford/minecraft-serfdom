/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.market.ForSaleBlockEntity;
import com.chunkworks.serfdom.market.Stalls;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** What a For Sale block shows (D-0006): the item it sells turning slowly over its counter, for
 * players and villagers alike; and to a player looking at it within {@link #READ} blocks, what it
 * sells in words above it ("8 Bread for 1 emerald", or "Sold out"), as a name tag is drawn. */
public final class ForSaleRenderer implements BlockEntityRenderer<ForSaleBlockEntity> {
    static final double READ = 8.0;
    private final ItemRenderer items;
    private final Font font;

    public ForSaleRenderer(BlockEntityRendererProvider.Context ctx) {
        items = ctx.getItemRenderer();
        font = ctx.getFont();
    }

    @Override public void render(ForSaleBlockEntity stall, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var sells = stall.template();
        if (sells.isEmpty() || stall.getLevel() == null) return;
        float turn = (stall.getLevel().getGameTime() + partialTick) * 2.0F;
        pose.pushPose();
        pose.translate(0.5, 0.98, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn % 360));
        pose.scale(0.5F, 0.5F, 0.5F);
        items.renderStatic(sells, ItemDisplayContext.FIXED, light, overlay, pose, buffers, stall.getLevel(), 0);
        pose.popPose();
        if (lookedAt(stall)) label(stall, pose, buffers, light);
    }

    /** effects: true iff the player looks at the stall within reading distance. */
    private static boolean lookedAt(ForSaleBlockEntity stall) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        return hit.getBlockPos().equals(stall.getBlockPos()) && mc.player.distanceToSqr(stall.getBlockPos().getCenter()) <= READ * READ;
    }

    private void label(ForSaleBlockEntity stall, PoseStack pose, MultiBufferSource buffers, int light) {
        var text = Stalls.label(stall);
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.pushPose();
        pose.translate(0.5, 1.45, 0.5);
        pose.mulPose(camera);
        pose.scale(0.025F, -0.025F, 0.025F);
        float x = -font.width(text) / 2F;
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255F) << 24;
        font.drawInBatch(text, x, 0, stall.clientOpen() ? 0xFFFFFFFF : 0xFFFF8080, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, background, light);
        pose.popPose();
    }
}
