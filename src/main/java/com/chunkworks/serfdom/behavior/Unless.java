/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

/** A vanilla behaviour that never starts while {@code skip} holds: a worker out defending is not sent
 * to panic or to hide by vanilla's triggers (D-0007), which would take its brain from the defence.
 * Otherwise it is the behaviour itself. */
public final class Unless<E extends LivingEntity> implements BehaviorControl<E> {
    private final BehaviorControl<E> inner;
    private final Predicate<E> skip;

    public Unless(BehaviorControl<E> inner, Predicate<E> skip) {
        this.inner = Objects.requireNonNull(inner);
        this.skip = Objects.requireNonNull(skip);
    }

    @Override public Behavior.Status getStatus() { return inner.getStatus(); }
    @Override public boolean tryStart(ServerLevel level, E entity, long gameTime) { return !skip.test(entity) && inner.tryStart(level, entity, gameTime); }
    @Override public void tickOrStop(ServerLevel level, E entity, long gameTime) { inner.tickOrStop(level, entity, gameTime); }
    @Override public void doStop(ServerLevel level, E entity, long gameTime) { inner.doStop(level, entity, gameTime); }
    @Override public String debugString() { return "Unless(" + inner.debugString() + ")"; }
}
