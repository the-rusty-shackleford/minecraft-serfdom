# Serfdom 0.6.0 (phase 4a, the market): verification

Run on 2026-10-05. 0.6.0 is 0.5.0 (phases 1, 2a, 2b and 3, never released; its record is
`release-0.5.0.md`) with phase 4a (D-0006): purses, trades through them, the morning deposit, the
hire fee paid to the villager, the For Sale block and its ledger, villagers who shop, and buying
food.

`./gradlew clean build` with the booth on Xephyr `:7`: `BUILD SUCCESSFUL`. The jar
`serfdom-0.6.0.jar` has sha1 `46040d9f1a5bdf0e28fb3e5c7d7515447a84ee9a`, with Carried 1.0.0 nested.
Phase 4a needs no change to the two siblings (Vanilla Wheels 1.11.0, Village Law 1.1.0).

## JUnit: 257 tests, 0 failures

The 213 of 0.5.0, plus:

| Suite | Tests | What it covers |
|---|---|---|
| `PurseTest` | 6 | a purse never below 0, rules refused; the start: found full, new at the line, never above the cap; paying short, exact, under; taking in under, to, past the cap, into a purse over a lowered cap; a morning's edges, negative times, the same morning, the next, missed; the deposit once a morning, under, at and over the line, ineligible, past the cap |
| `TillTest` | 3 | an offer read for what it pays and takes: buying, selling, emeralds second, both, both ways, none; open short, exact, over, covered by the takings; the purse after, netted, the cap |
| `ValuesTest` | 4 | what an offer shows; the median of none, one, odd, even; data items over tags, the first tag, data over the price lists, prices over food, food at bread's rate; 0 refusing an item; no food without bread's value |
| `HouseholdTest` | 6 | counts and needs that break their invariants; what it has of a need; adding; a morning's use, whole, two, half over two mornings, the most plentiful first and a tie; nothing owed with nothing left; wants, food first |
| `VerdictTest` | 8 | a stall open, closed three ways, its price an item, sales in a row; the chance's line; no value; can't afford, exact; the draw at and around the chance; a bargain at and under the base value; sales by want, day, stock, room, purse; invariants |
| `ShoppingTest` | 9 | who shops; each kind's hours at their edges; a trip due once a social time, with an emerald, a want and a sale left; the day's counts; the cheapest affordable stall, a cheaper one unaffordable, none affordable, ties; closed and barred stalls, a want nobody sells passed; a bought village's rule; sales for a want in items and points; a worker's food |
| `LedgerTest` | 6 | lines and their invariant; a day's totals by reaction; 64 lines kept; seven days kept; a clock turned back; a ledger's invariant |

`MenuTest` gained a test (buying: no food anywhere, not allowed, raw first, food it would overshoot
on at home and in the canteen, full, just short of full), and `WorkerLayoutTest` one (seven rows,
250 tall).

## GameTests: 80 passed

The 62 of 0.5.0 and 18 in `MarketGameTests`. Every batch but the market's now runs with the economy
off (`Yard.hour`), as it runs with hunger off: the GameTest server leaves earlier tests' areas
standing, stalls and all. Each shopping test stands alone in its batch and clears stalls earlier
batches left (`clearOldStalls`), since a villager shops anywhere within 64 blocks of its bed.

- **Trading, through the real trade menu:** a purse of three pays for three wheat sales in one
  shift-click and the fourth is sold out with 34 wheat still offered; the offer counts three uses;
  the offers packet's copy, and the copy read back off the wire, are sold out too; closed, the trade
  is open again. A purchase of bread puts an emerald in an empty purse and opens the wheat trade,
  which one sale shuts again; a purse at 63 taking 5 keeps 64. With the economy off, an empty purse
  pays for six sales.
- **Purses:** an egg's, a child's and a cured villager's start at 4; a structure's and one joining
  with none start full; one that had a purse keeps it. The hire fee (16) goes in. The morning
  deposit: a free villager and a hired one at 1 get 2, once; one at 4, a captive and a child get
  nothing; the next morning the free one, at 3, gets 2 more. A household eats two of three loaves,
  then its last. The Worker Screen shows the purse.
- **A townsman's trip** at 9300: with no food at home and five emeralds it walks to a stall twenty
  blocks off, buys one sale of six bread for an emerald (a bargain), carries it home shown in its
  hand, and puts it in its household. Too dear (one bread for an emerald): the head shake, nothing
  changes hands. One emerald against a price of two: it looks at an emerald and leaves. Two stalls:
  it buys at the cheaper. Two townsmen at one stall are served in turn and both buy. A stall 86
  blocks from the bed is never chosen. In a bought village it passes a stranger's cheaper stall and
  buys at the owner's.
- **Workers:** at dinner a hungry worker with nothing at home buys bread and eats it; a captive with
  emeralds beside it buys nothing. At its meeting a woodcutter whose shift found no axe buys a stone
  axe (a bargain) and it lands in its post's chest.
- **The stall:** a hopper above fills it with bread and keeps its dirt; one below takes its
  emeralds; a stranger can neither open nor break it, its owner can, and its stock drops; an
  explosion beside it leaves it. Base values: bread 1/6 and a stone axe 1 off the price lists, bone
  meal 0.1 from data, a baked potato at bread's rate, dirt none.

Three runs of the GameTests in a row passed at the final code.

## Booth: 85 checks, 46 photographs

| Photo | Shows | Checks in code |
|---|---|---|
| `37-stall-front` | the bread stall from its customers' side: the plank counter, the green cloth with its cream stripe, the painted emerald, the sign's coin, the loaf turning over it, the label "6 Bread for 1 emerald" | the client knows what it sells and that it is open; the camera looks at it |
| `38-market` | three stalls from behind, bread, paper and clay over them, the paper stall's label "1 Paper for 1 emerald" | the camera looks at the paper stall |
| `39-bargain` | the farmer at the bread stall with its loaf and green sparkles; the librarian at the paper stall; the mason with an emerald in its hand | the client sees the farmer's bread |
| `40-too-pricey` | the librarian's head shake at dear paper | the client sees its unhappy counter |
| `41-cant-afford` | the mason looking at its one emerald | the client sees the emerald in its hand |
| `42-carrying-home` | the farmer walking home with the loaf in its arms, the stalls' painted fronts behind it | the client sees it carrying bread away from the stall |
| `43-stall-screen` | the stall's screen: the bread on its slot, 6 a sale for 1 emerald, the stock, two emeralds of proceeds | the quantity, price, the slot and the proceeds as the client has them |
| `44-ledger` | the Ledger tab: today's 2 sales, 2 emeralds, and the latest visits: a butcher and a farmer each bought 6 for 1 emerald | the ledger's day as the client has it |
| `45-worker-screen-purse` | the Worker Screen's new Purse row: an emerald and 23 | the view says 23 |
| `46-trade-purse` | the trade screen with "Purse: 2" over its corner, the wheat-for-three-emeralds trade crossed out, the bread trade open | the client's offers, and the purse shown |

**Judged by eye:**
- The painted emerald reads as a gem against the planks, from the front and from 42's distance.
- The cloth reads as cloth, the stripe as its hem. The sign's coin is small but reads as green.
- The loaf floats over the counter in front of the sign, never inside it.
- The labels sit above the stall, on the dark ground name tags use.
- The ledger's six columns fit its 360-wide panel. The Worker Screen's purse row sits between Hunger
  and Needs, in the panel's new 250 height.

**Found on the way:**
- **The second bread buyer in 44 is real:** the kitchen's butcher (photo 35's griller), its home
  holding only raw beef, came over from 44 blocks away in its meeting hour.
- **The camera missed the stall** twice: a yaw of 180 faces north, and a pitch of 30 struck the
  grass short of the counter. Both shots now check what the camera looks at.
- **The ledger's last two headers ran together and off the panel** at 312 wide: 360 now.

## Mutation pass: 37 of 37 caught

`uv run --no-project python devtools/verification/mutate-0.6.0.py domain-each | a | b | c | d | e | a2 | d2`.

| Mutation | Caught by |
|---|---|
| D1 the purse has no cap | `PurseTest`, `TillTest` |
| D2 a morning seen again | `PurseTest` |
| D3 the deposit to the rich | `PurseTest` |
| D4 found starts at the line | `PurseTest` |
| D5 every trade open | `TillTest` |
| D6 a trade's takings ignored | `TillTest` |
| D7 a price list over data | `ValuesTest` |
| D8 the mean, not the median | `ValuesTest` |
| D9 a household uses nothing | `HouseholdTest` |
| D10 food not first | `HouseholdTest` |
| D11 any price is paid | `VerdictTest` |
| D12 every purchase a bargain | `VerdictTest` |
| D13 a short purse buys | `VerdictTest` |
| D14 the dearest stall | `ShoppingTest` |
| D15 any stall in a bought village | `ShoppingTest` |
| D16 two trips a social time | `ShoppingTest` |
| D17 the ledger keeps every line | `LedgerTest` |
| D18 a full worker goes to buy | `MenuTest` |
| G1 the purse closes nothing | the purse of three, the player's purchase |
| G2 the close lost in the packet's copy | the purse of three |
| G3 trades never move the purse | the purse of three, the player's purchase |
| G4 one saved before purses starts at the line | the made-new test |
| G5 a structure's villager is new | the made-new test |
| G6 the hire fee vanishes | the hire fee |
| G7 no morning | the morning deposit |
| G8 nobody goes shopping | the bargain, too pricey, two at a stall |
| G9 goods never put away | the bargain |
| G10 any stall in a bought village | the bought village |
| G11 any reach | beyond reach, after the fix below |
| G12 no lock | the cheaper stall and the lock |
| G13 a meal never buys | the hungry worker |
| G14 a captive buys | the hungry worker |
| G15 the post's needs never shopped for | the woodcutter's axe |
| G16 a hopper puts anything in | the hoppers, after the fix below |
| G17 a stranger breaks a stall | the hoppers |
| G18 an explosion takes a stall | the hoppers |
| G19 the economy switch ignored | the economy off |

**Two got past the tests the first time.**
- **G16:** a hopper pushes its slots in order, so its five bread go first, one every eight ticks;
  the test looked for the dirt the moment the bread was in, before the hopper had tried it. It now
  waits 60 ticks more (batch `a2`).
- **G11:** the far stall stands in open air, so a trip planned to it would never arrive, and
  "never visited" held either way. The test now asks the decision itself: no stall it may use is in
  reach of its bed (batch `d2`).

## Found by the gate

- **A closed trade showed open on the client** (the booth's photo 46, before the fix). The offers
  packet copies the offers before it writes them, and in single player hands the copy over
  unwritten; the copy dropped the purse's mark. The server still refused the trade, but no cross was
  drawn. The mark now goes with the copy, and the purse test builds the real packet and reads it back
  off the wire. Run against the old code once, the new check failed.
- **A villager in a newly generated chunk does not join as loaded from disk,** as the preview said
  it did (`addWorldGenChunkEntities` passes `false`). The purse's start is decided by NeoForge's
  `FinalizeSpawnEvent` instead: Rusty's call is kept as made. Found by the made-new test.
- **A full worker chose to go and buy** (`Menu` returned BUY at 20 with nothing anywhere). Buying
  now needs it not to be full.
- **Stalls from earlier batches stay in the GameTest world,** and shoppers walked to them. See the
  GameTests above.

## Not verified

- **Play on the box:** no villager has shopped on the server, and no purse there has paid for a
  trade.
- **A real village:** the tests' townsmen have their beds set by hand; a generated village's
  structure start (the purse's full start) is tested through the event, not a generated village.
- **A cure in the world:** the cured villager is tested through the zombie's saved conversion time.
- **Rusty's shaders and GPU:** the gate's photographs are software-rendered.
- **Rusty has not seen** photos 37 to 46.
- **One booth run went to the desktop display.** The first `./gradlew clean build` ran with the
  shell's `DISPLAY=:1`, and the build runs the booth: its window opened on Rusty's screen for two
  minutes (18:37:48 to 18:39:54) and closed itself. The gate's build was then run again on Xephyr,
  and these photos are that run's. The README's build section now says so.
