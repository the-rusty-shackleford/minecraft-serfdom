# Serfdom 0.2.0 (phase 1: 1a and 1b): verification

Run on 2026-10-04. 0.2.0 is phase 1 whole: 0.1.0's woodcutting and farming (its record is
`release-0.1.0.md`) and 1b's cook and blacksmith (D-0002). 0.1.0 was never released.

`./gradlew clean build`: `BUILD SUCCESSFUL`, the jar `serfdom-0.2.0.jar` (sha1
`8e5e0c75b3919f7ceb23a40a59313f9467a4a2f3`, Carried nested). The booth ran on Xephyr `:7` with
software rendering.

## JUnit: 118 tests, 0 failures

The 66 of 0.1.0, with `JobScriptTest`, `ShiftTest` and `SmallRulesTest` extended, plus:

| Suite | Tests | What it covers |
|---|---|---|
| `RecipesTest` | 25 | Warehouse Manager's planner tests carried over with stations; then a pickaxe from raw iron through the blast furnace with sticks from planks, ingots on hand spent before any are smelted, a netherite sword four stations deep, the first covered rule chosen and the first rule's shortages named, the station order within a yield, the item never spent below itself |
| `StockTest` | 9 | keep at its bounds; nine rows and a tenth; a repeated item; short with stored, coming and both; a row never spending another's keep; fuel for one, eight, nine items, a blast furnace, what is burning and in the slot, the slot's room; the charcoal load and bootstrap; repairs in whole quarters, the most worn first |
| `WorkshopTest` | 16 | each rule of the next step and each boundary between two: collect before rows; met, short, at its preferred station, at another kind when busy, waiting on fuel while a table step runs, waiting on what is coming, no usable station (naming every kind that would do), a board without a knife, a load held to the station's room, the first short row, another row's keep; charcoal (bootstrap, full load, none at the threshold, what is cooking counted, no second bootstrap, a row waiting on fuel); raw metal; a repair; their order |

`JobScriptTest` gained a workshop's stations and duties, `ShiftTest` the tidy rule, and
`SmallRulesTest` "no station" between "no tool" and "no fuel".

## GameTests: 32 passed

The 15 of 0.1.0, unchanged, and 17 in `WorkshopGameTests`. All run with Village Deed 2.2.0,
Thief 1.2.4, Farmer's Delight 1.3.3, Backpacks+ 0.7.0, Ranged Weapons Mod 2.12.0 and Metals and
Materials 1.0.3 loaded.

**The blacksmith**
- **Two pickaxes from six raw iron, two coal and two planks.**
  - The raw iron goes into the blast furnace, all six at once with one coal, and the furnace
    beside it stays empty.
  - The planks become four sticks, and two pickaxes end in the chest.
  - 300 ticks later there is no third pickaxe, one coal is left, the furnaces are empty, and the
    list shows the row stocked.
- **A netherite sword** from ancient debris, gold, a diamond sword with Sharpness III and a
  template: scrap from the blast furnace, the ingot at the table, the upgrade at the smithing
  table. The sword keeps Sharpness III.
- **Gear is never melted.**
  - Five raw copper are smelted with nothing on the list, beside an iron sword and an iron
    chestplate.
  - Then one iron nugget is kept. Vanilla's nugget-from-blasting would take either piece of gear
    and comes first in the book; the nuggets come from an ingot and the gear stays whole.
- **A worn axe** (150 of 250) is mended by two ingots to 26. Three ingots are left and nothing
  more is spent.
- **Charcoal with no coal.** From sixteen logs, two raw iron and two furnaces: three charcoal on
  two logs, then a full load on charcoal, then the iron smelted with it.
- **A player's furnaces are left alone.** Neither one smelting cobblestone nor one holding a
  player's finished stone is used; the raw iron goes into the empty one.
- **Two smiths on one post** keeping one iron sword make one.
- **A rifle at the weapons workbench** from twelve iron, three coal, a redstone, three planks and
  a stick: steel at the table, then the receivers, barrel, stock and rifle at the bench.
- **No fuel.** It shows on the worker and in the list until coal is stored, then the ingot is
  made and the need clears.
- **Planning time.** The eight hardest rows (a beacon, a rifle, a netherite sword, a machine gun,
  a cake, a jukebox, a piston, a lantern), all short, over the whole book of 1178 rules: 2.6 ms
  at worst over twenty runs. The slowest real plan in the runs took 3.7 ms.

**The cook**
- **Bread** from six wheat at the table.
- **Steaks.** Twelve raw beef with eight kept: the smoker, not the furnace beside it; eight
  steaks, and four beef stay raw.
- **Campfire and stove.** Four beef on a campfire with no fuel, picked up off the ground, and
  nothing left there. Two chickens on Farmer's Delight's stove, picked up.
- **Beef stew in a pot on a lit campfire.** Then the fire is put out and a second asked for:
  "no station" on the worker and in the list, and no second stew.
- **Minced beef.** The knife is fetched from the chest into the cook's hand, and both beef are
  cut, two minced each.
- **A cake** gives its three buckets back.

## The flake found on the way

The first `clean build` went red on 0.1.0's farming test: the farmer never planned. A loop of
six runs then failed the two-pickaxe test once, with no pickaxes.

The smith's trace showed it began with 1077 ticks of shift left, though its batch was set to
2200. Tests in a batch start seconds of wall time apart, and the GameTest server runs thousands
of ticks a second with daylight running. The farmer had likewise started at night.

Every `@BeforeBatch` now holds its hour (`Yard.hour` freezes daylight), and the default batch
has an hour of its own. After the fix, twelve looped runs and the gate's own run passed: 13 in a row, where the earlier
rate of two failures in eight would allow that about 2% of the time.

## Booth: 32 checks, 12 photographs

| Photo | Shows | Checks in code |
|---|---|---|
| `01-post-and-needs` | the post and six workers, one need each, "no station" (a struck-out anvil) third | the client sees all six needs |
| `02` to `06` | as 0.1.0 | as 0.1.0 |
| `07-smithy-and-kitchen` | a smithy (blast furnace, table, anvil, chest, post) and a kitchen (pot on a campfire, cutting board, table, smoker, chest, post) | — |
| `08-smithy-post` | the blacksmith's post screen with its "Stock list (3)..." button | a blacksmith's post, three rows |
| `09-smithy-stock` | the list: Iron Pickaxe 2 / 2 Stocked; Iron Sword "Short of 2 × Iron Ingot, 1 × Stick"; Netherite Sword "Needs a smithing table" | each row's status and words |
| `10-kitchen-stock` | the cook's list: Bread being made; Steak "No fuel: coal, charcoal or logs"; Minced Beef "Needs a knife" | each row's status |
| `11-picker-stew` | the picker searched for "stew": five stews | the search finds exactly the stews |
| `12-kitchen-stock-added` | the list with Baked Cod Stew added, keeping 16, short of its ingredients | the server added the row; the list shows four |

Judged by eye:
- the anvil icon reads beside the struck-out axe;
- rows and their reasons fit the panel at GUI scale 2;
- the long "no fuel" line was shortened after the first photographs cut it off.

## Mutation pass: 14 of 14 caught

Each mutation was applied alone, the suite named run, and the file restored
(`python3 devtools/verification/mutate-0.2.0.py`).

| Mutation | Caught by |
|---|---|
| M1 no finished load is collected | `WorkshopTest` |
| M2 a row may spend another row's keep | `WorkshopTest`, `StockTest` |
| M3 stations not ordered within a yield | `RecipesTest` |
| M4 cooking rules may take gear | gear is never melted (GameTest) |
| M5 one fuel too many | `StockTest` |
| M6 a player's furnace counts as the post's | a player's furnaces are left alone (GameTest) |
| M7 other workers' claims not counted as coming | two smiths make one sword (GameTest) |
| M8 a second charcoal bootstrap while one cooks | `WorkshopTest` |
| M9 repairs round a quarter up | `StockTest` |
| M10 a workshop keeps what it carries | `ShiftTest` |
| M11 what is coming is not counted | two smiths make one sword (GameTest) |
| M12 a load is not recorded as the post's | a player's furnaces are left alone (GameTest) |
| M13 "no station" shows after "no fuel" | `SmallRulesTest` |
| M14 a campfire's finished food is never collected | steaks off a campfire (GameTest) |

The first pass missed M4 and M6, and both tests were too weak:
- **M4:** the gear test kept nine nuggets, and nine of one kind per cell cannot come from one
  sword, so the table's ingot rule won either way. It now keeps one.
- **M6:** the player's furnace was still cooking, so the mutant found it busy and left it alone.
  A second furnace holding a player's finished stone now stands beside it.

## Not verified

- **Play on the box:** no cook or smith has worked at a real base. The pack's recipe book is
  larger than the gametest server's (Create and the rest), so planning time there is unmeasured.
- **Farmer's Delight's placed skillet** is left out (D-0002).
- **Many workers at a base:** performance there is not measured. A workshop worker plans between
  tasks and at most every five seconds while waiting. A plan reads the post's chests and
  stations; it never scans the area, which is indexed when blocks change.
