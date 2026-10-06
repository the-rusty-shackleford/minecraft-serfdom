/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.job.Jobs;
import com.chunkworks.serfdom.post.WorkPostBlock;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import com.chunkworks.serfdom.post.Posts;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Composition root (D-0001): the Work Post and the chain lead, a worker's saved state and its
 * synced need, the record of placed logs, a worker's activities, the Worker Screen's menu, the
 * server config, the job data, and the events that hire, assign, link and keep the post's storage
 * index; and (D-0006) the For Sale block, its menu and point of interest, every villager's purse,
 * household and basket, the shop activity, and the economy's listeners; and (D-0007) the defence
 * activity, a defender's arms, and the defence's listeners. */
@Mod(Serfdom.ID)
public final class Serfdom {
    public static final String ID = "serfdom";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ID);
    private static final DeferredRegister<Activity> ACTIVITIES = DeferredRegister.create(Registries.ACTIVITY, ID);
    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    private static final DeferredRegister<net.minecraft.world.entity.ai.village.poi.PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, ID);

    public static final DeferredBlock<WorkPostBlock> WORK_POST = BLOCKS.registerBlock("work_post", WorkPostBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion());
    public static final DeferredItem<BlockItem> WORK_POST_ITEM = ITEMS.registerSimpleBlockItem(WORK_POST);
    public static final DeferredItem<ChainLead> CHAIN_LEAD = ITEMS.registerItem("chain_lead", ChainLead::new, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkPostBlockEntity>> WORK_POST_ENTITY =
            BLOCK_ENTITIES.register("work_post", () -> BlockEntityType.Builder.of(WorkPostBlockEntity::new, WORK_POST.get()).build(null));

    /** The For Sale block (D-0006): a counter that sells one item to villagers. */
    public static final DeferredBlock<com.chunkworks.serfdom.market.ForSaleBlock> FOR_SALE = BLOCKS.registerBlock("for_sale", com.chunkworks.serfdom.market.ForSaleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava().noOcclusion());
    public static final DeferredItem<BlockItem> FOR_SALE_ITEM = ITEMS.registerSimpleBlockItem(FOR_SALE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.chunkworks.serfdom.market.ForSaleBlockEntity>> FOR_SALE_ENTITY =
            BLOCK_ENTITIES.register("for_sale", () -> BlockEntityType.Builder.of(com.chunkworks.serfdom.market.ForSaleBlockEntity::new, FOR_SALE.get()).build(null));
    /** Where villagers find For Sale blocks: the game's index of points of interest, never a scan. No
     * villager takes a ticket on one. */
    public static final DeferredHolder<net.minecraft.world.entity.ai.village.poi.PoiType, net.minecraft.world.entity.ai.village.poi.PoiType> FOR_SALE_POI = POIS.register("for_sale",
            () -> new net.minecraft.world.entity.ai.village.poi.PoiType(com.google.common.collect.ImmutableSet.copyOf(FOR_SALE.get().getStateDefinition().getPossibleStates()), 0, 1));

    /** An owned villager's state; absent or {@link Worker#NONE} on a free villager. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Worker>> WORKER = ATTACHMENTS.register("worker",
            () -> AttachmentType.builder(() -> Worker.NONE).serialize(Worker.CODEC, w -> w.owned()).build());
    /** The need a worker shows, as {@link com.chunkworks.serfdom.domain.Need#code}; synced to the
     * players that see it, never saved (a worker works it out again as it ticks). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Byte>> NEED = ATTACHMENTS.register("need",
            () -> AttachmentType.builder(() -> (byte) 0).sync(ByteBufCodecs.BYTE).build());
    /** The logs players placed in a chunk, as packed block positions. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LongOpenHashSet>> PLACED_LOGS = ATTACHMENTS.register("placed_logs",
            () -> AttachmentType.builder(() -> new LongOpenHashSet()).serialize(PlacedLogs.CODEC, s -> !s.isEmpty()).build());

    /** A worker's hunger and meal times (D-0005), saved with it; changed every few seconds, so kept
     * apart from its {@link Worker} record. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Appetite.Belly>> BELLY = ATTACHMENTS.register("belly",
            () -> AttachmentType.builder(() -> Appetite.Belly.FULL).serialize(Appetite.Belly.CODEC).build());

    /** Every villager's purse and shopping day (D-0006), saved with it. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.chunkworks.serfdom.market.Purses.Saved>> PURSE = ATTACHMENTS.register("purse",
            () -> AttachmentType.builder(() -> com.chunkworks.serfdom.market.Purses.Saved.EMPTY).serialize(com.chunkworks.serfdom.market.Purses.Saved.CODEC).build());
    /** A free villager's household (D-0006): what it bought and has at home. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.chunkworks.serfdom.domain.Household>> HOUSEHOLD = ATTACHMENTS.register("household",
            () -> AttachmentType.builder(() -> com.chunkworks.serfdom.domain.Household.EMPTY).serialize(com.chunkworks.serfdom.market.Households.CODEC).build());
    /** What a shopper carries home (D-0006), saved so a trip cut short loses nothing. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.chunkworks.serfdom.market.Baskets.Basket>> BASKET = ATTACHMENTS.register("basket",
            () -> AttachmentType.builder(() -> new com.chunkworks.serfdom.market.Baskets.Basket(java.util.List.of(), com.chunkworks.serfdom.domain.Shopping.Dest.HOME))
                    .serialize(com.chunkworks.serfdom.market.Baskets.Basket.CODEC, b -> !b.empty()).build());

    /** What a defender took from its chests (D-0007), saved so a reload or an unload loses nothing. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.chunkworks.serfdom.defence.Arms>> ARMS = ATTACHMENTS.register("arms",
            () -> AttachmentType.builder(() -> com.chunkworks.serfdom.defence.Arms.NONE).serialize(com.chunkworks.serfdom.defence.Arms.CODEC, a -> !a.blank()).build());

    /** Whether the chain is on a villager (D-0003); synced to the players that see it, so their
     * game draws the cuffs and knows a click on the trailer is a load. Never saved: the worker's
     * state is what is saved, and this is set from it as the villager joins. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> CUFFED = ATTACHMENTS.register("cuffed",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /** A worker's shift at its post. */
    public static final DeferredHolder<Activity, Activity> WORK = ACTIVITIES.register("work", () -> new Activity("serfdom_work"));
    /** A hired villager without a bed following its owner. */
    public static final DeferredHolder<Activity, Activity> FOLLOW = ACTIVITIES.register("follow", () -> new Activity("serfdom_follow"));
    /** A captive's idle hours: standing about near where it is (D-0003). */
    public static final DeferredHolder<Activity, Activity> STAY = ACTIVITIES.register("stay", () -> new Activity("serfdom_stay"));
    /** A villager in chains: still, unless its chain's holder leads it. */
    public static final DeferredHolder<Activity, Activity> HELD = ACTIVITIES.register("held", () -> new Activity("serfdom_held"));
    /** A captive walking home to the village it was taken from. */
    public static final DeferredHolder<Activity, Activity> ESCAPE = ACTIVITIES.register("escape", () -> new Activity("serfdom_escape"));

    /** A worker's meal (D-0005): it takes over the worker's day as a panic does, and gives it back. */
    public static final DeferredHolder<Activity, Activity> MEAL = ACTIVITIES.register("meal", () -> new Activity("serfdom_meal"));

    /** A shopping trip (D-0006): it takes over a villager's day as a meal does, and gives it back. */
    public static final DeferredHolder<Activity, Activity> SHOP = ACTIVITIES.register("shop", () -> new Activity("serfdom_shop"));

    /** A worker's defence in a raid (D-0007): arming, fighting, standing by, putting back. */
    public static final DeferredHolder<Activity, Activity> DEFEND = ACTIVITIES.register("defend", () -> new Activity("serfdom_defend"));

    /** One note of a captive's work song: the villager's own hum, heard within eight blocks. */
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> HUM = SOUNDS.register("captive.hum",
            () -> net.minecraft.sounds.SoundEvent.createFixedRangeEvent(id("captive.hum"), 8.0F));

    /** The Worker Screen's menu: the worker's four armour slots over the player's inventory (D-0004). */
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<WorkerMenu>> WORKER_MENU = MENUS.register("worker",
            () -> new net.minecraft.world.inventory.MenuType<>(WorkerMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    /** The For Sale block's menu (D-0006). */
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.chunkworks.serfdom.market.ForSaleMenu>> FOR_SALE_MENU = MENUS.register("for_sale",
            () -> new net.minecraft.world.inventory.MenuType<>(com.chunkworks.serfdom.market.ForSaleMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    /** requires: the mod bus and container; effects: registers content, config and listeners. */
    public Serfdom(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ATTACHMENTS.register(bus);
        ACTIVITIES.register(bus);
        SOUNDS.register(bus);
        MENUS.register(bus);
        POIS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, SerfdomConfig.SPEC);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) { e.accept(WORK_POST_ITEM); e.accept(FOR_SALE_ITEM); }
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(CHAIN_LEAD);
        });
        // "6": D-0006's purse, ledger and Worker Screen purse row. "7": D-0008's farm line on the post's screen.
        bus.addListener((RegisterPayloadHandlersEvent e) -> {
            var registrar = e.registrar("7");
            Screens.register(registrar);
            com.chunkworks.serfdom.market.Market.register(registrar);
        });
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new Jobs()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Hire.registerCommand(e.getDispatcher()));
        Hire.listen();
        Picks.listen();
        Posts.listen();
        com.chunkworks.serfdom.job.Holding.listen();
        com.chunkworks.serfdom.post.Farms.listen();
        PlacedLogs.listen();
        Workers.listen();
        Captures.listen();
        Remedies.listen();
        com.chunkworks.serfdom.compat.LawCompat.listen();
        com.chunkworks.serfdom.compat.WheelsCompat.register();
        com.chunkworks.serfdom.market.Market.listen(bus);
        com.chunkworks.serfdom.defence.Defenders.listen(bus);
    }

    /** Professions no villager can be hired with. */
    public static final Set<String> NO_TRADE = Set.of("minecraft:none", "minecraft:nitwit");
}
