/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Serfdom;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** The client's registrations: the two screens take what the server sends; need icons and post
 * outlines draw with the world; a villager in chains wears its cuffs. */
@EventBusSubscriber(modid = Serfdom.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Screens.Client.receivers(WorkerScreen::accept, PostScreen::accept);
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?> e) -> NeedIcons.render(e));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> PostOutline.render(e));
    }
    @SubscribeEvent public static void layers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        var renderer = event.getRenderer(net.minecraft.world.entity.EntityType.VILLAGER);
        if (renderer instanceof net.minecraft.client.renderer.entity.VillagerRenderer villagers) villagers.addLayer(new CuffsLayer(villagers));
    }
}
