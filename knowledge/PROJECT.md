# Serfdom

**0.10.0 (its own creative tab): released 2026-10-11 in pack 1.82.0.** Rusty, 2026-10-10: a
pane for every mod with recipes. The Work Post, the For Sale block and the Chain Lead have a tab of
their own, **Serfdom**, and stay in Functional Blocks and Tools (`PaneGameTests`). Found on the way: the
build named Vanilla Wheels 1.11.0's and Ranged Weapons Mod 2.12.0's jars in their repos' build folders,
both gone, so it no longer compiled; it takes Vanilla Wheels 1.14.0 from mavenLocal now, RWM 2.13.0
(the box's) and Trailer 2.5.0. Gate: the release gate (2026-10-10, `clean build --no-build-cache`) green with 329 JUnit, 121 gametests and the booth's 135 checks. Released on Rusty's go ("Release the 2026-10-10 batch and Survivalist Armor 0.2.0. This is my go."), tag `v0.10.0` at `965b35e`, the release gate (2026-10-11, `clean build --no-build-cache`) green again on that commit; sha1 `b3d95a21` on GitHub and on the server (the server repo's `knowledge/releases/pack-1.82.0.md`). Not yet seen in play on the box.

**0.9.3 (a villager grips its tool, arms out of the fold, D-0011): built and gated on 2026-10-06 (329
JUnit, 120 GameTests, booth 135 checks with 67 photos, 5 of 5 mutations), then the manacles, the chain
lead and the chain on folded arms put in an iron ingot's greys on Rusty's word and gated again (jar sha1
`92ca8cfd`). Released on 2026-10-07 (UTC) as pack 1.74.0 with Backpacks+ 0.7.1, on Rusty's "Proceed
homie everything looks great"; the server repo's `knowledge/releases/pack-1.74.0.md` is the record. On
the box's first start the base values took 62 ms (2543 at 0.8.0's), 42 map searches refused, and no
new map was saved.** Rusty: "they look weird holding tools,
as if they're holding the sprite and not the tool itself" (vanilla draws a villager's item as a
dropped one on the folded arms); of two options he picked B, the arms out as an illager's. Found on
the way: vanilla never plays a villager's swing (only monsters' and players'), so every swing Serfdom
sent since 1b was invisible. Judged in the booth in vanilla's look and under the pack's Fresh
Animations (`-PboothLook=fresh`, new). Record: `devtools/verification/release-0.9.3.md`.

**0.9.2 (every villager's navigation floats, D-0010): built and gated on 2026-10-06. Not released;
carried whole into 0.9.3.** Rusty saw a
farmer stuck in the farm's water and "pathfinding struggles in water in general": the navigation
Serfdom gives every villager dropped vanilla's `setCanFloat(true)`, so every villager, free or owned,
planned along the bottom of water. Reproduced first (a worker never left a channel two deep), fixed in
one line. Gate: `devtools/verification/release-0.9.2.md` (319 JUnit, 118 GameTests five runs in a row,
booth 125 checks with 59 photos; jar sha1 `5aad8c4a`). 0.9.2 carries 0.9.1 and 0.9.0 whole.

**0.9.1 (no structure search while sampling the price lists, D-0009): built and gated on 2026-10-06. Not
released; carried whole into 0.9.2.** It is 0.9.0 whole plus the startup-stall fix, in place of the
"0.8.1" once planned. Gate:
`devtools/verification/release-0.9.1.md` (319 JUnit, 114 GameTests, booth 125 checks with 59 photos, 4 of
4 mutations; jar sha1 `eb6ec2a8`). The stall was one listing: Backport 1.0.9's explorer maps, which the
sampling's skip of vanilla's treasure maps by name let through; each call searched the world for a
structure and saved a new map. Measured with the box's 99 mods: 10347 ms before, 245 ms after, no map
saved. The box holds nine such maps (ids 226 to 234, from the 1.73.0 start), left in place.

**0.9.0 (shared farms and sowing, D-0008): built and gated on 2026-10-06. Not released; carried whole
into 0.9.1.** Gate: `devtools/verification/release-0.9.0.md`
(319 JUnit, 113 GameTests seven runs in a row, booth 125 checks with 59 photos, 25 of 25 mutations; jar
sha1 `8500ca6b`). Rusty's calls: touching farming posts of one owner are one farm (over one big post);
sowing copies what grows near "so long as their copying does not interfere with what you decided is to
be grown". A farmer holds an 8 by 8 plot at a time, its own post's area first, and lets go when it
stops, dies or loses its job (else a 10-second lapse); the harvest goes to the nearest post's chests;
woodcutters hold their trees. Both old defects (two farmers chasing one route, two woodcutters at one
tree) were reproduced on 0.8.0 first. Found: a Work Post was open ground to the pathfinder (now a
fence to paths not ending at it). Not seen by Rusty: photos 56 to 59; nothing of it in live play.

**0.8.0 released 2026-10-06 in pack 1.73.0** with Vanilla Wheels 1.11.0 and Village Law 1.1.0, on
Rusty's go ("release all three"): public at github.com/the-rusty-shackleford/minecraft-serfdom,
tag `v0.8.0`, the gate's jar (sha1 `64c9330e`) on GitHub and on the server. The box's first start
loaded it beside Lithium with no error, and a saved villager in the CTOV village at -1264, -1040
(force-loaded with nobody on, then released) read `serfdom:purse {emeralds: 64}`. **Found on the
box:** the base values (`market/Prices`) are worked out on the server thread as it starts, 2543 ms
for 501 items, and the server logged "Can't keep up" 3698 ms behind just after `Done` (see Next).
Not yet seen in play: anything of Serfdom. The server repo's `knowledge/releases/pack-1.73.0.md`
has the deployment.

**0.8.0 (phase 5, raids and the base's defence, D-0007): built and gated on 2026-10-05.** Gate: `devtools/verification/release-0.8.0.md` (290 JUnit, 99 GameTests eight runs in a
row, booth 110 checks with 55 photos, 23 of 23 mutations; jar sha1 `64c9330e`). Rusty's call: workers never take a
launcher. When a raid comes, owned villagers (hired and captive) arm from their home chest and their
post's chests (the best ranged weapon they have ammunition for, a melee backup), fight the raid's
raiders within reach, hold fire with a friend in the line, hide with nothing to fight with, and put
everything back after. Guns through the Ranged Weapons protocol, reloading loose rounds only: RWM's
magazines aren't the protocol's (D-0007's build notes). Not seen by Rusty: photos 51 to 55. A
visual point open for him: a villager's folded arms draw a held weapon lying across them. 0.8.0
carries 0.7.0 whole and ships in its place, with the same two siblings. The plan's five phases are
all built.

**0.7.0 (phase 4b, the market's second half, D-0006): built and gated on 2026-10-05. Not released.**
Gate: `devtools/verification/release-0.7.0.md` (274 JUnit, 87 GameTests eight runs in a row, booth 94
checks with 50 photos, 27 of 27 mutations; jar sha1 `c9ad1f26`). Built without a new preview, on
Rusty's word. Climate (the bed's biome; goods from another climate at half again), taste (four
kinds of goods, 0.5 to 1.5 each, drawn from the UUID, leaning by trade), wants (up to three of an
item of a liked kind, at home), window shopping (with no need anything in reach sells, a look at each
stall not yet seen that day), and free villagers selling from their inventory (a farmer's bread). One
line of D-0006 did not hold as written: "no need" is read as "no need anything in reach sells" (see
D-0006, "Settled in the build: 4b"). Two 4a defects fixed on the way: a stall counted as storage, and
villagers planned their way over a stall. Not seen by Rusty: photos 47 to 50. 0.7.0 carries 0.6.0
whole and ships in its place, with the same two siblings. Phase 4 is done; phase 5 (raids and the base's
defence) is next in the plan, previewed first.

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
| Repo | Public at github.com/the-rusty-shackleford/minecraft-serfdom, jars on its Releases |
| Gate | 1a: `release-0.1.0.md`; 0.2.0: `release-0.2.0.md`; 0.3.0: `release-0.3.0.md`; 0.4.0: `release-0.4.0.md`; 0.5.0: `release-0.5.0.md`; 0.6.0: `release-0.6.0.md`; 0.7.0: `release-0.7.0.md`; 0.8.0: `release-0.8.0.md`; 0.9.0: `release-0.9.0.md`; 0.9.1: `release-0.9.1.md`; 0.9.2: `release-0.9.2.md`; 0.9.3: `release-0.9.3.md` (all in `devtools/verification/`) |
| Release | 0.8.0 in pack 1.73.0 (2026-10-06); 0.9.3 built, held by Rusty until his batch is done, then `add-file --replaces mods/serfdom-0.8.0.jar` |

Minecraft 1.21.1, NeoForge 21.1.248, Java 21. `com.chunkworks.serfdom`, AGPL-3.0-or-later, headers
"Rusty Shackleford and nfx". Nests Carried. Optional: Village Deed 2.2+ (bought villages, home
village ids) and Farmer's Delight 1.3 (tomatoes, rice).

The villager overhaul in Rusty's spec (`~/Downloads/serfdom-mod-spec.md`), in five phases. Phase 2
is split as phase 1 was: 2a, the capture, is [D-0003](decisions/D-0003.md); 2b, equipment slots
and the armour layer, is [D-0004](decisions/D-0004.md). Phase 3, hunger and meals, is
[D-0005](decisions/D-0005.md). Phase 4, the market, is [D-0006](decisions/D-0006.md): 4a and 4b
built. Phase 5, raids, is [D-0007](decisions/D-0007.md). Phase 1 is [D-0001](decisions/D-0001.md),
split by Rusty into:

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
  - 4b: `Climate`, `Taste` (the draw, the lean, likes, the favourite, wants), `Peddler` (spare stock,
    lots in whole emeralds); `Shopping` gained the stalls seen, the look (`browse`) and what a villager
    does now (`decide`).
  - 5: `Armoury` (what to take), `Defence` (who musters, the next step, the hand, reach, a reload, a
    blow's cooldown), `LineOfFire`.
  - 0.9.0 (D-0008): `Farm` (which posts are one farm, the plots, the next plot), `Holds` (one worker a
    plot or a tree, lapsing), `Sowing` (which crop a bare spot takes).
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
    `ForSaleRenderer`, `PurseLabel`. 0.9.1 (D-0009): `mixin/ServerLevelMixin` refuses map structure
    searches while `Prices` samples the price lists (`Prices.refusesSearch`).
  - 4b: `market/Tastes` (taste, climate, kinds and origins by tag, the bonus, what it wants),
    `market/Peddlers` (free villagers selling, one buyer at a time); `Needs` reads each trade's
    `taste` and `sells`; `Shoppers.Plan` goes to a stall or a seller, or looks; `Counter.buyFrom`;
    `Storage.storage` (never a stall); `ForSaleBlock` is a fence to the pathfinder.
  - 5: `defence/` (`Defenders` the module: the raid, the facts, targets, listeners; `Armouries` the
    chests; `Arms` the `serfdom:arms` attachment), `behavior/RaidDuty` (core), `behavior/Defend` (the
    `serfdom:defend` activity), `behavior/Unless` (vanilla's panic and raid triggers, skipped while
    defending), `compat/GunsCompat` (the Ranged Weapons protocol, compile only).
  - 0.9.0 (D-0008): `post/Farms` (a post's farm, cached by `Posts`' generation; the server's one sweep
    a tick of the most overdue field), `job/Field` (a farming post's area: its work by plot, what each
    spot grew, since when bare), `job/Holding` (the holds, by dimension, not saved); `Job.Task.hold`;
    `Farming` and `CropTask` work a plot (harvest, replant, sow, fetch seed); `Storage`'s farm lookups;
    `WorkerNavigation` makes a Work Post a fence to paths that don't end at it.
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
  `MarketGameTests`, `TasteGameTests`, `DefenceGameTests`, `SerfdomBooth`, `TestMod`. Every batch but the meal batches
  runs with hunger off, and every batch but the market's with the economy off (`Yard.hour`). A test
  that buys at a price near the base value gives its villager a taste (`Yard.villager` with a test of
  the taste).
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
- **Vanilla's trade lists can be read with a villager never added to the world,** but a map listing
  searches the world for a structure and saves a new map. 4a skipped `VillagerTrades.TreasureMapForEmeralds`
  by name, which Backport's own class got past (see 0.9.1); the search is now refused while sampling.

**Met in 4b:**
- **A block whose collision is no full block is open ground to vanilla's pathfinder,** and a mob plans
  to jump onto any block it can't walk through unless its path type is a fence
  (`WalkNodeEvaluator.findAcceptedNode`). The stall's sign made its top a full block high: window
  shoppers planned straight over the stall between two and stuck on its sign. NeoForge's
  `getBlockPathType` returning FENCE stops it.
- **NeoForge's ground navigation lifts a target that is a solid block to the block above.** A walk to a
  fence-typed stall ends beside it, so arrival must go by distance, as the shopper's 2.5 blocks do. The
  Work Post can't be a fence: workers' arrival leans on reaching above its pole (1a's gate test failed).
- **A taste drawn from the UUID touches every test that buys near the base value:** a random taste
  under 2/3 made 4a's bargains a coin toss. Tests pick a UUID for the taste they need, before the
  villager is added (two entities with one UUID refuse the second).
- **A villager needing nothing window-shops every stall in reach,** whoever it is: a seller in a test
  looked over the dearer stall and wrote a line in its ledger.
- **A fixture villager hired after the settle ticks takes a free bed near it** in the meantime: hire it
  as it is made.
- **A frozen (no AI) mob never lands,** and a ground navigation plans only for a mob on the ground:
  `createPath` gives nothing.
- **`level.playSound(null, entity, ...)` posts `PlayLevelSoundEvent.AtEntity`,** not `AtPosition`;
  `TestMod` hears both.
- **Python's output to a file is buffered:** a mutation batch's results appear only when it ends.

**Met in 5:**
- **Villagers have no attack damage attribute,** and `Mob.doHurtTarget` throws without one: the
  defence adds a fist's (1) to the villager type. Removing it crashes the server at the first blow,
  so it is the one rule the mutation pass leaves out.
- **Villagers' brains hold no attack target** (`Villager.MEMORY_TYPES`): vanilla's melee behaviours
  can't run on them, and the defence keeps its own target, as the shopping trip does.
- **`KeepBed` puts a worker's hand away whenever it isn't at work,** the tool's rule since 1a: it took a
  defender's sword a few ticks after it armed. `Workers.stash` leaves the defence's weapon alone.
- **A skeleton's aim (a fifth of the distance up) is for 1.6-speed arrows:** at a full draw's 3.0 every
  arrow went over the head. The lift is gravity's drop over the flight.
- **A raid's countdown to its first wave runs only while it has no raiders alive,** so raiders joined
  by hand hold its own waves back; a test stops its raid, or a wave reaches a later test. Raids within
  96 blocks are one raid: each raid test stands alone in its batch.
- **The Raid Omen's particles swarm about the player that carries it,** and creating a raid by hand
  doesn't take the effect away: the booth's camera wore it until it was removed.
- **RWM's magazines aren't protocol ammo stores** (no `AMMO_STORE` capability, no profile names one).
- **A villager set free gets vanilla's brain back,** so nothing of the defence runs on it after:
  `Workers.free` drops what it took. A rule that only an owned brain can reach can't be tested on a free
  villager; the mutation pass showed it by failing to fail.
- **One unexplained failure:** once, under mutation batch `b`, the hiding test's bow was not back in its
  chest by the test's end though the put-back had finished; 13 runs since have passed, and the
  put-back now logs what it puts where (`release-0.8.0.md`).

**Met in 0.9.0:**
- **A Work Post is open ground to vanilla's pathfinder** (its pole is no full block), so a villager
  walking past one plans over it and stands against the pole. It is a fence now to every path that
  doesn't end at it (`WorkerNavigation`); a path to the post stays vanilla's, since a worker's arrival
  leans on reaching above the pole.
- **Vanilla reads a villager's schedule at most every 20 ticks** (`Brain.updateActivityFromSchedule`):
  a test that ends a shift waits for the activity to change, not a fixed few ticks.
- **The hour is the level's:** a GameTest that moves the clock ends every other test's shifts in its
  batch, so such tests stand alone in a batch.
- **Sweeping one area a tick in reading order starves:** the reader's own area, stale again after its
  100-tick wait, took the tick every time, and the farm's other areas were never read. The server
  sweeps the most overdue area instead.
- **A test must not put a post on its own field's crops,** which then can never be taken.
- **The booth's camera dies of a fall in survival:** teleported 22 blocks up, it fell, and with no
  living player the farm's chunks stopped ticking (every farmer frozen mid-walk). The farm scene's
  camera is a spectator. `-PboothScene=farm` runs that scene alone and `-PboothTrace` logs every
  worker's plan, which found it in three short runs.
- **A build that fails to compile runs no tests,** and `run/logs/latest.log` is then the last run's:
  three runs were read as failing that never ran. Check the log's first line's time; the mutation
  script deletes the log first and refuses a run that did not compile.

**Met in 0.9.1:**
- **A map listing asked for an offer does real work in the world:** it searches for its structure,
  marks the one it finds as referenced (a structure takes one, so no later map leads there), and saves
  a new map. Sampling 1500 offers with a bare villager is safe only where nothing searches.
- **The GameTest world makes no structures** (`WorldOptions(0, false, false)`): a structure search
  there finds nothing whatever the code does. Count what was asked, and measure the rest on the box's
  mods.
- **A server on the desktop with the box's mods** answers what the GameTest server can't: copy
  `/data/mods` through `package attach ... base64` (a raw `cat` through attach changes binary bytes;
  check every sha1), install the NeoForge server with its installer, and give it another port, no RCON
  and a new world. Recipe in `devtools/verification/release-0.9.1.md`.

**Met in 0.9.2:**
- **A navigation swapped in after the villager's constructor loses what that constructor set on the old
  one.** Vanilla's `Villager.<init>` sets doors and floating; `WorkerNavigation` set only doors, and every
  villager pathed along the bottom of water from 0.1.0 to 0.9.1. A replacement takes over everything the
  constructor set (`aVillagersNavigationFloats` pins floating).
- **Water one deep traps nobody;** two deep with banks two high does, when the path is planned along the
  bottom. A water test needs the deep case.

**Met in 0.9.3:**
- **Vanilla advances a swing only for monsters and players** (`Monster.aiStep`, `Player.aiStep`): a
  villager's `swing` sets it going and it never moves, on either side, so no swing of a villager has
  ever shown. `VillagerMixin` advances it each tick.
- **A chop is shorter than a blow:** an iron axe fells an oak log in fewer ticks than a swing's six, and
  vanilla starts no new swing in the first half of one, so a woodcutter swings about once a log.
- **A defence that ends by itself clears what its tick sets;** only one stopped from outside (a capture
  rebuilds the brain, which stops every running behaviour) reaches `stop` with no tick between. A test
  of `stop` needs that case: the swordsman's test let the mutation past.
- **The booth's arms scene, run alone, passed and failed in the full run:** the full booth leaves the
  camera over the farm, the arms lot's chunks unloaded, and `ServerLevel.getEntity(id)` of a villager
  just added there was null. Move the camera first, then spawn; keep the entities, not their ids.
- **Fresh Animations replaces the villager model under Entity Model Features** (the pack's client): it
  keeps vanilla's part names and texture layout, animates `body` and `arms` with the body, and hides
  vanilla's `jacket` for its own `coat`. A layer that hangs from `body.translateAndRotate` follows it.
- **A rendering client of Rusty's counts:** check `pgrep -a java` for Prism's runtime as well as
  `pgrep -a Xephyr` before every booth run, not once a session; five booth runs went beside his game
  before it was seen. Another session's client counts too (a Backpacks+ network driver shared the
  desktop during the gate; the two sessions took turns by message).
- **The GameTests take sibling mods' jars by file name**, and Gradle drops a missing file from a
  classpath silently: a clean build of Backpacks+ 0.7.1 removed `backpacksplus-0.7.0.jar` mid-pass,
  and only the hiring test's check of the bag said so. When a sibling's version moves, move its pin.
- **A booth photograph can be wrong while every check passes:** in the gate's run, photos 23 and 39
  to 41 were grass seen straight down, the camera turned between two of the scene's teleports and put
  back by the next; a rerun was right. `photo` now logs the camera's place and turn on both sides, so
  a wrong one can be told from a wrong moment. Look at the photographs, not only the PASS lines.

## Next

- Rusty vets photos 56 to 59 (shared farms and sowing). He passed 60 to 67 in both looks ("everything
  looks great", 2026-10-06).
- Unasked, for Rusty if he wants them: woodcutting posts linked into a shared forest (the same rule
  would serve); a farmer with no seed buying some at a stall (today it shows the empty crate).
- Watch the box's log for Serfdom in live play: no hire, capture, market day, raid, shared farm or
  sowing has run there.
- Rusty vets 0.8.0's photos 51 to 55, 0.7.0's 47 to 50 and 0.6.0's 37 to 46.
- RWM's magazines as protocol ammo stores, if Rusty wants defenders to use them: a change to RWM.
- Rusty vets 0.5.0's photos 34 to 36 and the hungry icon in 01; 0.4.0's photos 18 to 33 (armour,
  clothes, the robe rule, the child, the trailer, the dressed Worker Screen); 0.3.0's photos 05 and 13 to 17 and the song (`run/work_song.wav`); and
  0.2.0's photos 07 to 12 and the "no station" icon in photo 01.
- Not covered by any test: how the armour looks (the fit, the robe and the hat are judged by the
  booth's photos, and `FitTest` checks the boxes only); Farmer's Delight's placed skillet (left out, D-0002); a pack's much
  larger recipe book (the planner was timed on the gametest server's: 2.6 ms for the eight
  hardest rows over 1178 rules); an escape across unloaded chunks; a remedy freed on load.
