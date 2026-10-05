# Serfdom

**0.6.0 (phase 4a, the market, D-0006): built and gated on 2026-10-05. Not released.**
Gate: `devtools/verification/release-0.6.0.md` (257 JUnit, 80 GameTests, booth 85 checks with 46
photos, 37 of 37 mutations; jar sha1 `46040d9f`). Rusty's call: day-one purses full (every villager
in the world before purses, and a newly generated village's people), newborns, cures and eggs at 4.
Not seen by Rusty: photos 37 to 46 (the stall, the market, the reactions, the stall's screen and
ledger, the purse on the Worker Screen and the trade screen). 0.6.0 carries 0.5.0 whole and ships in
its place, with the same two siblings. 4b (climate, taste, wants, window shopping, villagers
selling) is previewed in D-0006 and comes next, as 0.7.0.

**0.5.0 (phase 3, hunger and meals, D-0005): built and gated on 2026-10-05. Not released.**
Gate: `devtools/verification/release-0.5.0.md`. Rusty's call: the canteen (a worker eats from its
home chest, then from its owner's posts' chests within 48 blocks of its bed). Not seen by Rusty:
photos 34 to 36 and the new hungry icon in photo 01. 0.5.0 carries 0.4.0 and 0.3.0 whole and ships
in their place, with the same two siblings.

**0.4.0 (phase 2b, what a worker wears, D-0004): built and gated on 2026-10-05. Not released.**
Gate: `devtools/verification/release-0.4.0.md`. Rusty took every call of the preview: leggings take
the robe off; armour wears as on a player; set free drops the gear, while escaping or freed by the
law it leaves wearing it. Not seen by Rusty: photos 18 to 33. 0.4.0 carries 0.3.0 whole and ships
in its place, with the same two siblings.

**0.3.0 (phase 2a, the capture, D-0003): built and gated on 2026-10-04. Not released.**
Gate: `devtools/verification/release-0.3.0.md` (160 JUnit, 47 GameTests, booth 42 checks with 17
photos, 22 of 22 mutations caught; jar sha1 `0a140435`). Not seen by Rusty: photos 05 and 13 to
17, and the work song (`run/work_song.wav`, rendered by `devtools/sound/work_song.py`).
Rusty said "Sounds good" to the preview; his calls: captives summon no golems and count toward
no cats; no crime for a bought village's owner and trusted players. It needs Vanilla Wheels
1.11.0 (cargo rules, its D-0029) and Village Law 1.1.0 (`api/Cases.openHere`), built in their
repos and unreleased, to ship with it.

**0.2.0 (phase 1, 1a and 1b): built and gated on 2026-10-04. Not released.**
- 1a: Rusty has passed its booth photos and art, but has not seen it in play.
- 1b: the cook and the blacksmith (D-0002). Not yet seen by Rusty.
- 0.1.0 (1a alone) was never released.

| What | Where it stands |
|---|---|
| Repo | No remote yet |
| Gate | 1a: `release-0.1.0.md`; 0.2.0: `release-0.2.0.md`; 0.3.0: `release-0.3.0.md`; 0.4.0: `release-0.4.0.md`; 0.5.0: `release-0.5.0.md`; 0.6.0: `release-0.6.0.md` (all in `devtools/verification/`) |
| Release | Only on Rusty's go, as a new jar in the pack, with Vanilla Wheels 1.11.0 and Village Law 1.1.0 |

Minecraft 1.21.1, NeoForge 21.1.248, Java 21. `com.chunkworks.serfdom`, AGPL-3.0-or-later, headers
"Rusty Shackleford and nfx". Nests Carried. Optional: Village Deed 2.2+ (bought villages, home
village ids) and Farmer's Delight 1.3 (tomatoes, rice).

The villager overhaul in Rusty's spec (`~/Downloads/serfdom-mod-spec.md`), in five phases. Phase 2
is split as phase 1 was: 2a, the capture, is [D-0003](decisions/D-0003.md); 2b, equipment slots
and the armour layer, is [D-0004](decisions/D-0004.md). Phase 3, hunger and meals, is
[D-0005](decisions/D-0005.md). Phase 4, the market, is [D-0006](decisions/D-0006.md): 4a built,
4b previewed. Phase 1 is [D-0001](decisions/D-0001.md), split by Rusty into:

- **1a:** hiring, beds, the Work Post, woodcutting, farming, sorting, need icons, the Worker
  Screen, the chain lead, fence gates.
- **1b ([D-0002](decisions/D-0002.md)):** the cook and the blacksmith. A job is a set of
  stations; the post's stock list takes anything they make, and the worker makes what it needs
  first.

The plan for all five phases is part C of `~/.claude/plans/the-following-requests-were-witty-church.md`.
Each phase is previewed to Rusty before it is built.

## Shape

- `src/domain` (JDK only, all unit-tested):
  - `Cell`, `Spot`;
  - `WorkDay` (the day's parts);
  - `Hiring` (verdict, fee);
  - `Assignment` (bed → post, 48 blocks, 4 a post);
  - `Radius`;
  - `Need` (icon priority, wire code);
  - `Pace` (a player's break time);
  - `JobScript` (a job as data);
  - `Sorting` (holds, then tags, then overflow);
  - `Harvest`;
  - `Felling` (natural tree, doomed leaves);
  - `Shift` (the next step);
  - `Follow`;
  - 1b: `Station`, `Recipes` (Warehouse Manager's planner with stations), `Stock`, `Fuel`,
    `Repair`, `Workshop` (a workshop's next step and each row's standing).
  - 2a: `Bond`, `Area`, `Chain` (the gestures), `Capture` (verdict, the hold), `Remedy` (what each
    case is owed), `Escape`, `Birth`, `WorkSong`; `WorkDay` and `Pace` gained the captive's.
  - 2b: `Wardrobe` (who, what fits, Binding, shift-clicks), `Parting` (gear on each way out),
    `Fit` (the armour's stand-in poses, with box maths), `WorkerLayout` (the screen, pinned to
    688×288).
  - 3: `Hunger` (points, drain, the work speed), `Meals` (when), `Menu` (what, bite by bite);
    `Pace` gained hunger, `Need` gained `HUNGRY`.
  - 4a: `Purse` (cap, deposit, the start), `Till` (an offer as the purse sees it), `Values` (base
    values), `Household` (a free villager's goods), `Stall`, `Verdict` (at the counter), `Shopping`
    (who, when, which stall), `Ledger`; `Menu` gained the buy step, `WorkerLayout` the purse row.
- `src/main`:
  - `Serfdom` (registries), `SerfdomConfig`.
  - `Worker` (the saved attachment) and `Workers` (state, hire, beds, posts, needs, the tool between
    shifts, gestures, death).
  - `WorkerBrain` (the owned brain and its schedules), `Hire`, `Picks`, `Screens`
    (payloads), `ChainLead`, `PlacedLogs`.
  - 2a: `Captures` (the hold), `Remedies` (saved data, the law's remedy), `Humming`.
  - 2b: `WorkerMenu` (the Worker Screen's menu on the villager's own armour slots).
  - 3: `Appetite` (the saved belly, `serfdom:belly`), `job/Kitchen` (home chest, canteen, free
    stations, food facts), `behavior/MealTime` (core: drain, turn to the meal),
    `behavior/HaveMeal` (the `serfdom:meal` activity), `Posts.near`.
  - 4a: `market/` (`Market` the module's wiring; `Purses` the `serfdom:purse` attachment, the
    start, trades and the deposit; `Prices` and `Needs` the data; `Households`; `Baskets` what a
    shopper carries, `serfdom:basket`; the For Sale block, entity and menu; `Stalls` the POI lookup,
    the owner lock and the ledger payload; `Shoppers` the plan; `Counter` the sale),
    `behavior/ShopTime` (core, on free villagers' vanilla brains too) and `behavior/GoShopping`
    (the `serfdom:shop` activity), `mixin/MerchantOfferMixin`, `client/ForSaleScreen`,
    `ForSaleRenderer`, `PurseLabel`.
  - `behavior/`: `WorkShift`, `FollowOwner`, `KeepBed`, `OpenGates`, `WorkerNavigation`; 2a:
    `Stay`, `CaptiveNight`, `RunHome`.
  - `job/`: `Jobs` (data), `Woodcutting` + `WoodTask`, `Farming` + `CropTask`, `Storage`, `Tools`;
    1b: `RecipeBook` (rules off the game's recipes), `Stations` (kinds, states, loading,
    collecting), `WorkshopJob` (facts, choice, task), `WorkshopTask` (legs).
  - `post/`: `WorkPostBlock`, `WorkPostBlockEntity`, `Posts`, `Departed`.
  - `compat/`: `DeedCompat`, `FarmersDelightCompat`; 2a: `ThiefCompat`, `LawCompat`, `WheelsCompat`.
  - `client/`: the Worker and Work Post screens, `StockScreen`, `PickerScreen`, `NeedIcons`,
    `PostOutline`, `ChainLook`; 2a: `CuffsLayer`; 2b: `VillagerArmourLayer`, `VillagerElytraLayer`,
    `Dress` (robe and hat).
  - `mixin/`: the owned brain and every villager's navigation, the chain's drop, the chain's
    colour; a furnace's burn time and fuel duration; a smithing upgrade's three ingredients; 2a:
    a captive's golem, its bed and the cats, an owned child and its bed; 2b: a villager's armour
    wear (`VillagerWearMixin`), the hat under a helmet (`VillagerModelMixin`, client).
- `src/gametest`: `Yard` (fixtures), `Huts` (a village with a guard, from Village Law's tests),
  `WorkerGameTests`, `WorkshopGameTests`, `CaptiveGameTests`, `EquipmentGameTests`, `MealGameTests`,
  `MarketGameTests`, `SerfdomBooth`, `TestMod`. Every batch but the meal batches runs with hunger
  off, and every batch but the market's with the economy off (`Yard.hour`).
- `devtools/art/art.py` draws every texture; `devtools/sound/work_song.py` renders the captives'
  work song to `run/work_song.wav`.

## Gotchas met

- **The GameTest framework roofs each test in barrier blocks,** so every heightmap stops at the
  barrier. A real roof, overhang or cave ceiling would hide a tree the same way. Trees and crops
  are found by section palette, never by heightmap.
- **NeoForge fires `EntityInteract` inside `Player.interactOn`.** A fixture that also calls
  `CommonHooks.onInteractEntity` fires the event twice.
- **A level-1 villager with no XP and no workstation loses its trade within ticks** (vanilla's
  `ResetProfession`). Hiring fixtures use level 2 or above. Owned villagers skip the rule.
- **A mock or fake player has no payload channels,** and a payload sent to one threw inside an
  event and crashed the server tick. Every send checks `hasChannel` first.
- **`ChunkAccess.getHeight` is the top block itself;** `Level.getHeight` is the block above it.
- **The shift's "arrived" and a task's reach must agree.** When they didn't, a farmer walked on
  the spot forever, told to move to where it already stood. A task that says MOVE while the
  worker is already there now has its target skipped.
- **Gate closing:** a distance rule, and then a 0.4-block margin, left gates open while the worker
  idled just outside. The rule is now vanilla's own for doors: not on the path's two nodes, and
  nobody standing in the gateway itself.
- **`CANT_REACH_WALK_TARGET_SINCE` outlives the walk that set it.** A new walk read an old
  failure and gave up in its first tick: a woodcutter never reached its chest. Each new walk now
  wipes it first.
- **Three of the first ten mutations got past the tests:** no crown on a tree, placed logs
  ignored, and no gates. The checks came too early, or the crops could be reached over the
  fence. The tightened tests found the two bugs above.
- **GameTest daylight runs** (only mob spawning, weather, random ticks and fire are off). A test
  that needs an hour has its own batch with `@BeforeBatch` setting the time.
- **A worker's tool lives in its `Worker` record between shifts,** never in its hand: vanilla's
  `ShowTradesToPlayer` clears a villager's main hand when it stops.

**Met in 1b:**
- **The GameTest server ticks as fast as it can.** Thirty tests with furnaces in them finish in
  seconds. Wall-clock time says nothing about game time.
- **A furnace burns its fuel slot on its first tick.** A test that loads one and then reads the
  fuel slot finds it empty. Check fuel spent by what is left in the chests.
- **`StreamCodec.composite` stops at six fields** in 1.21.1. The post's view, with seven, is
  written by hand.
- **Every screen edit is checked against an 8-block reach.** A booth player farther from the
  post had a picker click refused, with no message.
- **Vanilla's nugget smelting takes iron tools and armour,** and with a yield of one it comes
  before the table's nine from an ingot. So the rule book drops anything with durability or that
  doesn't stack from cooking rules. A row of nuggets proves it in a GameTest; the raw-metal duty
  alone never would.
- **A campfire and Farmer's Delight's stove pop their food out as items.** Collecting picks them
  up within 2.5 blocks, and the record is forgotten once the fire is empty.
- **A pot's meal needs its container in slot 7.** That is the recipe's container, or else what
  the meal leaves behind (a bowl).
- **The charcoal bootstrap must not start again while its first batch cooks**, or a smith with no
  fuel lights every furnace with logs.

**Met in 2a:**
- **A test's player has no tick of its own.** Its tick is driven by its connection, which a mock
  has none of, so a hold judged in the player's tick never moved. Holds are judged in the server's
  tick, for every player holding.
- **A mob's view follows its head.** `getViewVector` reads `yHeadRot`; turning only `yRot` left a
  player looking at what it had turned from.
- **Saved data is shared by every test in a run.** The remedy ledger held other tests' captives;
  a check must be of this test's own cases.
- **A guard in an open arena can walk out of the hut and fall out of sight** of the crime it was
  there to see: the flee test failed in one run of three with no case opened. The hut's wall is
  three high now, and the witness checks say where the guard stood.
- **A conversion copies no attachments and, for a villager, no equipment.** A worker turned zombie
  would have lost its tool and load silently; `LivingConversionEvent.Post` drops them.
- **The trailer's body takes every click through its open doors**, so a passenger inside cannot be
  clicked; unloading is the crouch with a lead or a chain.

**Met in 2b:**
- **The killing blow wears the armour first.** A test that killed a dressed worker with a hit of
  1000 found no armour dropped: the hit took 250 from every piece and broke them all, as it would
  a player's. Tests kill with `kill()`, whose damage passes armour.
- **NeoForge's menu open with extra data can't reach a mock player.** Its
  `AdvancedOpenScreenPayload` is neither built in nor in the minecraft namespace, so the
  connection refuses it. The menu opens with vanilla's packet, and the view follows as Serfdom's
  payload, which `send` skips for a player without the channel.
- **Ticking a mock player by hand** (`doTick`) syncs its attachments and crashes the server the
  same way. A test asks the menu's `stillValid`, which is what that tick asks before shutting it.
- **Curios on the gametest server** sends its sync payload to every mock player as it joins, and
  every test with a player fails. Lucky's Wardrobe's client code needs Curios to load, so Curios
  goes in the booth's own `mods` folder only.
- **Guard Villagers draws a child villager's head 1.5 times larger** (its `bigHeadBabyVillager`
  client option, set in `renderToBuffer`). The helmet sat inside the head until `Fit.head` took the
  head's scale.
- **`VillagerModel.hatVisible` sets the head's visibility too,** and the profession layer calls it
  on every draw. Hiding the hat is done after it, by a mixin.
- **Vanilla never wears a mob's armour.** `LivingEntity.hurtArmor` is empty; NeoForge's
  `ArmorHurtEvent` route works for any entity once something calls `doHurtEquipment`.

**Met in 3:**
- **Breeding lives only in vanilla's idle package,** and a worker's idle hours are the meal
  windows. A meal therefore takes over the activity as vanilla's panic does, and gives the schedule
  back, rather than holding the hours.
- **1b's station collect takes a furnace's input back out when its output is empty** (meant for a
  stalled load). Polled from the start of a meal's cooking, it emptied the raw beef out of the
  smoker in 20 ticks. A furnace is now emptied only once done or stalled.
- **Waiting costs hunger:** a worker's hunger drains while it waits by a smoker, so a meal of two
  steaks onto 4 ends at 19.75, not 20.
- **Vanilla registers a bed as a home in a server task of its own** after the block is placed. A
  bed assigned in the same task reads "not a bed"; the booth places beds a few ticks before the
  workers.
- **Farmer's Delight's meals carry good effects** (Nourishment, Comfort), so food is refused only
  for a harmful effect or the `#serfdom:not_eaten` tag.
- **The existing batches hold hours inside the breakfast window,** where workers would eat the
  stock the cook tests count. They run with hunger off.
- **A screenshot taken the tick a block changes** shows the old block: the world's mesh is redrawn
  a few frames later.

**Met in 4a:**
- **A villager in a newly generated chunk does not join as loaded from disk.**
  `addWorldGenChunkEntities` passes `false`; only a chunk read from its save passes `true`. The
  preview said otherwise. NeoForge's `FinalizeSpawnEvent` names the spawn type instead (a coremod
  sends every `Mob.finalizeSpawn` call through it, though the sources show plain calls); a cure
  finalizes after the villager has joined.
- **The offers packet copies the offers** (`offers.copy()` in its constructor) before it writes
  them, and in single player hands the copy over unwritten. A transient flag on `MerchantOffer`
  must go with the copy, or the client never sees it. GameTests that read the server's offers miss
  this; build the packet and read it back.
- **The GameTest server leaves earlier tests' areas standing,** stalls and villagers and all, and a
  villager shops anywhere within 64 blocks of its bed. Shopping tests stand alone in their batch and
  clear old stalls; every other batch runs with the economy off.
- **`./gradlew build` runs the booth wherever `DISPLAY` points.** Run it with the Xephyr recipe's
  variables; with the desktop's `:1` in the shell it opened on Rusty's screen.
- **A hopper pushes its slots in order:** a test that a stall refuses a hopper's dirt must wait
  until the hopper has tried it.
- **Vanilla's trade lists can be read with a villager never added to the world,** but a treasure
  map's listing searches the world for a structure: skip `VillagerTrades.TreasureMapForEmeralds`
  (Villager API builds More Villagers' maps from the same class).

## Next

- Rusty vets 0.6.0's photos 37 to 46.
- Then 4b (D-0006's second half), as 0.7.0.
- Rusty vets 0.5.0's photos 34 to 36 and the hungry icon in 01; 0.4.0's photos 18 to 33 (armour,
  clothes, the robe rule, the child, the trailer, the dressed Worker Screen); 0.3.0's photos 05 and 13 to 17 and the song (`run/work_song.wav`); and
  0.2.0's photos 07 to 12 and the "no station" icon in photo 01.
- Then the release, on his go: a public repo for Serfdom, tags, GitHub releases; in the pack a new
  `add-file` for Serfdom and `--replaces` for Vanilla Wheels (1.10.1) and Village Law (1.0.0).
- Not covered by any test: how the armour looks (the fit, the robe and the hat are judged by the
  booth's photos, and `FitTest` checks the boxes only); Farmer's Delight's placed skillet (left out, D-0002); a pack's much
  larger recipe book (the planner was timed on the gametest server's: 2.6 ms for the eight
  hardest rows over 1178 rules); an escape across unloaded chunks; a remedy freed on load.
