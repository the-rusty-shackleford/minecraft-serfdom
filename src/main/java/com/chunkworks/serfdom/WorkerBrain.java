/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.behavior.FollowOwner;
import com.chunkworks.serfdom.behavior.KeepBed;
import com.chunkworks.serfdom.behavior.OpenGates;
import com.chunkworks.serfdom.behavior.WorkShift;
import com.chunkworks.serfdom.domain.WorkDay;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

/** An owned villager's brain (D-0001): vanilla's packages without the behaviours that claim a
 * workstation or a bed, take or reset a profession, or walk to a village, plus the worker's own
 * activities, on a schedule that follows its state. Free villagers keep vanilla's brain. */
public final class WorkerBrain {
    private static final float SPEED = 0.5F;
    private WorkerBrain() {}

    /** All day following the owner: a hired villager without a bed. */
    static final Supplier<Schedule> FOLLOWER = Suppliers.memoize(() -> new ScheduleBuilder(new Schedule())
            .changeActivityAt(0, Serfdom.FOLLOW.get()).build());
    /** A worker with a bed and no post: a villager's day at the base. */
    static final Supplier<Schedule> RESIDENT = Suppliers.memoize(() -> new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(WorkDay.WORK_END, Activity.MEET)
            .changeActivityAt(WorkDay.MEET_END, Activity.IDLE)
            .changeActivityAt(WorkDay.SLEEP_START, Activity.REST).build());
    /** A worker at a post: D-0001's day. */
    static final Supplier<Schedule> WORKER = Suppliers.memoize(() -> new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(WorkDay.WORK_START, Serfdom.WORK.get())
            .changeActivityAt(WorkDay.WORK_END, Activity.MEET)
            .changeActivityAt(WorkDay.MEET_END, Activity.IDLE)
            .changeActivityAt(WorkDay.SLEEP_START, Activity.REST).build());

    /** effects: the schedule a worker in this state keeps. A worker that lost its bed but keeps its
     * post still works; one with neither follows its owner. */
    public static Schedule scheduleOf(Worker worker) {
        if (worker.post().isPresent()) return WORKER.get();
        if (worker.bed().isPresent()) return RESIDENT.get();
        return FOLLOWER.get();
    }

    /** effects: when {@code villager} is an adult owned villager, gives {@code brain} the worker's
     * packages and schedule and returns true; otherwise changes nothing and returns false, so
     * vanilla builds its brain. */
    public static boolean register(Villager villager, Brain<Villager> brain) {
        if (villager.isBaby() || !Workers.owned(villager)) return false;
        var profession = villager.getVillagerData().getProfession();
        brain.addActivity(Activity.CORE, core());
        brain.addActivityWithConditions(Activity.MEET, VillagerGoalPackages.getMeetPackage(profession, SPEED),
                ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        brain.addActivity(Activity.REST, rest());
        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(profession, SPEED));
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(profession, SPEED));
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(profession, SPEED));
        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(profession, SPEED));
        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(profession, SPEED));
        brain.addActivity(Serfdom.WORK.get(), work());
        brain.addActivity(Serfdom.FOLLOW.get(), follow());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.setSchedule(scheduleOf(Workers.of(villager)));
        brain.updateActivityFromSchedule(villager.level().getDayTime(), villager.level().getGameTime());
        return true;
    }

    /** Vanilla's core package less the job-site and bed claims, the profession changes, and with
     * the bell kept: a bell at the base gathers workers at meeting time. */
    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> core() {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
                Pair.of(0, InteractWithDoor.create()),
                Pair.of(0, new OpenGates()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new VillagerPanicTrigger()),
                Pair.of(0, WakeUp.create()),
                Pair.of(0, ReactToBell.create()),
                Pair.of(0, SetRaidStatus.create()),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(3, new LookAndFollowTradingPlayerSink(SPEED)),
                Pair.of(5, GoToWantedItem.create(SPEED, false, 4)),
                Pair.of(10, AcquirePoi.create(h -> h.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14))),
                Pair.of(10, new KeepBed()));
    }

    /** Vanilla's rest package without the walk to the nearest free bed or the nearest village, and
     * without giving the bed up when somebody else lies in it: a worker sleeps in the bed it was
     * given or nowhere, and {@link KeepBed} decides when the bed is gone. */
    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> rest() {
        return ImmutableList.of(
                Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, SPEED, 1, 150, 1200)),
                Pair.of(3, new SleepInBed()),
                Pair.of(5, new RunOne<>(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(Pair.of(InsideBrownianWalk.create(SPEED), 4), Pair.of(new DoNothing(20, 40), 2)))),
                look(),
                Pair.of(99, UpdateActivityFromSchedule.create()));
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work() {
        return ImmutableList.of(Pair.of(5, new WorkShift()), Pair.of(99, UpdateActivityFromSchedule.create()));
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> follow() {
        return ImmutableList.of(Pair.of(5, new FollowOwner()), look(), Pair.of(99, UpdateActivityFromSchedule.create()));
    }

    /** Vanilla's minimal look behaviour (private there). */
    private static Pair<Integer, BehaviorControl<net.minecraft.world.entity.LivingEntity>> look() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(SetEntityLookTarget.create(EntityType.VILLAGER, 8.0F), 2),
                Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
                Pair.of(new DoNothing(30, 60), 8))));
    }

    /** effects: true iff profession can be hired: neither unemployed nor a nitwit. */
    public static boolean employed(VillagerProfession profession) {
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }
}
