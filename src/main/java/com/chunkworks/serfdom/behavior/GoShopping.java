/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Verdict;
import com.chunkworks.serfdom.market.Baskets;
import com.chunkworks.serfdom.market.Counter;
import com.chunkworks.serfdom.market.ForSaleBlockEntity;
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
 * names, waits its turn (one customer at a time; it gives up after {@link #QUEUE} ticks), looks over
 * what is for sale, and is judged at the counter ({@link Counter}); it reacts (a bargain's sparkles,
 * a purchase's "yes", the head shake, an emerald looked at); then, having bought, it carries the goods
 * home (one shown in its hand) and puts them away. A villager that still carries goods from a trip cut
 * short starts here at the walk home. When it is done, or anything cuts the trip short, it lets the
 * stall go, keeps whatever it carries for later, and turns back to its schedule. */
public final class GoShopping extends Behavior<Villager> {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    private static final float SPEED = 0.5F;
    static final int WALK_LIMIT = 1200, QUEUE = 600, LOOK = 20, REACT = 40;
    private static final double REACH = 2.5;

    private enum Step { TO_STALL, QUEUE, LOOK, REACT, HOME, DONE }
    private Step step = Step.DONE;
    private BlockPos dest;
    private long since;
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
        walk(v, plan.get().stall(), now, Step.TO_STALL);
        if (TRACE) LOG.info("Serfdom trace: {} goes shopping at {} for {}", v.getId(), plan.get().stall().toShortString(), plan.get().want().name());
    }

    @Override protected void tick(ServerLevel level, Villager v, long now) {
        switch (step) {
            case TO_STALL -> {
                if (!arrived(v, now)) return;
                step = Step.QUEUE;
                since = now;
            }
            case QUEUE -> {
                var stall = stall(level);
                if (stall.isEmpty()) { step = Step.DONE; return; }
                v.getLookControl().setLookAt(Vec3.atCenterOf(dest).add(0, 0.5, 0));
                if (stall.get().serve(v.getUUID(), now)) { step = Step.LOOK; since = now; return; }
                if (now - since > QUEUE) { step = Step.DONE; if (TRACE) LOG.info("Serfdom trace: {} gave up waiting at {}", v.getId(), dest.toShortString()); }
            }
            case LOOK -> {
                v.getLookControl().setLookAt(Vec3.atCenterOf(dest).add(0, 0.6, 0));
                if (now - since < LOOK) return;
                var stall = stall(level);
                if (stall.isEmpty()) { step = Step.DONE; return; }
                var visit = Counter.visit(level, v, stall.get(), plan.get().want());
                stall.get().letGo(v.getUUID());
                if (visit.isEmpty()) { step = Step.DONE; return; }
                if (visit.get().outcome().reaction().bought()) Baskets.carry(v, visit.get().goods(), plan.get().want().dest());
                else if (visit.get().outcome().reaction() == Verdict.Reaction.CANT_AFFORD) showEmerald(v);
                step = Step.REACT;
                since = now;
            }
            case REACT -> {
                if (now - since < REACT) return;
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

    @Override protected void stop(ServerLevel level, Villager v, long now) {
        stall(level).ifPresent(s -> s.letGo(v.getUUID()));
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
        if (plan.isEmpty() || !level.isLoaded(plan.get().stall())) return Optional.empty();
        return level.getBlockEntity(plan.get().stall()) instanceof ForSaleBlockEntity s ? Optional.of(s) : Optional.empty();
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
            if (TRACE) LOG.info("Serfdom trace: {} could not reach {} shopping", v.getId(), dest.toShortString());
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
