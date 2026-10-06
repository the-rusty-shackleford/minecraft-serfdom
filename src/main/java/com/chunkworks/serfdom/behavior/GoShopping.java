/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Verdict;
import com.chunkworks.serfdom.market.Baskets;
import com.chunkworks.serfdom.market.Counter;
import com.chunkworks.serfdom.market.ForSaleBlockEntity;
import com.chunkworks.serfdom.market.Peddlers;
import com.chunkworks.serfdom.market.Shoppers;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** A shopping trip (D-0006), the whole of the shop activity: the villager walks to the stall its plan
 * names, or (4b) to the villager selling what it needs, following it as it moves; waits its turn (one
 * customer at a time; it gives up after {@link #QUEUE} ticks), looks over what is for sale, and is
 * judged ({@link Counter}): for its need, or (4b, window shopping) for whatever need or taste the
 * stall's item meets; it reacts (a bargain's sparkles, a purchase's "yes", a favourite's celebration
 * and hop, the head shake, an emerald looked at, or only a glance when not interested); then, having
 * bought, it carries the goods home (one shown in its hand) and puts them away. A villager that still
 * carries goods from a trip cut short starts here at the walk home. When it is done, or anything cuts
 * the trip short, it lets the stall or seller go, keeps whatever it carries for later, and turns back
 * to its schedule. */
public final class GoShopping extends Behavior<Villager> {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    private static final float SPEED = 0.5F;
    static final int WALK_LIMIT = 1200, QUEUE = 600, LOOK = 20, REACT = 40, GLANCE = 10, FOLLOW = 10;
    private static final double REACH = 2.5;

    private enum Step { TO_STALL, TO_SELLER, QUEUE, LOOK, REACT, HOME, DONE }
    private Step step = Step.DONE;
    private BlockPos dest;
    private long since, react;
    private Optional<Shoppers.Plan> plan = Optional.empty();
    private boolean shownEmerald;

    public GoShopping() { super(ImmutableMap.of(), 4800); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager v) { return v.getBrain().isActive(Serfdom.SHOP.get()); }

    @Override protected boolean canStillUse(ServerLevel level, Villager v, long now) {
        return step != Step.DONE && v.getBrain().isActive(Serfdom.SHOP.get()) && !v.isSleeping() && !Workers.of(v).cuffed();
    }

    @Override protected void start(ServerLevel level, Villager v, long now) {
        shownEmerald = false;
        plan = Shoppers.take(v);
        Workers.stash(v);
        if (Baskets.of(v).isPresent()) { home(level, v, now); return; }
        if (plan.isEmpty()) { step = Step.DONE; return; }
        switch (plan.get().place()) {
            case Shoppers.AtStall s -> walk(v, s.pos(), now, Step.TO_STALL);
            case Shoppers.AtSeller s -> {
                var seller = seller(level);
                if (seller.isEmpty()) { step = Step.DONE; return; }
                walk(v, seller.get().blockPosition(), now, Step.TO_SELLER);
            }
        }
        if (TRACE) LOG.info("Serfdom trace: {} goes shopping at {} for {}", v.getId(), plan.get().place(), plan.get().need().map(w -> w.name()).orElse("a look"));
    }

    @Override protected void tick(ServerLevel level, Villager v, long now) {
        switch (step) {
            case TO_STALL -> {
                if (!arrived(v, now)) return;
                step = Step.QUEUE;
                since = now;
            }
            case TO_SELLER -> {
                var seller = seller(level);
                if (seller.isEmpty()) { step = Step.DONE; return; }
                // It follows the seller as it moves.
                if ((now - since) % FOLLOW == 0 && !seller.get().blockPosition().equals(dest)) {
                    dest = seller.get().blockPosition();
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(dest, SPEED, 1));
                }
                if (!arrived(v, now)) return;
                step = Step.QUEUE;
                since = now;
            }
            case QUEUE -> {
                if (plan.get().place() instanceof Shoppers.AtSeller) {
                    var seller = seller(level);
                    if (seller.isEmpty()) { step = Step.DONE; return; }
                    v.getLookControl().setLookAt(seller.get(), 30F, 30F);
                    if (Peddlers.serve(seller.get(), v.getUUID(), now)) { step = Step.LOOK; since = now; return; }
                } else {
                    var stall = stall(level);
                    if (stall.isEmpty()) { step = Step.DONE; return; }
                    v.getLookControl().setLookAt(Vec3.atCenterOf(dest).add(0, 0.5, 0));
                    if (stall.get().serve(v.getUUID(), now)) { step = Step.LOOK; since = now; return; }
                }
                if (now - since > QUEUE) { step = Step.DONE; if (TRACE) LOG.info("Serfdom trace: {} gave up waiting at {}", v.getId(), dest.toShortString()); }
            }
            case LOOK -> look(level, v, now);
            case REACT -> {
                if (now - since < react) return;
                hideEmerald(v);
                if (Baskets.of(v).isPresent()) home(level, v, now); else step = Step.DONE;
            }
            case HOME -> {
                if (!arrived(v, now)) return;
                Baskets.putAway(level, v);
                v.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                step = Step.DONE;
            }
            case DONE -> {}
        }
    }

    /** effects: it looks over what is offered, then is judged at the counter or by the seller. */
    private void look(ServerLevel level, Villager v, long now) {
        Optional<Counter.Visit> visit;
        if (plan.get().place() instanceof Shoppers.AtSeller at) {
            var seller = seller(level);
            if (seller.isEmpty()) { step = Step.DONE; return; }
            v.getLookControl().setLookAt(seller.get(), 30F, 30F);
            seller.get().getLookControl().setLookAt(v, 30F, 30F);
            if (now - since < LOOK) return;
            visit = Counter.buyFrom(level, v, seller.get(), at.item(), plan.get().need().orElseThrow());
            Peddlers.letGo(seller.get().getUUID(), v.getUUID());
        } else {
            v.getLookControl().setLookAt(Vec3.atCenterOf(dest).add(0, 0.6, 0));
            if (now - since < LOOK) return;
            var stall = stall(level);
            if (stall.isEmpty()) { step = Step.DONE; return; }
            visit = Counter.visit(level, v, stall.get(), plan.get().need());
            stall.get().letGo(v.getUUID());
        }
        if (visit.isEmpty()) { step = Step.DONE; return; }
        var reaction = visit.get().outcome().reaction();
        if (reaction.bought()) Baskets.carry(v, visit.get().goods(), visit.get().dest());
        else if (reaction == Verdict.Reaction.CANT_AFFORD) showEmerald(v);
        react = reaction == Verdict.Reaction.NOT_INTERESTED ? GLANCE : REACT;
        step = Step.REACT;
        since = now;
    }

    @Override protected void stop(ServerLevel level, Villager v, long now) {
        stall(level).ifPresent(s -> s.letGo(v.getUUID()));
        if (plan.isPresent() && plan.get().place() instanceof Shoppers.AtSeller at) Peddlers.letGo(at.seller(), v.getUUID());
        hideEmerald(v);
        Baskets.clearShown(v);
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        step = Step.DONE;
        var brain = v.getBrain();
        if (brain.isActive(Serfdom.SHOP.get())) brain.setActiveActivityIfPossible(brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L)));
    }

    /** effects: the walk home with the goods, shown in its hand; put away at once when there is
     * nowhere to walk to (a free villager without a bed keeps its household all the same). */
    private void home(ServerLevel level, Villager v, long now) {
        var basket = Baskets.of(v).orElseThrow();
        var to = Baskets.destination(level, v, basket.dest());
        if (to.isEmpty()) { Baskets.putAway(level, v); step = Step.DONE; return; }
        Baskets.show(v);
        walk(v, to.get(), now, Step.HOME);
    }

    private Optional<ForSaleBlockEntity> stall(ServerLevel level) {
        var at = plan.flatMap(Shoppers.Plan::stall);
        if (at.isEmpty() || !level.isLoaded(at.get())) return Optional.empty();
        return level.getBlockEntity(at.get()) instanceof ForSaleBlockEntity s ? Optional.of(s) : Optional.empty();
    }

    /** effects: the villager the plan buys from, while it is here and still sells. */
    private Optional<Villager> seller(ServerLevel level) {
        if (plan.isEmpty() || !(plan.get().place() instanceof Shoppers.AtSeller at)) return Optional.empty();
        return level.getEntity(at.seller()) instanceof Villager s && Peddlers.selling(s) ? Optional.of(s) : Optional.empty();
    }

    private void walk(Villager v, BlockPos to, long now, Step next) {
        dest = to;
        since = now;
        step = next;
        v.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, SPEED, 1));
    }

    /** effects: true once it stands by its destination; on a walk that took too long it gives up. */
    private boolean arrived(Villager v, long now) {
        if (v.position().distanceToSqr(Vec3.atBottomCenterOf(dest)) <= REACH * REACH) {
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return true;
        }
        if (now - since > WALK_LIMIT) {
            if (TRACE) LOG.info("Serfdom trace: {} could not reach {} shopping, at {} ({} blocks), walk target {}, can't reach since {}, path {}", v.getId(), dest.toShortString(),
                    v.blockPosition().toShortString(), String.format("%.1f", Math.sqrt(v.position().distanceToSqr(Vec3.atBottomCenterOf(dest)))),
                    v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getTarget().currentBlockPosition().toShortString()).orElse("none"),
                    v.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).map(String::valueOf).orElse("never"),
                    v.getNavigation().getPath() == null ? "none" : v.getNavigation().getPath().getNodeCount() + " nodes to " + v.getNavigation().getPath().getTarget().toShortString());
            step = Step.DONE;
            return false;
        }
        if (!v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(dest, SPEED, 1));
        return false;
    }

    /** effects: it looks at an emerald in its hand: it can't afford what it came for. */
    private void showEmerald(Villager v) {
        if (!v.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return;
        v.setItemSlot(EquipmentSlot.MAINHAND, Baskets.shownCopy(new ItemStack(Items.EMERALD)));
        shownEmerald = true;
    }

    private void hideEmerald(Villager v) {
        if (shownEmerald && Baskets.shown(v.getItemBySlot(EquipmentSlot.MAINHAND))) v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        shownEmerald = false;
    }
}
