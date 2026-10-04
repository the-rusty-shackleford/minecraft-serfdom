/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.post.WorkPostBlock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Assign bed and Assign job (D-0001): after the button, the player's next right-click on a bed, or
 * on a Work Post, within the pick's time, is the pick. Any other block is used as usual. */
public final class Picks {
    public enum Kind { BED, POST }
    private record Pick(UUID worker, Kind kind, long expires) {}
    private static final Map<UUID, Pick> PENDING = new ConcurrentHashMap<>();
    private Picks() {}

    /** effects: the player's next right-click on a bed (or a post) picks it for {@code worker}. */
    public static void start(ServerPlayer player, Villager worker, Kind kind) {
        PENDING.put(player.getUUID(), new Pick(worker.getUUID(), kind, player.serverLevel().getGameTime() + SerfdomConfig.PICK_SECONDS.get() * 20L));
        player.displayClientMessage(Component.translatable(kind == Kind.BED ? "message.serfdom.pick.bed" : "message.serfdom.pick.post",
                Workers.name(worker), SerfdomConfig.PICK_SECONDS.get()).withStyle(ChatFormatting.YELLOW), true);
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Picks::click);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> PENDING.remove(e.getEntity().getUUID()));
    }

    static void click(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var pick = PENDING.get(player.getUUID());
        if (pick == null) return;
        var level = player.serverLevel();
        if (level.getGameTime() > pick.expires()) { PENDING.remove(player.getUUID()); return; }
        var state = level.getBlockState(event.getPos());
        boolean bed = state.getBlock() instanceof BedBlock;
        boolean post = state.getBlock() instanceof WorkPostBlock;
        if (!(pick.kind() == Kind.BED ? bed : post)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        PENDING.remove(player.getUUID());
        if (!(level.getEntity(pick.worker()) instanceof Villager worker) || !Workers.of(worker).ownedBy(player.getUUID())) {
            tell(player, "message.serfdom.pick.gone", ChatFormatting.GRAY);
            return;
        }
        Workers.Picked answer;
        if (pick.kind() == Kind.BED) {
            var head = state.getValue(BedBlock.PART) == BedPart.HEAD ? event.getPos() : event.getPos().relative(state.getValue(BedBlock.FACING));
            answer = Workers.assignBed(level, worker, head);
        } else {
            answer = Workers.link(level, worker, player, event.getPos());
        }
        player.displayClientMessage(Component.translatable("message.serfdom.picked." + pick.kind().name().toLowerCase() + "." + answer.name().toLowerCase(),
                Workers.name(worker)).withStyle(answer == Workers.Picked.OK ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting colour) {
        player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
    }
}
