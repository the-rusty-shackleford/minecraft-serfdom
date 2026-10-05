/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;

/** Where everything sits on the Worker Screen (D-0004), in GUI pixels from the panel's top left:
 * the four wearing slots in a column, the worker drawn beside them, its details and the buttons to
 * their right, and the player's inventory below. Shared by the menu, which places the slots, and
 * the screen, which draws the rest. It must fit at GUI scale 5 on Rusty's 3440×1440 screen
 * (688×288). A slot's position is where its item is drawn; its frame is a pixel larger all round. */
public final class WorkerLayout {
    public static final int WIDTH = 312, HEIGHT = 228, PAD = 8, SLOT = 18, LINE = 11;
    /** The wearing slots' column: the first item's corner; each next one a slot lower. */
    public static final int WEAR_X = PAD + 1, WEAR_Y = 23;
    /** The worker's portrait. */
    public static final Rect PORTRAIT = new Rect(30, 22, 50, 72);
    /** The details: label and value columns, first row. */
    public static final int DETAIL_X = 88, VALUE_X = DETAIL_X + 58, DETAIL_Y = 24;
    /** The buttons: three on the first row under the details, Set free alone on the second. */
    public static final int BUTTON_W = 68, BUTTON_H = 20, BUTTON_GAP = 6, BUTTON_Y = DETAIL_Y + 5 * LINE + 4, FREE_Y = BUTTON_Y + BUTTON_H + 4;
    /** The player's inventory: the label, the three rows' first item corner, the hotbar's. */
    public static final int INVENTORY_X = (WIDTH - 9 * SLOT) / 2 + 1, INVENTORY_LABEL_Y = FREE_Y + BUTTON_H + 6,
            INVENTORY_Y = INVENTORY_LABEL_Y + 11, HOTBAR_Y = INVENTORY_Y + 3 * SLOT + 4;

    private WorkerLayout() {}

    /** A rectangle in GUI pixels. */
    public record Rect(int x, int y, int w, int h) {
        public Rect {
            if (w <= 0 || h <= 0) throw new IllegalArgumentException("a rectangle has a size: " + w + "×" + h);
        }
        public boolean overlaps(Rect o) { return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h; }
        public boolean inside(Rect o) { return x >= o.x && y >= o.y && x + w <= o.x + o.w && y + h <= o.y + o.h; }
    }

    /** The whole panel. */
    public static final Rect PANEL = new Rect(0, 0, WIDTH, HEIGHT);

    /** effects: the frame of the wearing slot at {@code index} (0 the head, 3 the feet). */
    public static Rect wearing(int index) {
        if (index < 0 || index > 3) throw new IllegalArgumentException("four wearing slots: " + index);
        return new Rect(WEAR_X - 1, WEAR_Y + index * SLOT - 1, SLOT, SLOT);
    }

    /** effects: the frame of the details' rows. */
    public static Rect details() { return new Rect(DETAIL_X, DETAIL_Y, WIDTH - PAD - DETAIL_X, 5 * LINE); }

    /** effects: the button at {@code index} on the first row (0 Assign bed, 1 Assign job, 2 Clear job). */
    public static Rect button(int index) {
        if (index < 0 || index > 2) throw new IllegalArgumentException("three buttons on the row: " + index);
        return new Rect(DETAIL_X + index * (BUTTON_W + BUTTON_GAP), BUTTON_Y, BUTTON_W, BUTTON_H);
    }

    /** effects: Set free, under Clear job. */
    public static Rect free() { return new Rect(button(2).x(), FREE_Y, BUTTON_W, BUTTON_H); }

    /** effects: the frame of the player's three inventory rows. */
    public static Rect inventory() { return new Rect(INVENTORY_X - 1, INVENTORY_Y - 1, 9 * SLOT, 3 * SLOT); }

    /** effects: the frame of the hotbar. */
    public static Rect hotbar() { return new Rect(INVENTORY_X - 1, HOTBAR_Y - 1, 9 * SLOT, SLOT); }

    /** effects: every part of the panel that must not overlap another. */
    public static List<Rect> parts() {
        return List.of(wearing(0), wearing(1), wearing(2), wearing(3), PORTRAIT, details(), button(0), button(1), button(2), free(), inventory(), hotbar());
    }

    /** effects: whether the panel fits a screen of that many GUI pixels. */
    public static boolean fits(int guiWidth, int guiHeight) { return WIDTH <= guiWidth && HEIGHT <= guiHeight; }
}
