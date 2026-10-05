# Serfdom 0.5.0 (phase 3, hunger and meals): verification

Run on 2026-10-05. 0.5.0 is 0.4.0 (phases 1, 2a and 2b, never released; its record is
`release-0.4.0.md`) with phase 3 (D-0005): hunger, breakfast and dinner, the canteen, cooking a
meal, and the hungry icon.

`./gradlew clean build`: `BUILD SUCCESSFUL`. The jar `serfdom-0.5.0.jar` has sha1
`2284b164b0af74f6a4427153dc8f071f40314151`, with Carried 1.0.0 nested. The booth ran on Xephyr `:7`
with software rendering. Phase 3 needs no change to the two siblings.

## JUnit: 213 tests, 0 failures

The 187 of 0.4.0, plus:

| Suite | Tests | What it covers |
|---|---|---|
| `HungerTest` | 10 | points in range, out of it and NaN refused. Draining awake and asleep, by the rate, nothing, held at 0, bad arguments refused. Eating to the cap. Wanting a bite: nothing that fills, full, one that fits, overshooting by exactly half and by more. The half and starved boundaries. Work speed at, between and past its bounds, against the floor, bad floors refused. The pace it stacks into. The drumsticks. The hungry icon after no bed and before the rest. The day the defaults make: dinner to breakfast to dinner, fed throughout; dinner missed, hungry at waking. |
| `MealsTest` | 6 | every window boundary, a later day and a negative time; once per window, yesterday's window, full; between meals only when hungry; never asleep, in chains, on its way home, without a bed; the wait at 1 tick short and at its end; the times after a meal eaten in a window, out of one, and not eaten |
| `MenuTest` | 10 | what is edible, raw and ready. Home first, most plentiful, a tie to what fills more, never what is refused, ready before cooking. The nearest post, the canteen before cooking. The dish that fills most, a pot's stew. Crafts to fill, capped at four and by stock, never past the overshoot. Only table meals at a table. Ingredients only from home. Raw only when no free station's recipe takes it. Nothing the worker wants. A choice's rep invariant. |

## GameTests: 62 passed

The 53 of 0.4.0 and 9 in `MealGameTests`. The meal batches hold their hours with hunger on; every
other batch runs with hunger off (`Yard.hour`), because they hold hours inside the breakfast window
and their workers would eat the stock the cook tests count.

- **Home chest:** at breakfast a worker at 6 with ten loaves at home eats three, to full. Breakfast
  is noted, the hungry icon goes, and its idle hour is back.
- **The canteen:** at dinner, with an empty home chest, a worker at 9 eats one steak from a cooking
  post's chest twenty blocks from its bed (a second would overshoot).
- **Cooking:** at breakfast, with six raw beef and two coal at home, a worker at 4 cooks two steaks
  at the free smoker and eats both.
  - Four beef and one coal are left, the smoker is left empty, and nothing cooked is left over.
  - The nearer smoker, holding a player's porkchop, is untouched.
- **A pot meal:** at dinner, with mushrooms and bowls at home, a worker at 9 cooks two mushroom
  stews in Farmer's Delight's pot on a lit campfire, eats both, and puts both bowls back.
- **Breaking off:** a woodcutter at 9 mid-shift turns to its meal, eats bread at home, and is back
  at work.
- **Slower:** a farmer at 5 works at exactly three quarters of a fed farmer's pace (`Jobs.speed`).
- **Starved:** a woodcutter at 0 with nothing to eat, after 300 ticks:
  - the oak stands, no logs are in, and the axe was never fetched;
  - the hungry icon shows, and its health is whole.

  With bread put in its home chest, it eats at its next look.
- **Captives:** at breakfast a captive at 8 eats from its home chest. A captive in chains at 8,
  with bread at home, never sits down to a meal (its meal times stay untouched) and grows hungrier.
- **Children and the screen:** an owned child doesn't hunger, draining it changes nothing, and its
  screen says so. A grown worker at 13.5 shows 14 half drumsticks.

Three runs of the GameTests in a row passed before the gate.

## Booth: 67 checks, 36 photographs

| Photo | Shows | Checks in code |
|---|---|---|
| `01-post-and-needs` | now seven workers, 1.7 apart, each with one need; the second, the new empty bowl: hungry | the client sees each worker's need, in the new order |
| `34-eating` | a farmer with a loaf in its crossed arms by its chest; both workers under the hungry bowl | the client sees the bread in its hand |
| `35-cooking` | the butcher by its lit smoker, smoke rising, while the farmer eats beside its chest, crumbs falling | the client sees the smoker lit and the butcher within 3 blocks, photographed 10 ticks later |
| `36-worker-screen-hunger` | the farmer's Worker Screen with a Hunger row of seven full drumsticks and three empty | the view says 14 half drumsticks |

**Judged by eye:**
- The bowl icon reads as an empty bowl beside the crate and the chest, by its tapered shape.
- The loaf sits in the crossed arms as the cook's food does.
- The smoker shows its fire on the front once the world has redrawn it.
- The drumsticks are vanilla's HUD sprites at their own size.

**Found on the way:**
- **Photo 01's seventh worker fell off the frame** at the old spacing of 2. At 1.7 apart all seven
  are in view and inside the icons' eight blocks.
- **The first eating shot showed the farmer's back:** it faced its chest, away from the camera.
  The chests now stand between the camera and the workers.
- **The first cooking shot showed an unlit smoker:** the photo was taken the tick the client
  learned it was lit, before the world's mesh was redrawn. It now waits 10 ticks.
- **The bed was "not a bed" in the booth:** vanilla registers a bed as a home in a task of its own.
  The booth places beds a few ticks before it assigns them.
- **One gate run failed at the screen:** the fed farmer wandered beyond eight blocks of the camera,
  and the server shut its screen, as it should. The booth now stills it and stands the player
  beside it.

## Mutation pass: 20 of 20 caught

`uv run --no-project python devtools/verification/mutate-0.5.0.py domain-each | a | b | c | d`.

| Mutation | Caught by |
|---|---|
| D1 asleep drains as fast as awake | `HungerTest` |
| D2 any bite however it overshoots | `HungerTest`, `MenuTest` |
| D3 hunger never slows work | `HungerTest` |
| D4 a window eaten twice | `MealsTest` |
| D5 a sleeper eats | `MealsTest` |
| D6 no wait between meals | `MealsTest` |
| D7 home's food passed over | `MenuTest` |
| D8 refused food eaten | `MenuTest` |
| D9 any table recipe a meal | `MenuTest` |
| D10 hungry shown after a tool | `HungerTest` |
| G1 no meal is ever due | seven meal tests |
| G2 no canteen | the canteen |
| G3 a smoker emptied before it cooks | cooking |
| G4 the bowls kept | the pot meal |
| G5 hunger never slows work | slower |
| G6 a starved worker works on | starved, after the fix below |
| G7 chains stop no meal | captives, after the fix below |
| G8 children hunger | children and the screen |
| G9 the meal keeps the day | home chest, breaking off |
| G10 the hungry icon never shows | home chest, starved |

**Two got past the tests the first time.**
- **G6:** a woodcutter fells top first and brings its logs in at the end, so "the oak stands, no
  logs in" held after 300 ticks even while it worked. The test now also checks that the axe never
  left its chest.
- **G7:** a worker in chains is refused again by the meal itself, so the mutated check changed
  nothing anyone could see except a meal sat down to and left at once. The test now checks that its
  meal times stay untouched.

Both were caught when rerun (batch `d`).

**The cooking test found a bug before the mutation pass.** 1b's station collect takes a furnace's
input back out when its output is empty (meant for a stalled load). Polled from the start, it
emptied the beef out of the smoker 20 ticks in. A furnace is now emptied only once it is done or
stalled.

## Not verified

- **Play on the box:** no worker has eaten on the server.
- **A day at full length:** the tests hold the hour, so the windows and the drain rate are checked
  apart (`HungerTest`'s day, the GameTests' meals), never through a whole running day.
- **A dish left cooking** when the meal is cut short (night, chains) stays in the station; no test
  cuts a meal short mid-cook.
- **Rusty's shaders and GPU:** the photographs are software-rendered.
- **Rusty has not seen** photos 34 to 36 or the bowl icon.
