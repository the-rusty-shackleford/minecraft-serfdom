/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Recipes.Rule;
import com.chunkworks.serfdom.domain.Recipes.Step;
import com.chunkworks.serfdom.domain.Workshop.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Workshop.next and Workshop.rows (D-0002), one partition per rule and each boundary between two:
 * <ul>
 * <li>collect: a READY station before a short row; BUSY and OFF are not collected;</li>
 * <li>rows: met; short of materials; a step at its preferred station; at another kind when that one
 *     is busy; a smelt waiting on fuel while a table step runs; waiting on what is coming; no
 *     usable station (none, and one only OFF), naming every kind that would do; a cutting board
 *     without a knife; a load held to the station's room; the first row met and the second worked;
 *     a row never spending another's keep;</li>
 * <li>charcoal: a bootstrap without fuel, a full load under {@link Fuel#LOW}, none at LOW, fuel on
 *     its way counted, no second bootstrap while the first cooks, a row waiting on fuel;</li>
 * <li>raw metal: loaded, kept back by a row, waiting on fuel;</li>
 * <li>repair: at a free anvil, none without one;</li>
 * <li>the order of all of them, and the need shown at rest.</li>
 * </ul> */
final class WorkshopTest {
    private static final String RAW = "minecraft:raw_iron", IRON = "minecraft:iron_ingot", PICK = "minecraft:iron_pickaxe", STICK = "minecraft:stick";
    private static final String OAK = "minecraft:oak_planks", LOG = "minecraft:oak_log", CHARCOAL = "minecraft:charcoal";
    private static final String COPPER_RAW = "minecraft:raw_copper", COPPER = "minecraft:copper_ingot";
    private static final String BEEF = "minecraft:beef", STEAK = "minecraft:cooked_beef", CARROT = "minecraft:carrot", POTATO = "minecraft:potato", BOWL = "minecraft:bowl";
    private static final String STEW = "farmersdelight:beef_stew", MINCED = "farmersdelight:minced_beef";
    private static Rule rule(String id, Station s, List<List<String>> cells, String result, int yield) { return new Rule(id, s, cells, result, yield); }
    private static final Rule BLAST_RAW = rule("blast_raw", Station.BLAST, List.of(List.of(RAW)), IRON, 1);
    private static final Rule SMELT_RAW = rule("smelt_raw", Station.FURNACE, List.of(List.of(RAW)), IRON, 1);
    private static final Rule R_STICK = rule("stick", Station.TABLE, List.of(List.of(OAK), List.of(OAK)), STICK, 4);
    private static final Rule R_PICK = rule("pick", Station.TABLE, List.of(List.of(IRON), List.of(IRON), List.of(IRON), List.of(STICK), List.of(STICK)), PICK, 1);
    private static final Rule R_CHARCOAL = rule("charcoal", Station.FURNACE, List.of(List.of(LOG)), CHARCOAL, 1);
    private static final Rule BLAST_COPPER = rule("blast_copper", Station.BLAST, List.of(List.of(COPPER_RAW)), COPPER, 1);
    private static final Rule SMOKE = rule("smoke", Station.SMOKER, List.of(List.of(BEEF)), STEAK, 1);
    private static final Rule FIRE = rule("fire", Station.CAMPFIRE, List.of(List.of(BEEF)), STEAK, 1);
    private static final Rule R_STEW = rule("stew", Station.POT, List.of(List.of(BEEF), List.of(CARROT), List.of(POTATO), List.of(BOWL)), STEW, 1);
    private static final Rule R_MINCE = rule("mince", Station.BOARD, List.of(List.of(BEEF)), MINCED, 2);
    private static final Recipes.Rules BOOK = Recipes.Rules.of(List.of(BLAST_RAW, SMELT_RAW, R_STICK, R_PICK, R_CHARCOAL, BLAST_COPPER, SMOKE, FIRE, R_STEW, R_MINCE));
    private static final Cell TABLE = new Cell(1, 0, 0), BLAST = new Cell(2, 0, 0), FURNACE = new Cell(3, 0, 0), ANVIL = new Cell(4, 0, 0);
    private static final Cell SMOKER = new Cell(5, 0, 0), CAMPFIRE = new Cell(6, 0, 0), POT = new Cell(7, 0, 0), BOARD = new Cell(8, 0, 0);
    private static Site site(Cell at, Station kind, State state) { return new Site(at, kind, state, kind == Station.CAMPFIRE ? 4 : 64); }
    private static final List<Site> SMITHY = List.of(site(TABLE, Station.TABLE, State.FREE), site(BLAST, Station.BLAST, State.FREE), site(FURNACE, Station.FURNACE, State.FREE), site(ANVIL, Station.ANVIL, State.FREE));

    /** A facts builder: defaults to the smithy, an empty list, no duties, a coal stored. */
    private static final class F {
        Stock stock = Stock.EMPTY; Map<String, Integer> stored = new HashMap<>(), coming = new HashMap<>(); List<Site> stations = SMITHY;
        Set<Duty> duties = EnumSet.noneOf(Duty.class); int fuel = 1, fuelComing; Optional<String> log = Optional.empty();
        List<String> raw = List.of(); List<Repair.Worn> worn = List.of(); boolean knife;
        F keep(String item, int n) { stock = stock.with(stock.rows().size(), new Stock.Row(item, n)); return this; }
        F store(String item, int n) { stored.put(item, n); return this; }
        F at(Site... s) { stations = List.of(s); return this; }
        Facts facts() { return new Facts(stock, stored, coming, stations, BOOK, duties, fuel, fuelComing, log, raw, worn, knife); }
        Choice next() { return Workshop.next(facts()); }
        RowState row(int i) { return Workshop.rows(facts()).get(i); }
    }

    // ---- collect ---------------------------------------------------------------------------------

    @Test void aFinishedLoadIsCollectedBeforeAnything() {
        var f = new F().keep(PICK, 1).store(RAW, 3).store(STICK, 2).at(site(FURNACE, Station.FURNACE, State.READY), site(TABLE, Station.TABLE, State.FREE), site(BLAST, Station.BLAST, State.FREE));
        assertEquals(new Collect(FURNACE), f.next());
        f.at(site(FURNACE, Station.FURNACE, State.BUSY), site(BLAST, Station.BLAST, State.OFF), site(TABLE, Station.TABLE, State.FREE));
        assertFalse(f.next() instanceof Collect, "busy and off are not collected");
    }

    // ---- rows ------------------------------------------------------------------------------------

    @Test void aMetRowRestsWithNoNeed() {
        var f = new F().keep(PICK, 1).store(PICK, 1);
        assertEquals(new Rest(Optional.empty()), f.next());
        assertEquals(Status.MET, f.row(0).status());
        assertEquals(1, f.row(0).have());
    }
    @Test void aShortRowShowsNoMaterialsAndNamesThem() {
        var f = new F().keep(PICK, 1).store(STICK, 2);
        assertEquals(new Rest(Optional.of(Need.NO_MATERIALS)), f.next());
        assertEquals(Status.SHORT, f.row(0).status());
        assertEquals(List.of(new Recipes.Shortage(List.of(IRON), 3)), f.row(0).shortages(), "three ingots, none makeable");
    }
    @Test void theFirstStepRunsAtItsPreferredStation() {
        var f = new F().keep(PICK, 2).store(RAW, 6).store(OAK, 2);
        assertEquals(new Load(BLAST, new Step(BLAST_RAW, 6, List.of(RAW)), false), f.next(), "six raw iron into the blast furnace");
    }
    @Test void aBusyStationsWorkGoesToAnotherKindThatDoesTheSame() {
        var f = new F().keep(PICK, 2).store(RAW, 6).store(OAK, 2)
                .at(site(TABLE, Station.TABLE, State.FREE), site(BLAST, Station.BLAST, State.BUSY), site(FURNACE, Station.FURNACE, State.FREE));
        assertEquals(new Load(FURNACE, new Step(SMELT_RAW, 6, List.of(RAW)), false), f.next());
    }
    @Test void aSmeltWaitsOnFuelWhileATableStepRuns() {
        var f = new F().keep(PICK, 2).store(RAW, 6).store(OAK, 2);
        f.fuel = 0;
        assertEquals(new Make(TABLE, new Step(R_STICK, 1, List.of(OAK, OAK))), f.next(), "the sticks, while the smelt waits");
        f.stored.remove(OAK); f.store(STICK, 4);
        assertEquals(new Rest(Optional.of(Need.NO_FUEL)), f.next());
        assertEquals(Status.NO_FUEL, f.row(0).status());
    }
    @Test void whatIsComingIsWaitedFor() {
        var f = new F().keep(PICK, 1).store(STICK, 2);
        f.coming.put(IRON, 3);
        assertEquals(new Rest(Optional.empty()), f.next(), "the ingots are in the furnace");
        assertEquals(Status.MAKING, f.row(0).status());
        assertEquals(new Rest(Optional.empty()), new F().keep(PICK, 1).at(site(TABLE, Station.TABLE, State.BUSY)).store(IRON, 3).store(STICK, 2).next(), "the table is in use");
    }
    @Test void noUsableStationNamesEveryKindThatWouldDo() {
        var f = new F().keep(PICK, 1).store(RAW, 3).store(STICK, 2).at(site(TABLE, Station.TABLE, State.FREE));
        assertEquals(new Rest(Optional.of(Need.NO_STATION)), f.next());
        assertEquals(Status.NO_STATION, f.row(0).status());
        assertEquals(Set.of(Station.BLAST, Station.FURNACE), f.row(0).missing(), "a smelt needs a blast furnace or a furnace");
        var cold = new F().keep(STEW, 1).store(BEEF, 1).store(CARROT, 1).store(POTATO, 1).store(BOWL, 1).at(site(POT, Station.POT, State.OFF));
        assertEquals(Status.NO_STATION, cold.row(0).status(), "a cold pot is no pot");
        assertEquals(Set.of(Station.POT), cold.row(0).missing());
        cold.at(site(POT, Station.POT, State.FREE));
        assertEquals(new Load(POT, new Step(R_STEW, 1, List.of(BEEF, CARROT, POTATO, BOWL)), false), cold.next());
    }
    @Test void aCuttingBoardWantsAKnife() {
        var f = new F().keep(MINCED, 4).store(BEEF, 2).at(site(BOARD, Station.BOARD, State.FREE));
        assertEquals(new Rest(Optional.of(Need.NO_TOOL)), f.next());
        f.knife = true;
        assertEquals(new Make(BOARD, new Step(R_MINCE, 2, List.of(BEEF))), f.next());
    }
    @Test void aLoadIsHeldToTheStationsRoom() {
        var f = new F().keep(STEAK, 12).store(BEEF, 12).at(site(CAMPFIRE, Station.CAMPFIRE, State.FREE));
        assertEquals(new Load(CAMPFIRE, new Step(FIRE, 4, List.of(BEEF)), false), f.next(), "a campfire holds four");
        f.at(site(SMOKER, Station.SMOKER, State.FREE), site(CAMPFIRE, Station.CAMPFIRE, State.FREE));
        assertEquals(new Load(SMOKER, new Step(SMOKE, 12, List.of(BEEF)), false), f.next(), "the smoker before the campfire");
    }
    @Test void theFirstShortRowIsWorked() {
        var f = new F().keep(PICK, 1).keep(STICK, 4).store(PICK, 1).store(OAK, 2);
        assertEquals(new Make(TABLE, new Step(R_STICK, 1, List.of(OAK, OAK))), f.next(), "the pickaxe is met, the sticks are not");
    }
    @Test void aRowNeverSpendsAnotherRowsKeep() {
        var f = new F().keep(PICK, 1).keep(IRON, 3).store(IRON, 3).store(STICK, 2);
        assertEquals(Status.SHORT, f.row(0).status(), "the three ingots are the second row's");
        assertEquals(Status.MET, f.row(1).status());
        f.store(IRON, 6);
        assertEquals(new Make(TABLE, new Step(R_PICK, 1, List.of(IRON, IRON, IRON, STICK, STICK))), f.next());
    }

    // ---- duties ----------------------------------------------------------------------------------

    @Test void charcoalBootstrapsWithoutFuelAndLoadsFullUnderLow() {
        var f = new F().store(LOG, 10);
        f.duties = EnumSet.of(Duty.CHARCOAL); f.log = Optional.of(LOG); f.fuel = 0;
        assertEquals(new Load(FURNACE, new Step(R_CHARCOAL, Fuel.BOOTSTRAP, List.of(LOG)), true), f.next(), "three logs in, burning logs");
        f.fuel = 2;
        assertEquals(new Load(FURNACE, new Step(R_CHARCOAL, 8, List.of(LOG)), false), f.next(), "under low: a full load on the fuel there is");
        f.fuel = Fuel.LOW;
        assertEquals(new Rest(Optional.empty()), f.next(), "at low: none");
        f.fuel = 2; f.fuelComing = 6;
        assertEquals(new Rest(Optional.empty()), f.next(), "what is cooking counts");
        f.fuel = 0; f.fuelComing = 3;
        assertEquals(new Rest(Optional.empty()), f.next(), "none stored but three on their way: no second bootstrap");
        f.keep(PICK, 1).store(RAW, 3).store(STICK, 2);
        f.fuel = 0; f.fuelComing = 0;
        assertEquals(new Load(FURNACE, new Step(R_CHARCOAL, Fuel.BOOTSTRAP, List.of(LOG)), true), f.next(), "a row waiting on fuel");
    }
    @Test void rawMetalIsSmeltedUnlessARowKeepsIt() {
        var f = new F().store(COPPER_RAW, 5);
        f.duties = EnumSet.of(Duty.RAW_METAL); f.raw = List.of(COPPER_RAW);
        assertEquals(new Load(BLAST, new Step(BLAST_COPPER, 5, List.of(COPPER_RAW)), false), f.next());
        f.keep(COPPER_RAW, 5);
        assertEquals(new Rest(Optional.empty()), f.next(), "a row keeps the five");
        f.stock = Stock.EMPTY; f.fuel = 0;
        assertEquals(new Rest(Optional.of(Need.NO_FUEL)), f.next());
    }
    @Test void aRepairNeedsAFreeAnvil() {
        var f = new F();
        f.duties = EnumSet.of(Duty.REPAIR);
        f.worn = List.of(new Repair.Worn("minecraft:iron_axe", 250, 150, 5));
        assertEquals(new Mend(ANVIL, 0), f.next());
        f.at(site(TABLE, Station.TABLE, State.FREE));
        assertEquals(new Rest(Optional.empty()), f.next());
    }
    @Test void theOrderIsCollectRowsCharcoalRawRepair() {
        var f = new F().keep(STICK, 4).store(OAK, 2).store(LOG, 10).store(COPPER_RAW, 5);
        f.duties = EnumSet.allOf(Duty.class); f.log = Optional.of(LOG); f.raw = List.of(COPPER_RAW);
        f.worn = List.of(new Repair.Worn("minecraft:iron_axe", 250, 150, 5));
        assertTrue(f.next() instanceof Make, "the row first");
        f.stock = Stock.EMPTY;
        assertEquals(R_CHARCOAL, ((Load) f.next()).step().rule(), "then charcoal, fuel being low");
        f.fuel = Fuel.LOW;
        assertEquals(BLAST_COPPER, ((Load) f.next()).step().rule(), "then raw metal");
        f.raw = List.of();
        assertEquals(new Mend(ANVIL, 0), f.next(), "then the repair");
    }
}
