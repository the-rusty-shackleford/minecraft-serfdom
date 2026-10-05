/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Serfdom;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client's registrations: the two screens take what the server sends, the Worker Screen as
 * its menu's screen; need icons and post outlines draw with the world; a villager wears its armour,
 * an elytra and its cuffs, its robe and hat dressed for what it wears; and (D-0006) the For Sale
 * block's screen, ledger and item above it, and the purse over the trade screen. */
@EventBusSubscriber(modid = Serfdom.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Screens.Client.receivers(WorkerScreen::accept, PostScreen::accept);
        com.chunkworks.serfdom.market.Market.Client.receivers(PurseLabel::accept, ForSaleScreen::accept);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e) -> PurseLabel.render(e));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Closing e) -> PurseLabel.closing(e));
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?> e) -> NeedIcons.render(e));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> PostOutline.render(e));
        // Last, so a draw another mod cancels leaves the model as it was.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderLivingEvent.Pre<?, ?> e) -> Dress.before(e));
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?> e) -> Dress.after(e));
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(Serfdom.WORKER_MENU.get(), WorkerScreen::new);
        event.register(Serfdom.FOR_SALE_MENU.get(), ForSaleScreen::new);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Serfdom.FOR_SALE_ENTITY.get(), ForSaleRenderer::new);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
        var renderer = event.getRenderer(EntityType.VILLAGER);
        if (renderer instanceof VillagerRenderer villagers) {
            villagers.addLayer(new VillagerArmourLayer(villagers, event.getContext()));
            villagers.addLayer(new VillagerElytraLayer(villagers, event.getEntityModels()));
            villagers.addLayer(new CuffsLayer(villagers));
        }
    }
}
