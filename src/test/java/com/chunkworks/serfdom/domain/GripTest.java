/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Armoury.Kind;
import com.chunkworks.serfdom.domain.Fit.Pose;
import com.chunkworks.serfdom.domain.Fit.Vec;
import com.chunkworks.serfdom.domain.Grip.Hold;
import com.chunkworks.serfdom.domain.Grip.Moment;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>{@code of}: a tool or not, by every weapon kind and none; a weapon's kind over its being a
 * tool;</li>
 * <li>{@code arms}: NONE and null refused; a tool at rest, walking (both phases), mid-blow and at the
 * blow's two ends; a bow and a crossbow, fighting (head level, pitched, turned) and not; the bob at
 * two ages;</li>
 * <li>{@code Moment}'s rep invariant: walkAmount and blow below 0, above 1 and at both ends; a value
 * not finite.</li>
 * </ul>
 * Where the hand is comes from the pose itself ({@link Pose#apply}), the bottom of a 12-pixel arm,
 * so a pose is checked by what it does, not only by its numbers. */
final class GripTest {
    private static final double EPS = 1e-9;
    /** The bottom of the arm's hand, in the arm's own frame (the arm is 12 long from 2 above its pivot). */
    private static final Vec HAND = new Vec(-1, 10, 0);

    private static Moment still() { return new Moment(0, 0, 0, 0, 0, false, 0); }
    private static Moment at(double walk, double amount, double blow, double yaw, double pitch, boolean fighting, double age) {
        return new Moment(walk, amount, blow, yaw, pitch, fighting, age);
    }
    /** effects: the pose without its bob at {@code age} (the bob only adds). */
    private static Pose unbobbed(Pose p, double age, int sign) {
        return new Pose(p.pivot(), p.xRot() - sign * Math.sin(age * 0.067) * 0.05, p.yRot(), p.zRot() - sign * (Math.cos(age * 0.09) * 0.05 + 0.05), p.scale());
    }

    @Test void theHoldIsTheWeaponsKindElseATool() {
        assertEquals(Hold.NONE, Grip.of(false, Optional.empty()));
        assertEquals(Hold.TOOL, Grip.of(true, Optional.empty()));
        assertEquals(Hold.TOOL, Grip.of(false, Optional.of(Kind.MELEE)));
        assertEquals(Hold.BOW, Grip.of(false, Optional.of(Kind.BOW)));
        assertEquals(Hold.CROSSBOW, Grip.of(false, Optional.of(Kind.CROSSBOW)));
        assertEquals(Hold.CROSSBOW, Grip.of(false, Optional.of(Kind.GUN)));
        assertEquals(Hold.CROSSBOW, Grip.of(false, Optional.of(Kind.LAUNCHER)));
        assertEquals(Hold.BOW, Grip.of(true, Optional.of(Kind.BOW)), "a weapon's kind decides over its being a tool");
        assertThrows(NullPointerException.class, () -> Grip.of(true, null));
    }

    @Test void nothingHeldOrNoMomentIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Grip.arms(Hold.NONE, still()));
        assertThrows(NullPointerException.class, () -> Grip.arms(null, still()));
        assertThrows(NullPointerException.class, () -> Grip.arms(Hold.TOOL, null));
    }

    @Test void aToolAtRestIsHeldALittleForwardFromTheShoulders() {
        var a = Grip.arms(Hold.TOOL, still());
        var right = unbobbed(a.right(), 0, 1);
        var left = unbobbed(a.left(), 0, -1);
        assertEquals(Grip.RIGHT_SHOULDER, right.pivot());
        assertEquals(Grip.LEFT_SHOULDER, left.pivot());
        assertEquals(-Math.PI / 10, right.xRot(), EPS);
        assertEquals(0, left.xRot(), EPS);
        assertEquals(0, right.yRot(), EPS);
        var hand = right.apply(HAND);
        assertTrue(hand.z() < -3, "the right hand is in front of the body's front (z -3): " + hand);
        assertTrue(hand.y() > 10, "and low, at the hip: " + hand);
        assertEquals(0, left.apply(HAND).z(), 1e-6, "the left hangs straight");
    }

    @Test void walkingSwingsTheArmsOppositeTheRightHalfAsFar() {
        for (double walk : new double[]{0, Math.PI / Grip.STRIDE}) {
            var still = Grip.arms(Hold.TOOL, at(walk, 0, 0, 0, 0, false, 0));
            var a = Grip.arms(Hold.TOOL, at(walk, 1, 0, 0, 0, false, 0));
            double right = a.right().xRot() - still.right().xRot(), left = a.left().xRot() - still.left().xRot();
            assertEquals(1.0, Math.abs(left), EPS, "the left swings a full radian at full speed");
            assertEquals(-0.5 * left, right, EPS, "the right swings the other way, half as far");
        }
    }

    @Test void aBlowLiftsTheToolAndBringsItDownAcross() {
        var rest = Grip.arms(Hold.TOOL, still());
        var lifted = Grip.arms(Hold.TOOL, at(0, 0, 0.3, 0, 0, false, 0));
        assertTrue(lifted.right().apply(HAND).y() < rest.right().apply(HAND).y() - 4, "mid-blow the hand is well up: " + lifted.right().apply(HAND));
        assertTrue(lifted.right().zRot() < rest.right().zRot(), "and across the body");
        assertNotEquals(Grip.RIGHT_SHOULDER, lifted.right().pivot(), "the shoulders twist");
        assertEquals(5, Math.hypot(lifted.right().pivot().x(), lifted.right().pivot().z()), EPS, "about the middle");
        for (double end : new double[]{0, 1}) {
            var a = Grip.arms(Hold.TOOL, at(0, 0, end, 0, 0, false, 0));
            assertEquals(rest.right().xRot(), a.right().xRot(), 1e-6, "at " + end + " the blow is over");
            assertEquals(rest.right().pivot().x(), a.right().pivot().x(), 1e-6);
            assertEquals(rest.left().xRot(), a.left().xRot(), 1e-6);
        }
    }

    @Test void aBowWhileFightingIsDrawnToTheEye() {
        for (double[] head : new double[][]{{0, 0}, {0.6, -0.4}, {-0.8, 0.3}}) {
            var a = Grip.arms(Hold.BOW, at(0, 0, 0, head[0], head[1], true, 0));
            var right = unbobbed(a.right(), 0, 1);
            var left = unbobbed(a.left(), 0, -1);
            assertEquals(-Math.PI / 2 + head[1], right.xRot(), EPS);
            assertEquals(-Math.PI / 2 + head[1], left.xRot(), EPS);
            assertEquals(-0.1 + head[0], right.yRot(), EPS, "the bow turns with the head");
            assertEquals(0.5 + head[0], left.yRot(), EPS, "the left hand across to the string");
        }
        var level = unbobbed(Grip.arms(Hold.BOW, at(0, 0, 0, 0, 0, true, 0)).right(), 0, 1);
        assertTrue(level.apply(HAND).z() < -9, "level, the arm points straight ahead: " + level.apply(HAND));
    }

    @Test void aCrossbowOrGunWhileFightingIsHeldToTheShoulder() {
        var a = Grip.arms(Hold.CROSSBOW, at(0, 0, 0, 0.2, -0.1, true, 0));
        var right = unbobbed(a.right(), 0, 1);
        var left = unbobbed(a.left(), 0, -1);
        assertEquals(-Math.PI / 2 - 0.1 + 0.1, right.xRot(), EPS);
        assertEquals(-0.3 + 0.2, right.yRot(), EPS);
        assertEquals(-1.5 - 0.1, left.xRot(), EPS);
        assertEquals(0.6 + 0.2, left.yRot(), EPS, "the left under the stock");
    }

    @Test void aRangedWeaponNotFightingIsCarriedAsATool() {
        for (var hold : new Hold[]{Hold.BOW, Hold.CROSSBOW}) {
            var m = at(1.3, 0.6, 0, 0.4, 0.2, false, 7);
            assertEquals(Grip.arms(Hold.TOOL, m), Grip.arms(hold, m), hold.name());
        }
    }

    @Test void theArmsBobWithAgeTheLeftTheOtherWay() {
        var a = Grip.arms(Hold.TOOL, still());
        assertEquals(-Math.PI / 10, a.right().xRot(), EPS, "no pitch bob at age 0");
        assertEquals(0.1, a.right().zRot(), EPS);
        assertEquals(-0.1, a.left().zRot(), EPS);
        var later = Grip.arms(Hold.TOOL, at(0, 0, 0, 0, 0, false, 40));
        assertEquals(-Math.PI / 10 + Math.sin(40 * 0.067) * 0.05, later.right().xRot(), EPS);
        assertEquals(-(Math.cos(40 * 0.09) * 0.05 + 0.05), later.left().zRot(), EPS);
    }

    @Test void aMomentOutOfRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> at(0, -0.01, 0, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> at(0, 1.01, 0, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> at(0, 0, -0.01, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> at(0, 0, 1.01, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> at(Double.NaN, 0, 0, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> at(0, 0, 0, Double.POSITIVE_INFINITY, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> at(0, 0, 0, 0, 0, false, Double.NaN));
        assertDoesNotThrow(() -> at(0, 1, 1, 0, 0, true, 0));
        assertDoesNotThrow(() -> at(0, 0, 0, 0, 0, true, 0));
    }
}
