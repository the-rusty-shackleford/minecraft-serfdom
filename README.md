# Serfdom

Villagers you hire live and work at your base, for **Minecraft 1.21.1 / NeoForge 21.1.248**.
Give a worker a bed and a Work Post, and it fells the trees or harvests the crops around the
post, or cooks or smiths at the stations around it, and puts what it makes away in your chests.

By Rusty Shackleford and nfx, AGPL-3.0-or-later.

This is phase 1 of five, released as 0.2.0: woodcutting and farming (1a), cooking and the
blacksmith (1b). The rest of the plan:

- **2:** capture with the chain, owners' rules and equipment.
- **3:** hunger and cooking meals.
- **4:** wallets, trade between villagers, and the For Sale block.
- **5:** raids and defending the base.

## Playing it

1. **Hire.** Sneak and right-click a villager that has a profession, with an **empty hand**. A
   green **[Hire for N emeralds]** line appears in chat, under Village Deed's offer.
   - N is 8 for each profession level, so a novice costs 8 and a master 40.
   - Click the line within 30 seconds. The fee comes out of everything you carry, bags included.
   - Unemployed villagers, nitwits and children can't be hired.
   - In a village someone bought with Village Deed, only the owner and the players they trust can
     hire.
   - With an emerald in your hand, sneak-use is still Thief's gift.
2. **Bring it home.** A hired villager without a bed follows you on foot. For a long trip, lead it
   on the **chain lead** (a lead and a chain, shapeless).
   - Right-click your worker with the chain to lead it, and again to let it go.
   - Tie it to a fence like any lead.
   - Pulled past ten blocks, the chain snaps and drops.
   - A normal lead still does nothing to a villager.
3. **The Worker Screen.** Sneak and right-click your worker. It shows:
   - its profession and level;
   - its bed and its job;
   - what it needs, if anything.

   It has three buttons:
   - **Assign bed:** then right-click a free bed within 30 seconds. The worker sleeps there from
     dusk, and no other villager takes that bed.
   - **Assign job:** then right-click a Work Post within 30 seconds. The post must be yours,
     within 48 blocks of the bed, and hold fewer than four workers. Assign job stays greyed out
     until the worker has a bed.
   - **Clear job:** the worker goes back to an ordinary villager's day at your base.

   A plain right-click still opens its trades. Its trades restock at its post, at most twice a
   day, as a villager's do at its workstation.
4. **The Work Post** is a fence and a sign, shapeless. Place it, then right-click it to set:
   - **Job:** Woodcutting, Farming, Cooking or Blacksmith.
   - **Radius:** for woodcutting 4 to 32, default 16; for the others 4 to 16, default 8. The area
     is the cube of that radius around the post.
   - **Outline:** shows the area. Every chest, barrel or shulker box in the area is the post's
     storage.
   - **Stock list** (cooking and blacksmith): what the workers keep in the chests. See below.

   Breaking the post sends its workers back to an ordinary day.

### The worker's day

| Time | What it does |
|---|---|
| 0–2000 | Idle |
| 2000–8000 | Works at its post |
| 8000–10000 | Meets at a bell, if one is in reach |
| 10000–12000 | Idle |
| 12000 to dawn | Sleeps in its bed |

Workers open doors and fence gates on their way and close them behind them.

### The jobs

- **Woodcutting** (needs an axe).
  - The worker fells the nearest natural tree whose trunk stands in the area: the whole tree,
    top first, 2×2 trees included.
  - Its leaves are cleared, its drops collected, and saplings planted where the trunk stood.
  - It leaves alone anything a player placed, and log pillars with no leaves on them, such as a
    village house's frame.
- **Farming** (needs a hoe).
  - The worker harvests ripe crops on farmland and replants each from its own harvest.
  - It also takes pumpkins and melons from their stems, picks Farmer's Delight tomatoes and cuts
    ripe rice.
  - It never tills new ground.

A worker takes its tool from the post's chests and wears it out as a player would. When it
breaks, the worker takes the next. Work goes at a player's speed with the same tool. A worker
whose profession matches its job works 25% faster: farmers at farming, More Villagers'
woodworker (or the fletcher, where there is none) at woodcutting, butchers at cooking, and
armorers, toolsmiths and weaponsmiths at the blacksmith's.

### The cook and the blacksmith

These two keep the post's **stock list** filled. Right-click the post, then **Stock list...**:

- Up to nine rows of "keep N of X", N from 1 to 999. The rows' order is their priority.
- **Add...** opens a picker of everything the job's stations make, with a search box. You can
  ask for things you don't have yet.
- Each row shows how many are stocked (or on their way) against how many it keeps, and in a
  second line whether it is **stocked**, **being made**, or why it is stuck: what it is short
  of ("Short of 2 × Iron Ingot, 1 × Stick"), the station it needs, no fuel, or no knife.
- `-` and `+` change the number (by ten with shift); `×` drops the row.

A worker makes whatever the rows lack, and first whatever that needs, from what the post's
chests hold: for two iron pickaxes it smelts the raw iron, cuts planks into sticks, then crafts.
It stops when every row is met. A row never uses up what another row keeps.

| Job | Stations it uses | Besides the list |
|---|---|---|
| Cooking | crafting table, furnace, smoker, campfire or Farmer's Delight's stove, Farmer's Delight's cooking pot and cutting board | makes charcoal when fuel runs low |
| Blacksmith | crafting table, furnace, blast furnace, smithing table, anvil, Ranged Weapons Mod's weapons workbench | makes charcoal; smelts raw metal into ingots; mends worn gear |

- **Anything the stations make** can be kept: food at the cook's (bread, cake, steaks, Farmer's
  Delight's meals), tools, weapons, armour, buckets, rails, netherite upgrades and guns at the
  blacksmith's.
- **Stations.** Where two stations make the same thing, the blast furnace or the smoker goes
  before the furnace, and the furnace before a campfire. A campfire or stove must be lit, and a
  pot must have heat under it.
- **Your stations stay yours.** A worker uses only an empty station and collects only what its
  post put in, so a furnace you are using is left alone.
- **Fuel** comes from the chests: coal, charcoal and coal blocks, only as much as a load needs
  (one coal does eight). When the chests hold fewer than eight, logs are made into charcoal. With
  none at all, two logs are burnt under three to start.
- **Raw metal.** The blacksmith smelts raw iron, copper and gold (and any mod's raw metals) into
  ingots. Ore blocks you mined with silk touch are left for you to fortune.
- **Repairs.** The blacksmith mends worn gear left in its chests at an anvil, as a player's
  material repair does. It uses only whole quarters of durability, so no ingot is wasted, and
  needs no XP. The anvil wears as it does for you, and two items are never merged.
- **Never melted.** Smelting never takes anything with durability or anything that doesn't
  stack, so swords and armour are never melted into nuggets.
- **The cutting board** needs a knife, which the cook fetches from the chests.
- **Leftovers.** Buckets and bowls go back to the chests.
- **Several workers.** Up to four on one post claim the station they use and what they make, so
  none makes what another already is.

Workers don't eat yet (phase 3), so the cook just fills your chests.

### Storage

A worker carries eight stacks. It puts them away when it is full and at the end of its shift.
Each item goes to:

1. a chest that already holds that item;
2. else the chest whose items share the most tags with it (seeds with seeds, logs with logs);
3. else the overflow chest, the one nearest the post.

### What a worker needs

When a worker lacks something, an icon floats over its head. You see it within eight blocks.

| Icon | Meaning |
|---|---|
| A bed | It has no bed. |
| A struck-out axe | There's no tool for its job in the post's chests (a cook's knife too). |
| A struck-out anvil | No station in the area that a worker can use makes what the list wants. |
| A flame over coal | No fuel. |
| An empty crate | The chests are short of what the list needs. |
| A full chest | Nothing it carries has anywhere to go. |

Work starts again by itself once the need is met. The stock list says which row lacks what.

Hired workers can summon iron golems as other villagers do: it takes five that have slept in the
last day gossiping together, or three panicking at a zombie.

## Server settings

`config/serfdom-server.toml`:

| Setting | Default | What it is |
|---|---|---|
| `hire_per_level` | 8 | Emeralds per profession level. |
| `offer_seconds` | 30 | How long a [Hire] line stays clickable. |
| `pick_seconds` | 30 | Time to click a bed or a post after the button. |
| `action_floor_ticks` | 10 | The shortest any action takes, so work stays visible. |
| `leaves_per_tick` | 4 | How fast a felled tree's leaves are cleared. |
| `craft_ticks` | 40 | Ticks one craft, cut or repair takes (a matching profession is 25% faster). |
| `workers` | true | The whole module: off, nobody can hire and owned villagers live as free ones. |

Jobs are data: `data/<namespace>/serfdom/job/<id>.json`. A file names:

- the tool tag;
- the target kind (`tree`, `crop` or `workshop`);
- the radius bounds;
- the professions that work faster;
- a fallback list for when the game has none of those professions;
- for a workshop, its `stations` (`table`, `workbench`, `smithing`, `board`, `blast_furnace`,
  `smoker`, `furnace`, `campfire`, `pot`, `anvil`) and `duties` (`charcoal`, `raw_metal`,
  `repair`).

A data pack can add a job built from these kinds, or change the radii, the professions or a
workshop's stations. The fuel a worker may burn is the item tag `#serfdom:fuel`.

## How it works

- **Brain.**
  - An owned villager's brain is built from vanilla's own packages, minus everything that claims
    a workstation or a bed, takes or resets a profession, or walks off to a village.
  - It also gets two activities of its own: following, and the shift.
  - Its schedule follows its state: following until it has a bed, an ordinary day with only a
    bed, and the worker's day with a post.
  - Free villagers keep vanilla's brain.
  - Without this, vanilla's `ResetProfession` would turn every novice hired away from its
    workstation jobless.
- **Sneak-use.** Sneak-use on an owned villager is taken at high priority, so neither Village
  Deed's offer nor Thief's gift ever sees it.
- **The shift** is a pure rule (`domain/Shift`): deposit, fetch the tool, work, or wait showing a
  need. A behaviour carries out each step.
- **Natural trees** (`domain/Felling`):
  - the base log stands on soil and the logs connected to it, at most 512, carry natural leaves
    at the top;
  - each chunk records the logs players placed;
  - the leaves cleared are exactly the ones vanilla would let decay once the logs are gone.
- **Searching the area.** Trees and crops are found by reading the area's chunk sections and
  skipping every section whose palette lacks a log or a crop, never block by block. Storage is
  the area's block entities with an item handler of 18 slots or more, so Create's vaults count;
  that index is rebuilt when a block changes in the area.
- **Gates.** Every villager walks with a navigation that is vanilla's, except that for a worker a
  closed fence gate counts as a wooden door. A behaviour opens and closes gates as vanilla's does
  doors.
- **The workshop** (D-0002):
  - **The planner** (`domain/Recipes`) is Warehouse Manager's, with a station on every rule. It
    plans the whole tree to depth 8, never spends an item on its own ingredients, and tries
    rules by yield, then by station, then by id.
  - **The rule book** (`job/RecipeBook`) is read off the game's recipes once per recipe set,
    on either side, so the picker uses the client's synced recipes. It covers crafting,
    smithing upgrades, Ranged Weapons Mod's assembly, cooking, and Farmer's Delight's pot and
    board.
  - **The next step** is a pure rule (`domain/Workshop`):
    1. collect a finished load;
    2. the first row's first step that can run now;
    3. charcoal;
    4. raw metal;
    5. a repair;
    6. else rest, showing the first need.
  - **Stations** are found with the storage. A station's state comes from its contents and the
    post's saved record of what it loaded.
  - **On their way:** what the post's loads will give, the other workers' claims, and what every
    worker carries all count toward a row.
  - **Tidy:** a workshop worker puts away everything it carries before it plans again.
- **The chain lead** is vanilla's leash on a villager: vanilla refuses villagers a lead, so any
  leash on one is the chain. It is drawn in iron, and a mixin makes the lead vanilla drops when a
  leash breaks drop as the chain.

Village Deed, Farmer's Delight and Ranged Weapons Mod are optional: without them there are no
bought villages; no tomatoes, rice, pot, stove or board; and no weapons workbench. Carried, the
inventory protocol, is nested in the jar.

## Building

```
./gradlew clean build
```

This runs the JUnit domain tests, the GameTests and the photo booth.

- Carried comes from mavenLocal: run `./gradlew publishToMavenLocal` in `minecraft-carried`
  first.
- The gametest server runs Village Deed from the sibling repo's `build/libs/villagedeed-2.2.0.jar`,
  Backpacks+ from `../minecraft-backpacks-plus/build/libs/backpacksplus-0.7.0.jar`, Ranged Weapons
  Mod 2.12.0 and Metals and Materials 1.0.3 from their repos' `build/libs`, and Thief and
  Farmer's Delight from Modrinth's maven.
- The booth needs a display: the Xephyr recipe in the workspace's `AGENTS.local.md`. Without one,
  add `-PskipBooth`.
- The GameTests log every worker's plan with `-Dserfdom.trace=true`, which the gametest run sets.

The art is drawn by `devtools/art/art.py`:

```
uv run --no-project --with pillow python devtools/art/art.py
```
