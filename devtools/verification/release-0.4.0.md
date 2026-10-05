# Serfdom 0.4.0 (phase 2b, what a worker wears): verification

Run on 2026-10-05. 0.4.0 is 0.3.0 (phase 1 and 2a, never released; its record is
`release-0.3.0.md`) with phase 2b (D-0004):
- the Worker Screen's four wearing slots;
- armour that wears on every villager;
- gear on each way a worker leaves;
- armour and Lucky's Wardrobe's clothes drawn on villagers.

`./gradlew clean build`: `BUILD SUCCESSFUL`. The jar `serfdom-0.4.0.jar` has sha1
`a218518da501ea1c3ae675e90b20ac02b4f8777a`, with Carried 1.0.0 nested. The booth ran on Xephyr `:7`
with software rendering. 2b needs no change to the two siblings 0.3.0 ships with (Vanilla Wheels
1.11.0, Village Law 1.1.0; their gates are in `release-0.3.0.md`).

## JUnit: 187 tests, 0 failures

The 160 of 0.3.0, plus:

| Suite | Tests | What it covers |
|---|---|---|
| `WardrobeTest` | 10 | who may use the slots: alive or not, owner or not, distance 0, at the reach, past it, far; the reasons in order; a negative or undefined distance refused. A piece's rep invariant and its own copy of its slots. Each slot against nothing, its own piece, another slot's, a piece for two slots, an unwearable stack, a bound piece. Taking off nothing, a plain piece and a bound one, in and out of creative. Shift-clicks from the slots (plain, bound in survival and in creative, nothing) and from the inventory and hotbar (its slot empty or worn, the first free of two, unwearable, nothing). A route's rep invariant. |
| `PartingTest` | 5 | each of the five ways out, with and without Curse of Vanishing; every way has a fate; the drop mark is above 1 |
| `FitTest` | 7 | the turning maths against the game's order. The helmet's corners equal the lifted helmet on the villager's head under seven head turns, at the head's own size and at Guard Villagers' 1.5 child head; a non-positive scale refused. The covers, each at least 0.25 pixels on every face that must be covered: the helmet over the head and its top layer over it, the chestplate over the robe and the body, the belt over the body, the chestplate over the belt, each sleeve on its upper arm and ending at the elbow, the leggings over the legs and the boots over the leggings at rest and at the widest swing either way. The robe rule. A box's corners in order. |
| `WorkerLayoutTest` | 5 | fits 688×288 (3440×1440 at GUI scale 5) and the booth's 640×360, not 300×200; every part inside the panel and apart from every other; the slots stacked; the inventory centred; a rectangle has a size |

## GameTests: 53 passed

The 47 of 0.3.0, four of them extended, and 6 in `EquipmentGameTests`. All run with Village Deed
2.2.0, Thief 1.2.4, Village Law 1.1.0, Guard Villagers 2.4.11, Vanilla Wheels 1.11.0, Trailer
2.4.0, Farmer's Delight 1.3.3, Backpacks+ 0.7.0, Ranged Weapons Mod 2.12.0, Metals and Materials
1.0.3 and Lucky's Wardrobe 2.0.0 loaded. Every click goes through the server's own handler for the
client's click packet (`handleContainerClick`), which asks first whether the menu may still be
used.

**In `EquipmentGameTests`:**
- **Dressing.** The owner's sneaking use opens the Worker Screen's menu on the worker. A diamond
  chestplate picked up from the inventory and clicked onto the chest slot is worn and marked to
  drop whole. Two ticks later the worker reads 8 armour. A zombie's hit of 6 takes 4.656 from it
  (8 points, toughness 2) and all 6 from its bare twin. A captive in chains and a child take a
  helmet by shift-click the same way.
- **Wear.** A zombie's hit of 8 does the following:
  - the iron helmet loses 2;
  - the chestplate with 1 durability left breaks off;
  - Unbreakable leggings and Lucky's Wardrobe's taiga boots lose nothing.

  A falling block wears a free villager's helmet. The test logs that the taiga boots give 1 armour
  point and have no durability.
- **Who.**
  - A stranger's sneaking use opens nothing. A forged menu of theirs is not valid, and its click
    takes nothing.
  - Nine blocks off, the owner's click takes nothing. Back within reach, the helmet comes off on
    the cursor.
  - The helmet won't go on the empty legs, and won't swap off bound boots.
  - Bound boots stay on in survival and come off in creative.
- **A screen left open.**
  - On a worker killed while it is open, the menu is no longer valid. A click takes nothing, and the
    chestplate lies on the ground once.
  - On a captive that goes free while it is open, a click takes nothing and the captive keeps its
    helmet.
- **Death.** Two dressed workers with an axe and five logs are killed, one with mob loot on and one
  with it off. Each drops one helmet (whole, as worn: damage 30), one chestplate, one pair of boots,
  the axe and the five logs. Their leggings, which carry Curse of Vanishing, are gone.
- **Shift-clicks.**
  - From the inventory: an iron helmet goes onto the head; Lucky's Wardrobe's trousers onto the
    legs and its boots onto the feet; an elytra onto the chest.
  - With the head already worn, Lucky's Wardrobe's farmer hat goes to the hotbar. A stick goes to
    the hotbar too.
  - The worn helmet goes back to the inventory, and a carved pumpkin goes on in its place.

**Extended in `CaptiveGameTests`:**
- **Turned zombie:** the worker's helmet and leggings drop with the axe, the logs and the chain,
  and the zombie villager wears nothing.
- **Set free:** the captive's golden helmet and leather boots lie where it stood, and it wears
  nothing.
- **Escape:** the runner reaches home still wearing the helmet its owner gave it, still marked to
  drop whole.
- **The law:** the captive freed by paying the fine keeps the chestplate its taker put on it, still
  marked to drop whole.

## Booth: 61 checks, 33 photographs

Photos `01` to `17` are 0.3.0's. New:

| Photo | Shows | Checks in code |
|---|---|---|
| `18-armour-materials-front` | seven villagers in leather (dyed), chainmail, iron, gold, diamond, netherite, and a mixed set | the client sees seven chestplates |
| `19-armour-materials-back` | the same from behind | — |
| `20`, `21`, `22` | a toolsmith in iron, close up: front, side, back | — |
| `23-armour-closeup-trimmed` | a turtle shell, an iron chestplate trimmed in gold, enchanted diamond leggings, green leather boots | — |
| `24-robe-rule` | a chestplate alone, leggings alone, boots alone, a helmet alone, bare | — |
| `25-heads-on-trades` | a helmet on each hatted trade (fisherman, shepherd, librarian, a leatherworker's turtle shell, a nitwit's blue leather cap), a carved pumpkin, a zombie's head | — |
| `26`, `27` | Lucky's Wardrobe: farmer hat and apron; top hat and the snowy set; the snowy hood and the taiga set; the desert set; front and back | each of the fourteen items is registered |
| `28-child-captive-elytra` | a child in iron, a captive in chains in a helmet and chestplate, an elytra | the client sees the captive in chains |
| `29-elytra-back` | the same from behind | — |
| `30-beside-zombie-villagers` | villagers in iron and diamond beside vanilla's zombie villagers in the same | — |
| `31-walking` | a villager in diamond walking, side on | — |
| `32-trailer-armoured` | the four captives in the trailer, dressed | — |
| `33-worker-screen-dressed` | the Worker Screen of the dressed toolsmith: portrait, four slots, details, buttons, inventory | the view came and bound the menu; the client's chest slot holds the trimmed chestplate |

**Judged by eye, at 3× crops:**
- **The helmet** sits on the taller head at the height of vanilla's zombie-villager helmet (photo
  30, side by side), with the face showing under it and the nose through it. The narrow pale strips
  at its lower corners, seen from the side, lie inside the helmet's own box. They look like the
  iron helmet texture's edge pixels, which a player's helmet carries too, but no player was
  photographed beside it.
- **The chestplate.**
  - Its shoulders and sleeves cover the crossed upper arms, which leaves the folded forearms bare.
  - Its top shows between the forearms and the neck, and its bottom under them.
  - From the side, the sleeve slants forward as the upper arm does, with the forearm block ahead of
    it. From behind, the shoulders read as pauldrons.
  - Over the robe (24, chestplate alone) no robe shows through.
- **The robe rule** (24). With leggings on, the robe is off and the legs are armoured to the boots.
  With boots alone, the hem hangs over the boots' tops. With a helmet alone, the farmer's straw brim
  is gone.
- **Hats** (25). No trade's hat or hood shows through any helmet. The pumpkin and the zombie's head
  are vanilla's own head layer, which villagers already had.
- **Lucky's Wardrobe** (26, 27): its hats sit on the head, its coats' sleeves wrap the crossed arms,
  and its robes and trousers hang as on a player. The snowy hood's black mask covers the face, with
  the villager's nose through it.
- **The cuffs** (28) still lie across the bare forearms over a chestplate.
- **The elytra** (29) hangs down the back, clear of the robe.
- **Walking** (31): the leggings and boots swing with the legs.
- **The trailer** (32): every helmet clears the roof.

**The booth found what the GameTests could not.** In the first run, the child in iron (28) wore no
helmet. Guard Villagers, in the pack, draws a child villager's head half as large again (its
`bigHeadBabyVillager` client option, set in `renderToBuffer`), and the helmet stayed adult-sized
inside it. `Fit.head` now takes the head's scale, `FitTest` covers a 1.5 head, and the second run's
28 and 29 show the helmet on the child.

**Not ours, in the background of 19:** a thin wood-coloured line over the leather farmer's head,
with smoke beside it. It is the first Work Post's area outline (drawn up to 96 blocks) and the
kitchen campfire, from photos 01 and 07, far behind the row. From the front (18), that
farmer shows nothing over its helmet.

## Mutation pass: 19 of 19 caught

`uv run --no-project python devtools/verification/mutate-0.4.0.py domain-each | a | a2 | b | c`.
Each domain mutation ran alone. The GameTest mutations ran in batches, each mutation aimed at tests
no other mutation in its batch touches, and the catch was read off the failed tests' names. Files
are restored from memory.

| Mutation | Caught by |
|---|---|
| D1 a stranger uses the slots | `WardrobeTest` |
| D2 binding ignored | `WardrobeTest` |
| D3 a slot takes anything | `WardrobeTest` |
| D4 an escapee drops its gear | `PartingTest` |
| D5 a vanishing piece drops | `PartingTest` |
| D6 the chest not deepened | `FitTest` |
| D7 the helmet's lift not turned | `FitTest` |
| D8 the panel too tall for GUI scale 5 | `WorkerLayoutTest` |
| G1 armour never wears | wear |
| G2 a dead worker's gear left on it | death, turned zombie |
| G3 the screen never shuts | a screen left open, who |
| G4 a slot takes anything | who, after the fix below |
| G5 set free keeps its gear on | set free |
| G6 no mark to drop whole | dressing, escape, the law |
| G7 a zombie keeps nothing of what it wore | turned zombie |
| G8 a stranger's menu is valid | who |
| G9 an escapee drops its gear | escape |
| G10 the law's freed captive drops its gear | the law |
| G11 shift-clicks put nothing on | shift-clicks, dressing |

**G4 got past the tests the first time.** The "won't go on the feet" check clicked the helmet onto
the feet while bound boots were on them, so Binding refused the click before the slot's own rule
was ever asked. The check now tries the empty legs first. Rerun alone (batch `a2`), G4 is caught.

The drawing rules (the fit, the robe, the hat) are not mutated here; the photographs judge them.

## Gotchas met on the way

- **The killing blow wears the armour first.** A worker killed by a hit of 1000 dropped no armour:
  the hit took 250 from every piece and broke them all, as it would a player's. The tests kill with
  `kill()`.
- **NeoForge's menu open with extra data cannot reach a mock player,** and ticking one by hand
  (`doTick`) syncs its attachments and crashes the test server. Both are the payload-channel gotcha
  of phase 1 again.
- **Curios on the GameTest server** sends its sync payload to every mock player as it joins, and
  the tests with a player failed. Lucky's Wardrobe's client code needs Curios to load, so the booth alone gets it,
  from its own `mods` folder.

## Not verified

- **Play on the box:** nobody has dressed a worker on the server.
- **Rusty's shaders and GPU:** the photographs are software-rendered without shaders.
- **Mods' own armour models other than Lucky's Wardrobe** (GeckoLib armour, if the pack has any)
  get the same poses and scales but have not been photographed.
- **Rusty has not seen** photos 18 to 33.
