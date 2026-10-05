/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A Work Post's stock list (D-0002): rows of "keep N of X", in the order the player put them,
 * which is the order they are worked in. Immutable.
 *
 * <p>Rep invariant: at most {@link #MAX_ROWS} rows; no two rows name the same item. */
public record Stock(List<Row> rows) {
    public static final int MAX_ROWS = 9;
    public static final int MAX_KEEP = 999;
    public static final Stock EMPTY = new Stock(List.of());

    /** Keep {@code keep} of {@code item}. Immutable. RI: item not blank; 1 &le; keep &le;
     * {@link #MAX_KEEP}. */
    public record Row(String item, int keep) {
        public Row {
            Objects.requireNonNull(item);
            if (item.isBlank()) throw new IllegalArgumentException("blank item");
            if (keep < 1 || keep > MAX_KEEP) throw new IllegalArgumentException("keep " + keep);
        }
    }

    public Stock {
        rows = List.copyOf(rows);
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException(rows.size() + " rows");
        var seen = new HashSet<String>();
        for (var r : rows) if (!seen.add(r.item())) throw new IllegalArgumentException("two rows of " + r.item());
    }

    /** effects: the list with row {@code index} set to {@code row}, or {@code row} added at the end
     * when index is the size; the list unchanged when another row already names the item or the list
     * is full. requires: 0 &le; index &le; size. */
    public Stock with(int index, Row row) {
        if (index < 0 || index > rows.size()) throw new IndexOutOfBoundsException(index);
        for (int i = 0; i < rows.size(); i++) if (i != index && rows.get(i).item().equals(row.item())) return this;
        var out = new ArrayList<>(rows);
        if (index == rows.size()) { if (rows.size() >= MAX_ROWS) return this; out.add(row); }
        else out.set(index, row);
        return new Stock(out);
    }

    /** effects: the list without row {@code index}. requires: 0 &le; index &lt; size. */
    public Stock without(int index) {
        var out = new ArrayList<>(rows);
        out.remove(index);
        return new Stock(out);
    }

    /** effects: how many more of the row's item are wanted: its keep less what is stored and what
     * is on its way, never below 0. */
    public static int shortBy(Row row, Map<String, Integer> stored, Map<String, Integer> coming) {
        return Math.max(0, row.keep() - stored.getOrDefault(row.item(), 0) - coming.getOrDefault(row.item(), 0));
    }

    /** effects: what row {@code index}'s plan may spend: {@code counts} less what every other row
     * keeps of its own item, never below 0, so a row never uses up another row's stock.
     * requires: 0 &le; index &lt; size. */
    public Map<String, Integer> spendable(int index, Map<String, Integer> counts) {
        var out = new HashMap<>(counts);
        for (int j = 0; j < rows.size(); j++) {
            if (j == index) continue;
            var r = rows.get(j);
            out.computeIfPresent(r.item(), (k, n) -> Math.max(0, n - r.keep()));
        }
        return out;
    }

    /** effects: {@code counts} less what every row keeps of its item, never below 0: what the
     * duties beyond the list may spend. */
    public Map<String, Integer> spare(Map<String, Integer> counts) {
        var out = new HashMap<>(counts);
        for (var r : rows) out.computeIfPresent(r.item(), (k, n) -> Math.max(0, n - r.keep()));
        return out;
    }
}
