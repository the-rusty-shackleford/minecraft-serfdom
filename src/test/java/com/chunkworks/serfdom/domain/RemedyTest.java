/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: one case or several (two players, two villages); paid, fled, and paid after fled;
 * a case owed nothing; a captive owed again under another case, forgotten, or never owed; ledgers
 * that break the rep invariant. */
final class RemedyTest {
    private static final UUID ANN = new UUID(1, 1), BOB = new UUID(2, 2);
    private static final UUID C1 = new UUID(10, 1), C2 = new UUID(10, 2), C3 = new UUID(10, 3), C4 = new UUID(10, 4);
    private static final Remedy.Case ANN_OAK = new Remedy.Case(ANN, "deed:oak"), ANN_ELM = new Remedy.Case(ANN, "deed:elm"), BOB_OAK = new Remedy.Case(BOB, "deed:oak");

    private static Remedy ledger() {
        return Remedy.EMPTY.owe(ANN_OAK, C1).owe(ANN_OAK, C2).owe(ANN_ELM, C3).owe(BOB_OAK, C4);
    }

    @Test void payingFreesExactlyWhatThatCaseIsOwed() {
        var paid = ledger().paid(ANN_OAK);
        assertEquals(Set.of(C1, C2), paid.freed());
        assertEquals(Set.of(), paid.after().owedTo(ANN_OAK), "the case is settled");
        assertEquals(Set.of(C3), paid.after().owedTo(ANN_ELM), "the same player's case with another village is kept");
        assertEquals(Set.of(C4), paid.after().owedTo(BOB_OAK), "another player's case with the same village is kept");
    }
    @Test void fleeingForgetsTheCaseSoALaterDebtFreesNobody() {
        var fled = ledger().fled(ANN_OAK);
        assertEquals(Set.of(), fled.owedTo(ANN_OAK));
        assertEquals(Set.of(), fled.paid(ANN_OAK).freed(), "the debt paid after fleeing frees nothing");
        assertEquals(Set.of(C3), fled.owedTo(ANN_ELM));
        assertEquals(Set.of(C4), fled.owedTo(BOB_OAK));
    }
    @Test void aCaptureAfterFleeingIsOwedToTheNextPayment() {
        var later = ledger().fled(ANN_OAK).owe(ANN_OAK, C1);
        assertEquals(Set.of(C1), later.paid(ANN_OAK).freed());
    }
    @Test void aCaseOwedNothingFreesNothingAndChangesNothing() {
        var none = new Remedy.Case(BOB, "deed:elm");
        var r = ledger();
        assertEquals(Set.of(), r.paid(none).freed());
        assertEquals(r, r.paid(none).after());
        assertSame(r, r.fled(none));
    }
    @Test void aCaptiveIsOwedToOneCaseOnly() {
        var moved = ledger().owe(BOB_OAK, C1);
        assertEquals(Set.of(C2), moved.owedTo(ANN_OAK), "taken again by another, it is owed to the new case");
        assertEquals(Set.of(C1, C4), moved.owedTo(BOB_OAK));
    }
    @Test void aForgottenCaptiveIsFreedByNoCaseAndAnEmptyCaseGoes() {
        var r = ledger().forget(C3);
        assertFalse(r.owed().containsKey(ANN_ELM), "the case owed only C3 is gone");
        assertEquals(Set.of(C1, C2), r.owedTo(ANN_OAK));
        assertSame(r, r.forget(new UUID(99, 99)), "forgetting a stranger changes nothing");
    }
    @Test void ledgersThatBreakTheRepAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Remedy.of(Map.of(ANN_OAK, Set.of())));
        assertThrows(IllegalArgumentException.class, () -> Remedy.of(Map.of(ANN_OAK, Set.of(C1), BOB_OAK, Set.of(C1))));
        assertThrows(NullPointerException.class, () -> new Remedy.Case(null, "x"));
    }
    @Test void theLedgerIsAValue() {
        assertEquals(ledger(), Remedy.of(Map.of(ANN_OAK, Set.of(C1, C2), ANN_ELM, Set.of(C3), BOB_OAK, Set.of(C4))));
        var r = ledger();
        assertThrows(UnsupportedOperationException.class, () -> r.owed().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.owedTo(ANN_OAK).clear());
    }
}
