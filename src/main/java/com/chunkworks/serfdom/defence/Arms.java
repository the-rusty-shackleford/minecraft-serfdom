/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.defence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** What a defender took from its chests in a raid (D-0007), saved with it so a reload or an unload
 * loses nothing: everything it carries but what is in its hand, each with the chest it came from (none
 * once its chest would not take it back); whether its main hand holds one of ours, and the chest that
 * came from; and the raid it last armed for (the raid's id, -1 none), so it tries once a raid.
 * Immutable.
 *
 * <p>Rep invariant: no kept stack is empty; handFrom is empty unless holds. Abstraction function: the
 * defender carries the kept stacks and, when {@code holds}, the stack in its main hand; each goes back
 * to its chest, or home. */
public record Arms(List<Kept> kept, boolean holds, Optional<BlockPos> handFrom, int raid) {
    public static final Arms NONE = new Arms(List.of(), false, Optional.empty(), -1);

    /** A stack it carries and the chest it came from. */
    public record Kept(ItemStack stack, Optional<BlockPos> from) {
        public Kept {
            Objects.requireNonNull(stack);
            if (stack.isEmpty()) throw new IllegalArgumentException("an empty stack kept");
            stack = stack.copy();
            Objects.requireNonNull(from);
        }
        static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(Kept::stack),
                BlockPos.CODEC.optionalFieldOf("from").forGetter(Kept::from)).apply(i, Kept::new));
    }

    public Arms {
        kept = List.copyOf(kept);
        Objects.requireNonNull(handFrom);
        if (!holds && handFrom.isPresent()) throw new IllegalArgumentException("a hand's chest with nothing held");
    }

    public static final Codec<Arms> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kept.CODEC.listOf().optionalFieldOf("kept", List.of()).forGetter(Arms::kept),
            Codec.BOOL.optionalFieldOf("holds", false).forGetter(Arms::holds),
            BlockPos.CODEC.optionalFieldOf("hand_from").forGetter(Arms::handFrom),
            Codec.INT.optionalFieldOf("raid", -1).forGetter(Arms::raid)).apply(i, Arms::new));

    /** effects: true iff it carries anything it took. */
    public boolean carries() { return !kept.isEmpty() || holds; }

    /** effects: true iff there is nothing to save. */
    public boolean blank() { return !carries() && raid < 0; }

    /** effects: these arms carrying {@code k}, and in hand one of ours from {@code from} when
     * {@code holding}. */
    public Arms with(List<Kept> k, boolean holding, Optional<BlockPos> from) { return new Arms(k, holding, holding ? from : Optional.empty(), raid); }

    public Arms with(List<Kept> k) { return new Arms(k, holds, handFrom, raid); }

    public Arms tried(int raidId) { return new Arms(kept, holds, handFrom, raidId); }
}
