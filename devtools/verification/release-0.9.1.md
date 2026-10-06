# Serfdom 0.9.1 (no structure search while sampling the price lists): verification

Run on 2026-10-06. 0.9.1 is 0.9.0 (built and gated, unreleased; `release-0.9.0.md`) with D-0009: while
the base values sample every profession's price list, a map's structure search finds nothing, so a map
listing gives no offer before it searches the world or saves a map. It replaces 0.9.0, which was never
released, and ships in its place.

`./gradlew clean build` with the booth on Xephyr `:7` (the Gradle daemon stopped first, so the build's
environment was the Xephyr recipe's): `BUILD SUCCESSFUL` in 5 m 24 s. The jar `serfdom-0.9.1.jar` has
sha1 `eb6ec2a8230417e964f5c44f79519b9767a0b505`, with Carried 1.0.0 nested. No payload changed: the
network version stays "7", so 0.9.0 and 0.9.1 clients and servers accept each other.

## The defect, measured before the fix

On a NeoForge 21.1.248 server on the desktop, with the box's 99 mod jars copied off it (every sha1
matched against the box), its `config`, `villagerpacks` and `moonlight-global-datapacks`, and a new
world (no RCON, another port), Serfdom 0.9.0 with timing lines added (never committed):

| Part | Time |
|---|---|
| The whole build | 10347 ms |
| Sampling, 31 professions, 502 listings x 3 seeds | 10335 ms |
| of which Backport 1.0.9's `ExplorerMapForEmeralds`, cartographer, 9 calls | 10183 ms |
| the next slowest listing (fisherman's enchanted item) | 6 ms |
| Making the 31 villagers | 117 ms |
| Food | 7 ms |
| `Values.table` | 4 ms |

The world then held `map_0.dat` to `map_8.dat`, all saved in one second, though nobody had joined. The
box's own log line for the same build was 2543 ms; the box's world has been explored, where the local
one was new (an inference, not measured).

On the box: maps 226 to 234 were saved at 02:27:23 on 2026-10-06, the first autosave after the 1.73.0
start at 02:22; their sizes are the local nine's, in the same order; `idcounts.dat` has not changed
since. Left in place (D-0009).

## After the fix, on the same server

The same mods and a new world, with `serfdom-0.9.1.jar`:

| | 0.9.0 | 0.9.1 |
|---|---|---|
| Base values worked out in | 10347 ms | 245 ms |
| Items valued, from the price lists | 501, 383 | 501, 383 |
| Map searches refused | (none refused) | 42 |
| Maps in the world after | 9 | 0 |

No "Can't keep up" in the two minutes after `Done`.

## JUnit: 319 tests, 0 failures

Unchanged from 0.9.0: nothing in the domain changed.

## GameTests: 114 passed

The 113 of 0.9.0 and `theBaseValuesSearchForNoStructure` (batch `trade`): every
`TreasureMapForEmeralds` listing on the server's price lists counted (three, the cartographer's),
`Prices.build`, then 9 searches refused (three seeds each), no map id taken between two
`getFreeMapId` calls around the build, a village search after the build not refused, and bread's value
still a sixth of an emerald. On the GameTest server the build refuses 9 searches and takes 125 ms at
start.

The GameTest world makes no structures (`GameTestServer`'s `WorldOptions(0, false, false)`), so there a
search finds nothing with or without the guard. What the test pins is the refusals; that a refusal
spares the search and the map is the local server's before and after, above.

## Booth: 125 checks, 59 photographs

Unchanged from 0.9.0, all passing.

## Mutation pass: 4 of 4 caught

`uv run --no-project python devtools/verification/mutate-0.9.1.py a b c d`, each alone, since all four
aim at the one test:

| Mutation | Caught by |
|---|---|
| M1 the guard never refuses | `theBaseValuesSearchForNoStructure` (the only failure) |
| M2 sampling never marks its thread | the same |
| M3 the mark outlives the sampling | the same (the village search after the build is refused) |
| M4 the mixin is not applied | the same |

Not a mutation the GameTest server can catch: the guard counting a search but letting it run, since its
world has no structures to find. The before and after on the box's mods covers it.
