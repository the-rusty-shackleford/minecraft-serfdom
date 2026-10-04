/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.post.Posts;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** The outline of a Work Post's area (D-0001), drawn for each post near the camera whose outline is
 * switched on: the cube of its radius, in the board's wood colour. */
public final class PostOutline {
    static final double RANGE = 96.0;
    private PostOutline() {}

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        boolean drew = false;
        for (var pos : Posts.client(mc.level.dimension())) {
            if (pos.distToCenterSqr(camera) > RANGE * RANGE) continue;
            if (!(mc.level.getBlockEntity(pos) instanceof WorkPostBlockEntity post) || !post.outline()) continue;
            int r = post.radius();
            var box = new AABB(pos.getX() - r, pos.getY() - r, pos.getZ() - r, pos.getX() + r + 1, pos.getY() + r + 1, pos.getZ() + r + 1)
                    .move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(event.getPoseStack(), lines, box, 0.86F, 0.66F, 0.36F, 1.0F);
            drew = true;
        }
        if (drew) buffers.endBatch(RenderType.lines());
    }
}
