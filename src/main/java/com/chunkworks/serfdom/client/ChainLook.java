/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

/** The chain lead's colours. Vanilla shades alternate leash segments by 0.7 and 1.0 of a brown
 * whose red is 0.5; the chain keeps the alternation, as links in an iron ingot's two face greys
 * (#a8a8a8 and #d8d8d8). Render thread only. */
public final class ChainLook {
    private static final float[] DARK = {0.659F, 0.659F, 0.659F};
    private static final float[] LIGHT = {0.847F, 0.847F, 0.847F};
    private static boolean drawing;
    private ChainLook() {}

    public static void drawing(boolean on) { drawing = on; }
    public static boolean drawing() { return drawing; }

    /** effects: the link colour for a segment vanilla colours with red {@code red}: dark for its
     * darker shade (0.35), light for its lighter (0.5). */
    public static float[] colour(float red) { return red < 0.43F ? DARK : LIGHT; }
}
