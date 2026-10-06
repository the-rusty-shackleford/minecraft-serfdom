/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.domain.Ledger;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.domain.Stall;
import com.chunkworks.serfdom.domain.Verdict;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** A villager at a stall's counter, or (4b) at a villager selling (D-0006): it judges what is offered
 * for one of its wants ({@link Verdict}), and buying, pays from its purse into the stall's proceeds or
 * the seller's purse and takes the goods from the stock or the seller's inventory; a stall's visit is
 * written in its ledger, and the villager reacts where it stands. What it will pay is the item's base
 * value times its climate's bonus, its taste's and, for a need, the need's ({@link Tastes#bonus}). A
 * window shopper (4b) judges the item for any need it meets first, then for its taste; otherwise it is
 * not interested. */
public final class Counter {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    /** The name of a want for taste, not need. */
    public static final String TASTE = "taste";
    private Counter() {}

    /** What came of a visit: the outcome, the goods bought and where they go, and whether they are from
     * its favourite category (4b). */
    public record Visit(Verdict.Outcome outcome, List<ItemStack> goods, Shopping.Dest dest, boolean favourite) {}

    /** effects: what the villager will pay for one {@code stack}, in emeralds: its base value times the
     * villager's bonuses; 0 for an item with no value. */
    public static double willing(ServerLevel level, Villager villager, ItemStack stack, boolean need) {
        return Verdict.willing(Prices.value(level, stack).orElse(0), Tastes.bonus(level, villager, stack, need));
    }

    /** effects: the villager's visit to {@code stall}, for {@code need}, or with none a look (window
     * shopping), as the class describes; empty when the stall no longer sells anything it came for, or
     * the villager has no purchase left today (nothing is written then). */
    public static Optional<Visit> visit(ServerLevel level, Villager villager, ForSaleBlockEntity stall, Optional<Shopping.Want> need) {
        var s = stall.stall();
        if (!s.open() || need.map(n -> !n.items().contains(s.item())).orElse(false)) return Optional.empty();
        long now = level.getDayTime();
        if (Shopping.salesLeft(Purses.of(villager).day(), now, perDay()) < 1) return Optional.empty();
        // Seen for the day, however it came here (a meal's purchase too).
        var seen = Purses.of(villager);
        Purses.set(villager, seen.with(Shopping.saw(seen.day(), now, stall.getBlockPos().asLong())));
        var template = stall.template();
        var want = need.or(() -> forNeed(level, villager, s.item()));
        boolean needed = want.isPresent();
        if (want.isEmpty()) {
            int n = Tastes.wanted(level, villager, template);
            if (n > 0) want = Optional.of(new Shopping.Want(TASTE, Set.of(s.item()), n, Shopping.Unit.ITEMS, Tastes.wantsTo(villager)));
        }
        var visit = judge(level, villager, template, s, want, needed);
        var outcome = visit.outcome();
        List<ItemStack> goods = List.of();
        if (outcome.reaction().bought()) {
            goods = stall.sell(outcome.sales());
            pay(villager, outcome.sales() * stall.price(), outcome.sales(), now);
        }
        stall.write(new Ledger.Line(now, BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString(),
                outcome.reaction(), outcome.sales(), outcome.sales() * stall.quantity(), outcome.sales() * stall.price()));
        react(level, villager, outcome.reaction(), visit.favourite());
        if (TRACE) LOG.info("Serfdom trace: {} at the stall {} for {}: {} x{} ({} a piece, willing {}, purse {})", villager.getId(), stall.getBlockPos().toShortString(),
                want.map(Shopping.Want::name).orElse("a look"), outcome.reaction(), outcome.sales(), s.each(), willing(level, villager, template, needed), Purses.emeralds(villager));
        return Optional.of(new Visit(outcome, goods, want.map(Shopping.Want::dest).orElse(Tastes.wantsTo(villager)), visit.favourite()));
    }

    /** effects: the buyer's purchase from {@code seller} of its {@code item} for {@code need} (4b): the
     * goods out of the seller's inventory, the emeralds from the buyer's purse into the seller's (past
     * its cap lost); both react. Empty when the seller no longer sells it, has no lot to spare, or the
     * buyer has no purchase left today. */
    public static Optional<Visit> buyFrom(ServerLevel level, Villager buyer, Villager seller, net.minecraft.world.item.Item item, Shopping.Want need) {
        var ware = Peddlers.ware(level, seller, item);
        if (ware.isEmpty() || !ware.get().stall().open() || !need.items().contains(ware.get().stall().item())) return Optional.empty();
        long now = level.getDayTime();
        if (Shopping.salesLeft(Purses.of(buyer).day(), now, perDay()) < 1) return Optional.empty();
        var s = ware.get().stall();
        var visit = judge(level, buyer, new ItemStack(item), s, Optional.of(need), true);
        var outcome = visit.outcome();
        List<ItemStack> goods = List.of();
        if (outcome.reaction().bought()) {
            goods = Peddlers.take(seller, item, outcome.sales() * s.quantity());
            int paid = outcome.sales() * s.price();
            pay(buyer, paid, outcome.sales(), now);
            Purses.receive(seller, paid);
            seller.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, seller, SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, seller.getVoicePitch());
        }
        react(level, buyer, outcome.reaction(), visit.favourite());
        if (TRACE) LOG.info("Serfdom trace: {} buys from {} for {}: {} x{} of {} ({} a piece, willing {}, purse {})", buyer.getId(), seller.getId(), need.name(),
                outcome.reaction(), outcome.sales(), s.item(), s.each(), willing(level, buyer, new ItemStack(item), true), Purses.emeralds(buyer));
        return Optional.of(new Visit(outcome, goods, need.dest(), visit.favourite()));
    }

    /** effects: the first of the villager's needs now that {@code item} meets. */
    private static Optional<Shopping.Want> forNeed(ServerLevel level, Villager villager, String item) {
        return Needs.wants(level, villager).stream().filter(w -> w.items().contains(item)).findFirst();
    }

    /** effects: the verdict on {@code s} for {@code want} (none: not interested), and whether a purchase
     * is from the villager's favourite category. */
    private static Visit judge(ServerLevel level, Villager villager, ItemStack stack, Stall s, Optional<Shopping.Want> want, boolean need) {
        var saved = Purses.of(villager);
        int left = Shopping.salesLeft(saved.day(), level.getDayTime(), perDay());
        int wanted = want.map(w -> Shopping.salesFor(w, s.quantity(), Needs.fills(stack))).orElse(0);
        var outcome = wanted < 1 ? new Verdict.Outcome(Verdict.Reaction.NOT_INTERESTED, 0)
                : Verdict.judge(new Verdict.Facts(Prices.value(level, stack).orElse(0), Tastes.bonus(level, villager, stack, need), s, saved.purse().emeralds(),
                        wanted, left, villager.getRandom().nextDouble()));
        return new Visit(outcome, List.of(), Tastes.wantsTo(villager), outcome.reaction().bought() && Tastes.favourite(villager, stack));
    }

    private static void pay(Villager villager, int paid, int sales, long now) {
        var saved = Purses.of(villager);
        Purses.set(villager, saved.with(saved.purse().pay(paid)).with(Shopping.sold(saved.day(), now, sales)));
    }

    private static int perDay() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.SALES_PER_DAY.get() : 3; }

    /** effects: the villager's reaction, seen and heard where it stands: a bargain's green sparkles and
     * its "yes"; a purchase's "yes"; and for one from its favourite category (4b) vanilla's celebration
     * and a hop besides; vanilla's head shake and "no" at too dear a price; a "hmm" when it can't afford
     * it (its look at an emerald is the shopper's to show); nothing when not interested (a glance, the
     * shopper's). */
    public static void react(ServerLevel level, Villager villager, Verdict.Reaction reaction, boolean favourite) {
        switch (reaction) {
            case BARGAIN -> {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.6, villager.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
                level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, villager.getVoicePitch());
            }
            case BOUGHT -> level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, villager.getVoicePitch());
            case TOO_PRICEY -> {
                villager.setUnhappyCounter(40);
                level.playSound(null, villager, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, villager.getVoicePitch());
            }
            case CANT_AFFORD -> level.playSound(null, villager, SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, villager.getVoicePitch() * 0.85F);
            case NOT_INTERESTED -> {}
        }
        if (favourite && reaction.bought()) {
            level.playSound(null, villager, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, villager.getVoicePitch());
            villager.getJumpControl().jump();
        }
    }
}
