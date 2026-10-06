/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Taste.Category;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>the draw: the same UUID twice; two UUIDs; the four categories of one UUID; its range;</li>
 * <li>the lean: u at 0, inside, near 1; a lean of -1, 0, +1; a lean outside [-1, 1], a spread outside
 * [0, 1); many villagers' mean by lean;</li>
 * <li>favourite: one highest; a tie, to the first; likes: above, at, below 1;</li>
 * <li>the factor: no category, one, several (the highest);</li>
 * <li>wants: no value; no category; a category it doesn't like, one it does, one of several; has none,
 * some, the most, more; most 0; a negative count;</li>
 * <li>a multiplier that breaks the invariant.</li>
 * </ul> */
final class TasteTest {
    private static final Map<Category, Double> EVEN = Map.of();

    @Test void aTasteIsDrawnFromTheUuidAndNeverChanges() {
        var id = UUID.fromString("6b9a1e4c-1d7f-4e8a-9c2b-3f5d7a9e1b2c");
        var t = Taste.of(id.getMostSignificantBits(), id.getLeastSignificantBits(), EVEN, 0.5);
        assertEquals(t, Taste.of(id.getMostSignificantBits(), id.getLeastSignificantBits(), EVEN, 0.5), "the same villager, the same taste");
        var other = UUID.fromString("6b9a1e4c-1d7f-4e8a-9c2b-3f5d7a9e1b2d");
        assertNotEquals(t, Taste.of(other.getMostSignificantBits(), other.getLeastSignificantBits(), EVEN, 0.5), "one bit apart, another taste");
        var draws = EnumSet.allOf(Category.class).stream().mapToDouble(c -> Taste.draw(id.getMostSignificantBits(), id.getLeastSignificantBits(), c)).distinct().count();
        assertEquals(4, draws, "each category its own draw");
        // The draw is written out, so it is the same on every JDK and every version of this mod: a
        // villager's taste never changes. Pinned.
        assertEquals(0.40434049808190575, Taste.draw(1L, 2L, Category.FOOD));
        assertEquals(0.24425610375545082, Taste.draw(1L, 2L, Category.LUXURY));
        for (int i = 0; i < 2000; i++) {
            var u = new UUID(i * 0x9E3779B97F4A7C15L, ~i);
            for (var c : Category.values()) {
                double d = Taste.draw(u.getMostSignificantBits(), u.getLeastSignificantBits(), c);
                assertTrue(d >= 0 && d < 1, "a draw in [0, 1): " + d);
                double m = Taste.of(u.getMostSignificantBits(), u.getLeastSignificantBits(), EVEN, 0.5).of(c);
                assertTrue(m >= 0.5 && m < 1.5, "a multiplier in [0.5, 1.5): " + m);
            }
        }
    }

    @Test void theLeanBendsTheDrawWithinTheRange() {
        assertEquals(0.5, Taste.lean(0, 1, 0.5), 1e-12, "the bottom, leaning up");
        assertEquals(0.5, Taste.lean(0, -1, 0.5), 1e-12, "the bottom, leaning down");
        assertEquals(1.0, Taste.lean(0.5, 0, 0.5), 1e-12, "even: halfway is 1");
        assertEquals(1.0, Taste.lean(0.25, 1, 0.5), 1e-12, "leaning up: a quarter's root is a half");
        assertEquals(1.0, Taste.lean(Math.sqrt(0.5), -1, 0.5), 1e-12, "leaning down: the square");
        assertTrue(Taste.lean(0.999999, -1, 0.5) < 1.5);
        assertEquals(1.0, Taste.lean(0.7, 1, 0), 1e-12, "no spread: 1 always");
        assertThrows(IllegalArgumentException.class, () -> Taste.lean(1, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> Taste.lean(-0.1, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> Taste.lean(0.5, 1.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> Taste.lean(0.5, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Taste.lean(0.5, 0, -0.1));
        // Over many villagers, a profession's lean shows in the mean.
        double up = 0, even = 0, down = 0;
        int n = 4000;
        for (int i = 0; i < n; i++) {
            long most = i * 0x632BE59BD9B4E019L, least = i ^ 0x5DEECE66DL;
            up += Taste.of(most, least, Map.of(Category.FOOD, 1.0), 0.5).food();
            even += Taste.of(most, least, EVEN, 0.5).food();
            down += Taste.of(most, least, Map.of(Category.FOOD, -1.0), 0.5).food();
        }
        assertEquals(1.0, even / n, 0.02, "even: a mean of 1");
        assertEquals(1 + 1 / 6.0, up / n, 0.02, "leaning up: 0.5 + E[sqrt u] = 0.5 + 2/3");
        assertEquals(1 - 1 / 6.0, down / n, 0.02, "leaning down: 0.5 + E[u^2] = 0.5 + 1/3");
        var leaned = Taste.of(7, 9, Map.of(Category.DECOR, 1.0), 0.5);
        var plain = Taste.of(7, 9, EVEN, 0.5);
        assertTrue(leaned.decor() >= plain.decor(), "a lean never lowers its own category");
        assertEquals(plain.food(), leaned.food(), "nor touches another");
    }

    @Test void theFavouriteIsTheHighestAndItLikesWhatItRatesAboveOne() {
        assertEquals(Category.DECOR, new Taste(0.6, 1.2, 1.4, 0.9).favourite());
        assertEquals(Category.TOOLS, new Taste(0.6, 1.4, 1.4, 1.4).favourite(), "a tie, to the first");
        assertEquals(Category.FOOD, Taste.EVEN.favourite());
        var t = new Taste(1.01, 1.0, 0.99, 1.3);
        assertTrue(t.likes(Category.FOOD));
        assertFalse(t.likes(Category.TOOLS), "1 is not liked");
        assertFalse(t.likes(Category.DECOR));
        assertTrue(t.favourite(Set.of(Category.LUXURY, Category.FOOD)));
        assertFalse(t.favourite(Set.of(Category.FOOD)));
        assertFalse(t.favourite(Set.of()));
    }

    @Test void anItemIsValuedByTheCategoryItRatesHighest() {
        var t = new Taste(0.6, 1.2, 1.4, 0.9);
        assertEquals(1.0, t.factor(Set.of()), "no category: no matter of taste");
        assertEquals(0.6, t.factor(Set.of(Category.FOOD)));
        assertEquals(1.4, t.factor(Set.of(Category.FOOD, Category.DECOR)), "a golden apple, food and luxury, by the higher");
        assertEquals(0.9, t.factor(Set.of(Category.LUXURY, Category.FOOD)));
    }

    @Test void itWantsAFewOfAnythingWithAValueInACategoryItLikes() {
        var t = new Taste(0.6, 1.2, 1.4, 0.9);
        assertEquals(3, t.wants(Set.of(Category.DECOR), true, 0, 3));
        assertEquals(2, t.wants(Set.of(Category.DECOR), true, 1, 3));
        assertEquals(0, t.wants(Set.of(Category.DECOR), true, 3, 3), "it has the most");
        assertEquals(0, t.wants(Set.of(Category.DECOR), true, 5, 3), "more than the most");
        assertEquals(0, t.wants(Set.of(Category.DECOR), false, 0, 3), "no value: nobody wants it");
        assertEquals(0, t.wants(Set.of(), true, 0, 3), "no category");
        assertEquals(0, t.wants(Set.of(Category.FOOD), true, 0, 3), "a category it doesn't like");
        assertEquals(3, t.wants(Set.of(Category.FOOD, Category.TOOLS), true, 0, 3), "one of several it likes");
        assertEquals(0, t.wants(Set.of(Category.TOOLS), true, 0, 0), "most 0: wants nothing");
        assertThrows(IllegalArgumentException.class, () -> t.wants(Set.of(), true, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> t.wants(Set.of(), true, 0, -1));
    }

    @Test void aMultiplierOfNoneOrInfinityIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Taste(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Taste(1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Taste(1, 1, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new Taste(1, 1, 1, Double.POSITIVE_INFINITY));
    }
}
