/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.domain.Assignment;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.post.Posts;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Owned villagers (D-0001): reading and changing a worker's state, hiring, beds, posts, the need
 * it shows, and the gestures on it. Server side unless said otherwise. */
public final class Workers {
    private Workers() {}

    /** effects: the villager's state; {@link Worker#NONE} when it is free. Either side. */
    public static Worker of(Villager villager) { return villager.getExistingData(Serfdom.WORKER).orElse(Worker.NONE); }

    /** effects: true iff the villager is somebody's worker and the workers module is on. Either
     * side: the module switch is the server's, so a client counts every owned villager as one. */
    public static boolean owned(Villager villager) {
        if (!of(villager).owned()) return false;
        return villager.level().isClientSide() || !SerfdomConfig.SPEC.isLoaded() || SerfdomConfig.WORKERS.get();
    }

    /** effects: saves {@code worker} as the villager's state and rebuilds its brain, so its
     * schedule follows the new state. */
    public static void set(ServerLevel level, Villager villager, Worker worker) {
        if (worker.owned()) villager.setData(Serfdom.WORKER, worker);
        else villager.removeData(Serfdom.WORKER);
        villager.refreshBrain(level);
        showNeed(villager);
    }

    /** effects: makes {@code villager} {@code player}'s worker: its old village's bed, workstation
     * and bell are given back, its profession is kept, and it follows the player until it has a
     * bed. */
    public static void hire(ServerLevel level, Villager villager, ServerPlayer player, Optional<String> homeVillage) {
        for (var memory : java.util.List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT)) {
            villager.releasePoi(memory);
            villager.getBrain().eraseMemory(memory);
        }
        set(level, villager, Worker.hired(player.getUUID(), player.getGameProfile().getName(), homeVillage));
    }

    // ---- beds -------------------------------------------------------------------------------

    /** The answer to a bed or a post being picked. */
    public enum Picked { OK, NOT_A_BED, TAKEN, TOO_FAR, NO_BED, POST_FULL, NOT_YOUR_POST, MISSING }

    /** requires: bed is a bed's head. effects: gives the worker this bed when it is free or its
     * own: takes the bed's ticket so no villager claims it, gives back the bed it had, and drops
     * its job when the post is out of reach of the new bed. */
    public static Picked assignBed(ServerLevel level, Villager villager, BlockPos bed) {
        var pois = level.getPoiManager();
        if (pois.getType(bed).filter(t -> t.is(PoiTypes.HOME)).isEmpty()) return Picked.NOT_A_BED;
        var worker = of(villager);
        var target = GlobalPos.of(level.dimension(), bed);
        if (!worker.bed().equals(Optional.of(target))) {
            if (pois.take(t -> t.is(PoiTypes.HOME), (t, p) -> p.equals(bed), bed, 1).isEmpty()) return Picked.TAKEN;
            releaseBed(level, villager, worker);
        }
        villager.getBrain().setMemory(MemoryModuleType.HOME, target);
        var before = worker.assignment();
        var after = before.withBed(Worker.spot(target));
        if (before.post().isPresent() && after.post().isEmpty()) leavePost(level, villager, worker);
        set(level, villager, worker.with(after));
        return Picked.OK;
    }

    private static void releaseBed(ServerLevel level, Villager villager, Worker worker) {
        worker.bed().ifPresent(old -> {
            var at = level.getServer().getLevel(old.dimension());
            if (at != null && at.isLoaded(old.pos()) && at.getPoiManager().getType(old.pos()).filter(t -> t.is(PoiTypes.HOME)).isPresent())
                at.getPoiManager().release(old.pos());
        });
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
    }

    /** effects: the worker has lost its bed (broken, or taken while it was away): it keeps its job
     * and shows that it has no bed. */
    public static void bedLost(ServerLevel level, Villager villager) {
        var worker = of(villager);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        set(level, villager, worker.with(worker.assignment().withoutBed()));
    }

    // ---- posts ------------------------------------------------------------------------------

    /** effects: links the worker to the post at {@code pos} for {@code player}: refused for a post
     * that is not the player's, without a bed, more than 48 blocks from the bed, or with four
     * others on it. */
    public static Picked link(ServerLevel level, Villager villager, ServerPlayer player, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof WorkPostBlockEntity post)) return Picked.MISSING;
        if (!post.ownedBy(player.getUUID())) return Picked.NOT_YOUR_POST;
        var worker = of(villager);
        var spot = Worker.spot(GlobalPos.of(level.dimension(), pos));
        int others = (int) post.workers().stream().filter(u -> !u.equals(villager.getUUID())).count();
        var out = worker.assignment().link(spot, others);
        if (!out.ok()) return switch (out.refusal().orElseThrow()) {
            case NO_BED -> Picked.NO_BED;
            case TOO_FAR -> Picked.TOO_FAR;
            case POST_FULL -> Picked.POST_FULL;
        };
        if (!worker.post().equals(Optional.of(GlobalPos.of(level.dimension(), pos)))) leavePost(level, villager, worker);
        post.addWorker(villager.getUUID(), name(villager));
        set(level, villager, worker.with(out.assignment()));
        return Picked.OK;
    }

    /** effects: the worker leaves its job and lives as a villager at the base. */
    public static void clearJob(ServerLevel level, Villager villager) {
        var worker = of(villager);
        leavePost(level, villager, worker);
        set(level, villager, worker.with(worker.assignment().withoutPost()));
    }

    private static void leavePost(ServerLevel level, Villager villager, Worker worker) {
        worker.post().flatMap(p -> Posts.loaded(level.getServer(), p)).ifPresent(post -> post.removeWorker(villager.getUUID()));
    }

    /** effects: a dead worker leaves its post now when it is loaded, else when it next loads. */
    private static void departPost(ServerLevel level, Villager villager, Worker worker) {
        worker.post().ifPresent(p -> Posts.loaded(level.getServer(), p).ifPresentOrElse(
                post -> post.removeWorker(villager.getUUID()),
                () -> com.chunkworks.serfdom.post.Departed.get(level.getServer()).add(p, villager.getUUID())));
    }

    /** effects: the name a worker goes by: its custom name, else its profession. Either side. */
    public static Component name(Villager villager) { return villager.getName(); }

    /** effects: the villager's profession as its title reads ("Farmer", "Woodworker"), by the key
     * vanilla and NeoForge give it: {@code entity.minecraft.villager.[<namespace>.]<path>}. */
    public static Component profession(Villager villager) {
        var id = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        return Component.translatable("entity.minecraft.villager." + ("minecraft".equals(id.getNamespace()) ? "" : id.getNamespace() + ".") + id.getPath());
    }

    // ---- needs ------------------------------------------------------------------------------

    private static final java.util.Map<UUID, Need> SHIFT_NEEDS = new java.util.concurrent.ConcurrentHashMap<>();

    /** effects: records the need the worker's shift is stuck on (empty when it is not stuck) and
     * shows the first of its needs. */
    public static void shiftNeed(Villager villager, Optional<Need> need) {
        if (need.isPresent()) SHIFT_NEEDS.put(villager.getUUID(), need.get());
        else SHIFT_NEEDS.remove(villager.getUUID());
        showNeed(villager);
    }

    /** effects: the needs the worker has now. */
    public static Set<Need> needs(Villager villager) {
        var worker = of(villager);
        var needs = EnumSet.noneOf(Need.class);
        if (!worker.owned()) return needs;
        if (worker.bed().isEmpty()) needs.add(Need.NO_BED);
        var shift = SHIFT_NEEDS.get(villager.getUUID());
        if (shift != null) needs.add(shift);
        return needs;
    }

    /** effects: sets the synced need to the first of the worker's needs, telling the players who
     * see it only when it changed. */
    public static void showNeed(Villager villager) {
        byte code = Need.code(Need.shown(needs(villager)));
        if (villager.getData(Serfdom.NEED) != code) {
            villager.setData(Serfdom.NEED, code);
            villager.syncData(Serfdom.NEED);
        }
    }

    /** effects: the need a client shows over the villager. Either side. */
    public static Optional<Need> shownNeed(Villager villager) { return Need.of(villager.getData(Serfdom.NEED)); }

    // ---- the tool between shifts ------------------------------------------------------------

    /** effects: puts the tool the worker keeps into its hand for the shift. */
    public static void equip(Villager villager) {
        var worker = of(villager);
        if (worker.tool().isEmpty()) return;
        var hand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!hand.isEmpty()) villager.getInventory().addItem(hand);
        villager.setItemSlot(EquipmentSlot.MAINHAND, worker.tool());
        villager.setData(Serfdom.WORKER, worker.withTool(ItemStack.EMPTY));
    }

    /** effects: takes the tool out of the worker's hand and keeps it until the next shift, so
     * nothing a villager does with its hand between shifts (showing its trades) touches it. */
    public static void stash(Villager villager) {
        var worker = of(villager);
        var hand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (hand.isEmpty() || !worker.owned()) return;
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (worker.tool().isEmpty()) villager.setData(Serfdom.WORKER, worker.withTool(hand));
        else villager.getInventory().addItem(hand);
    }

    // ---- gestures and death -------------------------------------------------------------------

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Workers::interact);
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> { if (e.getEntity() instanceof Villager v && !v.level().isClientSide && of(v).owned()) stash(v); });
        NeoForge.EVENT_BUS.addListener(Workers::drops);
    }

    /** effects: sneak-use on an owned villager opens the Worker Screen for its owner and names the
     * owner to anyone else; the chain lead leads a worker or lets it go. Village Deed's offer and
     * Thief's gift never see an owned villager. */
    static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Villager villager)) return;
        var held = event.getItemStack();
        boolean chain = held.is(Serfdom.CHAIN_LEAD.get());
        if (!owned(villager)) {
            if (chain) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (event.getEntity() instanceof ServerPlayer player)
                    player.displayClientMessage(Component.translatable("message.serfdom.chain.not_a_worker").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        if (!chain && !event.getEntity().isSecondaryUseActive()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || !(villager.level() instanceof ServerLevel level)) return;
        if (chain) { ChainLead.use(level, villager, player, held); return; }
        var worker = of(villager);
        if (worker.ownedBy(player.getUUID())) Screens.openWorker(player, villager);
        else player.displayClientMessage(Component.translatable("message.serfdom.someones_worker", worker.ownerName()).withStyle(ChatFormatting.GRAY), true);
    }

    /** effects: a worker that dies drops the tool it keeps and everything it carries. */
    static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) return;
        var worker = of(villager);
        if (!worker.owned()) return;
        var out = new java.util.ArrayList<ItemStack>();
        if (!worker.tool().isEmpty()) out.add(worker.tool());
        out.addAll(villager.getInventory().removeAllItems());
        for (var stack : out) event.getDrops().add(new ItemEntity(villager.level(), villager.getX(), villager.getY(), villager.getZ(), stack));
        villager.setData(Serfdom.WORKER, worker.withTool(ItemStack.EMPTY));
        if (villager.level() instanceof ServerLevel level) departPost(level, villager, worker);
    }

    /** effects: whether the stack is the job's tool: it lies in {@code tag}. */
    public static boolean serves(ItemStack stack, Optional<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> tag) {
        return tag.isEmpty() || (!stack.isEmpty() && stack.is(tag.get()));
    }

    /** Emeralds, the fee's coin. */
    public static boolean emerald(ItemStack stack) { return stack.is(Items.EMERALD); }
}
