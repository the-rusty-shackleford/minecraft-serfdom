# Serfdom 0.3.0 (phase 2a, the capture): verification

Run on 2026-10-04. 0.3.0 is 0.2.0 (phase 1, never released; its record is `release-0.2.0.md`)
with phase 2a (D-0003): taking villagers with the chain, the law's remedy, captives in the
trailer, the captive's day, escape, the work song, owned children, freeing.

`./gradlew clean build`: `BUILD SUCCESSFUL`, the jar `serfdom-0.3.0.jar` (sha1
`0a140435cc1a9f7fe4f00a17722cbbcef9205409`, Carried 1.0.0 nested). The booth ran on Xephyr `:7`
with software rendering. It ships with two siblings, each gated in its own repo the same evening:

| Mod | Jar sha1 | Gate |
|---|---|---|
| Vanilla Wheels 1.11.0 (cargo rules, its D-0029) | `fbc86b18a60b3722835917338e5af62a454ad7ba` | 124 JUnit, 91 GameTests, booth 47 checks |
| Village Law 1.1.0 (`api/Cases.openHere`) | `0c244139be72d884ef2c8401a6381beba149eef6` | 38 JUnit, 11 GameTests, booth 19 checks |

## JUnit: 160 tests, 0 failures

The 118 of 0.2.0, with `WorkDayTest` extended (the captive's day), plus:

| Suite | Tests | What it covers |
|---|---|---|
| `ChainTest` | 10 | every valid combination of villager (free; owned and loose; cuffed and held by nobody, the player or another) × owner or not × hand (empty, chain, other) × sneaking: 54, each one act; facts that cannot be are refused |
| `CaptureTest` | 7 | the verdict over all sixteen combinations and its order; the hold before, at and after its time; each failure at the last tick; their order; the reach at, inside and past its bound |
| `RemedyTest` | 8 | paying frees exactly that case's captives (two players, two villages); fleeing forgets, so a later debt frees nothing; a capture after fleeing is owed to the next payment; a captive owed to one case only; forgetting; bad ledgers refused; the ledger as a value |
| `EscapeTest` | 9 | due at the window's first and last tick and just outside; a day rolled; awake; negative times; chance 0 and 1; the same roll for the same night; about 5% of 40000 nights for two captives; home inside, on the edge and outside a village from above, at any height, another dimension; near where it was taken; the goal |
| `CaptiveRulesTest` | 6 | a child's owner; the captive's pace with and without the bonus; bad slowdowns; the song's pitches, every phrase in range ending on the tonic, cues back to back; when a phrase may begin |

## GameTests: 47 passed

The 32 of 0.2.0 (the 1a chain test rewritten for the cuffs: a snap no longer drops the chain)
and 15 in `CaptiveGameTests`. All run with Village Deed 2.2.0, Thief 1.2.4, Village Law 1.1.0,
Guard Villagers 2.4.11, Vanilla Wheels 1.11.0, Trailer 2.4.0, Farmer's Delight 1.3.3, Backpacks+
0.7.0, Ranged Weapons Mod 2.12.0 and Metals and Materials 1.0.3 loaded. The law's tests stand in a
village: Village Law's hut fixture, a structure tagged as a village and as Thief-protected, with a
Guard Villagers guard inside.

- **The hold.** Let go at one second: free, no chain used. Held two seconds: the taker's captive,
  in chains, on their chain, one chain used, walking at 0.9, a held villager's day, where it was
  taken kept. Then the use key still held: a repeated use with the chain, and one empty-handed,
  leave the cuffs on; let go, a use takes them off and the chain comes back.
- **Refusals.** A nitwit and a child; three blocks off; looking away.
- **The chain.** A snap past ten blocks leaves it standing in its cuffs, nothing dropped; a passer
  takes the loose chain and lets go, and cannot take the cuffs off; in chains it does not trade
  (the use stops before normal priority); a chain someone else holds is not broken; the owner takes
  the cuffs off and the chain comes back.
- **The law.** A guard saw the capture: Thief's heavy crime, a case with the hut's village, the
  captive owed to it, and the guard never sets on the taker; paying 15 of 20 emeralds frees it,
  chains off, chain not given back. Leaving: banished, the case forgets it, it stays taken; the
  debt paid afterwards frees nothing. Only villagers saw: the crime and a bystander's −150, no case.
  Outside the village's structure: no crime. A bought village: the owner takes freely with a guard
  watching (no crime, no case), a stranger's chain is refused.
- **The captive's life.** At 9000 the hired worker meets and the captive at the same post works;
  idle in place, asleep at night; a captive farmer farms at 1.125, a hired one at 1.25.
- **Golems and cats.** Five owned villagers asleep lately in five beds: hired, each wants a golem
  and their beds bring a cat (vanilla's `CatSpawner`, called); made captives, none wants one and
  the beds bring none.
- **A child** of two of one owner's workers is the owner's, hired, keeps the bed it is born into
  (vanilla's `giveBedToChild`, called) and takes no post; a child across owners is free.
- **Turned zombie** in chains with an axe and logs: the axe, the logs and the chain drop, and the
  zombie villager is nobody's.
- **Set free** from the screen: free, chains off, the owner's chain back, a free villager's pace.
- **The work song.** A captive at work for 6000 ticks sings whole phrases, its first notes a
  phrase's cues in order; a hired worker and a captive only walking sing nothing.
- **Escape.** With the chance at 1, two captives asleep at midnight roll, get up when the roll
  said, and walk home; one is free inside its village (judged where it stood on its last tick as a
  captive); the other is cuffed by a passer on the way and stays a captive.
- **The trailer.** Five captives at the open doors: an empty-handed click boards the four nearest,
  in chains with nobody holding them; the fifth stays on its chain; aboard, each box lies inside
  the trailer's body and under its roof, and still after it turns 45 degrees; a crouch with a
  chain lets them all out, still in chains.

## Flakes found on the way

- **The flee test failed once in three runs:** no case opened. The guard stands in a hut with a
  one-high wall in an arena of open air, and a guard that wanders over the wall falls out of sight
  of the crime. The wall is now three high, and the witness checks say where the guard stood,
  how far off and whether it had line of sight. Five looped runs then passed, and three gate runs.
- **The escape test failed once by a block:** it read the runner's position a tick after it went
  free, after the free villager had walked on. It now records where the runner stood on its last
  tick as a captive, which is where its walk home was judged.

## Booth: 42 checks, 17 photographs

| Photo | Shows | Checks in code |
|---|---|---|
| `01` to `12` | as 0.2.0; `05` now a worker in chains on the chain lead, the cuffs across its arms | `05`: the client sees it in chains and on the chain |
| `13-capture-hold` | the chain held on a farmer: its head shakes, "Taking Farmer. Hold on..." | the server holds; the client draws the chain |
| `14-captive-cuffed` | the farmer in chains, the cuffs across its crossed forearms, the chain to the player's hand, its "no bed" icon, "Farmer is yours now, in chains." | the server's captive; the client sees the cuffs and the chain |
| `15-captive-screen` | its Worker Screen: Status "In chains" in amber, Set free on a second row | status CUFFED; Set free live |
| `16-trailer-rear` | the trailer's open doors with captives standing inside, facing forward | the client sees four aboard |
| `17-trailer-side` | the trailer from its side: nothing through the walls or roof | — |

Judged by eye, at 3× crops:
- **The cuffs** read as a short length of chain lying across the forearms (vanilla's chain block
  on its side, half size, no art of ours).
- **In the trailer** the farmer's hat brim clears the side wall and every head clears the roof.
- **The first `13`** drew the chain like a bow: the bow's use animation pulled the item into the
  middle of the view as a grey block, and the camera was so steep it cut off the villager's head.
  The use has no animation now, the hold is told on the action bar, and the camera is level.

**The booth found a bug the GameTests had not.** After the capture, a player still holding the use
key has the client repeat the use every four ticks, and the first repeat was the owner's chain
gesture: the cuffs came straight off. A test's player never repeats, so no GameTest saw it.
`Captures.settling` now swallows the taker's uses on the captive until the key has been let go
for ten ticks, and the hold test makes those repeats.

## Mutation pass: 22 of 22 caught

`uv run --no-project python devtools/verification/mutate-0.3.0.py domain-each | a | b | c`. Each
domain mutation ran alone; the GameTest mutations ran in batches, each mutation in a batch aimed
at a different test, the catch read off the failed test's name. Files are restored from memory.

| Mutation | Caught by |
|---|---|
| D1 a non-owner takes the cuffs off | `ChainTest` |
| D2 looking away ignored | `CaptureTest` |
| D3 fleeing keeps the debt | `RemedyTest` |
| D4 a night rolled twice | `EscapeTest` |
| D5 a captive meets | `WorkDayTest` |
| D6 a captive works at full pace | `CaptiveRulesTest` |
| D7 a child of two owners is owned | `CaptiveRulesTest` |
| G1 a snapped chain drops | the chains stay on (and the 1a chain test) |
| G2 captives want golems | golems and cats |
| G3 a capture owed to no case | a guard saw it |
| G4 nobody gets up | escape |
| G5 the reach ignored | refusals |
| G6 hired workers hum | the work song |
| G7 a converted worker drops nothing | turned zombie |
| G8 captives' beds bring cats | golems and cats |
| G9 paying frees nobody | a guard saw it |
| G10 captives are no cargo | the trailer |
| G11 a stranger may take a bought village's people | a bought village |
| G12 no child is owned | a child |
| G13 set free keeps the owner's chain | set free |
| G14 a captive works through no meeting | the captive's life |
| G15 a held key's repeat takes the cuffs off | the hold |

The siblings' new rules were mutated too: Vanilla Wheels' empty-hand load, its unload with
another rule's lead, and cargo that is not an animal (3 of 3 caught by its two new GameTests);
Village Law's `openHere` never open and always open (2 of 2 caught by the four law tests that
read it).

## Audio

`uv run --no-project --with numpy python devtools/sound/work_song.py` renders the four phrases to
`run/work_song.wav` (40 s, 26 notes) from vanilla's villager `idle2`, as the game plays it. Each
note's settled pitch was measured in the render against 95 Hz times its rate: every one within
2 Hz. Not heard by anyone yet.

## Not verified

- **Play on the box:** no capture, summons, escape or trailer load has happened on the server.
- **The song** has been measured, not listened to; Rusty hears it first.
- **The hold's first-person look** is judged on software rendering only.
- **Long escapes** across unloaded chunks: an escapee stops at the edge of what is loaded and
  goes on when it is loaded again; no test walks it that far.
- **A remedy for a captive not loaded** when the case is paid is freed as it next loads
  (`Remedies.freeOnLoad`); no test unloads one.
