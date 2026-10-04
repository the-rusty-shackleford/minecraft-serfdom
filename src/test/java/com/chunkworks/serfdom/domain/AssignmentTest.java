/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static com.chunkworks.serfdom.domain.Assignment.Refusal.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: link with no bed, with the post at 48 and 49 blocks, in another dimension, with 3
 * and 4 others at the post, relinking; a new bed near and far from the post; losing the bed;
 * clearing the job; constructing against the invariant. */
final class AssignmentTest {
    static final String OVER = "minecraft:overworld";
    static final Spot BED = new Spot(OVER, 0, 64, 0);

    @Test void aJobNeedsABedFirst() {
        var out = Assignment.NONE.link(new Spot(OVER, 5, 64, 5), 0);
        assertFalse(out.ok());
        assertEquals(Optional.of(NO_BED), out.refusal());
        assertSame(Assignment.NONE, out.assignment(), "a refusal leaves the assignment as it was");
    }
    @Test void thePostMayBeFortyEightBlocksFromTheBedAndNoFurther() {
        var bedded = Assignment.NONE.withBed(BED);
        var at48 = bedded.link(new Spot(OVER, 48, 64, 0), 0);
        assertTrue(at48.ok());
        assertEquals(Optional.of(new Spot(OVER, 48, 64, 0)), at48.assignment().post());
        assertEquals(Optional.of(TOO_FAR), bedded.link(new Spot(OVER, 49, 64, 0), 0).refusal());
        assertEquals(Optional.of(TOO_FAR), bedded.link(new Spot(OVER, 34, 64, 34), 0).refusal(), "the reach is straight-line: 34,34 is 48.08 away");
        assertTrue(bedded.link(new Spot(OVER, 33, 64, 33), 0).ok(), "33,33 is 46.7 away");
    }
    @Test void aPostInAnotherDimensionIsTooFar() {
        assertEquals(Optional.of(TOO_FAR), Assignment.NONE.withBed(BED).link(new Spot("minecraft:the_nether", 0, 64, 0), 0).refusal());
    }
    @Test void aPostHoldsFourWorkers() {
        var bedded = Assignment.NONE.withBed(BED);
        var post = new Spot(OVER, 10, 64, 0);
        assertTrue(bedded.link(post, 3).ok());
        assertEquals(Optional.of(POST_FULL), bedded.link(post, 4).refusal());
        assertThrows(IllegalArgumentException.class, () -> bedded.link(post, -1));
    }
    @Test void aNewBedKeepsAPostInReachAndDropsOneOutOfIt() {
        var post = new Spot(OVER, 40, 64, 0);
        var linked = Assignment.NONE.withBed(BED).link(post, 0).assignment();
        var near = linked.withBed(new Spot(OVER, 60, 64, 0));
        assertEquals(Optional.of(post), near.post());
        var far = linked.withBed(new Spot(OVER, -10, 64, 0));
        assertEquals(Optional.empty(), far.post(), "50 blocks from the post: the job goes");
        assertEquals(Optional.of(new Spot(OVER, -10, 64, 0)), far.bed());
    }
    @Test void losingTheBedKeepsTheJob() {
        var post = new Spot(OVER, 5, 64, 0);
        var lost = Assignment.NONE.withBed(BED).link(post, 0).assignment().withoutBed();
        assertEquals(Optional.empty(), lost.bed());
        assertEquals(Optional.of(post), lost.post());
        assertEquals(Optional.of(NO_BED), lost.link(new Spot(OVER, 6, 64, 0), 0).refusal(), "but relinking needs a bed again");
    }
    @Test void clearingTheJobKeepsTheBed() {
        var cleared = Assignment.NONE.withBed(BED).link(new Spot(OVER, 5, 64, 0), 0).assignment().withoutPost();
        assertEquals(Optional.of(BED), cleared.bed());
        assertEquals(Optional.empty(), cleared.post());
    }
    @Test void theInvariantIsChecked() {
        assertThrows(IllegalArgumentException.class, () -> new Assignment(Optional.of(BED), Optional.of(new Spot(OVER, 100, 64, 0))));
        assertThrows(IllegalArgumentException.class, () -> new Assignment(Optional.of(BED), Optional.of(new Spot("minecraft:the_end", 0, 64, 0))));
    }
}
