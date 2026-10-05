# Serfdom

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
| Gate | 1a: `release-0.1.0.md`; 0.2.0: `release-0.2.0.md`; 0.3.0: `release-0.3.0.md` (all in `devtools/verification/`) |
| Release | Only on Rusty's go, as a new jar in the pack, with Vanilla Wheels 1.11.0 and Village Law 1.1.0 |

Minecraft 1.21.1, NeoForge 21.1.248, Java 21. `com.chunkworks.serfdom`, AGPL-3.0-or-later, headers
"Rusty Shackleford and nfx". Nests Carried. Optional: Village Deed 2.2+ (bought villages, home
village ids) and Farmer's Delight 1.3 (tomatoes, rice).

The villager overhaul in Rusty's spec (`~/Downloads/serfdom-mod-spec.md`), in five phases. Phase 2
is split as phase 1 was: 2a, the capture, is [D-0003](decisions/D-0003.md); 2b, equipment slots
and the armour layer, is next. Phase 1 is [D-0001](decisions/D-0001.md), split by Rusty into:

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
- `src/main`:
  - `Serfdom` (registries), `SerfdomConfig`.
  - `Worker` (the saved attachment) and `Workers` (state, hire, beds, posts, needs, the tool between
    shifts, gestures, death).
  - `WorkerBrain` (the owned brain and its schedules), `Hire`, `Picks`, `Screens`
    (payloads), `ChainLead`, `PlacedLogs`.
  - 2a: `Captures` (the hold), `Remedies` (saved data, the law's remedy), `Humming`.
  - `behavior/`: `WorkShift`, `FollowOwner`, `KeepBed`, `OpenGates`, `WorkerNavigation`; 2a:
    `Stay`, `CaptiveNight`, `RunHome`.
  - `job/`: `Jobs` (data), `Woodcutting` + `WoodTask`, `Farming` + `CropTask`, `Storage`, `Tools`;
    1b: `RecipeBook` (rules off the game's recipes), `Stations` (kinds, states, loading,
    collecting), `WorkshopJob` (facts, choice, task), `WorkshopTask` (legs).
  - `post/`: `WorkPostBlock`, `WorkPostBlockEntity`, `Posts`, `Departed`.
  - `compat/`: `DeedCompat`, `FarmersDelightCompat`; 2a: `ThiefCompat`, `LawCompat`, `WheelsCompat`.
  - `client/`: the Worker and Work Post screens, `StockScreen`, `PickerScreen`, `NeedIcons`,
    `PostOutline`, `ChainLook`; 2a: `CuffsLayer`.
  - `mixin/`: the owned brain and every villager's navigation, the chain's drop, the chain's
    colour; a furnace's burn time and fuel duration; a smithing upgrade's three ingredients; 2a:
    a captive's golem, its bed and the cats, an owned child and its bed.
- `src/gametest`: `Yard` (fixtures), `Huts` (a village with a guard, from Village Law's tests),
  `WorkerGameTests`, `WorkshopGameTests`, `CaptiveGameTests`, `SerfdomBooth`, `TestMod`.
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

## Next

- Rusty vets 0.3.0: photos 05 and 13 to 17, and the song (`run/work_song.wav`). Still unseen from
  0.2.0: 1b's photos 07 to 12 and the "no station" icon in photo 01.
- Then the release, on his go: a public repo for Serfdom, tags for all three, GitHub releases;
  in the pack a new `add-file` for Serfdom and `--replaces` for Vanilla Wheels (1.10.1) and
  Village Law (1.0.0).
- Phase 2b (equipment slots and the armour layer) is next: preview it first.
- Not covered by any test: Farmer's Delight's placed skillet (left out, D-0002); a pack's much
  larger recipe book (the planner was timed on the gametest server's: 2.6 ms for the eight
  hardest rows over 1178 rules); an escape across unloaded chunks; a remedy freed on load.
