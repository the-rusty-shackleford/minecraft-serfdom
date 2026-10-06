# Serfdom 0.9.0 (shared farms and sowing): verification

Run on 2026-10-06. 0.9.0 is 0.8.0 (released in pack 1.73.0) with D-0008: farming posts whose areas
touch are one farm, each farmer holds a plot at a time (its own post's area first) and the others fill
in when it can't work; bare farmland is sown with what grows near, never over what the player
planted; two woodcutters on one post fell two trees. Rusty's calls are in D-0008; its "Settled in the
build" holds what the build found.

`./gradlew clean build` with the booth on Xephyr `:7` (the Gradle daemon stopped first, so the build's
environment was the Xephyr recipe's): `BUILD SUCCESSFUL` in 5 m 28 s. The jar `serfdom-0.9.0.jar` has sha1
`8500ca6b5cc55dde33edfbb9d2f068ae47da113a`, with Carried 1.0.0 nested. No sibling changes. The network
version is "7" (the post screen's payload), so a 0.8.0 client is refused at login: every player takes
the pack's jar.

## The defects, reproduced before the fix

The first two GameTests were written and run on 0.8.0's code, with nothing else changed, before any
of D-0008 was built (`./gradlew runGameTestServer`, 2 of 101 failed):

- `twoFarmersOnOnePostNeverChaseOneCrop`: "a farmer walked toward wheat the other had taken, for 160
  ticks".
- `twoWoodcuttersFellTwoTrees`: "both woodcutters went for the same tree", each of the two going for
  both oaks in turn.

Both pass at 0.9.0.

## JUnit: 319 tests, 0 failures

The 290 of 0.8.0, plus:

| Suite | Tests | What it covers |
|---|---|---|
| `FarmTest` | 10 | linking: overlapping, touching exactly (r1 + r2 + 1), one block apart, along x, z and y, at a corner, unequal radii either way round, the same post, another owner, another job; a chain of three whose ends don't touch, a post alone, the input's order, a start not among the posts; plots at 0, 7, 8, -1, -8, -9; the next plot: its own area's before a nearer one, the nearest of its own, elsewhere when its own has none, held plots skipped, its own hold kept, ties by plot, no offers, all held; refused values |
| `HoldsTest` | 10 | a free place; another's hold at LAPSE - 1 and LAPSE; renewing moves the lapse; who holds (nobody, live, lapsed) and held by another (free, its own, another's live and lapsed); letting go by the holder, by another, of a lapsed hold, of nothing; everything one worker holds; pruning; 3000 holds with pruning on the way; nulls |
| `SowingTest` | 9 | a spot that grew a crop sown with it among another kind and among stems, a stem where a stem grew; a never-planted spot with one kind 1 away, at the square's edge and corner, 5 away; two kinds, one remembered on a bare spot; nothing near; only stems, a stem beside, a stem diagonal; one tick short of the wait, exactly the wait, a wait of 0; the layer's edge and corner; the order; crops never sown; refused inputs |

## GameTests: 113 passed

The 99 of 0.8.0 (one asked less of, below) and 14 in `FarmGameTests`:

- **One post:** two farmers at a 16 by 8 field of ripe wheat, both holding plots within 60 ticks of
  the first, neither ever walking to wheat the other took, all of it harvested and replanted; two
  woodcutters and two oaks (both nearer the one), both holding trees within 60 ticks of the first,
  never going for the same tree, both felled.
- **A farm of posts:** a farmer on one of two touching posts harvests the other's area and puts the
  wheat in its own post's chest (the other has none); a post one block too far and another owner's
  touching post are left alone; a farmer standing in the other post's area takes its own area's wheat
  first; with chests at both posts, a farmer at work in the second's area puts its harvest there.
- **Filling in:** the second farmer killed while it holds a plot: the plot free two ticks later, the
  first farmer finishing the field; a farmer's shift ended mid-plot: the plot free the tick the shift
  stops; one of two farmers frozen past its hold's lapse, the other taking its plot: unfrozen, it never
  walks into that plot.
- **Sowing:** two gaps in a row of young wheat still bare at 1100 ticks, then sown with wheat, two of
  the farmer's eight seeds spent; three gaps sown with three seeds taken from the farm's chest (16 to
  13); where wheat and carrots meet, and among pumpkin stems only, bare after a control gap nearby was
  sown; a carrot among young wheat, seen by the farm, then broken: a carrot again, never wheat.
- **Cost:** below.

Six runs in a row passed at the final code (113 of 113 each), and the gate's build made a seventh.

## Cost

`aFieldsSweepIsCheap` sweeps a radius-16 post's whole area of ripe wheat (33 by 33, 1088 crops) twenty
times and times a farmer's look for work over it, on the GameTest server with earlier batches'
villagers still at work:

| Run | Sweep, median | Sweep, worst | Look for work, median | Look for work, worst |
|---|---|---|---|---|
| 1 | 895 µs | 32534 µs (the first, cold) | 266 µs | 1002 µs |
| 2 | 1081 µs | 1924 µs | 319 µs | 659 µs |
| 3 | 2278 µs | 3734 µs | 247 µs | 775 µs |

About a millisecond a sweep, so a farm of nine radius-16 posts read at once would have cost about nine
in one tick. An area is now swept the first time it is read, and after that the server sweeps one area
a tick, the most overdue of those read in the last 200 ticks and swept 100 or more ago
(`Farms.listen`). A first try, one sweep a tick in reading order, starved the other posts' areas (four
tests failed: the farmer's own area, stale again after each 100-tick wait, took the tick every time).

## Found in the build

- **A Work Post was open ground to the pathfinder** (its pole is no full block), so a farmer bound
  across the farm planned straight over a post and stood against its pole: `aFarmerWorksItsOwnAreaFirst`
  logged "could not reach" four times running for the other post's wheat. With the fix switched off,
  0.8.0's own `aFarmerHarvestsRipeCropsAndSortsThem` failed too, its farmer stuck beside its post at the
  shift's end, unable to reach its chests on the far side. The post is now a fence to every path that
  doesn't end at it; a path to the post is vanilla's as before.
- **The 1a gate test's margin.** `aWorkerOpensTheGateAndShutsItBehindIt` asked that the farmer end more
  than 1.5 blocks past the gate. Logged at its rest: 1.516 without the fence rule, 1.477 with it, out of
  the field with the gate shut both times; the farmer stops where vanilla's arrival leaves it, two
  blocks from its post, and its slide decides the hundredths. The test now asks that its whole body be
  past the gate block.
- **Mistakes in the new tests, not the code:** three tests put a Work Post on a crop of their own
  field, so that crop could never be taken; one ended a shift and looked 5 ticks later, though vanilla
  reads a villager's schedule only every 20 ticks (it now waits for the shift to stop and checks that
  same tick); tests that move the clock stand alone in their batches, since a shift ended for one
  ends every farmer's.

## Booth: 125 checks, 59 photographs

The 110 checks and 55 photographs of 0.8.0, and a farm of nine radius-4 posts (3 by 3, each over a
water source, outlines on), ripe wheat but for a 2 by 2 block of young wheat in the south-east with a
4 by 4 patch bare in its middle, one chest of five hoes and 32 wheat seeds, five farmers on five posts:

| Photo | Shows | Checks in code |
|---|---|---|
| `58-sowing-before` | from above: the bare brown patch in young wheat, four posts over their water | the client sees the patch's 16 spots bare |
| `56-farm-shared` | the farm from the south, over its beds: five farmers out in the ripe cells, each with a harvested strip behind it; the young block and its patch | the server counts five farmers each holding a plot of its own, 300 ticks after they first all did |
| `57-farm-post-screen` | the central post's screen: Farming, radius 4, one farmer, "Farm: 9 posts, 5 farmers" | the screen's view says 9 posts and 5 farmers |
| `59-sowing-after` | the patch sown, its sprouts against the taller young wheat, the two sowers' hats on it | the client sees wheat on all 16 spots |

`sow_after_seconds` is 2 in the booth (it can't wait a minute a spot), and the camera is a spectator.
**Judged by eye:** the first staging photographed the farmers bunched at the barn chest, the moment they
had taken their hoes and plots, from too far to see them; and its patch, at the corner of the young
wheat beside ripe wheat being harvested, looked like any harvested ground once sown. The farm photo now
waits 300 ticks and is nearer, and the patch sits in the middle of young wheat nobody harvests. The
sprouts of freshly sown wheat are small: the after photo shows the change, but not loudly.

Two runs failed first: the camera, teleported 22 blocks up in survival, fell to its death, and with no
living player the farm's chunks stopped ticking, every farmer frozen mid-walk (found in three short
runs of the scene alone, `-PboothScene=farm -PboothTrace`, after a GameTest copy of the farm with hunger
and the market on had all five at work by tick 200).

## Mutation pass: 25 of 25 caught

`uv run --no-project python devtools/verification/mutate-0.9.0.py domain-each | a | b | c | d | e | f | g
| h | i`: the domain mutations one JUnit run each, the GameTest ones in batches aimed at disjoint tests,
three alone because they change how every farmer moves.

| Mutation | Caught by |
|---|---|
| D1 areas one block apart link | `FarmTest` |
| D2 another owner's post links | `FarmTest` |
| D3 its own area not first | `FarmTest` |
| D4 a hold never lapses | `HoldsTest` |
| D5 another's live hold is taken | `HoldsTest` |
| D6 no wait before sowing | `SowingTest` |
| D7 a spot copies where kinds meet | `SowingTest` |
| D8 stems are copied | `SowingTest` |
| D9 what a spot grew is forgotten | `SowingTest` |
| G1 farmers ignore holds (alone) | two farmers at one field |
| G2 woodcutters ignore holds (second pass, alone) | two woodcutters |
| G3 a lost hold is never noticed | the frozen farmer |
| G4 the dead keep their plot | the farmer killed |
| G5 a shift that stops keeps its plot | the shift ended |
| G6 a farm of one (alone) | helping in a touching post's area |
| G7 the harvest only to its own post | the nearest post's chest |
| G8 no seed fetched | seed from the farm's chest |
| G9 a post is open ground (alone) | its own area first |
| G10 its own area not first | its own area first |
| G11 no wait before sowing | the gap after the wait |
| G12 what a spot grew is forgotten (second pass, alone) | the carrot among wheat |
| G13 a spot copies where kinds meet | where kinds meet |
| G14 stems are copied | among stems |
| G15 areas one block apart link | helping in a touching post's area |
| G16 another owner's post links | a gap or another owner |

- **G1 and G2 first got past their tests:** without holds the second worker is refused the plot or
  tree at its first step and waits, so the two never share one, and both tests only asked that they
  never do. Both now also ask that both workers hold a place within 60 ticks of the first; G1 was
  caught that way in the first pass, G2 in the second.
- **G12 was masked by G13 in batch `f`:** with both, the broken carrot's spot was sown with the last
  kind found, here the carrot. Alone, it is caught.
- **A gate mistake, caught before it misled:** after the first pass a test edit failed to compile, and
  three runs reported batch `g`'s old log as their own (the five failures it had then). The script now
  deletes the log before a run and refuses one that did not compile.
- Batch `g` also failed 0.7.0's `aTownsmanBuysAFreeFarmersSpareBreadRatherThanADearerStalls`, which no
  mutation of the batch touches; it passed in every clean run (seven).
