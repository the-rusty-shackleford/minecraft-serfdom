/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Fit.Box;
import com.chunkworks.serfdom.domain.Fit.Pose;
import com.chunkworks.serfdom.domain.Fit.Vec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The fit checked against both models' boxes, copied here from the game (VillagerModel, and
 * HumanoidModel's mesh with the armour layers' deformations: 1.0 outer, 0.5 inner, legs 0.1 less).
 * A guard against clipping by the numbers; the booth's photographs judge the look.
 *
 * <p>Partitions: the turning maths (each axis, the game's order); the helmet at rest and under head
 * turns (none, pitch, yaw, the unhappy shake's roll, all three), on a head at its size and on a
 * child's grown half as large again by Guard Villagers; a scale that is not positive refused; the chest over the robe; the
 * leggings' belt over the body; the outer layer over the inner; each sleeve on its upper arm and off
 * the forearms; the legs at rest and at the widest swing either way; the robe with and without
 * anything on the legs. Every cover is by at least {@link #MARGIN} pixels on every face it must
 * cover. */
final class FitTest {
    private static final double MARGIN = 0.25, EPS = 1e-9, OUTER = 1.0, INNER = 0.5;
    // The villager's boxes, each in its own part's frame.
    private static final Box HEAD_OVERLAY = Box.of(-4, -10, -4, 8, 10, 8, 0.51), BODY = Box.of(-4, 0, -3, 8, 12, 6, 0), ROBE = Box.of(-4, 0, -3, 8, 20, 6, 0.5);
    private static final Box RIGHT_UPPER_ARM = Box.of(-8, -2, -2, 4, 8, 4, 0), LEFT_UPPER_ARM = Box.of(4, -2, -2, 4, 8, 4, 0), FOREARMS = Box.of(-4, 2, -2, 8, 4, 4, 0);
    private static final Box LEG = Box.of(-2, 0, -2, 4, 12, 4, 0);
    private static final Pose ARMS = Pose.turned(Fit.ARMS_PIVOT, Fit.ARMS_PITCH, 0, 0);
    // The armour's boxes, each in the stand-in part's frame.
    private static final Box HELMET = Box.of(-4, -8, -4, 8, 8, 8, OUTER), HELMET_TOP = Box.of(-4, -8, -4, 8, 8, 8, OUTER + 0.5);
    private static final Box CHESTPLATE = Box.of(-4, 0, -2, 8, 12, 4, OUTER), BELT = Box.of(-4, 0, -2, 8, 12, 4, INNER);
    private static final Box RIGHT_SLEEVE = Box.of(-3, -2, -2, 4, 12, 4, OUTER), LEFT_SLEEVE = Box.of(-1, -2, -2, 4, 12, 4, OUTER);
    private static final Box LEGGING = Box.of(-2, 0, -2, 4, 12, 4, INNER - 0.1), BOOT = Box.of(-2, 0, -2, 4, 12, 4, OUTER - 0.1);
    private static final double[][] HEAD_TURNS = {{0, 0, 0}, {0.4, 0, 0}, {-0.8, 0, 0}, {0, 1.2, 0}, {0, 0, 0.3}, {0.4, -0.7, -0.3}, {-0.3, 2.5, 0.25}};

    /** effects: the box {@code armour} makes, posed by {@code stand}, seen in the frame of the
     * villager part posed by {@code part}; an axis-aligned box when the two share their turns. */
    private static Box seen(Box armour, Pose stand, Pose part) {
        var corners = new ArrayList<Vec>();
        for (int i = 0; i < 8; i++) {
            var c = new Vec((i & 1) == 0 ? armour.min().x() : armour.max().x(), (i & 2) == 0 ? armour.min().y() : armour.max().y(), (i & 4) == 0 ? armour.min().z() : armour.max().z());
            corners.add(inverse(part, stand.apply(c)));
        }
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (var c : corners) {
            double[] v = {c.x(), c.y(), c.z()};
            for (int a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], v[a]); hi[a] = Math.max(hi[a], v[a]); }
        }
        return new Box(new Vec(lo[0], lo[1], lo[2]), new Vec(hi[0], hi[1], hi[2]));
    }

    /** effects: the point of the parent's frame {@code q}, in the frame of the part posed by {@code p}. */
    private static Vec inverse(Pose p, Vec q) {
        var d = new Vec(q.x() - p.pivot().x(), q.y() - p.pivot().y(), q.z() - p.pivot().z());
        double cz = Math.cos(-p.zRot()), sz = Math.sin(-p.zRot());
        var a = new Vec(cz * d.x() - sz * d.y(), sz * d.x() + cz * d.y(), d.z());
        double cy = Math.cos(-p.yRot()), sy = Math.sin(-p.yRot());
        var b = new Vec(cy * a.x() + sy * a.z(), a.y(), -sy * a.x() + cy * a.z());
        double cx = Math.cos(-p.xRot()), sx = Math.sin(-p.xRot());
        var r = new Vec(b.x(), cx * b.y() - sx * b.z(), sx * b.y() + cx * b.z());
        return new Vec(r.x() / p.scale().x(), r.y() / p.scale().y(), r.z() / p.scale().z());
    }

    private enum Face { LEFT, RIGHT, TOP, BOTTOM, FRONT, BACK }
    private static final List<Face> SIDES_AND_TOP = List.of(Face.LEFT, Face.RIGHT, Face.TOP, Face.FRONT, Face.BACK);
    private static final List<Face> ALL = List.of(Face.values());

    /** effects: asserts {@code outer} covers {@code inner} on each face named, by MARGIN. */
    private static void covers(Box outer, Box inner, List<Face> faces, String what) {
        for (var f : faces) {
            double by = switch (f) {
                case LEFT -> inner.min().x() - outer.min().x();
                case RIGHT -> outer.max().x() - inner.max().x();
                case TOP -> inner.min().y() - outer.min().y();
                case BOTTOM -> outer.max().y() - inner.max().y();
                case FRONT -> inner.min().z() - outer.min().z();
                case BACK -> outer.max().z() - inner.max().z();
            };
            assertTrue(by >= MARGIN - EPS, what + ": the " + f + " face covered by " + by + ", wants " + MARGIN + "; outer " + outer + " inner " + inner);
        }
    }

    private static void near(Vec a, Vec b, String what) {
        assertEquals(a.x(), b.x(), 1e-9, what + " x");
        assertEquals(a.y(), b.y(), 1e-9, what + " y");
        assertEquals(a.z(), b.z(), 1e-9, what + " z");
    }

    @Test void partsTurnAboutXThenYThenZAsTheGameDoes() {
        double q = Math.PI / 2;
        near(new Vec(0, 0, 1), Fit.rotate(q, 0, 0, new Vec(0, 1, 0)), "x turns y to z");
        near(new Vec(1, 0, 0), Fit.rotate(0, q, 0, new Vec(0, 0, 1)), "y turns z to x");
        near(new Vec(0, 1, 0), Fit.rotate(0, 0, q, new Vec(1, 0, 0)), "z turns x to y");
        near(new Vec(-1, 0, 0), Fit.rotate(q, 0, q, new Vec(0, 0, -1)), "x first (−z to y), then z (y to −x)");
    }

    @Test void theHelmetSitsHigherInTheHeadsOwnFrameHoweverTheHeadTurnsOrGrows() {
        // 1.5: Guard Villagers' big head on a child.
        for (double grow : new double[]{1.0, 1.5}) for (var t : HEAD_TURNS) {
            var scale = new Vec(grow, grow, grow);
            var stand = Fit.head(t[0], t[1], t[2], scale);
            var villagerHead = new Pose(new Vec(0, 0, 0), t[0], t[1], t[2], scale);
            for (var corner : List.of(HELMET.min(), HELMET.max(), new Vec(3, -8, -4), new Vec(-4, 0, 4)))
                near(villagerHead.apply(corner.plus(new Vec(0, -Fit.HEAD_LIFT, 0))), stand.apply(corner), "a corner at turns " + t[0] + "," + t[1] + "," + t[2] + " grown " + grow);
            var helmet = seen(HELMET, stand, villagerHead);
            covers(helmet, HEAD_OVERLAY, SIDES_AND_TOP, "the helmet over the head");
            covers(seen(HELMET_TOP, stand, villagerHead), helmet, ALL, "the helmet's top layer over the helmet");
        }
        assertThrows(IllegalArgumentException.class, () -> Fit.head(0, 0, 0, new Vec(0, 1, 1)));
    }

    @Test void theChestplateCoversTheRobesTopAndTheBeltTheBody() {
        var none = Pose.turned(new Vec(0, 0, 0), 0, 0, 0);
        var chest = seen(CHESTPLATE, Fit.body(), none);
        covers(chest, ROBE, SIDES_AND_TOP, "the chestplate over the robe");
        covers(chest, BODY, SIDES_AND_TOP, "the chestplate over the body");
        var belt = seen(BELT, Fit.body(), none);
        covers(belt, BODY, SIDES_AND_TOP, "the leggings' belt over the body (the robe is off)");
        covers(chest, belt, List.of(Face.LEFT, Face.RIGHT, Face.FRONT, Face.BACK), "the chestplate over the belt");
    }

    @Test void eachSleeveSitsOnItsUpperArmAndLeavesTheForearmsBare() {
        var right = seen(RIGHT_SLEEVE, Fit.arm(true), ARMS);
        var left = seen(LEFT_SLEEVE, Fit.arm(false), ARMS);
        covers(right, RIGHT_UPPER_ARM, ALL, "the right sleeve");
        covers(left, LEFT_UPPER_ARM, ALL, "the left sleeve");
        // Over the forearms no more than the elbow's pixel; the cuffs lie across the middle.
        assertTrue(right.max().x() <= FOREARMS.min().x() + 1 + EPS, "the right sleeve ends at the elbow: " + right);
        assertTrue(left.min().x() >= FOREARMS.max().x() - 1 - EPS, "the left sleeve ends at the elbow: " + left);
    }

    @Test void theLeggingsAndBootsFollowTheLegsThroughTheirSwing() {
        for (boolean right : new boolean[]{false, true}) for (double swing : new double[]{0, 0.7, -0.7}) {
            var villagerLeg = Pose.turned(new Vec(right ? -2 : 2, 12, 0), swing, 0, 0);
            var legging = seen(LEGGING, Fit.leg(right, swing), villagerLeg);
            covers(legging, LEG, ALL, "the legging at " + swing);
            covers(seen(BOOT, Fit.leg(right, swing), villagerLeg), legging, List.of(Face.LEFT, Face.RIGHT, Face.FRONT, Face.BACK, Face.BOTTOM), "the boot over the legging at " + swing);
        }
    }

    @Test void anythingOnTheLegsTakesTheRobeOff() {
        assertTrue(Fit.robeShown(false));
        assertFalse(Fit.robeShown(true));
    }

    @Test void aBoxHasItsCornersInOrder() {
        assertThrows(IllegalArgumentException.class, () -> new Box(new Vec(1, 0, 0), new Vec(0, 1, 1)));
        assertEquals(new Box(new Vec(-1, -1, -1), new Vec(3, 5, 7)), Box.of(0, 0, 0, 2, 4, 6, 1));
    }
}
