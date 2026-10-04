/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The chain lead (D-0001, Rusty's idea): a lead for workers, the one way to take a worker
 * somewhere far. It snaps past ten blocks like a lead and drops as itself. Vanilla's lead still
 * fails on villagers. In phase 2 the same item cuffs a free villager. */
public final class ChainLead extends Item {
    public ChainLead(Properties properties) { super(properties); }

    /** effects: on a worker the player leads, lets it go and gives the chain back; on a worker
     * led by nobody, leads it, using one chain outside creative; on one led by someone else, says so. */
    static void use(ServerLevel level, Villager worker, ServerPlayer player, ItemStack held) {
        if (worker.isLeashed()) {
            if (worker.getLeashHolder() == player) {
                worker.dropLeash(true, false);
                if (!player.getAbilities().instabuild) com.chunkworks.carried.api.Carried.giveOrDrop(player, new ItemStack(Serfdom.CHAIN_LEAD.get()));
                player.displayClientMessage(Component.translatable("message.serfdom.chain.off", Workers.name(worker)).withStyle(ChatFormatting.GRAY), true);
            } else {
                player.displayClientMessage(Component.translatable("message.serfdom.chain.led").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        worker.setLeashedTo(player, true);
        if (!player.getAbilities().instabuild) held.shrink(1);
        player.displayClientMessage(Component.translatable("message.serfdom.chain.on", Workers.name(worker)).withStyle(ChatFormatting.GRAY), true);
    }
}
