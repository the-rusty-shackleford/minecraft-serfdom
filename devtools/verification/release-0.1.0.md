# Serfdom 0.1.0 (phase 1a): verification

Run on 2026-10-04 with `./gradlew clean build`: `BUILD SUCCESSFUL`, the jar `serfdom-0.1.0.jar`
(Carried nested). The booth ran on Xephyr `:7` with software rendering.

## JUnit: 66 tests, 0 failures

| Suite | Tests | What it covers |
|---|---|---|
| `AssignmentTest` | 8 | no bed; the post at 48 and 49 blocks, straight-line and diagonal; another dimension; 3 and 4 others; a new bed near and far; a lost bed; a cleared job; the invariant |
| `FellingTest` | 14 | see below |
| `HarvestTest` | 5 | — |
| `HiringTest` | 5 | every combination of the four facts; fees by level and outside 1 to 5 |
| `JobScriptTest` | 5 | — |
| `ShiftTest` | 6 | wind-down at its boundary; full; tool fetched or shown; no tool needed |
| `SmallRulesTest` | 11 | `Radius`, `Need`, `Pace`, `Follow`, `Spot` |
| `SortingTest` | 8 | holders, sharers, overflow; full bins; ties |
| `WorkDayTest` | 4 | — |

`FellingTest` covers:
- a lone oak;
- a bare pillar;
- a crowned pillar on stone;
- a placed log in a trunk, and a placed log feeding leaves;
- interlocked canopies;
- a 2×2 trunk with a diagonal branch;
- another species touching;
- more than 512 logs;
- leaves exactly at the reach and past it, without a second tree and through persistent leaves;
- the looking limit;
- `baseBelow`.

## GameTests: 15 passed

All run with Village Deed 2.2.0, Thief 1.2.4, Farmer's Delight 1.3.3 and Backpacks+ 0.7.0 loaded.

**Hiring and ownership**
- **Hiring.** A level-3 farmer costs 24 of 30 emeralds and becomes a follower that shows "no bed".
  A nitwit and a child are not hireable. A player short of 16 with 5 pays nothing. A bag pays a
  level-2 cleric's 16. A free villager keeps vanilla's schedule.
- **The novice trap.** A hired novice farmer keeps its trade for 200 ticks; a free one beside it
  loses it.
- **Sneak-use.** On a worker, the owner's and a stranger's sneak-use never reach normal priority,
  where Village Deed listens; a plain use does. A free villager's sneak-use reaches it.

**Beds and posts**
- **Beds.** At night the worker walks past a nearer free bed to the one it was given, which was
  picked by a click on the bed's foot, and sleeps in it. The nearer bed stays free.
- **Linking.** Refused without a bed and with four others on the post; picked by a click; a
  worker's day after.
- **Distance.** A post 63 blocks from the bed is refused.
- **A broken post** sends its worker back to a resident's day, keeping its bed.

**Work**
- **Woodcutting.**
  - The stored iron axe is fetched and the natural oak felled whole.
  - The axe wears at least a use per log, the leaves the oak fed are cleared, and the base is
    replanted.
  - Four hundred ticks later the bare log pillar and a player's log column crowned with natural
    leaves still stand.
  - At the shift's end the logs are in the chest.
- **Farming.**
  - Ripe wheat, carrots and beetroot are harvested and replanted at age 0, and young wheat is
    left.
  - The pumpkin is taken and its stem stays. No grass is tilled.
  - At the shift's end the wheat is in the chest holding wheat, and every wheat seed is in the
    chest holding seeds.
- **Needs.**
  - No axe stored shows "no tool" and fells nothing; an axe put in is fetched and the need clears.
  - A full load with every chest full shows "chest full"; an emptied chest takes the load.
- **Gates.** Wheat deep inside a fenced field with one gate: the gate opens, the wheat is
  harvested, and the gate is shut once the farmer is back out.
- **Restock.** A worker whose trades were all out of stock restocks at its post.

**The chain, death and saving**
- **The chain.**
  - Vanilla's lead fails on a worker.
  - The chain does nothing to a free villager and is kept.
  - On the worker it leads, using one chain.
  - Past ten blocks it breaks, and a chain drops, never a lead.
- **Death.** A dead worker drops its axe once and its five logs and leaves its post. A post told
  of a worker who died while it was unloaded takes it off its list on load.
- **Save and load.** A saved worker loads with its owner, bed, post, bed memory and worker's
  schedule.

## Booth: 10 checks, 6 photographs

| Photo | Shows | Checks in code |
|---|---|---|
| `01-post-and-needs` | The post facing the camera, its outline drawn; five workers each showing one need | the client sees all five needs; the outline and radius |
| `02-worker-screen` | The Worker Screen | Assign bed live; Assign job and Clear job greyed |
| `03-post-screen` | The post's screen | at least two jobs; radius 4 |
| `04-post-screen-radius-5` | The post's screen after a click on [+] | the server took radius 5 |
| `05-chain` | A worker on the chain, drawn as dark and light iron links | the client sees the leash |
| `06-items` | The Work Post and the chain lead in the inventory | — |

Judged by eye:
- the icons are readable within eight blocks and absent beyond;
- the board's crossed axe and hoe read at a glance in close-up.

## Mutation pass: 10 of 10 caught

Each mutation was applied, the GameTests run, and the file restored
(`python3 devtools/verification/mutate.py`).

| Mutation | Caught by |
|---|---|
| M1 owned villagers keep vanilla's `ResetProfession` | the novice trap |
| M2 a tree needs no crown of leaves | woodcutting (the bare pillar is felled) |
| M3 the record of placed logs is ignored | woodcutting (the player's column is felled) |
| M4 deposits go to the last choice | farming (sorting) |
| M5 the fee ignores the level | hiring |
| M6 the chain drops as a lead (`EntityMixin` off) | the chain |
| M7 workers do not open gates | gates |
| M8 sneak-use on a worker taken at LOWEST, after Village Deed | sneak-use |
| M9 no restock at the post | restock |
| M10 the shift never winds down | farming and woodcutting (nothing stored) |

The first pass missed M2, M3 and M7: the woodcutting test checked the builds before the worker
could have reached them, and the gate test's crops could be reached over the fence. With those
tests tightened, two real bugs showed and were fixed before this pass:
- a stale `CANT_REACH_WALK_TARGET_SINCE` that made a new walk give up at once;
- a gate left open beside an idling worker.

## Not verified

- **Play on the box:** no live player has hired, housed or worked a villager.
- **Performance** has not been measured with many workers at a base. The per-tick paths are a
  worker's shift step (no scans), `KeepBed` every 40 ticks, and `OpenGates`. Area searches run
  per target, not per tick.
- **Golem summoning** by hired workers is vanilla's code, kept, and not exercised by a test.
- **Farmer's Delight tomatoes and rice** compile against 1.3.3 and follow its code, read from
  the jar, but are not exercised by a GameTest.
- **A 2×2 tree** is felled and replanted in `FellingTest`, not in a GameTest.
