/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;
import java.util.Optional;

/** How a villager holds what is in its hand (D-0011). Vanilla draws anything a villager holds as an
 * item lying on the ground, propped in front of its folded arms. A tool or a weapon is held instead:
 * the arms come out of the fold, as an illager's do, the right hand gripping it as a player's hand
 * does, and they move as a player's arms move: a swing with the walk, the tool a little forward, a
 * blow when it strikes or works, a bow or a crossbow brought up to the eye while it fights. Anything
 * else (bread, an emerald, a trade's goods) is shown on the folded arms as vanilla shows it.
 *
 * <p>Model space is the game's (pixels, y down, z toward the back, radians), as in {@link Fit}: the
 * arms pivot at the shoulders, {@link #RIGHT_SHOULDER} and {@link #LEFT_SHOULDER}, where a player's
 * and an illager's do, so the hand, its item and a chestplate's sleeve sit where they sit on a player. */
public final class Grip {
    public static final Fit.Vec RIGHT_SHOULDER = new Fit.Vec(-5, 2, 0);
    public static final Fit.Vec LEFT_SHOULDER = new Fit.Vec(5, 2, 0);
    /** A player's walk: the arm's swing a step, and how far it swings. */
    static final double STRIDE = 0.6662, SWING = 1.0;
    /** How far forward a held tool brings the arm, as a player's does. */
    static final double HOLD = Math.PI / 10;

    private Grip() {}

    /** What the hand holds, as far as the arms are concerned. */
    public enum Hold {
        /** Nothing a hand grips: vanilla's folded arms. */
        NONE,
        /** A tool or a melee weapon. */
        TOOL,
        /** A bow, drawn to the eye while fighting. */
        BOW,
        /** A crossbow, a gun or a launcher, held to the shoulder while fighting. */
        CROSSBOW
    }

    /** effects: the hold for an item that is a tool ({@code tool}) or the weapon {@code weapon} (as the
     * defence classes them), or neither. A weapon's kind decides over its being a tool. */
    public static Hold of(boolean tool, Optional<Armoury.Kind> weapon) {
        Objects.requireNonNull(weapon);
        if (weapon.isPresent()) return switch (weapon.get()) {
            case MELEE -> Hold.TOOL;
            case BOW -> Hold.BOW;
            case CROSSBOW, GUN, LAUNCHER -> Hold.CROSSBOW;
        };
        return tool ? Hold.TOOL : Hold.NONE;
    }

    /** The two arms' poses, in the body's frame. */
    public record Arms(Fit.Pose right, Fit.Pose left) {
        public Arms { Objects.requireNonNull(right); Objects.requireNonNull(left); }
    }

    /** What the arms answer to this frame: the walk's phase and how fast it goes (vanilla's limb swing
     * and its amount), the blow's progress (0 none, to 1 done), the head's turn and pitch, whether it is
     * fighting, and its age in ticks (the arms' idle bob). RI: walkAmount and blow in [0, 1]; all
     * finite. */
    public record Moment(double walk, double walkAmount, double blow, double headYaw, double headPitch, boolean fighting, double age) {
        public Moment {
            if (!(walkAmount >= 0 && walkAmount <= 1)) throw new IllegalArgumentException("walkAmount in [0, 1]: " + walkAmount);
            if (!(blow >= 0 && blow <= 1)) throw new IllegalArgumentException("blow in [0, 1]: " + blow);
            if (!Double.isFinite(walk) || !Double.isFinite(headYaw) || !Double.isFinite(headPitch) || !Double.isFinite(age)) throw new IllegalArgumentException("not finite");
        }
    }

    /** requires: {@code hold} is not NONE.
     * effects: the arms for {@code hold} at {@code m}, as a player's arms are posed (HumanoidModel):
     * <ul>
     * <li>a bow while fighting: both arms along the head's aim, the left a little across;</li>
     * <li>a crossbow while fighting: the crossbow hold, the left arm under the stock;</li>
     * <li>otherwise: both swing with the walk, half as far for the right, which is a tenth of a turn
     * forward; a blow lifts it and brings it down across the body, as a player's does.</li>
     * </ul>
     * Both then bob a little with its age, the left the opposite way, as a player's do. */
    public static Arms arms(Hold hold, Moment m) {
        var posed = pose(hold, m);
        return new Arms(bob(posed.right(), m.age(), 1), bob(posed.left(), m.age(), -1));
    }

    /** effects: {@code p} with vanilla's idle bob at {@code age} (AnimationUtils.bobModelPart), {@code sign}
     * 1 for the right arm and -1 for the left. */
    static Fit.Pose bob(Fit.Pose p, double age, int sign) {
        return new Fit.Pose(p.pivot(), p.xRot() + sign * Math.sin(age * 0.067) * 0.05, p.yRot(),
                p.zRot() + sign * (Math.cos(age * 0.09) * 0.05 + 0.05), p.scale());
    }

    private static Arms pose(Hold hold, Moment m) {
        Objects.requireNonNull(hold);
        Objects.requireNonNull(m);
        if (hold == Hold.NONE) throw new IllegalArgumentException("nothing held");
        if (m.fighting() && hold == Hold.BOW) {
            return new Arms(Fit.Pose.turned(RIGHT_SHOULDER, -Math.PI / 2 + m.headPitch(), -0.1 + m.headYaw(), 0),
                    Fit.Pose.turned(LEFT_SHOULDER, -Math.PI / 2 + m.headPitch(), 0.1 + m.headYaw() + 0.4, 0));
        }
        if (m.fighting() && hold == Hold.CROSSBOW) {
            return new Arms(Fit.Pose.turned(RIGHT_SHOULDER, -Math.PI / 2 + m.headPitch() + 0.1, -0.3 + m.headYaw(), 0),
                    Fit.Pose.turned(LEFT_SHOULDER, -1.5 + m.headPitch(), 0.6 + m.headYaw(), 0));
        }
        double right = Math.cos(m.walk() * STRIDE + Math.PI) * SWING * m.walkAmount() * 0.5 - HOLD;
        double left = Math.cos(m.walk() * STRIDE) * SWING * m.walkAmount();
        if (m.blow() <= 0) return new Arms(Fit.Pose.turned(RIGHT_SHOULDER, right, 0, 0), Fit.Pose.turned(LEFT_SHOULDER, left, 0, 0));
        // A player's blow (HumanoidModel.setupAttackAnimation), the body's twist given to the arms alone:
        // the shoulders turn about the middle, the right arm lifts and comes down across.
        double twist = Math.sin(Math.sqrt(m.blow()) * 2 * Math.PI) * 0.2;
        var rightAt = new Fit.Vec(-Math.cos(twist) * 5, RIGHT_SHOULDER.y(), Math.sin(twist) * 5);
        var leftAt = new Fit.Vec(Math.cos(twist) * 5, LEFT_SHOULDER.y(), -Math.sin(twist) * 5);
        double eased = 1 - Math.pow(1 - m.blow(), 4);
        double lift = Math.sin(eased * Math.PI) * 1.2 + Math.sin(m.blow() * Math.PI) * -(m.headPitch() - 0.7) * 0.75;
        return new Arms(Fit.Pose.turned(rightAt, right - lift, twist * 3, Math.sin(m.blow() * Math.PI) * -0.4),
                Fit.Pose.turned(leftAt, left + twist, twist, 0));
    }
}
