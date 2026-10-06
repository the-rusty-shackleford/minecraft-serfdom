# Serfdom 0.7.0 (phase 4b, the market's second half): verification

Run on 2026-10-05. 0.7.0 is 0.6.0 (phases 1 to 3 and 4a, never released; its record is
`release-0.6.0.md`) with phase 4b (D-0006): climate, taste, wants, window shopping, and villagers
selling to villagers. Built without a new preview, on Rusty's word; D-0006's "Settled in the build: 4b"
holds the one reading of the rule that did not hold as written, and the details the rule left open.

`./gradlew clean build` with the booth on Xephyr `:7` (the Gradle daemon stopped first, so the build's
environment was the Xephyr recipe's): `BUILD SUCCESSFUL`. The jar `serfdom-0.7.0.jar` has sha1
`c9ad1f266d320696dbff57996bc00e3758855395`, with Carried 1.0.0 nested. Phase 4b needs no change to the
two siblings (Vanilla Wheels 1.11.0, Village Law 1.1.0).

## JUnit: 274 tests, 0 failures

The 257 of 0.6.0, plus:

| Suite | Tests | What it covers |
|---|---|---|
| `ClimateTest` | 2 | a biome hot, cold, both, neither; an item untagged, from here, from elsewhere, tagged both, in each climate's village; another bonus; a bonus of none or NaN refused |
| `TasteTest` | 6 | the draw: one UUID twice, two a bit apart, four categories, the range over 2000 villagers, two draws pinned so a taste never changes; the lean at u 0, inside, near 1, at -1, 0, +1, refused outside its range, the mean of 4000 villagers by lean; the favourite, a tie; likes above, at, below 1; an item of no kind, one, two; wants with no value, no kind, a kind disliked, one of several liked, has none, some, the most, more, most 0; multipliers that break the invariant |
| `PeddlerTest` | 3 | a lot for no value, an emerald, more (rounded both ways, past 64), less (bread's sixth, rounded up, past 64); spare over, at, under what it keeps; as a stall open, short of a lot, its proceeds never full |

`ShoppingTest` gained six: the stalls seen today, one and two, forgotten tomorrow, kept through a trip
and a sale; a look at the nearest open stall it may use, never a closed, barred or seller's, the next
once one is seen, none once all are, a tie; a need first though a stall nearer sells a want, then a
look, then nothing, a new day; needs nobody sells, a look, and the trip still counted; a seller cheaper
than a stall for a need, never for a look; nobody out of hours, without an emerald or a sale left, a
captive; a worker in its meeting hours.

## GameTests: 87 passed

The 80 of 0.6.0 and 7 in `TasteGameTests`. 4a's shopping tests now give their villagers a taste for
what they buy at least even (`Yard.villager` with a test of the taste, `Yard.tasting`): a taste is drawn
from the UUID, and a random one below 2/3 makes 4a's bargain at the base value a coin toss.

- **Climate and taste:** three masons with beds in the desert, the snow and the plains are hot, cold
  and temperate (the biome set with `fillbiome`). What each will pay for raw salmon (from the cold) and
  orange terracotta (from the badlands), neither a matter of taste: its value, or half again from a
  climate not its own; the climate's and the need's together; a carpet by the taste for decor, needed
  and not; a golden carrot by the higher of food and luxury. A farmer's taste is its UUID's draw with its
  file's lean, a nitwit's with its own; a farmer sells bread, potatoes, carrots and beetroot keeping 3, 8,
  8 and 0; a librarian sells nothing.
- **Window shopping:** a farmer whose favourite is decor, needing nothing, looks at the nearer stall
  (paper: "not interested", nothing changes hands), then buys a sale of four carpets at the farther at
  their base value (a bargain), celebrates (`entity.villager.celebrate` heard from it) and hops (off the
  ground three ticks later), takes them home, and goes to neither again that day; both are saved as seen.
  A fletcher with a pickaxe at home and a taste for tools buys two more at a stall, to the three it wants
  at most, into its home chest, and no more.
- **Villagers selling:** a librarian with no food at home buys six bread from a free farmer carrying 64
  rather than four from a nearer stall; the emerald goes into the farmer's purse; a hired farmer nearer
  still, with 64, sells nothing (`Peddlers.wares` is empty for it).
- **A stall in the way:** a For Sale block beside a worker's bed is not its home chest, nor one in a
  post's area the post's storage; a villager just west of one stall, bound for another, plans its way
  around the first and ends beside the second, on the ground.

Eight runs of the GameTests in a row passed at the final code, every one 87 of 87, and the gate's
build made a ninth.

## Booth: 94 checks, 50 photographs

| Photo | Shows | Checks in code |
|---|---|---|
| `47-window-glance` | the farmer whose favourite is decor at the paper stall, looking it over; the unemployed villager and the farmer selling behind | the paper stall's ledger has a visit |
| `48-favourite-hop` | the same farmer in the air over its shadow at the carpet stall, green sparkles (a bargain), the white carpet across its arms | the client sees it holding a carpet, off the ground |
| `49-bought-from-a-farmer` | the unemployed villager with a loaf in its hand and green sparkles, beside the farmer it bought from | the client sees the bread in its hand within 3.5 blocks of the farmer |
| `50-ledger-not-interested` | the paper stall's Ledger tab: today 0 sales, not interested 2; "Villager wasn't interested", "Farmer wasn't interested" | the ledger's day as the client has it |

And after the photos: the window shopper has four carpets at home; the buyer six bread (at home or on
its way); the farmer 11 emeralds and 58 bread.

The 4b scene stands 88 blocks past the market, beyond its villagers' 64 blocks of shopping and the
booth's simulation distance; the farmer selling stands still (no AI), so the photograph finds it. The
4a market's farmer now has a taste for food at least even, for the same reason as the GameTests. Photo
44 (4a's ledger) now shows two "not interested" as well: the librarian and the mason, needing nothing
the bread stall sells, looked it over after their own trips.

**Judged by eye** (crops at three times): 48's hop is plain, the villager a body's height of shadow
above the grass; the carpet in its arms reads only as a thin white sliver, as a carpet item does. 49's
loaf reads as bread. 47 shows a villager at a stall, and only the ledger says it passed. All four are
taken from 7 to 10 blocks, as 4a's were.

## Mutation pass: 27 of 27 caught

`uv run --no-project python devtools/verification/mutate-0.7.0.py domain-each | a | b | c | d`.

| Mutation | Caught by |
|---|---|
| D1 nothing is foreign | `ClimateTest` |
| D2 its own climate's goods foreign | `ClimateTest` |
| D3 the lean pulls the wrong way | `TasteTest` |
| D4 the favourite is the lowest | `TasteTest` |
| D5 it likes what it rates 1 | `TasteTest` |
| D6 an item by the lower of its kinds | `TasteTest` |
| D7 it wants what it doesn't like | `TasteTest` |
| D8 no most to a want | `TasteTest` |
| D9 a lot of one, whatever the value | `PeddlerTest` |
| D10 it sells what it keeps | `PeddlerTest` |
| D11 a stall seen is looked at again | `ShoppingTest` |
| D12 it goes to look at a seller | `ShoppingTest` |
| D13 never a need, only looks | `ShoppingTest` |
| D14 a need nothing sells isn't counted | `ShoppingTest` |
| D15 yesterday's stalls never forgotten | `ShoppingTest` |
| G1 no want for taste | the window shopper, the worker's want |
| G2 climate ignored | climate and taste |
| G3 taste ignored | climate and taste |
| G4 the trade's lean unread | the taste is its own |
| G5 a hired worker sells | the farmer's bread |
| G6 villagers never sell | the farmer's bread |
| G7 the seller's purse takes nothing | the farmer's bread |
| G8 no celebration | the window shopper |
| G9 a stall is storage | not storage |
| G10 the stalls seen never kept (both writes) | the window shopper |
| G11 a worker's want blind to its chest | the worker's want |
| G12 a mob may climb a stall (the code before the fix) | the way around |

G10 and G12 ran together and also failed the reach test and the worker's want: without the plan's save
of its day, the trip counted for nothing and the stall was seen again.

## Found by the gate

- **Villagers planned their way over a For Sale block** and stuck on its sign (2 of 8 runs; the window
  shopper bound from the paper stall to the carpets, "could not reach" with a 12-step straight path). A
  block whose collision is no full block is open ground to vanilla, and a mob plans to jump onto any it
  can't walk through unless it is a fence; the sign makes the stall a full block high. The stall is now
  no open ground and a fence (NeoForge's `getBlockPathType`); a way around it ends beside it, where a
  shopper's 2.5 blocks of arrival have always been met. Run against the old code (G12), the new test
  fails.
- **The Work Post was given the same and taken back:** 1a's gate test failed with it, a farmer stopping
  in the gateway, because a worker's arrival at its post leans on reaching above the pole. A villager can
  stand on a pole; nobody stands on the stall's sign edge.
- **A For Sale block counted as storage** (its 27 slots): a worker's home chest beside its bed, a post's
  storage in its area. Found reading `Kitchen.home` for 4b's wants.
- **A free villager needing nothing window-shops every stall in reach,** so the seller farmer looked
  over the dearer stall in the peddler test, and a ledger line appeared there: the test now asks that no
  sale was made.
- **A fixture villager hired after the settle ticks** is a free villager until then and took the
  buyer's bed (1 of 8 runs): it is hired as it is made.
- **A frozen (no AI) villager never lands,** and a ground navigation plans only for a mob on the ground:
  the path test lets its villager live.

## Not verified

- **Play on the box:** nothing of 4a or 4b has run on the server.
- **A real village's biomes:** climates are tested on biomes set by command in a flat world.
- **A farmer selling its own harvest:** the tests' and the booth's farmers are handed their bread; a
  vanilla farmer making bread from its wheat (`WorkAtComposter.makeBread`) was read in the source, not
  seen selling it.
- **A buyer following a seller across a village:** the GameTest's farmer walks about a little; the
  booth's stands still.
- **Rusty's shaders and GPU:** the photographs are software-rendered.
- **Rusty has not seen** photos 47 to 50, nor 37 to 46.
