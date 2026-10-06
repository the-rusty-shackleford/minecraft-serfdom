/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.serfdom.compat.DeedCompat;
import com.chunkworks.serfdom.compat.LawCompat;
import com.chunkworks.serfdom.compat.ThiefCompat;
import com.chunkworks.serfdom.domain.Chain;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.domain.Parting;
import com.chunkworks.serfdom.post.Posts;
import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/** Owned villagers (D-0001, D-0003): reading and changing a worker's state, hiring and capture, the
 * chain, freeing, beds, posts, the need it shows, and the gestures on it. Server side unless said
 * otherwise. */
public final class Workers {
    private static final Logger LOG = LogUtils.getLogger();
    /** The modifier a captive walks with: slower by the configured share. */
    private static final ResourceLocation CAPTIVE_PACE = Serfdom.id("captive_pace");
    private Workers() {}

    /** effects: the villager's state; {@link Worker#NONE} when it is free. Server side: the record
     * is not synced. */
    public static Worker of(Villager villager) { return villager.getExistingData(Serfdom.WORKER).orElse(Worker.NONE); }

    /** effects: true iff the villager is somebody's worker and the workers module is on. */
    public static boolean owned(Villager villager) {
        if (!of(villager).owned()) return false;
        return !SerfdomConfig.SPEC.isLoaded() || SerfdomConfig.WORKERS.get();
    }

    /** effects: true iff the chain is on the villager. Either side: the chain is synced. */
    public static boolean cuffed(Villager villager) { return villager.getData(Serfdom.CUFFED); }

    /** effects: saves {@code worker} as the villager's state, tells the players who see it whether
     * it is in chains, and rebuilds its brain, so its schedule follows the new state. */
    public static void set(ServerLevel level, Villager villager, Worker worker) {
        if (worker.owned()) villager.setData(Serfdom.WORKER, worker);
        else {
            villager.removeData(Serfdom.WORKER);
            villager.removeData(Serfdom.BELLY);
        }
        if (villager.getData(Serfdom.CUFFED) != worker.cuffed()) {
            villager.setData(Serfdom.CUFFED, worker.cuffed());
            villager.syncData(Serfdom.CUFFED);
        }
        villager.refreshBrain(level);
        showNeed(villager);
    }

    /** effects: gives back the villager's bed, workstation, potential workstation and bell, so its
     * old village keeps them, as hiring and capture do. */
    private static void leaveVillage(Villager villager) {
        for (var memory : java.util.List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT)) {
            villager.releasePoi(memory);
            villager.getBrain().eraseMemory(memory);
        }
    }

    /** effects: makes {@code villager} {@code player}'s worker: its old village's bed, workstation
     * and bell are given back, its profession is kept, and it follows the player until it has a
     * bed. */
    public static void hire(ServerLevel level, Villager villager, ServerPlayer player, Optional<String> homeVillage) {
        leaveVillage(villager);
        set(level, villager, Worker.hired(player.getUUID(), player.getGameProfile().getName(), homeVillage));
    }

    /** requires: the villager is free and can be taken. effects: makes it {@code player}'s captive,
     * cuffed with the chain in {@code held} (used up outside creative) and led by the player: its
     * old village keeps its bed, workstation and bell; it remembers where it was taken and the
     * village around that, and walks slower. Then the capture's crime is committed, and when an
     * officer saw it the captive is owed to the player's case with the village (D-0003). */
    public static void capture(ServerLevel level, Villager villager, ServerPlayer player, ItemStack held) {
        var at = villager.blockPosition();
        var village = DeedCompat.around(level, at);
        leaveVillage(villager);
        set(level, villager, Worker.captive(player.getUUID(), player.getGameProfile().getName(), GlobalPos.of(level.dimension(), at),
                village.map(DeedCompat.Found::id), village.map(DeedCompat.Found::bounds)));
        pace(villager);
        villager.setLeashedTo(player, true);
        if (!player.getAbilities().instabuild && held.is(Serfdom.CHAIN_LEAD.get())) held.shrink(1);
        player.displayClientMessage(Component.translatable("message.serfdom.capture.done", name(villager)).withStyle(ChatFormatting.GOLD), true);
        boolean seen = ThiefCompat.commitCapture(level, player, at);
        var owedTo = LawCompat.openHere(player);
        owedTo.ifPresent(v -> Remedies.owe(player, v, villager.getUUID()));
        LOG.info("Serfdom: {} took {} at {} ({}); seen {}, owed to the case with {}", player.getScoreboardName(), villager.getUUID(), at.toShortString(),
                village.map(DeedCompat.Found::id).orElse("no village"), seen, owedTo.orElse("none"));
    }

    /** requires: {@code way} is one of the ways a living worker goes free (set free, escapes, freed
     * by the law).
     * effects: the villager is free again: its chain comes off (and is not given back), it steps out
     * of any vehicle, its bed and post are given up, it walks at a free villager's pace, the tool it
     * keeps drops where it stands (and anything it took to defend the base), what it wears does as
     * {@link Parting#fate} says for {@code way}
     * (set free, it drops; escaping or freed by the law, it stays on), and it lives as a free
     * villager does, which walks it to the nearest village. What it carries stays with it. No case
     * is owed it any more. */
    public static void free(ServerLevel level, Villager villager, Parting.Way way) {
        if (way == Parting.Way.DIES || way == Parting.Way.CONVERTS) throw new IllegalArgumentException("a living worker goes free: " + way);
        var worker = of(villager);
        if (!worker.owned()) return;
        if (villager.isLeashed()) villager.dropLeash(true, false);
        if (villager.isPassenger()) villager.stopRiding();
        if (!worker.tool().isEmpty()) villager.spawnAtLocation(worker.tool());
        // What it took from its owner's chests in a raid (D-0007) drops with the tool.
        com.chunkworks.serfdom.defence.Defenders.drop(villager);
        var hand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!hand.isEmpty() && worker.post().isPresent()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            villager.spawnAtLocation(hand);
        }
        for (var piece : takeOff(villager, way)) villager.spawnAtLocation(piece);
        releaseBed(level, villager, worker);
        leavePost(level, villager, worker);
        Remedies.forget(level.getServer(), villager.getUUID());
        set(level, villager, Worker.NONE);
        pace(villager);
        LOG.info("Serfdom: {} ({}) is free ({})", villager.getUUID(), worker.captive() ? "a captive" : "hired", way);
    }

    /** effects: the walking pace the villager's state calls for: a captive's slowdown on a captive
     * (the configured share, applied afresh, so a changed setting reaches captives as they load),
     * none on anyone else. */
    static void pace(Villager villager) {
        var speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(CAPTIVE_PACE);
        if (of(villager).captive() && SerfdomConfig.CAPTIVE_SLOWDOWN.get() > 0)
            speed.addPermanentModifier(new AttributeModifier(CAPTIVE_PACE, -SerfdomConfig.CAPTIVE_SLOWDOWN.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // ---- beds -------------------------------------------------------------------------------

    /** The answer to a bed or a post being picked. */
    public enum Picked { OK, NOT_A_BED, TAKEN, TOO_FAR, NO_BED, POST_FULL, NOT_YOUR_POST, MISSING, CHILD }

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

    /** effects: a newborn {@code child} of {@code mother} and {@code father} is their owner's,
     * hired, when they have the same one ({@link com.chunkworks.serfdom.domain.Birth}); else it is
     * free, as vanilla's are. */
    public static void born(Villager child, Villager mother, Villager father) {
        if (mother.level().isClientSide() || (SerfdomConfig.SPEC.isLoaded() && !SerfdomConfig.WORKERS.get())) return;
        var m = of(mother);
        var owner = com.chunkworks.serfdom.domain.Birth.owner(m.owner(), of(father).owner());
        if (owner.isEmpty()) return;
        child.setData(Serfdom.WORKER, Worker.hired(owner.get(), m.ownerName(), Optional.empty()));
        LOG.info("Serfdom: a child of {} and {} is born to {}", mother.getUUID(), father.getUUID(), m.ownerName());
    }

    /** effects: how many beds within {@code radius} blocks of {@code pos} are a loaded captive's, so
     * a village's cats leave them out of its count. A captive not loaded is not counted. */
    public static int captiveBedsNear(ServerLevel level, BlockPos pos, int radius) {
        int n = 0;
        var box = new net.minecraft.world.phys.AABB(pos).inflate(radius + 32.0);
        for (var v : level.getEntitiesOfClass(Villager.class, box, v -> of(v).captive())) {
            var bed = of(v).bed();
            if (bed.isPresent() && bed.get().dimension() == level.dimension() && bed.get().pos().distSqr(pos) <= (double) radius * radius) n++;
        }
        return n;
    }

    /** effects: a child born to two of one owner's workers in the bed {@code bed} it was given
     * (D-0003): the bed is its own, as a given bed is. */
    public static void bornInto(Villager child, GlobalPos bed) {
        var worker = of(child);
        if (!worker.owned()) return;
        child.setData(Serfdom.WORKER, worker.with(worker.assignment().withBed(Worker.spot(bed))));
    }

    // ---- posts ------------------------------------------------------------------------------

    /** effects: links the worker to the post at {@code pos} for {@code player}: refused for a child,
     * a post that is not the player's, without a bed, more than 48 blocks from the bed, or with
     * four others on it. */
    public static Picked link(ServerLevel level, Villager villager, ServerPlayer player, BlockPos pos) {
        if (villager.isBaby()) return Picked.CHILD;
        if (!(level.getBlockEntity(pos) instanceof com.chunkworks.serfdom.post.WorkPostBlockEntity post)) return Picked.MISSING;
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

    /** effects: the need the worker's shift is stuck on, if it is (D-0006: what it shops for). */
    public static Optional<Need> shiftNeed(Villager villager) { return Optional.ofNullable(SHIFT_NEEDS.get(villager.getUUID())); }

    /** effects: the needs the worker has now. */
    public static Set<Need> needs(Villager villager) {
        var worker = of(villager);
        var needs = EnumSet.noneOf(Need.class);
        if (!worker.owned()) return needs;
        if (worker.bed().isEmpty()) needs.add(Need.NO_BED);
        if (Appetite.of(villager).hunger().hungry()) needs.add(Need.HUNGRY);
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
        // A defender's weapon (D-0007) is not a tool: it stays in hand, and goes back to its chest.
        if (com.chunkworks.serfdom.defence.Defenders.arms(villager).holds()) return;
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        // A purchase only shown in its hand is in its basket (D-0006), not a tool.
        if (com.chunkworks.serfdom.market.Baskets.shown(hand)) return;
        if (worker.tool().isEmpty()) villager.setData(Serfdom.WORKER, worker.withTool(hand));
        else villager.getInventory().addItem(hand);
    }

    // ---- gestures, death and joining --------------------------------------------------------

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Workers::interact);
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> { if (e.getEntity() instanceof Villager v && !v.level().isClientSide && of(v).owned()) stash(v); });
        NeoForge.EVENT_BUS.addListener(Workers::drops);
        NeoForge.EVENT_BUS.addListener(Workers::converted);
        NeoForge.EVENT_BUS.addListener(Workers::joined);
    }

    /** effects: a use on a villager, as {@link Chain#gesture} says: the capture, the chain put on,
     * taken, taken off or let go, the Worker Screen or its owner's name; a villager in chains does
     * not trade. Taken before normal priority, so Village Deed's offer and Thief's gift never see an
     * owned villager or a chain. */
    static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !(villager.level() instanceof ServerLevel level)) return;
        if (SerfdomConfig.SPEC.isLoaded() && !SerfdomConfig.WORKERS.get()) return;
        var held = event.getItemStack();
        if (Captures.settling(player, villager)) {
            // Just taken, the use still held: nothing until the player lets go.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        var hand = held.is(Serfdom.CHAIN_LEAD.get()) ? Chain.Hand.CHAIN : held.isEmpty() ? Chain.Hand.EMPTY : Chain.Hand.OTHER;
        var worker = of(villager);
        var holder = !worker.cuffed() || !(villager.getLeashHolder() instanceof Player p) ? Chain.Holder.NOBODY
                : p == player ? Chain.Holder.ACTOR : Chain.Holder.SOMEONE_ELSE;
        var act = Chain.gesture(new Chain.Facts(worker.owned(), worker.cuffed(), holder, worker.ownedBy(player.getUUID()), hand, player.isSecondaryUseActive()));
        if (act == Chain.Act.PASS) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        switch (act) {
            case CAPTURE -> Captures.begin(level, villager, player, InteractionHand.MAIN_HAND);
            case CUFF -> {
                set(level, villager, worker.withCuffs(true));
                villager.setLeashedTo(player, true);
                if (!player.getAbilities().instabuild) held.shrink(1);
                tell(player, Component.translatable("message.serfdom.chain.on", name(villager)));
            }
            case TAKE -> {
                villager.setLeashedTo(player, true);
                tell(player, Component.translatable("message.serfdom.chain.taken", name(villager)));
            }
            case UNCUFF -> {
                villager.dropLeash(true, false);
                set(level, villager, worker.withCuffs(false));
                if (!player.getAbilities().instabuild) Carried.giveOrDrop(player, new ItemStack(Serfdom.CHAIN_LEAD.get()));
                tell(player, Component.translatable("message.serfdom.chain.off", name(villager)));
            }
            case LET_GO -> {
                villager.dropLeash(true, false);
                tell(player, Component.translatable("message.serfdom.chain.let_go", name(villager)));
            }
            case HELD -> tell(player, Component.translatable("message.serfdom.chain.led"));
            case SCREEN -> Screens.openWorker(player, villager);
            case WHOSE -> tell(player, Component.translatable("message.serfdom.someones_worker", worker.ownerName()));
            case REFUSE -> {
                villager.setUnhappyCounter(40);
                level.playSound(null, villager, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, villager.getVoicePitch());
            }
            case PASS -> {}
        }
    }

    private static void tell(ServerPlayer player, Component message) { player.displayClientMessage(message.copy().withStyle(ChatFormatting.GRAY), true); }

    /** effects: a worker that dies drops the tool it keeps, everything it carries, what it wears
     * (but a piece with Curse of Vanishing) and the chain on it, and leaves its post; no case is
     * owed it any more. What vanilla's own death drops already took (a piece marked to drop whole,
     * with mob loot on) is not there to drop twice. */
    static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        var worker = of(villager);
        if (!worker.owned()) return;
        for (var stack : belongings(villager, worker, Parting.Way.DIES)) event.getDrops().add(new ItemEntity(level, villager.getX(), villager.getY(), villager.getZ(), stack));
        villager.setData(Serfdom.WORKER, worker.withTool(ItemStack.EMPTY));
        departPost(level, villager, worker);
        Remedies.forget(level.getServer(), villager.getUUID());
    }

    /** effects: a worker turned into a zombie villager (or a witch) drops what a dead one does: the
     * game carries over none of it (it would delete what the worker wore), nor its owner, so the
     * converted villager is nobody's. */
    static void converted(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        var worker = of(villager);
        if (!worker.owned()) return;
        stash(villager);
        worker = of(villager);
        for (var stack : belongings(villager, worker, Parting.Way.CONVERTS)) level.addFreshEntity(new ItemEntity(level, villager.getX(), villager.getY(), villager.getZ(), stack));
        releaseBed(level, villager, worker);
        departPost(level, villager, worker);
        Remedies.forget(level.getServer(), villager.getUUID());
        LOG.info("Serfdom: worker {} turned into {} and dropped its things", villager.getUUID(), event.getOutcome().getType());
    }

    /** requires: {@code way} is dying or turning zombie.
     * effects: what an owned villager leaves as it goes {@code way}: the tool it keeps, what it
     * carries, what it wears (taken off as {@link #takeOff} does), and the chain when it is cuffed;
     * its inventory is emptied. */
    private static java.util.List<ItemStack> belongings(Villager villager, Worker worker, Parting.Way way) {
        var out = new java.util.ArrayList<ItemStack>();
        if (!worker.tool().isEmpty()) out.add(worker.tool());
        out.addAll(villager.getInventory().removeAllItems());
        out.addAll(takeOff(villager, way));
        if (worker.cuffed()) out.add(new ItemStack(Serfdom.CHAIN_LEAD.get()));
        return out;
    }

    /** effects: takes off what the villager wears as {@link Parting#fate} says for {@code way}: a
     * piece that drops is taken off and returned, one that vanishes is taken off and lost, one that
     * stays on is left. */
    static java.util.List<ItemStack> takeOff(Villager villager, Parting.Way way) {
        var out = new java.util.ArrayList<ItemStack>();
        for (var slot : WorkerMenu.WORN) {
            var piece = villager.getItemBySlot(slot);
            if (piece.isEmpty()) continue;
            switch (Parting.fate(way, WorkerMenu.piece(piece, null).vanishing())) {
                case DROPS -> { villager.setItemSlot(slot, ItemStack.EMPTY); out.add(piece); }
                case VANISHES -> villager.setItemSlot(slot, ItemStack.EMPTY);
                case STAYS_ON -> {}
            }
        }
        return out;
    }

    /** effects: a villager joining a level shows whether it is in chains, and a captive walks at the
     * configured pace. */
    static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)) return;
        var worker = of(villager);
        villager.setData(Serfdom.CUFFED, worker.cuffed());
        if (worker.captive()) pace(villager);
    }

    /** effects: whether the stack is the job's tool: it lies in {@code tag}. */
    public static boolean serves(ItemStack stack, Optional<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> tag) {
        return tag.isEmpty() || (!stack.isEmpty() && stack.is(tag.get()));
    }

    /** Emeralds, the fee's coin. */
    public static boolean emerald(ItemStack stack) { return stack.is(Items.EMERALD); }
}
