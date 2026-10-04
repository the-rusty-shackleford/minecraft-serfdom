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
 * synced need, the record of placed logs, a worker's two activities, the server config, the job
 * data, and the events that hire, assign, link and keep the post's storage index. */
@Mod(Serfdom.ID)
public final class Serfdom {
    public static final String ID = "serfdom";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ID);
    private static final DeferredRegister<Activity> ACTIVITIES = DeferredRegister.create(Registries.ACTIVITY, ID);

    public static final DeferredBlock<WorkPostBlock> WORK_POST = BLOCKS.registerBlock("work_post", WorkPostBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion());
    public static final DeferredItem<BlockItem> WORK_POST_ITEM = ITEMS.registerSimpleBlockItem(WORK_POST);
    public static final DeferredItem<ChainLead> CHAIN_LEAD = ITEMS.registerItem("chain_lead", ChainLead::new, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkPostBlockEntity>> WORK_POST_ENTITY =
            BLOCK_ENTITIES.register("work_post", () -> BlockEntityType.Builder.of(WorkPostBlockEntity::new, WORK_POST.get()).build(null));

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

    /** A worker's shift at its post. */
    public static final DeferredHolder<Activity, Activity> WORK = ACTIVITIES.register("work", () -> new Activity("serfdom_work"));
    /** A hired villager without a bed following its owner. */
    public static final DeferredHolder<Activity, Activity> FOLLOW = ACTIVITIES.register("follow", () -> new Activity("serfdom_follow"));

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    /** requires: the mod bus and container; effects: registers content, config and listeners. */
    public Serfdom(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ATTACHMENTS.register(bus);
        ACTIVITIES.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, SerfdomConfig.SPEC);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(WORK_POST_ITEM);
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(CHAIN_LEAD);
        });
        bus.addListener((RegisterPayloadHandlersEvent e) -> Screens.register(e.registrar("1")));
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new Jobs()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Hire.registerCommand(e.getDispatcher()));
        Hire.listen();
        Picks.listen();
        Posts.listen();
        PlacedLogs.listen();
        Workers.listen();
    }

    /** Professions no villager can be hired with. */
    public static final Set<String> NO_TRADE = Set.of("minecraft:none", "minecraft:nitwit");
}
