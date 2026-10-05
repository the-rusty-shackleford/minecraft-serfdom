/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** How a player's armour is fitted to a villager (D-0004). Armour is drawn by vanilla's armour
 * layer on a stand-in player model posed like the villager, and the fit lies wholly in that
 * stand-in's parts: where each pivots, how it turns, how it is scaled. Never in where the armour's
 * boxes sit, because a mod's own armour model (Lucky's Wardrobe's clothes) keeps its boxes and is
 * given only the stand-in's poses and scales; so clothes fit as armour does.
 *
 * <p>The fit, against the villager's own model (head 10 tall, body 6 deep under a robe 7 deep that
 * hangs to 3.5 pixels above the feet, two 8-pixel upper arms crossed in front):
 * <ul>
 * <li>the helmet sits {@link #HEAD_LIFT} higher, onto the taller head, turning and scaling about
 * the head's own pivot (vanilla lifts the zombie villager's helmet the same distance);</li>
 * <li>the chest {@link #BODY_DEPTH} times deeper, so a chestplate covers the robe's top;</li>
 * <li>the sleeves on the crossed upper arms, {@link #ARM_LENGTH} of their length, the folded
 * forearms left bare;</li>
 * <li>the legs swing with the villager's.</li>
 * </ul>
 * Anything worn on the legs takes the robe off ({@link #robeShown}), so leggings and trousers show.
 *
 * <p>Model space is the game's: pixels, y down, z toward the back. A part maps a point p of its own
 * to its parent as pivot + Rz·Ry·Rx·(scale ∘ p), as the game's model parts do. */
public final class Fit {
    /** How much higher the helmet sits than on a player, in pixels. */
    public static final double HEAD_LIFT = 2.0;
    /** How much deeper than a player's the chest is. */
    public static final double BODY_DEPTH = 1.32;
    /** How long a sleeve is on a villager's upper arm, as a share of a player's. */
    public static final double ARM_LENGTH = 0.7;
    /** Where a sleeve sits along the villager's upper arm, in the arms' frame (pixels). */
    public static final double ARM_SHIFT = -0.8;
    /** The villager's folded arms: their pivot and their pitch, from its model. */
    public static final Vec ARMS_PIVOT = new Vec(0, 3, -1);
    public static final double ARMS_PITCH = -0.75;
    /** How far a sleeve's centre lies out from the arms' middle, past a player's arm's own offset. */
    public static final double ARM_OUT = 5.0;
    /** The villager's legs' pivots lie this far either side of the middle. */
    public static final double LEG_OUT = 2.0;
    /** How much farther back an elytra hangs than on a player: a villager's robe is 1.5 pixels deeper. */
    public static final double ELYTRA_BACK = 1.5;

    private Fit() {}

    /** A point or a direction in model space (pixels). */
    public record Vec(double x, double y, double z) {
        public Vec plus(Vec o) { return new Vec(x + o.x, y + o.y, z + o.z); }
        public Vec times(Vec s) { return new Vec(x * s.x, y * s.y, z * s.z); }
    }

    /** A box between two corners.
     * <p>Rep invariant: min is no greater than max on every axis. */
    public record Box(Vec min, Vec max) {
        public Box {
            if (min.x > max.x || min.y > max.y || min.z > max.z) throw new IllegalArgumentException("min above max: " + min + " " + max);
        }
        /** effects: the box the game builds from a corner, a size and a deformation. */
        public static Box of(double x, double y, double z, double w, double h, double d, double grow) {
            return new Box(new Vec(x - grow, y - grow, z - grow), new Vec(x + w + grow, y + h + grow, z + d + grow));
        }
    }

    /** A model part's pose: its pivot, its three turns (radians) and its scale. */
    public record Pose(Vec pivot, double xRot, double yRot, double zRot, Vec scale) {
        public static final Vec ONE = new Vec(1, 1, 1);
        public Pose { Objects.requireNonNull(pivot); Objects.requireNonNull(scale); }
        /** effects: a part turned only, about {@code pivot}. */
        public static Pose turned(Vec pivot, double xRot, double yRot, double zRot) { return new Pose(pivot, xRot, yRot, zRot, ONE); }
        /** effects: the point of this part at {@code p}, in its parent's frame. */
        public Vec apply(Vec p) { return pivot.plus(rotate(xRot, yRot, zRot, p.times(scale))); }
    }

    /** effects: {@code v} turned as the game turns a part: about x, then y, then z. */
    public static Vec rotate(double xRot, double yRot, double zRot, Vec v) {
        double cx = Math.cos(xRot), sx = Math.sin(xRot), cy = Math.cos(yRot), sy = Math.sin(yRot), cz = Math.cos(zRot), sz = Math.sin(zRot);
        double y1 = cx * v.y - sx * v.z, z1 = sx * v.y + cx * v.z;
        double x2 = cy * v.x + sy * z1, z2 = -sy * v.x + cy * z1;
        return new Vec(cz * x2 - sz * y1, sz * x2 + cz * y1, z2);
    }

    /** requires: every scale is positive.
     * effects: the stand-in head's pose for a villager head turned and scaled so: the head's own
     * turns and scale, with its pivot moved so the helmet sits {@link #HEAD_LIFT} higher in the
     * head's own frame, however the head is turned or scaled. (Guard Villagers draws a child's head
     * half as large again.) The helmet's top layer takes the same pose. */
    public static Pose head(double xRot, double yRot, double zRot, Vec scale) {
        if (!(scale.x() > 0 && scale.y() > 0 && scale.z() > 0)) throw new IllegalArgumentException("a scale is positive: " + scale);
        return new Pose(rotate(xRot, yRot, zRot, new Vec(0, -HEAD_LIFT, 0).times(scale)), xRot, yRot, zRot, scale);
    }

    /** effects: the stand-in body's pose: unturned, deepened. */
    public static Pose body() { return new Pose(new Vec(0, 0, 0), 0, 0, 0, new Vec(1, 1, BODY_DEPTH)); }

    /** effects: a stand-in arm's pose, {@code right} or left: on the villager's upper arm in the
     * folded arms' frame, shortened. */
    public static Pose arm(boolean right) {
        var along = new Vec(right ? -ARM_OUT : ARM_OUT, ARM_SHIFT, 0);
        return new Pose(ARMS_PIVOT.plus(rotate(ARMS_PITCH, 0, 0, along)), ARMS_PITCH, 0, 0, new Vec(1, ARM_LENGTH, 1));
    }

    /** effects: a stand-in leg's pose, {@code right} or left, swung as the villager's is. */
    public static Pose leg(boolean right, double xRot) { return Pose.turned(new Vec(right ? -LEG_OUT : LEG_OUT, 12, 0), xRot, 0, 0); }

    /** effects: whether the villager's robe is drawn: not while anything is worn on its legs. */
    public static boolean robeShown(boolean legsWorn) { return !legsWorn; }
}
