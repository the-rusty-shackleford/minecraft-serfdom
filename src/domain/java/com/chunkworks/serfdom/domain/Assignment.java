/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;
import java.util.Optional;

/** A worker's setup: its bed and its Work Post (D-0001). A job needs a bed first; the post must be
 * within 48 blocks of the bed and hold at most four workers. Immutable.
 *
 * <p>Rep invariant: if both a bed and a post are set, the post is within
 * {@link #MAX_BED_TO_POST} of the bed. A post without a bed is allowed: it is a worker whose bed
 * was broken, who keeps working and shows that it has no bed. */
public record Assignment(Optional<Spot> bed, Optional<Spot> post) {
    public static final int MAX_BED_TO_POST = 48;
    public static final int MAX_WORKERS = 4;
    public static final Assignment NONE = new Assignment(Optional.empty(), Optional.empty());

    public Assignment {
        Objects.requireNonNull(bed);
        Objects.requireNonNull(post);
        if (bed.isPresent() && post.isPresent() && !bed.get().within(post.get(), MAX_BED_TO_POST))
            throw new IllegalArgumentException("post " + post.get() + " is more than " + MAX_BED_TO_POST + " from bed " + bed.get());
    }

    /** Why a link to a post was refused. */
    public enum Refusal { NO_BED, TOO_FAR, POST_FULL }

    /** The result of an attempt: the assignment after it, and the refusal if it failed (the
     * assignment is then unchanged). */
    public record Outcome(Assignment assignment, Optional<Refusal> refusal) {
        public boolean ok() { return refusal.isEmpty(); }
    }

    /** effects: this worker with {@code newBed}. Its post is kept when it is within reach of the
     * new bed and dropped otherwise; {@link #post()} on the result says which. */
    public Assignment withBed(Spot newBed) {
        Objects.requireNonNull(newBed);
        var keep = post.filter(p -> newBed.within(p, MAX_BED_TO_POST));
        return new Assignment(Optional.of(newBed), keep);
    }

    /** effects: this worker without a bed (its bed was broken or taken); the post is kept. */
    public Assignment withoutBed() { return new Assignment(Optional.empty(), post); }

    /** requires: othersAtPost &ge; 0, the post's workers not counting this one.
     * effects: this worker linked to {@code newPost}, or refused: without a bed, with the post
     * more than 48 blocks from the bed or in another dimension, or with the post already holding
     * four others. */
    public Outcome link(Spot newPost, int othersAtPost) {
        Objects.requireNonNull(newPost);
        if (othersAtPost < 0) throw new IllegalArgumentException("othersAtPost < 0: " + othersAtPost);
        if (bed.isEmpty()) return refuse(Refusal.NO_BED);
        if (!bed.get().within(newPost, MAX_BED_TO_POST)) return refuse(Refusal.TOO_FAR);
        if (othersAtPost >= MAX_WORKERS) return refuse(Refusal.POST_FULL);
        return new Outcome(new Assignment(bed, Optional.of(newPost)), Optional.empty());
    }

    /** effects: this worker without a job. */
    public Assignment withoutPost() { return new Assignment(bed, Optional.empty()); }

    private Outcome refuse(Refusal why) { return new Outcome(this, Optional.of(why)); }
}
