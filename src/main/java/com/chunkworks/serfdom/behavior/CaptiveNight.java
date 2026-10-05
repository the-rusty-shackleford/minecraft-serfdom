/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.Worker;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Escape;
import com.chunkworks.serfdom.domain.WorkDay;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import org.slf4j.Logger;

/** A captive's nights (D-0003), checked once a second in its core package: asleep in its bed at
 * midnight it rolls ({@link Escape#roll}), and on a success it gets up at the moment the roll chose
 * and is on its way home. A captive not loaded through the night's window rolls nothing for it, and
 * one woken before its moment, or still asleep past two, stays. */
public final class CaptiveNight extends Behavior<Villager> {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int EVERY = 20;
    private long next;

    public CaptiveNight() { super(ImmutableMap.of()); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return level.getGameTime() >= next; }

    @Override protected void start(ServerLevel level, Villager villager, long gameTime) {
        next = gameTime + EVERY;
        var worker = Workers.of(villager);
        if (!worker.captive() || worker.cuffed() || worker.escaping()) return;
        long now = level.getDayTime();
        var night = worker.night();
        if (night.getUpAt().isPresent()) {
            long at = night.getUpAt().get();
            if (now < at) return;
            boolean sameNight = Escape.day(now) == Escape.day(at) && WorkDay.tickOfDay(now) < Escape.WINDOW_END + EVERY;
            if (sameNight && villager.isSleeping()) {
                villager.stopSleeping();
                Workers.set(level, villager, worker.withNight(new Worker.Night(night.rolled(), Optional.empty(), true)));
                LOG.info("Serfdom: captive {} gets up at {} and walks home", villager.getUUID(), villager.blockPosition().toShortString());
            } else {
                villager.setData(com.chunkworks.serfdom.Serfdom.WORKER, worker.withNight(new Worker.Night(night.rolled(), Optional.empty(), false)));
            }
            return;
        }
        if (!Escape.due(now, night.rolled(), villager.isSleeping() && worker.bed().isPresent())) return;
        long day = Escape.day(now);
        var roll = Escape.roll(seed(villager), day, SerfdomConfig.ESCAPE_CHANCE.get());
        Optional<Long> getUp = roll.isPresent() ? Optional.of(Math.max(now, day * WorkDay.DAY + roll.getAsInt())) : Optional.empty();
        villager.setData(com.chunkworks.serfdom.Serfdom.WORKER, worker.withNight(new Worker.Night(day, getUp, false)));
        if (getUp.isPresent()) LOG.info("Serfdom: captive {} means to escape at {}", villager.getUUID(), getUp.get());
    }

    /** effects: the captive's own seed for its nights' rolls. */
    static long seed(Villager villager) { return villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits(); }
}
