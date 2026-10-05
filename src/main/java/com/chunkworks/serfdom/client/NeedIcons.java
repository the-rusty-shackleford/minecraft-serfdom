/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Need;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** A worker's need as a small icon over its head (D-0001), only when it lacks something and only
 * close up: within {@link #RANGE} blocks of the camera, and never over its portrait on the Worker
 * Screen, whose details say it. The icons are one sheet,
 * {@code textures/gui/needs.png}, one 16-pixel square per {@link Need} in declaration order. */
public final class NeedIcons {
    static final double RANGE = 8.0;
    private static final ResourceLocation SHEET = Serfdom.id("textures/gui/needs.png");
    private static final float SIZE = 0.35F;
    private NeedIcons() {}

    static void render(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.isInvisible() || WorkerScreen.drawingPortrait()) return;
        var need = Workers.shownNeed(villager);
        if (need.isEmpty()) return;
        var dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher.distanceToSqr(villager) > RANGE * RANGE) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, villager.getBbHeight() + (villager.hasCustomName() ? 0.75 : 0.5), 0.0);
        pose.mulPose(dispatcher.cameraOrientation());
        var buffer = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(SHEET));
        var m = pose.last();
        int count = Need.values().length;
        float u0 = (float) need.get().ordinal() / count, u1 = (float) (need.get().ordinal() + 1) / count;
        float h = SIZE / 2;
        int light = event.getPackedLight();
        vertex(buffer, m, -h, -h, u1, 1, light);
        vertex(buffer, m, h, -h, u0, 1, light);
        vertex(buffer, m, h, h, u0, 0, light);
        vertex(buffer, m, -h, h, u1, 0, light);
        pose.popPose();
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose m, float x, float y, float u, float v, int light) {
        buffer.addVertex(m, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, 0.0F, 1.0F, 0.0F);
    }
}
