# Serfdom

Villagers you hire, or take in chains, live and work at your base, for **Minecraft 1.21.1 /
NeoForge 21.1.248**. Give a worker a bed and a Work Post, and it fells the trees or harvests the
crops around the post, or cooks or smiths at the stations around it, and puts what it makes away
in your chests.

By Rusty Shackleford and nfx, AGPL-3.0-or-later.

This is 0.9.2:
- phase 1: woodcutting and farming, cooking and the blacksmith;
- phase 2a: the capture (D-0003);
- phase 2b: what a worker wears (D-0004);
- phase 3: hunger and meals (D-0005);
- phase 4: the market (D-0006): 4a, purses, the For Sale block, and villagers who shop; 4b, climate,
  taste, wants, window shopping, and villagers selling to villagers;
- phase 5: raids and defending the base (D-0007);
- shared farms and sowing (D-0008): farming posts that touch are one farm, its farmers working a
  plot each and filling in for one another, and bare farmland sown with what grows around it.
- 0.9.1 (D-0009): working out the base values no longer searches the world for map structures,
  which had held the server 2.5 seconds at every start and saved nine unused maps.
- 0.9.2 (D-0010): villagers swim through water again; since 0.1.0 every villager, free or hired,
  planned its paths along the bottom of water, and one in water two deep never climbed out.

The five phases are the whole of the plan; shared farms came after, from players asking for far
larger farms.

## Playing it

1. **Hire.** Sneak and right-click a villager that has a profession, with an **empty hand**. A
   green **[Hire for N emeralds]** line appears in chat, under Village Deed's offer.
   - N is 8 for each profession level, so a novice costs 8 and a master 40.
   - Click the line within 30 seconds. The fee comes out of everything you carry, bags included.
   - Unemployed villagers, nitwits and children can't be hired.
   - In a village someone bought with Village Deed, only the owner and the players they trust can
     hire.
   - With an emerald in your hand, sneak-use is still Thief's gift.
2. **Bring it home.** A hired villager without a bed follows you on foot. For a long trip, put it
   in the **chain lead** (a lead and a chain, shapeless). See [The chain](#the-chain).
3. **The Worker Screen.** Sneak and right-click your worker. It shows:
   - the worker, beside its head, chest, legs and feet slots (see
     [What a worker wears](#what-a-worker-wears));
   - its profession and level;
   - its bed and its job;
   - its hunger, as ten drumsticks;
   - its purse, in emeralds (see [The market](#the-market));
   - what it needs, if anything;
   - your inventory below.

   It has four buttons:
   - **Assign bed:** then right-click a free bed within 30 seconds. The worker sleeps there from
     dusk, and no other villager takes that bed.
   - **Assign job:** then right-click a Work Post within 30 seconds. The post must be yours,
     within 48 blocks of the bed, and hold fewer than four workers. Assign job stays greyed out
     until the worker has a bed.
   - **Clear job:** the worker goes back to an ordinary villager's day at your base.
   - **Set free** (click it twice): the worker is free again and walks to the nearest village. A
     chain on it comes back to you; the tool it keeps, and everything it wears, drop where it
     stands.

   A plain right-click still opens its trades (not in chains). Its trades restock at its post, at most twice a
   day, as a villager's do at its workstation.
4. **The Work Post** is a fence and a sign, shapeless. Place it, then right-click it to set:
   - **Job:** Woodcutting, Farming, Cooking or Blacksmith.
   - **Radius:** for woodcutting 4 to 32, default 16; for the others 4 to 16, default 8. The area
     is the cube of that radius around the post.
   - **Outline:** shows the area. Your farming posts whose areas touch are one farm (see
     [Shared farms](#shared-farms)). Every chest, barrel or shulker box in the area is the post's
     storage.
   - **Stock list** (cooking and blacksmith): what the workers keep in the chests. See below.

   Breaking the post sends its workers back to an ordinary day.

### The chain

The **chain lead** is the cuffs. A villager with the chain on it is **in chains**: it doesn't work,
trade, wander or run away. It goes where whoever holds its chain leads it, and stands still when
nobody does. You see the cuffs across its crossed wrists.

| What you do | What happens |
|---|---|
| Right-click an owned villager with the chain | It is in chains and you lead it (one chain used). Anyone can, to move someone's worker. |
| Right-click a villager in chains that nobody holds, empty-handed or with the chain | You take its chain. |
| Right-click one you hold, as its owner | The chains come off and the chain comes back to you. |
| Right-click one you hold, as anyone else | You let go. It stays in chains: only the owner takes them off. |
| Pull it past ten blocks | The chain snaps from your hand. It stays in chains, standing there, nothing dropped. |
| Right-click a fence while leading | It is tied there, as with any lead. |

A normal lead still does nothing to a villager. A villager in chains that dies drops the chain.

### Taking a captive

**Hold right-click with the chain on a free villager for two seconds**, within two blocks,
looking at it. It screams as you start and stands still while you hold. Let go, step away or look
off it, and it slips free. When the two seconds are up it is your **captive**, in chains, on your
chain.

- Only a grown villager with a trade can be taken: not a nitwit, an unemployed villager or a
  child. A villager that is already someone's is put in chains instead.
- In a village someone bought with Village Deed, only the owner and the players they trust can
  take its people, and for them it is **no crime**. Anyone else's chain is refused.
- Captives cost nothing, but taking one is **Thief's heavy crime** where it stood:
  - nobody but the captive saw: nothing more;
  - other villagers saw: Thief's reputation hit with each of them;
  - a guard saw: Village Law's summons. **Pay** the fine (15) and the guards free every villager
    you took in that case and keep the chains. **Leave**, and you keep them; paying the debt
    later frees nothing.
  - Outside a village's buildings, Thief counts no crime at all.
- A captive stays a captive. It is never a hired worker, though you can set it free.

### Captives

A captive out of chains takes a bed and a post like any worker, but:

| Time | With a post | Without one |
|---|---|---|
| 0–2000 | Stands about where it is | Stands about where it is |
| 2000–10000 | Works (through the hired workers' meeting) | Stands about |
| 10000–12000 | Stands about | Stands about |
| 12000 to dawn | Sleeps in its bed | Sleeps in its bed |

- It walks and works **10% slower** (and a matching profession's 25% still counts: 1.25 × 0.9).
- It doesn't follow you, meet, chat, trade with other villagers or have children.
- It never summons iron golems or counts toward one, and its bed doesn't count toward a
  village's cats.
- At work it now and then **hums a phrase of a slow, low work song** in its own voice, about
  eight blocks around, subtitled "Captive hums". Never idle, asleep or in chains.
- **Escape.** Asleep in its bed at midnight, a captive rolls once a night: 5%. On a success it
  gets up before two and walks toward the village it was taken from (or, if there was none, where
  it was taken), day and night. Once inside that village it is free. Put it back in chains on the
  way and the escape is over. A captive not loaded at midnight rolls nothing that night.

### The trailer

Vanilla Wheels' trailer carries captives as it carries animals:

- Open its doors (crouch and right-click a door), then **right-click the trailer with an empty
  hand** (or a lead or a chain) while you lead villagers in chains. Up to four board, nearest
  first, standing in the trailer's bed and turning with it.
- They ride in chains with nobody holding them.
- To let everyone out, crouch and right-click the open doors with a **lead or a chain** in hand.
  They step out behind, still in chains: take their chains to lead them on.

### What a worker wears

Dress any worker of yours (hired, captive, in chains, or a child) in the Worker Screen's four
slots. Click a piece on, or shift-click it from your inventory into its slot.

- **What fits.** A slot takes whatever you could wear there yourself: armour, a turtle shell, a
  carved pumpkin, a mob head, an elytra, and Lucky's Wardrobe's clothes.
- **Protection.** It protects as on you: armour points, toughness, knockback resistance and
  enchantments.
- **Wear.** Armour wears out as yours does. Each hit takes durability off every piece, and a
  falling block wears the helmet. A worn-out piece breaks off, and nothing replaces it until you
  put another on. Workers never dress themselves. Unbreakable pieces and Lucky's Wardrobe's clothes
  never wear.
- **Curse of Binding** keeps a piece on, as on you, except in creative.
- **Only you.** Only the owner can open the screen, within eight blocks. If the worker dies, goes
  free or is led away while the screen is open, nothing more can be taken through it.
- **How it looks.** Anything on its legs takes the villager's robe off, so leggings and trousers
  show. A chestplate alone goes over the robe. Anything on its head hides its trade's hat.
- **Any villager.** Armour shows on every villager, so a piece a dispenser put on a free villager
  shows too.

When a worker stops being yours:

| How | What it wore |
|---|---|
| It dies, or turns into a zombie villager | Drops with its other things. A piece with Curse of Vanishing is gone. |
| You set it free | It takes everything off and drops it where it stands. |
| It escapes, or the law frees it | It leaves wearing it. |

A piece put on through the screen always drops whole when the villager dies, whoever it belongs
to by then. Catch the escapee again, or kill it, to get your gear back.

### Meals

Workers grow hungry and eat breakfast and dinner. Hired workers and captives alike; children and
free villagers don't hunger.

- **Hunger** runs from 20 down to 0, like yours. A waking hour costs a point and a sleeping hour
  half a point, so two meals a day keep a worker fed. Below half it works slower, down to half
  speed just short of 0. At 0 it stops working until it has eaten. Hunger never kills.
- **Breakfast (0–2000) and dinner (10000–12000):** a worker that isn't full eats once in each.
  Below half at any other time, it breaks off its work, or the meeting, to eat. It never eats
  asleep, in chains, while escaping, or without a bed.
- **Where the food comes from,** the first that has some:
  1. **Its home chest:** the chest (or barrel, or any storage of 18 slots or more) nearest its bed,
     within 8 blocks.
  2. **The canteen:** the chests of any of your Work Posts within 48 blocks of its bed, nearest
     first. Your cook's kitchen feeds everyone.
  3. **Cooking:** ingredients from its home chest, cooked at a free furnace, smoker, campfire,
     stove or Farmer's Delight pot within 16 blocks of the home chest, or bread and stews made at
     a crafting table. Fuel comes from the home chest. A furnace or pot you are using is left
     alone.
  4. **Raw food,** only when nothing near home can cook it.
- **What it eats:** ready food (bread, cooked meat, carrots, Farmer's Delight's meals), the most
  plentiful first, one at a time until full, never one it would overshoot on by more than half.
  - It never eats food with a harmful effect (rotten flesh, raw chicken, suspicious stew).
  - It never eats what `#serfdom:not_eaten` lists: golden apples, chorus fruit, Farmer's
    Delight's dough, pasta and crust.
  - Raw meat and potatoes are cooked first when they can be.
- You see it eat, food in hand, with crumbs. Bowls and anything it cooked but didn't eat go back to
  its home chest.
- **Buying food.** With no food at all at home or in the canteen, a worker that isn't full and
  holds an emerald walks to a For Sale block within 64 blocks of its bed and buys the cheapest food
  it will pay for, enough to fill up; it eats there and takes the rest home. Captives never buy. See
  [The market](#the-market).

### The market

**Every villager has a purse:** a number of emeralds, at most 64. Wandering traders have none.

- **Trading with you.** A villager pays for what it buys from you out of its purse. A trade it
  can't pay shows sold out (the red cross) and gives nothing; the emeralds you pay it go into its
  purse, and past 64 they are lost (the sale still goes through). The trade screen shows its purse
  over the panel's corner. A sold-out trade of this kind is only closed while you trade: restocking
  and prices are vanilla's.
- **Where purses start.** Every villager that was in the world before this version, and every
  villager a newly generated village brings, starts full (64). One born, cured, hatched from an egg,
  summoned or spawned starts at 4.
- **The morning deposit.** The first time a free or hired villager is seen in a morning (0–2000),
  if its purse holds fewer than 4 it gets 2. A villager not loaded that morning misses it. Captives
  and children never get it.
- **The hire fee** goes into the hired villager's purse.

**The For Sale block** is a sign over a chest over an emerald, shaped. Place it, then right-click
it:

- **Stall tab.** Click an item onto the slot at the top left: that is what it sells, components and
  all (an empty click clears it). Set how many a sale and the price of a sale (1 to 64 each; shift
  for eight at a time). Its 18 stock slots take only that item; its 9 proceeds slots hold the
  emeralds it takes, and only give. It is open while its stock holds a sale and its proceeds can take
  a price.
- **Ledger tab.** Each of the last seven days' sales, emeralds, and the visits that came to nothing
  (too pricey, couldn't afford, not interested), and the latest visits: who (by trade), when, and
  what they did.
- **Above it** the item it sells turns slowly. Look at it within eight blocks to read "8 Bread for 1
  emerald", or "sold out".
- **Hoppers and pipes** fill its stock (that item only) and take its emeralds.
- **Only you** open it or break it; anyone else is told whose it is and what it sells. Explosions
  leave it. Broken, it drops its stock and proceeds.

**Who shops, and when.** Free and hired villagers, grown, with a bed: a free villager from 9000 to
11000, a hired worker in its meeting hours (8000–10000), while it holds an emerald and has a sale
left today (at most three a day). Captives, children and villagers in chains never shop. A villager
whose bed is in a village someone bought with Village Deed shops only at the owner's stalls and those
of the players the owner trusts.

- **For a need,** once each of those times: it goes to whatever sells its need cheapest within 64
  blocks of its bed (among those it can afford a sale at): a For Sale block, or a free villager
  selling (see below). At a stall it waits its turn (one customer at a time), looks it over, and buys
  or not.
- **Window shopping.** When nothing in reach sells anything it needs, it walks to the nearest stall
  it hasn't seen today and looks: something it needs it judges as a need; something it wants (see
  below) it judges for its taste; anything else gets a glance, and the ledger says "not interested".
  Then the next stall it hasn't seen, through its social time. Every stall it goes to counts as seen
  for the day.

- **What a free villager needs.** A free villager keeps a household: what it has at home, saved on
  it (a town house has no chest). Its trade's needs list says how many of each it keeps and how many
  it uses a day: food for everyone (keeps 4, eats 2), a farmer's hoe and bone meal, a butcher's raw
  meat and coal, a smith's coal and iron, a librarian's paper, and so on. Each morning it uses its
  share, and whatever falls short of what it keeps it goes to buy.
- **What a worker needs.** Food, when the ready food in its home chest and the canteen fills less
  than a day (20 points), which goes to its home chest; and whatever its post shows it lacks (its
  job's tool, fuel, or what its stock rows are short of), which it puts in the post's chests as its
  job sorts. It spends its own purse on it.
- **What a villager wants.** Every villager has a taste for four kinds of goods (food, tools, decor
  and luxury): a multiplier from 0.5 to 1.5 for each, drawn from the villager itself, so it never
  changes and no two are alike, and leaning by trade (a butcher toward food, a mason toward decor, a
  toolsmith toward tools, a cleric toward luxury...). It likes a kind it rates above 1, and its
  favourite is the one it rates highest. It wants up to three of anything with a base value in a kind
  it likes, counted at home (its household, or a worker's home chest; a worker whose home chest is
  full or missing wants nothing). What it buys for a want goes home with it.
- **Climate.** A village's climate comes from the biome at a villager's bed: hot (desert, savanna,
  badlands, jungle), cold (the snowy biomes) or temperate. Some goods come from a hot climate (cactus,
  terracotta, acacia and jungle wood, bamboo, cocoa, melons, tropical fish...) or a cold one (snow,
  ice, salmon, goat horns); a villager pays half again for goods from a climate not its own.
- **What it will pay:** the item's base value × its climate's bonus (1.5 for foreign goods) × its
  taste for the item's kind (an item of no kind: 1; of two kinds: the higher) × 1.5 if it needs it.
  At or under that it buys; above it, its chance falls in a straight line to nothing at half again
  more. A purse short of one sale can't afford it. It buys as many sales as cover what it needs or
  wants, while it can pay and the stall has them.
- **What you see.** A bargain (at or under the item's base value): green sparkles and a "yes". Any
  other purchase: a "yes". Something from its favourite kind: it celebrates and hops besides. Too
  pricey: the head shake and a "no". Can't afford: it looks at an emerald in its hand. Not interested:
  a glance, and on to the next. Then it walks home with what it bought in its arms.
- **Villagers selling to villagers.** A free villager sells what its trade makes from its own
  inventory: in practice a town farmer's bread, potatoes, carrots and beetroot, keeping three bread,
  eight potatoes and eight carrots for itself. It sells at the item's base value, in lots worth whole
  emeralds (six bread for one). A villager who needs it, and finds the farmer cheapest, walks over
  (following it if it moves), and pays from its purse into the farmer's. Hired workers and captives
  never sell.
- **Base values,** in emeralds an item: the data packs' values first, then what villagers' own price
  lists ask or pay for it (the median), then, for food, bread's price for each point it fills.
  Anything else has none, and nobody buys it.
- **Your workers never sell your goods:** what is in your posts' chests stays yours. Sell through a
  stall; your workers shop there like anyone.

### Raids

A base with owned villagers in their beds is a village to a raid, as any village is. When a raid
starts (the bell, before the first wave), every owned villager, hired or captive, grown, out of chains
and not running home, arms itself from its home chest and its post's chests:

- **What it takes:** the best ranged weapon it has ammunition for (a gun, a crossbow or a bow), with up
  to 64 rounds or arrows; and the best melee weapon (a sword, an axe, a mace, a trident) as a backup.
  Best is the most damage a second, and a ranged weapon with ammunition always comes first: villagers
  are fragile. Workers sharing a chest take first come, first served.
- **Never a launcher:** the rocket launcher stays in the chest. Its rockets would blow holes in your
  base.
- **Guns** reload from loose rounds the gun takes. Magazines stay in the chest: Ranged Weapons Mod's
  magazines aren't the protocol's yet.
- **Fighting:** it goes for the raiders it can see within 32 blocks, never chasing past 48 blocks
  from its post or bed. A bow draws as a player's does, a crossbow charges, a gun fires at its rate from
  its range; a sword hits as in a player's hand. It never shoots while you, a villager, a golem, a guard
  or a pet is within a block of its line of fire.
- **Out of ammunition** it draws its melee weapon; with none, it hides as vanilla's villagers do. One
  with nothing to fight with hides from the start. Free villagers always hide.
- **After the raid** it puts each weapon and what ammunition is left back in the chest it came from
  (else its home chest, else on the ground) and goes back to its day. Armour stays on.
- **While it defends** it doesn't panic, eat, shop or trade. If it dies, or is set free, freed by
  the law or runs away, it drops what it took where it stands.

### Children

A child of two villagers you own (hired) is yours, hired, and keeps the bed it was born into. It
can't take a job until it grows up. A child of your villager and someone else's, or a free one, is
free.

### The worker's day

| Time | What it does |
|---|---|
| 0–2000 | Idle |
| 2000–8000 | Works at its post |
| 8000–10000 | Meets at a bell, if one is in reach; or goes shopping, for something it needs or to look |
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
  - Two woodcutters on one post fell two trees: each holds the tree it is felling.
- **Farming** (needs a hoe).
  - The worker harvests ripe crops on farmland and replants each from its own harvest.
  - It also takes pumpkins and melons from their stems, picks Farmer's Delight tomatoes and cuts
    ripe rice.
  - It sows bare farmland as [Sowing](#sowing) says.
  - It never tills new ground.
  - It works a plot at a time, and holds it, so two farmers never work one plot (see
    [Shared farms](#shared-farms)).

A worker takes its tool from the post's chests and wears it out as a player would. When it
breaks, the worker takes the next. Work goes at a player's speed with the same tool. A worker
whose profession matches its job works 25% faster: farmers at farming, More Villagers'
woodworker (or the fletcher, where there is none) at woodcutting, butchers at cooking, and
armorers, toolsmiths and weaponsmiths at the blacksmith's.

### Shared farms

- **Touching posts are one farm.** Your farming posts whose areas touch or overlap (their outlines
  meet) are one farm, linked through one another. Each keeps its radius, its chests and its four
  workers, and a post with nobody on it is just more field. A bigger farm is more posts: nine at
  radius 16, three by three, cover 99 by 99 blocks. The post's screen says "Farm: 9 posts, 7
  farmers".
- **Who works where.** The field is cut into plots, the 8 by 8 columns of the world's grid. A farmer
  works one plot at a time and holds it, and no other farmer works a held plot. It works its own
  post's area first, nearest plot first, and helps anywhere on the farm when its own area has
  nothing left. It looks again after every plot, so it goes home as soon as there is work there.
- **Filling in.** A farmer lets go of its plot when the plot is done, when its shift stops (a meal, a
  raid, the end of the day), and when it starves, dies or loses its job. Otherwise (unloaded, kept
  trading, stuck) its hold lapses after ten seconds. The next free farmer takes the plot.
- **Chests.** A farmer puts its harvest in the chests of the nearest post on the farm that have
  room, sorted as in [Storage](#storage), and takes a hoe or seed from the nearest that holds one.
  Put chests at one post only to make it the farm's barn.

### Sowing

Farmers sow bare farmland (farmland with nothing on it). They never replace a crop and never till:

1. **A spot that has grown a crop gets that crop again.** Plant a carrot in a wheat field and that
   spot stays a carrot spot.
2. **A spot that has never grown anything copies its neighbours only when they all agree:** every
   crop within 4 blocks of it, at its height, must be of one kind. Where two kinds meet, the spot is
   left for you. Plant one crop and they spread it, a few blocks each pass. To keep new ground for
   another crop, plant one of that crop in it.
3. **Stems are never copied,** so a never-planted spot within 4 blocks of a melon or pumpkin stem
   stays bare: the fruit needs the ground beside its stem.
4. **A spot waits a minute bare before anyone sows it,** so your own planting comes first.

Seed comes from what the farmer carries (its harvests' spare seed), then from the farm's chests.
When sowing is all that is left and there is no seed anywhere, it shows the empty crate. Rice grows
in water, not on farmland, and is never sown.

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

The cook's chests are the canteen: workers whose home chests are empty eat from them (see
[Meals](#meals)).

### Storage

A worker carries eight stacks. It puts them away when it is full and at the end of its shift, in
its post's chests (on a farm, those of the nearest post with room). Each item goes to:

1. a chest that already holds that item;
2. else the chest whose items share the most tags with it (seeds with seeds, logs with logs);
3. else the overflow chest, the one nearest the post.

### What a worker needs

When a worker lacks something, an icon floats over its head. You see it within eight blocks.

| Icon | Meaning |
|---|---|
| A bed | It has no bed. |
| An empty bowl | It is hungry: below half. |
| A struck-out axe | There's no tool for its job in the post's chests (a cook's knife too). |
| A struck-out anvil | No station in the area that a worker can use makes what the list wants. |
| A flame over coal | No fuel. |
| An empty crate | The chests are short of what the list needs, or of the seed a farmer would sow. |
| A full chest | Nothing it carries has anywhere to go. |

Work starts again by itself once the need is met. The stock list says which row lacks what.

Hired workers can summon iron golems as other villagers do: it takes five that have slept in the
last day gossiping together, or three panicking at a zombie. Captives never do (Rusty's call).

A worker that dies, or is turned into a zombie villager, drops the tool it keeps, everything it
carries, everything it wears and the chain on it. A cured one is a free villager.

## Server settings

`config/serfdom-server.toml`:

| Setting | Default | What it is |
|---|---|---|
| `hire_per_level` | 8 | Emeralds per profession level. |
| `offer_seconds` | 30 | How long a [Hire] line stays clickable. |
| `pick_seconds` | 30 | Time to click a bed or a post after the button. |
| `action_floor_ticks` | 10 | The shortest any action takes, so work stays visible. |
| `leaves_per_tick` | 4 | How fast a felled tree's leaves are cleared. |
| `sow_after_seconds` | 60 | How long a spot of farmland must be bare before a farmer sows it. |
| `craft_ticks` | 40 | Ticks one craft, cut or repair takes (a matching profession is 25% faster). |
| `capture_ticks` | 40 | How long the chain is held on a villager to take it. |
| `capture_reach` | 2.0 | How far from the villager the hold keeps going. |
| `escape_chance` | 0.05 | A captive's chance each night of getting up and walking home. |
| `captive_slowdown` | 0.10 | How much slower a captive walks and works. |
| `humming` | true | Captives hum their work song at work. |
| `hunger` | true | Workers grow hungry and eat. Off: nobody hungers and work never slows for it. |
| `hunger_per_hour` | 1.0 | Hunger points a worker loses each waking hour; half that asleep. |
| `hungry_floor` | 0.5 | How fast a worker just short of starving works, as a share of its fed pace. |
| `economy` | true | Purses, the For Sale block's customers and shopping. Off: villagers trade as vanilla's, nobody shops, no deposit. |
| `purse_cap` | 64 | The most a purse holds. |
| `deposit` | 2 | The morning deposit. |
| `deposit_below` | 4 | A purse under this gets the deposit; a villager born or cured starts with this many. |
| `shop_reach` | 64 | Blocks from its bed within which a villager shops. |
| `sales_per_day` | 3 | The most sales a villager buys in a day, meals included. |
| `need_bonus` | 1.5 | What a villager will pay for something it needs, as a multiple of its base value. |
| `climate_bonus` | 1.5 | What a villager will pay for goods from a climate not its own, as a multiple. |
| `taste_spread` | 0.5 | How far a villager's taste for each kind of goods runs either side of 1. |
| `wants_each` | 3 | The most of one item a villager wants for its taste, at home. |
| `defence` | true | Owned villagers arm from their chests in a raid. Off: they hide as vanilla's do. |
| `defence_reach` | 48 | Blocks from its post or bed past which a defender never chases a raider. |
| `ammo_carried` | 64 | The most arrows or rounds a defender carries out of its chests. |
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

The market's data (D-0006):

- **Needs, taste and wares:** `data/<namespace>/serfdom/needs/<name>.json`, one profession's file
  (`"*"` is everyone's, first): `{"profession": "minecraft:farmer", "needs": [{"name": "hoe", "tag":
  "minecraft:hoes", "keep": 1, "per_day": 0.125}], "taste": {"food": 0.5, "tools": 0.5}, "sells":
  {"minecraft:bread": 3}}`. A need names a `tag`, `items`, or `"food": true` (any ready food).
  `taste` leans each kind of goods from -1 to 1 (everyone's and the profession's add up); `sells` is
  what a free villager of the trade sells from its inventory, and how many of each it keeps (a tag
  with `#`). Every field but `profession` may be left out.
- **Kinds of goods and climates** are item tags: `#serfdom:taste/food`, `/tools`, `/decor`,
  `/luxury`; `#serfdom:climate/hot`, `/cold`. A village's climate is the biome tags
  `#serfdom:climate/hot` and `/cold`.
- **Values:** `data/<namespace>/serfdom/values/<name>.json`, `{"values": {"minecraft:bone_meal":
  0.1, "#c:gems": 3}}`, emeralds an item, a tag with `#`. A value of 0 means nobody buys it. Files
  are read in order of id, entries in file order; an item's own entry beats a tag's.

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
  skipping every section whose palette lacks a log, a crop or farmland, never block by block.
  Storage is the area's block entities with an item handler of 18 slots or more, so Create's vaults
  count; that index is rebuilt when a block changes in the area.
- **Fields** (D-0008, `job/Field`). A farming post's area is swept into its field: the ripe crops and
  the spots due for sowing, by plot, what each spot last grew, and since when each has been bare
  (none of it saved). An area is swept the first time it is read; after that the server sweeps one
  area a tick, the most overdue of those read in the last 200 ticks and swept 100 or more ago. A
  radius-16 area full of wheat takes about a millisecond.
- **Farms and holds** (`domain/Farm`, `post/Farms`, `domain/Holds`, `job/Holding`). Which posts are one
  farm (cached until a post loads, unloads, or takes a new job, radius or owner), the plots, the next
  plot (its own area's first, then the nearest), and who holds which plot or tree: renewed every tick
  a worker walks or works for it, let go when its task ends or its shift stops, lapsing 200 ticks
  after its last renewal; not saved.
- **Sowing** (`domain/Sowing`): the four rules above, worked out for an area in one pass by counting
  each kind over a sliding 9 by 9 window.
- **Paths.** Every villager walks with a navigation that is vanilla's, except that for a worker a
  closed fence gate counts as a wooden door, and that a Work Post is a fence to any path that does
  not end at it: its pole is no full block, so vanilla would plan straight over it. A behaviour opens
  and closes gates as vanilla's does doors. It replaces the navigation vanilla's constructor made, so
  it sets what that one had: doors and floating (D-0010; without floating, paths ran along the bottom
  of water).
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
- **The chain** (D-0003) is vanilla's leash on a villager, drawn in iron, plus the worker's saved
  `cuffed` flag, synced to the client (`serfdom:cuffed`) for the cuffs layer and the trailer. The
  leash is who holds the chain; the flag is whether it is on. When vanilla drops a leash's lead
  (snapped, holder dead or gone), a mixin drops nothing for a villager in chains. The gestures are
  a pure table (`domain/Chain`).
- **The capture** (`Captures`, `domain/Capture`): the server starts the player's use of the chain
  and judges the hold every server tick: present, still in use, within reach, looked at, done.
  Then `Workers.capture` makes the captive, commits Thief's HEAVY crime through `ThiefCompat`, and
  asks Village Law's `Cases.openHere` which case it joined; `Remedies` (overworld saved data,
  `domain/Remedy`) owes the captive to that case, frees what a PAID case is owed (later, as it
  loads, if it isn't loaded) and forgets what a FLED case was owed.
- **Captives' brains**: three more activities, `stay` (idle within three blocks of where it began),
  `held` (in chains: still) and `escape` (`RunHome`: sixteen-block legs toward home); a core
  behaviour (`CaptiveNight`) rolls the night's escape (`domain/Escape`, seeded by the villager and
  the day) and gets it up. The slowdown is a movement modifier, `serfdom:captive_pace`, and a
  factor in `domain/Pace`.
- **Golems and cats**: a mixin makes `Villager.wantsToSpawnGolem` false for a captive, which every
  summons asks; another takes the loaded captives' beds off `CatSpawner`'s count.
- **Children**: a mixin on `Villager.getBreedOffspring` makes a child of two of one owner's
  villagers theirs (`domain/Birth`); one on `VillagerMakeLove.giveBedToChild` records its bed.
- **The work song** (`domain/WorkSong`, `Humming`): four phrases in natural minor, each note
  vanilla's villager `idle2` at the note's pitch (sound event `serfdom:captive.hum`, eight-block
  range). `devtools/sound/work_song.py` renders them to `run/work_song.wav` to be heard.
- **The trailer**: `compat/WheelsCompat` registers a cargo rule with Vanilla Wheels 1.11
  (`api/CargoRules`, its D-0029): a villager in chains rides as cargo and loads while its chain's
  holder clicks.
- **The wearing slots** (D-0004):
  - `WorkerMenu` is a menu over the villager's own four armour slots and the player's inventory,
    so what it wears is vanilla's armour.
  - The rules are pure: `domain/Wardrobe` (who, what fits, Binding, shift-clicks) and
    `domain/Parting` (gear on each way out).
  - The server asks whether the menu may still be used on every click and tick.
  - The menu opens with vanilla's packet, and the view payload binds the client's menu right after.
  - A piece put on gets drop chance 2.0, vanilla's mark for "always drops whole".
  - `mixin/VillagerWearMixin` gives every villager a player's `hurtArmor` and `hurtHelmet`.
- **Meals** (D-0005):
  - Hunger is pure (`domain/Hunger`), saved in its own attachment (`serfdom:belly`) and drained
    every 100 ticks by a core behaviour (`MealTime`).
  - That behaviour turns the brain to the `serfdom:meal` activity when `domain/Meals` says a meal
    is due, as vanilla's panic does. The meal (`HaveMeal`) gives the schedule back, so idle time,
    breeding and the meeting are untouched.
  - Each bite is chosen by `domain/Menu` from what `job/Kitchen` reads: the home chest, the
    canteen (`Posts.near`), and the free stations near home. Dishes are cooked with 1b's recipe
    book, fuel arithmetic and station loading.
  - `Jobs.speed` takes hunger's work speed, and the shift waits while starved.
- **The market** (D-0006):
  - **Purses** (`market/Purses`, `domain/Purse`) are an attachment on every villager
    (`serfdom:purse`, with its shopping day). NeoForge's `FinalizeSpawnEvent` says how a villager
    was made: a structure's starts full, any other spawn at the line; a villager joining with no
    purse at all (saved before) gets a full one. A villager generated with its chunk does not join
    as "loaded from disk", so the join flag can't tell.
  - **Trades:** a mixin closes the offers the purse can't pay as a player starts trading
    (`domain/Till`), a transient flag `MerchantOffer.isOutOfStock` reads; the flag goes with the
    copy the offers packet makes. NeoForge's `TradeWithVillagerEvent` moves the emeralds and the
    offers are sent again.
  - **The stall** (`market/ForSaleBlockEntity`) is a point of interest (`serfdom:for_sale`), so
    shoppers find it through the game's index, never by scanning; its item handler takes the sold
    item into the stock slots and gives emeralds from the proceeds.
  - **Shopping:** a core behaviour (`ShopTime`, on free villagers' vanilla brains too) pays the
    deposit, uses the household, and in social time plans a trip (`market/Shoppers`,
    `domain/Shopping`); the trip (`GoShopping`, the `serfdom:shop` activity) takes over as a meal
    does and gives the schedule back. At the counter `domain/Verdict` judges, `market/Counter` makes
    the sale and writes `domain/Ledger`. What it carries home is saved (`serfdom:basket`), one piece
    shown in its hand marked so nothing takes it for a tool or drops it.
  - **Values** (`market/Prices`, `domain/Values`) are worked out once the server starts, from the
    data and every profession's price list, tried with a villager never added to the world. While
    they are, a map structure search finds nothing (`mixin/ServerLevelMixin`, D-0009), so a map
    listing (vanilla's cartographer, Backport's explorer maps, any mod's that searches through the
    level) gives no offer, searches nothing and saves no map. The log line `Serfdom: base values for
    N items (M from the price lists, K map searches refused), worked out in T ms` says how long it
    took: about 250 ms with the box's 99 mods on a desktop (it was 10 seconds there before D-0009),
    and a much larger T means some listing does slow work another way.
  - **4b:** `domain/Shopping.decide` says what a villager does in its social time (a need first,
    then a look at a stall it hasn't seen, saved with its purse as `seen`). `market/Tastes` reads a
    villager's taste (`domain/Taste`, drawn from its UUID by a hash written out in the domain, so it
    never changes), its climate (`domain/Climate`, the bed's biome) and what it wants.
    `market/Peddlers` finds free villagers selling (`domain/Peddler`: spare stock, lots in whole
    emeralds) by an entity search around the buyer's bed, only when it has a need to shop for.
  - **Storage** never counts a For Sale block, though its handler has 27 slots: neither a worker's
    home chest nor a post's storage.
  - **Paths** go around a For Sale block, never over it: it tells the pathfinder it is no open
    ground and is a fence (NeoForge's `getBlockPathType`). Vanilla would plan to jump onto it, its
    sign making it a full block high, and a villager could not stand there past the sign.
- **Raids** (D-0007):
  - A core behaviour (`RaidDuty`) turns an owned villager's brain to the `serfdom:defend` activity
    when a raid is on where it stands (vanilla's own `getRaidAt`). The defence (`Defend`) asks
    `domain/Defence` what to do every half second: arm, fight, stand by, hide, put back.
  - What to take is `domain/Armoury`, read off the chests by `defence/Armouries`. What it carries is
    saved on it (`serfdom:arms`, `defence/Arms`), each thing with the chest it came from.
  - Guns go through the Ranged Weapons protocol (`compat/GunsCompat`, compiled against it, never
    nested); a gun's shot is the protocol's, aimed eye to middle.
  - The line of fire is pure (`domain/LineOfFire`): a friend's box grown by a block, against the
    segment.
  - Villagers get an attack damage attribute of 1, a fist's, so a sword hits as in a player's hand.
  - Out defending, vanilla's panic and raid triggers are wrapped (`Unless`) so they never take its
    brain from it.
- **The armour layer** (`client/VillagerArmourLayer`):
  - Vanilla's `HumanoidArmorLayer` draws on a stand-in player model posed from the villager's
    model each frame, by `domain/Fit`: the helmet lifted 2 pixels about the head's own pivot (and
    grown with Guard Villagers' big child heads), the chest 1.32× deeper, and sleeves at 0.7 on the
    crossed upper arms.
  - The fit is only in poses and scales, which is all NeoForge passes to a mod's own armour model,
    so Lucky's Wardrobe's clothes fit as armour does.
  - `client/Dress`, with a mixin after the profession layer, hides the robe under leggings and the
    hat under anything on the head.
  - An elytra hangs 1.5 pixels farther back.

Village Deed, Farmer's Delight, Ranged Weapons Mod, Thief, Village Law 1.1+ and Vanilla Wheels
1.11+ are optional: without them there are no bought villages (and no stalls barred from one); no tomatoes, rice, pot, stove or
board; no weapons workbench and no guns in a raid; no crime in a capture; no case for it; and no trailer. Carried, the
inventory protocol, is nested in the jar.

## Building

```
./gradlew clean build
```

This runs the JUnit domain tests, the GameTests and the photo booth.

- Carried and the Ranged Weapons protocol come from mavenLocal: run `./gradlew publishToMavenLocal`
  in `minecraft-carried` and `minecraft-ranged-weapons` first.
- The gametest server runs Village Deed from the sibling repo's `build/libs/villagedeed-2.2.0.jar`,
  Backpacks+ from `../minecraft-backpacks-plus/build/libs/backpacksplus-0.7.0.jar`, Ranged Weapons
  Mod 2.12.0, Metals and Materials 1.0.3, Village Law 1.1.0, Vanilla Wheels 1.11.0 and Trailer
  2.4.0 from their repos' `build/libs` (build those first), and Thief, Guard Villagers,
  Farmer's Delight and Lucky's Wardrobe from Modrinth's maven.
- The booth also loads Curios, which Lucky's Wardrobe's client needs, from its own `run/booth/mods`
  folder (`prepareBoothMods`). On the gametest server it would send payloads to the mock players.
- The booth needs a display: the Xephyr recipe in the workspace's `AGENTS.local.md`. Run the
  build with that recipe's `DISPLAY=:7` and Mesa variables, never with the desktop's display in
  the environment: the build opens the booth's window wherever `DISPLAY` points. Without a display,
  add `-PskipBooth`.
- The GameTests log every worker's plan with `-Dserfdom.trace=true`, which the gametest run sets.
  For the booth, `-PboothTrace` does the same, and `-PboothScene=farm` runs the farm scene alone.

The art is drawn by `devtools/art/art.py`:

```
uv run --no-project --with pillow python devtools/art/art.py
```
