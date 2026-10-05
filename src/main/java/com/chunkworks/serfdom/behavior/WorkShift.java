/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Humming;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.JobScript;
import com.chunkworks.serfdom.domain.Shift;
import com.chunkworks.serfdom.domain.WorkDay;
import com.chunkworks.serfdom.job.Job;
import com.chunkworks.serfdom.job.Jobs;
import com.chunkworks.serfdom.job.Storage;
import com.chunkworks.serfdom.post.Posts;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.slf4j.Logger;

/** A worker's shift at its post (D-0001), the whole of its work activity. Between actions it asks
 * {@link Shift#next} what to do, then walks there and does it: empties its inventory into the
 * post's storage, fetches the job's tool, or finds a target and works it a tick at a time. Stuck,
 * it waits by the post showing what it lacks, and asks again every few seconds. Each time it comes
 * to the post it restocks its trades, within vanilla's limits. */
public final class WorkShift extends Behavior<Villager> {
    private static final Logger LOG = LogUtils.getLogger();
    /** Logs every plan, for finding out why a worker does what it does: {@code -Dserfdom.trace=true}. */
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    private static final float SPEED = 0.6F;
    /** Ticks a walk may take before the place counts as out of reach. */
    private static final int WALK_LIMIT = 600;
    /** Ticks a target that could not be reached is left alone. */
    private static final int SKIP_FOR = 2400;
    private static final int WAIT = 100;

    private enum Mode { PLAN, WALK, WORK, WAIT }
    private Mode mode = Mode.PLAN;
    private BlockPos dest;
    private int reach;
    private long walkSince, waitUntil;
    private Runnable arrive;
    private Job.Task task;
    /** Set when the next drops would not fit although a slot may be free; cleared by a deposit. */
    private boolean full;
    /** Set by a new walk: the brain's memory of an earlier failure is wiped before it is read. */
    private boolean pendingFresh;
    private final Map<BlockPos, Long> skipped = new HashMap<>();

    public WorkShift() { super(ImmutableMap.of(), Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager worker) { return Workers.of(worker).post().isPresent(); }

    @Override protected boolean canStillUse(ServerLevel level, Villager worker, long gameTime) {
        return worker.getBrain().isActive(Serfdom.WORK.get()) && Workers.of(worker).post().isPresent();
    }

    @Override protected void start(ServerLevel level, Villager worker, long gameTime) {
        Workers.equip(worker);
        mode = Mode.PLAN;
    }

    @Override protected void stop(ServerLevel level, Villager worker, long gameTime) {
        if (task != null) task.abandon(level, worker);
        task = null;
        full = false;
        arrive = null;
        mode = Mode.PLAN;
        worker.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        Humming.stop(worker, gameTime);
        Workers.stash(worker);
        Workers.shiftNeed(worker, Optional.empty());
    }

    @Override protected void tick(ServerLevel level, Villager worker, long now) {
        if (worker.isTrading() || worker.isSleeping()) return;
        var post = post(level, worker);
        if (post.isEmpty()) return;
        var job = Jobs.get(post.get().job());
        if (job.isEmpty()) { Workers.shiftNeed(worker, Optional.empty()); idle(worker, post.get(), now); return; }
        // A captive's song (D-0003): begun at a work action, carried through the walks and the plans
        // between actions, ended by waiting with nothing to do.
        if (mode == Mode.WAIT) Humming.stop(worker, now);
        else Humming.tick(level, worker, now, mode == Mode.WORK);
        switch (mode) {
            case WAIT -> { if (now >= waitUntil) mode = Mode.PLAN; }
            case WALK -> walk(level, worker, now);
            case WORK -> work(level, worker, job.get(), now);
            case PLAN -> plan(level, worker, post.get(), job.get(), now);
        }
    }

    /** effects: the worker's post when it is loaded; when its chunk is loaded and the post is gone
     * or no longer lists this worker, the worker leaves the job. */
    private Optional<WorkPostBlockEntity> post(ServerLevel level, Villager worker) {
        var at = Workers.of(worker).post().orElseThrow();
        var post = Posts.loaded(level.getServer(), at);
        if (post.isPresent() && post.get().workers().contains(worker.getUUID())) return post;
        var there = level.getServer().getLevel(at.dimension());
        if (there != null && there.isLoaded(at.pos())) {
            LOG.info("Serfdom: {} lost its post at {}", worker.getUUID(), at.pos());
            Workers.clearJob(level, worker);
        }
        return Optional.empty();
    }

    private void plan(ServerLevel level, Villager worker, WorkPostBlockEntity post, JobScript job, long now) {
        restockIfNear(worker, post, now);
        var tag = Jobs.tool(job);
        var carried = worker.getInventory();
        boolean holds = Workers.serves(worker.getItemBySlot(EquipmentSlot.MAINHAND), tag);
        boolean carrying = !carried.isEmpty();
        boolean noSlot = true;
        for (int i = 0; i < carried.getContainerSize(); i++) if (carried.getItem(i).isEmpty()) { noSlot = false; break; }
        boolean isFull = full || noSlot;
        boolean tidy = job.target() == JobScript.Target.WORKSHOP;
        int left = WorkDay.shiftLeft(level.getDayTime(), Workers.of(worker).captive());
        boolean wantsRoom = carrying && (isFull || tidy || left <= Shift.WIND_DOWN);
        boolean wantsTool = tag.isPresent() && !holds;
        var facts = new Shift.Facts(left, carrying, isFull, tag.isPresent(), holds,
                wantsTool && Storage.toolAt(level, post, tag.get()).isPresent(),
                wantsRoom && Storage.room(level, post, carried), tidy);
        var next = Shift.next(facts);
        if (TRACE) LOG.info("Serfdom trace: {} at {} {} -> {}", worker.getId(), worker.blockPosition().toShortString(), facts, next);
        var need = next.need();
        switch (next.step()) {
            case DEPOSIT -> Storage.depositTarget(level, post, carried).ifPresentOrElse(
                    pos -> walkTo(pos, 2, now, () -> { Storage.depositAt(level, post, carried, pos); full = false; mode = Mode.PLAN; }),
                    () -> waitFor(now));
            case FETCH_TOOL -> Storage.toolAt(level, post, tag.orElseThrow()).ifPresentOrElse(
                    pos -> walkTo(pos, 2, now, () -> {
                        var tool = Storage.takeTool(level, pos, tag.get());
                        if (!tool.isEmpty()) {
                            var hand = worker.getItemBySlot(EquipmentSlot.MAINHAND);
                            if (!hand.isEmpty()) carried.addItem(hand);
                            worker.setItemSlot(EquipmentSlot.MAINHAND, tool);
                        }
                        mode = Mode.PLAN;
                    }),
                    () -> waitFor(now));
            case WORK -> {
                skipped.values().removeIf(until -> until <= now);
                if (task == null) {
                    var found = Jobs.code(job).find(level, worker, post, skipped::containsKey);
                    task = found.task().orElse(null);
                    if (task == null) need = found.need();
                }
                if (TRACE) LOG.info("Serfdom trace: {} target {}", worker.getId(), task == null ? "none" : task.key().toShortString());
                if (task == null) idle(worker, post, now);
                else walkTo(task.stand(), task.reach(), now, () -> mode = Mode.WORK);
            }
            case WAIT, REST -> idle(worker, post, now);
        }
        Workers.shiftNeed(worker, need);
    }

    private void work(ServerLevel level, Villager worker, JobScript job, long now) {
        if (task == null) { mode = Mode.PLAN; return; }
        switch (task.tick(level, worker, Jobs.speed(job, worker))) {
            case WORKING -> {}
            case MOVE -> {
                if (arrived(worker, task.stand(), task.reach())) {
                    // There and still told to move: the target cannot be worked from anywhere the
                    // shift walks to. Leave it alone a while rather than walk on the spot.
                    if (TRACE) LOG.info("Serfdom trace: {} cannot work {} from {}", worker.getId(), task.stand().toShortString(), worker.blockPosition().toShortString());
                    skipped.put(task.key(), now + SKIP_FOR);
                    task.abandon(level, worker);
                    task = null;
                    mode = Mode.PLAN;
                } else {
                    walkTo(task.stand(), task.reach(), now, () -> mode = Mode.WORK);
                }
            }
            case FULL -> { full = true; mode = Mode.PLAN; }
            case PAUSE -> mode = Mode.PLAN;
            case DONE -> { task = null; mode = Mode.PLAN; }
        }
    }

    private void walkTo(BlockPos pos, int within, long now, Runnable then) {
        // A failure from an earlier walk is no news about this one.
        pendingFresh = true;
        dest = pos;
        reach = within;
        walkSince = now;
        arrive = then;
        mode = Mode.WALK;
    }

    private void walk(ServerLevel level, Villager worker, long now) {
        var brain = worker.getBrain();
        if (pendingFresh) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            pendingFresh = false;
        }
        if (arrived(worker, dest, reach)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            var then = arrive;
            arrive = null;
            then.run();
            return;
        }
        var stuck = brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        if (now - walkSince > WALK_LIMIT || (stuck.isPresent() && now - stuck.get() > WAIT)) {
            if (TRACE) LOG.info("Serfdom trace: {} could not reach {}", worker.getId(), dest.toShortString());
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            if (task != null && dest.equals(task.stand())) {
                skipped.put(task.key(), now + SKIP_FOR);
                task.abandon(level, worker);
                task = null;
            }
            arrive = null;
            waitFor(now);
            return;
        }
        if (brain.getMemory(MemoryModuleType.WALK_TARGET).isEmpty())
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(dest), SPEED, reach));
    }

    /** effects: true iff the worker stands within {@code reach} + 1 of {@code pos} along the axes. */
    public static boolean arrived(Villager worker, BlockPos pos, int reach) { return worker.blockPosition().distManhattan(pos) <= reach + 1; }

    /** effects: waits by the post until asked again. */
    private void idle(Villager worker, WorkPostBlockEntity post, long now) {
        var at = post.getBlockPos();
        if (worker.blockPosition().distManhattan(at) > 4 && worker.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty())
            worker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(at), SPEED, 2));
        waitFor(now);
    }

    private void waitFor(long now) {
        waitUntil = now + WAIT;
        mode = Mode.WAIT;
    }

    /** effects: a worker by its post restocks its trades as a villager at its workstation does, at
     * most twice a day, and counts as having worked there. */
    private static void restockIfNear(Villager worker, WorkPostBlockEntity post, long now) {
        if (worker.blockPosition().distManhattan(post.getBlockPos()) > 4) return;
        worker.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
        if (worker.shouldRestock()) worker.restock();
    }
}
