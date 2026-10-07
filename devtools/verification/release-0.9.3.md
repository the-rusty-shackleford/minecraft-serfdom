# Serfdom 0.9.3 (a villager grips its tool, arms out of the fold): verification

Run on 2026-10-06. 0.9.3 is 0.9.2 (built and gated, unreleased; `release-0.9.2.md`) with D-0011: a
villager holding a tool or a weapon has its arms out of the fold and grips it, swings it as it works or
strikes, and a defender aims its bow, crossbow or gun; vanilla never played a villager's swing, and now
does. It replaces 0.9.2, which was never released, and ships in its place.

`./gradlew clean build` with the booth on Xephyr `:7` (the recipe's environment on the command line;
the Gradle daemon was not stopped first, since another session was building beside it): `BUILD
SUCCESSFUL` in 5 m 45 s. The jar `serfdom-0.9.3.jar` had sha1
`f086a349abe7ff5a2eb90b5648cb70234be9cd82`, with Carried 1.0.0 nested. No payload changed: the network
version stays "7".

After the gate, `build.gradle` (a GameTest dependency) and the booth (a log line) changed, below; the jar
rebuilt from that tree had the same sha1. That was commit e79ee35.

**Then Rusty's colours** (below, "After the photographs"): the gate again, `BUILD SUCCESSFUL` in 5 m
36 s, JUnit run (329, 0 failures), 120 GameTests, booth 135 checks with 67 photographs. **The jar
`serfdom-0.9.3.jar` now has sha1 `92ca8cfdc40586ec7bb258c072b70b1fff589a2c`.**

## JUnit: 329 tests, 0 failures

The gate's `test` task came from Gradle's build cache, so it was run again with `./gradlew test
--rerun`: 329 tests, 0 failures. New: `GripTest`, 10 tests of `domain/Grip`: the hold is the weapon's
kind else a tool; a tool at rest, the walking swing, the blow, a bow drawn and a crossbow or gun held
while fighting (carried as a tool otherwise), the bob; nothing held, or a moment out of range, refused.

## GameTests: 120 passed

The 118 of 0.9.2 and two new:

- `aVillagersSwingPlaysAndEnds`: a free villager swung once is part way through the blow three ticks
  on and done ten ticks after that. Before 0.9.3 its swing stood at its start for ever.
- `aDefenderChainedMidFightIsNoLongerAggressive`: a defender fighting an invulnerable vindicator is
  aggressive; chained mid-fight, it stops defending and is no longer aggressive the same tick.

Two old tests check more: the woodcutter's oak (a swing a log at the least) and the farmer's harvest (a
swing at each crop and the pumpkin) count swings started; the swordsman was aggressive while it fought
and is not once the raid is over.

Again after the `build.gradle` change below: 120 of 120.

## Booth: 135 checks, 67 photographs

125 checks and 59 photographs as in 0.9.2, plus the arms scene (photos 60 to 67, ten checks): a row of
six tools from the front, close, from the side and from behind, beside a farmer whose bread stays on
folded arms; a captive gripping its hoe and a bow, a crossbow and a gun aimed; the captive's manacled
wrists; a blow mid-swing; a walk. The checks are what the client sees: every one of the row gripping,
the bread not, the three aimed (`isAggressive` and gripping), the captive cuffed and gripping, and the
swing part way through.

**Under Fresh Animations:** `./gradlew runPhotoBooth -PboothLook=fresh -PboothScene=arms` (Entity Model
Features 3.2.4 and Entity Texture Features in the booth's mods, the pack's Fresh Animations 1.10.4 on),
on the code committed: 10 checks, all passing. The photos are in `run/booth/screenshots-fresh/`: the
arms lean and bob with Fresh Animations' body, and its folded arms hide like vanilla's.

## Mutation pass: 5 of 5 caught

`uv run --no-project python devtools/verification/mutate-0.9.3.py a b c`; every mutated file was
checked against a sha1 taken before the pass, and all were restored.

| Mutation | Caught by |
|---|---|
| M1 a villager's swing never moves | `aVillagersSwingPlaysAndEnds`, the woodcutter's oak (and the farmer's harvest) |
| M2 no blow while chopping | the woodcutter's oak |
| M3 no blow at a harvest | the farmer's harvest |
| M4 a defender is never aggressive | the swordsman (and the chained defender) |
| M5 aggression kept when the defence stops | `aDefenderChainedMidFightIsNoLongerAggressive` |

M5 got past the swordsman's test on its first pass: a defence that ends by itself clears the flag in its
own tick first, so only one stopped from outside (a capture rebuilds the brain) reaches `stop` with the
flag still set. The chained defender's test was written for it.

The pass's batch c also failed `hiringTakesTheFeeAndMakesAFollower` ("Backpacks+'s expedition bag is
registered"), which M5 does not touch: see the Backpacks+ jar below.

## What was found

- **The GameTests' Backpacks+ jar went missing.** `build.gradle` put
  `../minecraft-backpacks-plus/build/libs/backpacksplus-0.7.0.jar` on the GameTest server by name; a
  clean build of Backpacks+ 0.7.1 in its repo, at 19:49:56 during the mutation pass, replaced it, and
  Gradle drops a missing file from a classpath without a word. The hiring test's check of the bag is what
  caught it. The pin is now 0.7.1, the version the next release carries.
- **Four of the gate's photographs show the camera turned, though every check passed.** Photo 23 (the
  armour close-up) and photos 39 to 41 (the market) are grass seen straight down, a texel the same size
  from top to bottom of the frame (23 turned sideways as well), until the scene's next teleport put the
  camera back (photo 42 is right). The booth ran again with the camera's place and turn logged at every
  photograph (now in `photo`, both the client's and the server's): at 39 to 41 the player stood at the
  scene's spot, yaw 180, pitch 16, on both sides, and the photographs show the market as they should
  (the horizon at rows 214, 234, 234, 234 and 271 for pitches 18, 16, 16, 16 and 12). The likeliest cause
  is a pointer moving over the Xephyr window, which turns a player whose mouse is grabbed; it was not
  reproduced. The wiki's images are made from the second run.
- **A beige patch on the robe at the belt** of a villager with its arms out is vanilla's own level
  badge (`profession_level/iron.png`, an apprentice's, 2 by 3 pixels on the robe's front), which the
  folded arms half hid: a farmer with bread on folded arms shows its top edge under them.
- **The wiki's ledger image cut its last line:** the stall now lists four visits, and the crop stopped at
  three. It is the whole panel now.

## After the photographs: an iron ingot's greys

Rusty, on photo 65: the manacles' thickness is fine, the colour "way too dark, it should be the same
color as an iron ingot. Same with the chain lead"; and, asked, the chain across folded arms too ("all
ingot grey"). D-0011 has it. Changed, all client art:

- **The manacles:** a texture of ours (`manacles` in `devtools/art/art.py`), the ingot's #d8d8d8 with a
  white edge and a #a8a8a8 foot, in place of the anvil's (its greys average less than half the ingot's).
- **The chain lead:** its item drawn in the ingot's greys; the links strung to the villager
  (`ChainLook`) #d8d8d8 and #a8a8a8, where they were #a8adb8 and #4d4f57.
- **The chain across folded arms:** vanilla's chain model whole (`models/block/cuff_chain.json` takes
  `minecraft:block/chain` as its parent), with a texture of ours (`cuff_chain`): three-wide links in the
  two strips the model draws, on the rhythm vanilla's links keep so the planes alternate. `CuffsLayer`
  turns it as the chain block's blockstate does for axis x (BlockModelRotation's
  `rotateYXZ(-90°, -90°, 0)`).

Judged in the gate's photographs against the run before, at three times: photo 05 (the lead strung to
a captive, light grey, its links still alternating close up), 13 (the lead in hand), 14 (the chain on
folded arms, in the place and turn vanilla's had), 64 and 65 (the manacles, light bands at both
wrists), and 65 again under Fresh Animations (`-PboothLook=fresh -PboothScene=arms`, 10 checks; the set
in `run/booth/screenshots-fresh/` is retaken). Every photograph of the gate framed as the run before
(no turned camera). The mutation pass was not rerun: nothing it mutates changed.

At a distance the lead's two greys read as one light line more than as links; put to Rusty with the
ingot's shadow grey (#727272) offered for the darker link, he passed the photographs as they are
("looks good").

## Not seen

Nobody has seen the greys in play.
