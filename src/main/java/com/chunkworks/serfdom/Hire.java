/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.serfdom.compat.DeedCompat;
import com.chunkworks.serfdom.domain.Hiring;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Hiring (D-0001). Sneak-use with an empty hand on a villager that can be hired adds a green
 * [Hire for N] line under Village Deed's offer; a click within the offer's time pays the fee from
 * everything the player carries into the villager's purse (D-0006) and makes the villager theirs. The gesture is taken after Village
 * Deed has made its offer, so the trade screen does not cover the line. */
public final class Hire {
    /** How near the villager must still be when the player clicks. */
    static final double REACH = 8.0;
    private record Offer(UUID villager, int fee, long expires) {}
    private static final Map<UUID, Offer> OFFERS = new ConcurrentHashMap<>();
    private Hire() {}

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, Hire::offer);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> OFFERS.remove(e.getEntity().getUUID()));
    }

    static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("serfdom").then(Commands.literal("hire").executes(ctx -> hire(ctx.getSource().getPlayerOrException()))));
    }

    /** effects: the verdict on {@code player} hiring {@code villager}. */
    static Hiring.Verdict verdict(ServerLevel level, Villager villager, ServerPlayer player) {
        return Hiring.verdict(!villager.isBaby(), Workers.of(villager).owned(),
                WorkerBrain.employed(villager.getVillagerData().getProfession()),
                DeedCompat.allows(level, villager.blockPosition(), player.getUUID()));
    }

    static void offer(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) return;
        if (!(event.getTarget() instanceof Villager villager) || !event.getEntity().isSecondaryUseActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !(villager.level() instanceof ServerLevel level)) return;
        if (!SerfdomConfig.WORKERS.get()) return;
        switch (verdict(level, villager, player)) {
            case HIRE -> {
                int fee = Hiring.fee(villager.getVillagerData().getLevel(), SerfdomConfig.HIRE_PER_LEVEL.get());
                OFFERS.put(player.getUUID(), new Offer(villager.getUUID(), fee, level.getGameTime() + SerfdomConfig.OFFER_SECONDS.get() * 20L));
                int carrying = Carried.count(player, Workers::emerald);
                player.sendSystemMessage(Component.translatable("message.serfdom.hire.button", fee).withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/serfdom hire"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.serfdom.hire.hover", Workers.name(villager), fee, carrying)))));
            }
            case NOT_YOURS -> player.sendSystemMessage(Component.translatable("message.serfdom.hire.not_yours",
                    DeedCompat.owner(level, villager.blockPosition()).orElse("?")).withStyle(ChatFormatting.GRAY));
            default -> { return; }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** effects: hires the villager the player was last offered, while the offer lives, the
     * villager is near and still hireable, and the player carries the fee. */
    static int hire(ServerPlayer player) {
        var offer = OFFERS.remove(player.getUUID());
        var level = player.serverLevel();
        if (offer == null || level.getGameTime() > offer.expires()) { tell(player, "message.serfdom.hire.expired"); return 0; }
        if (!(level.getEntity(offer.villager()) instanceof Villager villager) || !villager.isAlive() || villager.distanceTo(player) > REACH) {
            tell(player, "message.serfdom.hire.gone"); return 0;
        }
        if (verdict(level, villager, player) != Hiring.Verdict.HIRE) { tell(player, "message.serfdom.hire.gone"); return 0; }
        if (!Carried.take(player, Workers::emerald, offer.fee(), taken -> {})) {
            player.sendSystemMessage(Component.translatable("message.serfdom.hire.short", offer.fee(), Carried.count(player, Workers::emerald)).withStyle(ChatFormatting.RED));
            return 0;
        }
        Workers.hire(level, villager, player, DeedCompat.village(level, villager.blockPosition()));
        // The fee is paid to the villager (D-0006).
        if (com.chunkworks.serfdom.market.Purses.on()) com.chunkworks.serfdom.market.Purses.receive(villager, offer.fee());
        player.sendSystemMessage(Component.translatable("message.serfdom.hire.done", Workers.name(villager), offer.fee()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static void tell(ServerPlayer player, String key) { player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY)); }
}
