/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.compat.DeedCompat;
import com.chunkworks.serfdom.domain.Capture;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The capture (D-0003): a player holds use with the chain on a free villager for the configured
 * time. The villager screams as the hold begins and is held still through it; the hold is judged
 * each tick by {@link Capture#tick}, and the moment it is done the villager is the player's captive.
 * Until the player lets go of the use key, their uses on the captive do nothing: a client holding
 * the key repeats the use every four ticks once it is no longer drawing the chain (and its last
 * chain may be the one just used up), and the first repeat would take the cuffs straight off (the
 * booth found it). The key counts as let go after {@link #RELEASED} ticks with neither the chain
 * drawn nor a use. Server side only. */
public final class Captures {
    /** Ticks with neither the chain drawn nor a use that mean the player let go: a held key repeats
     * every four. */
    public static final int RELEASED = 10;

    /** A hold: on whom, with which hand, for how many ticks so far, whether it is done and only waits
     * for the player to let go, and the last game tick the player was still at it. */
    private record Hold(UUID villager, InteractionHand hand, int held, boolean done, long touched) {
        Hold next() { return new Hold(villager, hand, held + 1, done, touched); }
        Hold touched(long now) { return new Hold(villager, hand, held, done, now); }
    }
    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();
    private Captures() {}

    static void listen() {
        // Judged from the server's tick, for every player holding: a player's own tick is driven by
        // its connection, which a test's player has none of.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            for (var id : HOLDS.keySet()) {
                var player = e.getServer().getPlayerList().getPlayer(id);
                if (player == null) HOLDS.remove(id);
                else tick(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> HOLDS.remove(e.getEntity().getUUID()));
    }

    /** effects: true iff {@code player} is holding the chain on {@code villager} now, to take it. */
    public static boolean holding(ServerPlayer player, Villager villager) {
        var hold = HOLDS.get(player.getUUID());
        return hold != null && !hold.done() && hold.villager().equals(villager.getUUID());
    }

    /** effects: true iff {@code player} has just taken {@code villager} and has not let go of the
     * use key yet; a use counts as still holding it. Their uses on it do nothing until they let go. */
    public static boolean settling(ServerPlayer player, Villager villager) {
        var hold = HOLDS.get(player.getUUID());
        if (hold == null || !hold.done() || !hold.villager().equals(villager.getUUID())) return false;
        HOLDS.put(player.getUUID(), hold.touched(player.serverLevel().getGameTime()));
        return true;
    }

    /** effects: the chain on a free villager: the hold begins when the villager can be taken, and the
     * player is told why when it cannot. A use repeated while the same hold runs changes nothing. */
    static void begin(ServerLevel level, Villager villager, ServerPlayer player, InteractionHand hand) {
        if (holding(player, villager)) return;
        var verdict = Capture.verdict(!villager.isBaby(), Workers.of(villager).owned(),
                WorkerBrain.employed(villager.getVillagerData().getProfession()), DeedCompat.allows(level, villager.blockPosition(), player.getUUID()));
        switch (verdict) {
            case TAKE -> {}
            case NOT_YOURS -> { tell(player, Component.translatable("message.serfdom.capture.not_yours", DeedCompat.owner(level, villager.blockPosition()).orElse("?")), ChatFormatting.RED); return; }
            default -> { tell(player, Component.translatable("message.serfdom.capture." + verdict.name().toLowerCase(java.util.Locale.ROOT)), ChatFormatting.GRAY); return; }
        }
        HOLDS.put(player.getUUID(), new Hold(villager.getUUID(), hand, 0, false, level.getGameTime()));
        player.startUsingItem(hand);
        // The scream: the villager's own hurt and "no", louder and higher; its head shakes.
        level.playSound(null, villager, SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.6F, 1.35F);
        level.playSound(null, villager, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.6F, 1.25F);
        villager.setUnhappyCounter(40);
        holdStill(villager, player);
        tell(player, Component.translatable("message.serfdom.capture.holding", Workers.name(villager)), ChatFormatting.YELLOW);
    }

    private static void tick(ServerPlayer player) {
        var hold = HOLDS.get(player.getUUID());
        if (hold == null) return;
        boolean using = player.isUsingItem() && player.getUsedItemHand() == hold.hand() && player.getUseItem().is(Serfdom.CHAIN_LEAD.get());
        var level = player.serverLevel();
        long now = level.getGameTime();
        if (hold.done()) {
            if (using) HOLDS.put(player.getUUID(), hold.touched(now));
            else if (now - hold.touched() > RELEASED) HOLDS.remove(player.getUUID());
            return;
        }
        var villager = level.getEntity(hold.villager()) instanceof Villager v ? v : null;
        boolean present = villager != null && villager.isAlive() && !Workers.of(villager).owned();
        double distance = present ? player.distanceTo(villager) : 0.0;
        var step = Capture.tick(hold.held(), SerfdomConfig.CAPTURE_TICKS.get(), present, using, distance, SerfdomConfig.CAPTURE_REACH.get(),
                present && looking(player, villager));
        switch (step) {
            case HOLDING -> {
                HOLDS.put(player.getUUID(), hold.next());
                holdStill(villager, player);
            }
            case DONE -> {
                // Kept until the player lets go of the key; see the class comment.
                HOLDS.put(player.getUUID(), new Hold(hold.villager(), hold.hand(), hold.held(), true, now));
                Workers.capture(level, villager, player, player.getItemInHand(hold.hand()));
            }
            default -> {
                HOLDS.remove(player.getUUID());
                if (using) player.stopUsingItem();
                tell(player, Component.translatable("message.serfdom.capture." + step.name().toLowerCase(java.util.Locale.ROOT)), ChatFormatting.GRAY);
            }
        }
    }

    /** effects: true iff the player's line of sight, four blocks long, meets the villager's box. */
    static boolean looking(ServerPlayer player, Villager villager) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(4.0));
        var box = villager.getBoundingBox().inflate(0.3);
        return box.contains(eye) || box.clip(eye, end).isPresent();
    }

    /** effects: the villager stops where it is and looks at its taker. */
    private static void holdStill(Villager villager, ServerPlayer player) {
        villager.getNavigation().stop();
        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }

    private static void tell(ServerPlayer player, Component message, ChatFormatting colour) {
        player.displayClientMessage(message.copy().withStyle(colour), true);
    }
}
