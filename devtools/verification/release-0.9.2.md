# Serfdom 0.9.2 (every villager's navigation floats): verification

Run on 2026-10-06. 0.9.2 is 0.9.1 (built and gated, unreleased; `release-0.9.1.md`) with D-0010:
the navigation Serfdom gives every villager sets `setCanFloat(true)`, as the vanilla one it replaces
had it, so villagers path through water at the surface again. It replaces 0.9.1, which was never
released, and ships in its place.

`./gradlew clean build` with the booth on Xephyr `:7` (the Gradle daemon stopped first): `BUILD
SUCCESSFUL` in 5 m 16 s. The jar `serfdom-0.9.2.jar` has sha1
`5aad8c4a52ad72d361ba8ff19ed9d1ee96f955f5`, with Carried 1.0.0 nested. No payload changed: the
network version stays "7".

## The defect, reproduced before the fix

The four new GameTests were written and run on 0.9.1's code (`./gradlew runGameTestServer`, 2 of 118
failed):

- `aVillagersNavigationFloats`: "a free villager's navigation floats, as vanilla's does" failed.
- `aWorkerSwimsADeepChannelToItsOwner`: after 600 ticks the worker was at z 18.5 of the channel's 14 to
  18, against the north bank, at y 2.5 (the surface of water two deep), in the water: it never climbed
  out.

The other two, `aWorkerWalksRoundAFarmsWaterHole` and `aWorkerClimbsOutOfAFarmsWaterHole`, passed on
0.9.1 too: a one-deep hole traps nobody. They stay as guards. Since that run is 0.9.2 with the fix
taken out, it is also the mutation: the fix's one line removed, both tests that guard it fail.

## JUnit: 319 tests, 0 failures

Unchanged: nothing in the domain changed.

## GameTests: 118 passed

The 114 of 0.9.1 and, in `WorkerGameTests`:

- **A deep channel:** banks two high on both sides of a channel five wide and two deep across the whole
  yard; a worker with no bed on the south bank follows its owner on the north bank, and stands on the
  north bank out of the water.
- **A farm's water hole on the way:** a field of farmland with a hole of water one deep, flush with it,
  on the straight line between a worker and its owner; the worker reaches its owner without ever being
  in water.
- **Out of the hole:** a worker standing in that hole climbs out and reaches its owner off the field.
- **Floating:** a free villager's navigation and a worker's both read `canFloat()`.

Five runs in a row passed (118 of 118 each): two by hand, the gate's, and three more.

## Booth: 125 checks, 59 photographs

Unchanged from 0.9.0, all passing.
