/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.domain.Ledger;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.domain.Verdict;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** A villager at a stall's counter (D-0006): it judges the stall for one of its wants ({@link
 * Verdict}), and buying, pays from its purse into the stall's proceeds and takes the goods from its
 * stock; the visit is written in the stall's ledger, and the villager reacts where it stands. */
public final class Counter {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    private Counter() {}

    /** What came of a visit: the outcome and the goods bought. */
    public record Visit(Verdict.Outcome outcome, List<ItemStack> goods) {}

    /** effects: the villager's visit to {@code stall} for {@code want}, as the class describes; empty
     * when the stall no longer sells anything it wants, or the villager has no purchase left today
     * (nothing is written then). */
    public static Optional<Visit> visit(ServerLevel level, Villager villager, ForSaleBlockEntity stall, Shopping.Want want) {
        var s = stall.stall();
        if (!s.open() || !want.items().contains(s.item())) return Optional.empty();
        var saved = Purses.of(villager);
        long now = level.getDayTime();
        int perDay = SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.SALES_PER_DAY.get() : 3;
        int left = Shopping.salesLeft(saved.day(), now, perDay);
        if (left < 1) return Optional.empty();
        double base = Prices.value(level, stall.template()).orElse(0);
        int wanted = Shopping.salesFor(want, stall.quantity(), Needs.fills(stall.template()));
        double bonus = SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.NEED_BONUS.get() : 1.5;
        var outcome = wanted < 1 ? new Verdict.Outcome(Verdict.Reaction.NOT_INTERESTED, 0)
                : Verdict.judge(new Verdict.Facts(base, bonus, s, saved.purse().emeralds(), wanted, left, villager.getRandom().nextDouble()));
        List<ItemStack> goods = List.of();
        int paid = 0, items = 0;
        if (outcome.reaction().bought()) {
            goods = stall.sell(outcome.sales());
            paid = outcome.sales() * stall.price();
            items = outcome.sales() * stall.quantity();
            Purses.set(villager, saved.with(saved.purse().pay(paid)).with(Shopping.sold(saved.day(), now, outcome.sales())));
        }
        stall.write(new Ledger.Line(now, BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString(),
                outcome.reaction(), outcome.sales(), items, paid));
        react(level, villager, outcome.reaction());
        if (TRACE) LOG.info("Serfdom trace: {} at the stall {} for {}: {} x{} (base {}, {} a piece, purse {})", villager.getId(), stall.getBlockPos().toShortString(),
                want.name(), outcome.reaction(), outcome.sales(), base, s.each(), saved.purse().emeralds());
        return Optional.of(new Visit(outcome, goods));
    }

    /** effects: the villager's reaction, seen and heard where it stands: a bargain's green sparkles and
     * its "yes"; a purchase's "yes"; vanilla's head shake and "no" at too dear a price; a "hmm" when it
     * can't afford it (its look at an emerald is the shopper's to show); nothing when not interested. */
    public static void react(ServerLevel level, Villager villager, Verdict.Reaction reaction) {
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
    }
}
