# Serfdom

**0.1.0 (phase 1a): built and gated on 2026-10-04. Not released. Rusty has passed the booth
photos and the art; he has not yet seen it in play.**

| What | Where it stands |
|---|---|
| Repo | No remote yet |
| Gate | 66 JUnit, 15 GameTests, booth 10 checks with 6 photos, mutation pass in `devtools/verification/release-0.1.0.md` |
| Release | Only on Rusty's go, as a new jar in the pack |

Minecraft 1.21.1, NeoForge 21.1.248, Java 21. `com.chunkworks.serfdom`, AGPL-3.0-or-later, headers
"Rusty Shackleford and nfx". Nests Carried. Optional: Village Deed 2.2+ (bought villages, home
village ids) and Farmer's Delight 1.3 (tomatoes, rice).

The villager overhaul in Rusty's spec (`~/Downloads/serfdom-mod-spec.md`), in five phases. Phase 1
is [D-0001](decisions/D-0001.md), split by Rusty into:

- **1a (this):** hiring, beds, the Work Post, woodcutting, farming, sorting, need icons, the
  Worker Screen, the chain lead, fence gates.
- **1b (next):** cooking and blacksmith.

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
  - `Follow`.
- `src/main`:
  - `Serfdom` (registries), `SerfdomConfig`.
  - `Worker` (the saved attachment) and `Workers` (state, hire, beds, posts, needs, the tool between
    shifts, gestures, death).
  - `WorkerBrain` (the owned brain and its three schedules), `Hire`, `Picks`, `Screens`
    (payloads), `ChainLead`, `PlacedLogs`.
  - `behavior/`: `WorkShift`, `FollowOwner`, `KeepBed`, `OpenGates`, `WorkerNavigation`.
  - `job/`: `Jobs` (data), `Woodcutting` + `WoodTask`, `Farming` + `CropTask`, `Storage`, `Tools`.
  - `post/`: `WorkPostBlock`, `WorkPostBlockEntity`, `Posts`, `Departed`.
  - `compat/`: `DeedCompat`, `FarmersDelightCompat`.
  - `client/`: the two screens, `NeedIcons`, `PostOutline`, `ChainLook`.
  - `mixin/`: the owned brain and every villager's navigation, the chain's drop, the chain's
    colour.
- `src/gametest`: `Yard` (fixtures), `WorkerGameTests`, `SerfdomBooth`, `TestMod`.
- `devtools/art/art.py` draws every texture.

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

## Next

- Rusty vets 1a. The booth photos and the art (Work Post board, chain lead) passed on
  2026-10-04 ("Photos look good"). Still to see: a worker felling and farming at his base. Then
  the release on his go: a public repo, a tag, a GitHub release, and a new `add-file` in the pack.
- 1b: cooking and blacksmith, the "keep X in stock" lists on the post, smelting before crafting.
  Its preview comes first.
- Phase 2 needs Vanilla Wheels 1.11's cargo hook and Village Law's `CaseSettledEvent`.
