/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** A worker's day in game ticks (D-0001): idle until 2000, work until 8000, meet until 10000, idle
 * until 12000, then sleep until the day turns at 24000. A captive's day (D-0003) has no meeting:
 * it works on until 10000. */
public final class WorkDay {
    public static final int DAY = 24000;
    public static final int WORK_START = 2000;
    public static final int WORK_END = 8000;
    public static final int MEET_END = 10000;
    /** The end of a captive's work: it skips the meeting and works through it. */
    public static final int CAPTIVE_WORK_END = MEET_END;
    public static final int SLEEP_START = 12000;
    private WorkDay() {}

    public enum Part { IDLE, WORK, MEET, SLEEP }

    /** effects: the part of a captive's day {@code dayTime} falls in: idle until 2000, work until
     * 10000, idle until 12000, then sleep. Never MEET. */
    public static Part atCaptive(long dayTime) {
        int t = tickOfDay(dayTime);
        if (t < WORK_START) return Part.IDLE;
        if (t < CAPTIVE_WORK_END) return Part.WORK;
        if (t < SLEEP_START) return Part.IDLE;
        return Part.SLEEP;
    }

    /** effects: the ticks left in the shift at {@code dayTime} for a captive when {@code captive},
     * else for a hired worker; 0 outside its working hours. */
    public static int shiftLeft(long dayTime, boolean captive) {
        if (!captive) return shiftLeft(dayTime);
        int t = tickOfDay(dayTime);
        return t >= WORK_START && t < CAPTIVE_WORK_END ? CAPTIVE_WORK_END - t : 0;
    }

    /** effects: the tick within the day of {@code dayTime}, in [0, 24000); negative times count
     * back from the turn of a day. */
    public static int tickOfDay(long dayTime) { return (int) Math.floorMod(dayTime, (long) DAY); }

    /** effects: the part of the worker's day {@code dayTime} falls in. */
    public static Part at(long dayTime) {
        int t = tickOfDay(dayTime);
        if (t < WORK_START) return Part.IDLE;
        if (t < WORK_END) return Part.WORK;
        if (t < MEET_END) return Part.MEET;
        if (t < SLEEP_START) return Part.IDLE;
        return Part.SLEEP;
    }

    /** effects: the ticks left in the shift at {@code dayTime}; 0 outside working hours. */
    public static int shiftLeft(long dayTime) {
        int t = tickOfDay(dayTime);
        return t >= WORK_START && t < WORK_END ? WORK_END - t : 0;
    }
}
