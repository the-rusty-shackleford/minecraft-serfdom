/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Appetite;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Fuel;
import com.chunkworks.serfdom.domain.Meals;
import com.chunkworks.serfdom.domain.Menu;
import com.chunkworks.serfdom.domain.Recipes;
import com.chunkworks.serfdom.domain.Station;
import com.chunkworks.serfdom.job.Kitchen;
import com.chunkworks.serfdom.job.RecipeBook;
import com.chunkworks.serfdom.job.Stations;
import com.chunkworks.serfdom.job.Storage;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** A worker's meal (D-0005), the whole of its meal activity: bite by bite it asks {@link Menu} what
 * it has next, then walks to its home chest or a post's chest and eats, or fetches a dish's
 * ingredients and fuel from home, cooks them at a free station near home, waits by it, collects and
 * eats. It eats with the food in its hand, vanilla's eating sound and crumbs, a player's 1.6 seconds
 * a bite. It carries its meal in its hands, never in its own inventory, so vanilla's sharing of food
 * between villagers never sees it; what is left (a cooked dish not eaten, bowls) goes back to the
 * home chest at the end, or to the ground. With no food anywhere and an emerald in its purse, it
 * walks to a stall near its bed and buys food to eat (D-0006). When it has eaten all it wants, or finds nothing, it
 * notes the meal ({@link Meals#after}) and turns back to its schedule. A dish left cooking when it
 * gives up (too slow, or the meal is cut short) stays in the station. */
public final class HaveMeal extends Behavior<Villager> {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    private static final float SPEED = 0.6F;
    private static final int WALK_LIMIT = 600, COOK_LIMIT = 1600, BITE = 32, POLL = 20;
    private static final double REACH = 2.5;

    private enum Step { CHOOSE, WALK, EAT, COOK, QUEUE, DONE }
    private Step step = Step.CHOOSE;
    private BlockPos dest;
    private Runnable arrive;
    private long since;
    private final SimpleContainer hands = new SimpleContainer(9);
    private Optional<BlockPos> home = Optional.empty();
    private List<WorkPostBlockEntity> canteen = List.of();
    private boolean ate, ended;
    private ItemStack bite = ItemStack.EMPTY;
    private int biteLeft, nutrition;
    private Optional<String> leaves = Optional.empty();
    private BlockPos station;
    private Station kind;
    private String dish;
    private int expect, got;
    private Optional<com.chunkworks.serfdom.market.Shoppers.Plan> buying = Optional.empty();

    public HaveMeal() { super(ImmutableMap.of(), 4800); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager worker) { return worker.getBrain().isActive(Serfdom.MEAL.get()); }

    @Override protected boolean canStillUse(ServerLevel level, Villager worker, long gameTime) {
        return !ended && worker.getBrain().isActive(Serfdom.MEAL.get()) && Appetite.hungers(worker) && !Workers.of(worker).cuffed() && !worker.isSleeping();
    }

    @Override protected void start(ServerLevel level, Villager worker, long gameTime) {
        ended = false;
        ate = false;
        buying = Optional.empty();
        bite = ItemStack.EMPTY;
        hands.clearContent();
        Workers.stash(worker);
        home = Kitchen.home(level, worker);
        canteen = Kitchen.canteen(level, worker);
        step = Step.CHOOSE;
        if (TRACE) LOG.info("Serfdom trace: {} sits down to a meal at {} (hunger {}), home {}, {} posts near", worker.getId(), level.getDayTime() % 24000,
                Appetite.of(worker).hunger().points(), home.map(BlockPos::toShortString).orElse("none"), canteen.size());
    }

    @Override protected void tick(ServerLevel level, Villager worker, long now) {
        switch (step) {
            case CHOOSE -> choose(level, worker, now);
            case WALK -> walk(level, worker, now);
            case EAT -> eat(level, worker);
            case COOK -> cook(level, worker, now);
            case QUEUE -> queue(level, worker, now);
            case DONE -> {}
        }
    }

    @Override protected void stop(ServerLevel level, Villager worker, long gameTime) {
        buying.ifPresent(p -> { if (level.getBlockEntity(p.stall()) instanceof com.chunkworks.serfdom.market.ForSaleBlockEntity s) s.letGo(worker.getUUID()); });
        buying = Optional.empty();
        if (!bite.isEmpty()) hands.addItem(bite);
        bite = ItemStack.EMPTY;
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        Kitchen.putBack(level, worker, home, hands);
        if (!ended) note(level, worker);
        worker.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        var brain = worker.getBrain();
        if (brain.isActive(Serfdom.MEAL.get())) brain.setActiveActivityIfPossible(brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L)));
    }

    // ---- choosing ---------------------------------------------------------------------------

    private void choose(ServerLevel level, Villager worker, long now) {
        var hunger = Appetite.of(worker).hunger();
        // A dish just cooked, carried, comes first.
        var carried = new HashSet<String>();
        for (int i = 0; i < hands.getContainerSize(); i++) if (!hands.getItem(i).isEmpty()) carried.add(RecipeBook.key(hands.getItem(i).getItem()));
        var foods = Kitchen.foods(carried, worker);
        for (int i = 0; i < hands.getContainerSize(); i++) {
            var s = hands.getItem(i);
            if (s.isEmpty()) continue;
            var f = foods.get(RecipeBook.key(s.getItem()));
            if (f != null && Menu.edible(RecipeBook.key(s.getItem()), foods) && hunger.wants(f.nutrition())) { startBite(worker, hands.removeItem(i, 1), f); return; }
        }
        var free = home.map(h -> Kitchen.free(level, h, Kitchen.holdsFuel(level, h))).orElse(List.<Stations.Found>of());
        var facts = home.isPresent() ? Kitchen.facts(level, worker, hunger, home.get(), canteen, free)
                : new Menu.Facts(hunger, java.util.Map.of(), canteen.stream().map(p -> Storage.counts(level, p)).toList(),
                        Kitchen.foods(canteen.stream().flatMap(p -> Storage.counts(level, p).keySet().stream()).toList(), worker),
                        RecipeBook.rules(level, Kitchen.HEAT), Recipes.Rules.NONE, Kitchen.tableMeals());
        var c = Menu.choose(facts.buying(mayBuy(level, worker)));
        if (TRACE) LOG.info("Serfdom trace: {} at hunger {} chooses {} {} {}", worker.getId(), hunger.points(), c.kind(), c.item(), c.rule().map(Recipes.Rule::id).orElse(""));
        switch (c.kind()) {
            case NOTHING -> end(level, worker);
            case EAT_HOME, EAT_RAW -> walkTo(level, worker, home.get(), now, () -> takeAndBite(level, worker, home.get(), c.item(), facts));
            case EAT_POST -> {
                var post = canteen.get(c.post());
                var chest = Storage.holding(level, post, s -> RecipeBook.key(s.getItem()).equals(c.item()));
                if (chest.isEmpty()) { end(level, worker); return; }
                walkTo(level, worker, chest.get(), now, () -> takeAndBite(level, worker, chest.get(), c.item(), facts));
            }
            case COOK -> fetch(level, worker, c, free, now);
            case BUY -> buy(level, worker, now);
        }
    }

    // ---- buying (D-0006) ----------------------------------------------------------------------

    /** effects: true iff the worker may buy food: the economy is on, it is not a captive, it holds an
     * emerald and has a purchase left today. */
    private static boolean mayBuy(ServerLevel level, Villager worker) {
        if (!com.chunkworks.serfdom.market.Purses.on() || Workers.of(worker).captive()) return false;
        var saved = com.chunkworks.serfdom.market.Purses.of(worker);
        int perDay = com.chunkworks.serfdom.SerfdomConfig.SPEC.isLoaded() ? com.chunkworks.serfdom.SerfdomConfig.SALES_PER_DAY.get() : 3;
        return saved.purse().emeralds() >= 1 && com.chunkworks.serfdom.domain.Shopping.salesLeft(saved.day(), level.getDayTime(), perDay) > 0;
    }

    /** effects: walks to the stall near its bed with the cheapest food it can afford, to buy enough to
     * fill up; nothing found, the meal ends hungry. */
    private void buy(ServerLevel level, Villager worker, long now) {
        int short_ = (int) Math.ceil(com.chunkworks.serfdom.domain.Hunger.MAX - Appetite.of(worker).hunger().points());
        if (short_ < 1) { end(level, worker); return; }
        var want = new com.chunkworks.serfdom.domain.Shopping.Want(com.chunkworks.serfdom.domain.Household.FOOD, com.chunkworks.serfdom.market.Needs.readyFoods(level),
                short_, com.chunkworks.serfdom.domain.Shopping.Unit.POINTS, com.chunkworks.serfdom.domain.Shopping.Dest.HOME);
        buying = com.chunkworks.serfdom.market.Shoppers.choose(level, worker, List.of(want));
        if (buying.isEmpty()) { end(level, worker); return; }
        if (TRACE) LOG.info("Serfdom trace: {} goes to buy food at {}", worker.getId(), buying.get().stall().toShortString());
        walkTo(level, worker, buying.get().stall(), now, () -> { step = Step.QUEUE; since = level.getGameTime(); });
    }

    /** effects: waits its turn at the stall and buys there, the food into its hands; it gives up after
     * the shop's wait. */
    private void queue(ServerLevel level, Villager worker, long now) {
        var plan = buying.orElse(null);
        if (plan == null || !(level.getBlockEntity(plan.stall()) instanceof com.chunkworks.serfdom.market.ForSaleBlockEntity stall)) { end(level, worker); return; }
        worker.getLookControl().setLookAt(Vec3.atCenterOf(plan.stall()).add(0, 0.5, 0));
        if (!stall.serve(worker.getUUID(), now)) {
            if (now - since > GoShopping.QUEUE) end(level, worker);
            return;
        }
        var visit = com.chunkworks.serfdom.market.Counter.visit(level, worker, stall, plan.want());
        stall.letGo(worker.getUUID());
        buying = Optional.empty();
        if (visit.isEmpty() || !visit.get().outcome().reaction().bought()) { end(level, worker); return; }
        for (var s : visit.get().goods()) {
            var rest = hands.addItem(s);
            if (!rest.isEmpty()) worker.spawnAtLocation(rest);
        }
        step = Step.CHOOSE;
    }

    private void takeAndBite(ServerLevel level, Villager worker, BlockPos from, String item, Menu.Facts facts) {
        var s = Storage.takeOne(level, from, st -> RecipeBook.key(st.getItem()).equals(item));
        if (s.isEmpty()) { step = Step.CHOOSE; return; }
        startBite(worker, s, facts.foods().get(item));
    }

    // ---- eating -----------------------------------------------------------------------------

    private void startBite(Villager worker, ItemStack food, Menu.Food f) {
        bite = food.copyWithCount(1);
        nutrition = f.nutrition();
        leaves = f.leaves();
        biteLeft = BITE;
        worker.setItemSlot(EquipmentSlot.MAINHAND, bite.copy());
        step = Step.EAT;
    }

    private void eat(ServerLevel level, Villager worker) {
        if (--biteLeft % 4 == 0) {
            var look = worker.getLookAngle();
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bite), worker.getX() + look.x * 0.4, worker.getEyeY() - 0.25, worker.getZ() + look.z * 0.4,
                    4, 0.08, 0.05, 0.08, 0.05);
            level.playSound(null, worker, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.5F + 0.5F * worker.getRandom().nextInt(2),
                    (worker.getRandom().nextFloat() - worker.getRandom().nextFloat()) * 0.2F + 1.0F);
        }
        if (biteLeft > 0) return;
        var belly = Appetite.of(worker);
        Appetite.set(worker, belly.with(belly.hunger().eat(nutrition)));
        ate = true;
        leaves.ifPresent(l -> hands.addItem(new ItemStack(RecipeBook.item(l))));
        bite = ItemStack.EMPTY;
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        step = Step.CHOOSE;
    }

    // ---- cooking ----------------------------------------------------------------------------

    /** effects: walks home for the dish's ingredients and its fuel, then to the nearest free station
     * of the dish's kind to cook or craft it. */
    private void fetch(ServerLevel level, Villager worker, Menu.Choice c, List<Stations.Found> free, long now) {
        var rule = c.rule().orElseThrow();
        var at = free.stream().filter(f -> f.kind() == rule.station()).findFirst();
        var holder = RecipeBook.recipe(level, rule);
        if (at.isEmpty() || holder.isEmpty() || home.isEmpty()) { end(level, worker); return; }
        var recipe = holder.get().value();
        int times = c.times();
        int cookTicks = RecipeBook.cookTicks(recipe);
        ItemStack fuel = ItemStack.EMPTY;
        if (rule.station().burns()) {
            var be = level.getBlockEntity(at.get().pos());
            var slot = be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity f ? f.getItem(1) : ItemStack.EMPTY;
            var fuelItem = !slot.isEmpty() ? Optional.of(RecipeBook.key(slot.getItem())) : Kitchen.mostFuel(level, home.get());
            if (fuelItem.isPresent()) {
                var one = new ItemStack(RecipeBook.item(fuelItem.get()));
                int burn = Math.max(1, Stations.burnTicks(be, one));
                int inSlot = slot.isEmpty() ? 0 : slot.getCount();
                int units = Fuel.units(times, cookTicks, burn, Stations.burnLeft(be), inSlot, one.getMaxStackSize() - inSlot);
                if (units > 0) fuel = one.copyWithCount(units);
            }
        }
        boolean container = false;
        if (rule.station() == Station.POT) {
            int ingredients = 0;
            for (var i : recipe.getIngredients()) if (!i.isEmpty()) ingredients++;
            container = rule.cells().size() > ingredients;
        }
        var want = new LinkedHashMap<String, Integer>();
        for (var pick : c.picks()) want.merge(pick, rule.station() == Station.TABLE ? times : times, Integer::sum);
        if (!fuel.isEmpty()) want.merge(RecipeBook.key(fuel.getItem()), fuel.getCount(), Integer::sum);
        final var burnStack = fuel;
        final boolean lastIsContainer = container;
        walkTo(level, worker, home.get(), now, () -> {
            if (!Storage.take(level, home.get(), want, hands)) { end(level, worker); return; }
            walkTo(level, worker, at.get().pos(), level.getGameTime(), () -> cookAt(level, worker, at.get(), rule, c.picks(), times, burnStack, cookTicks, lastIsContainer, recipe));
        });
    }

    private void cookAt(ServerLevel level, Villager worker, Stations.Found at, Recipes.Rule rule, List<String> picks, int times, ItemStack fuel, int cookTicks,
                        boolean lastIsContainer, net.minecraft.world.item.crafting.Recipe<?> recipe) {
        worker.swing(InteractionHand.MAIN_HAND);
        if (at.kind() == Station.TABLE) {
            for (int n = 0; n < times; n++) {
                var taken = new ArrayList<ItemStack>();
                for (var pick : picks) taken.add(removeFrom(pick, 1));
                var made = taken.stream().anyMatch(ItemStack::isEmpty) ? Optional.<RecipeBook.Made>empty() : RecipeBook.grid(level, recipe, taken);
                if (made.isEmpty()) { for (var t : taken) hands.addItem(t); break; }
                hands.addItem(made.get().result());
                for (var l : made.get().leftovers()) hands.addItem(l);
            }
            step = Step.CHOOSE;
            return;
        }
        var stacks = new ArrayList<ItemStack>();
        for (var pick : picks) stacks.add(removeFrom(pick, times));
        var burn = fuel.isEmpty() ? ItemStack.EMPTY : removeFrom(RecipeBook.key(fuel.getItem()), fuel.getCount());
        if (!Kitchen.freeNow(level, at, !burn.isEmpty()) || stacks.stream().anyMatch(s -> s.getCount() < times)
                || !Stations.load(level, worker, at.pos(), at.kind(), stacks, burn, cookTicks, lastIsContainer)) {
            for (var s : stacks) hands.addItem(s);
            if (!burn.isEmpty()) hands.addItem(burn);
            if (TRACE) LOG.info("Serfdom trace: {} could not load {} at {}", worker.getId(), rule.id(), at.pos().toShortString());
            end(level, worker);
            return;
        }
        station = at.pos();
        kind = at.kind();
        dish = rule.result();
        expect = times * rule.yield();
        got = 0;
        since = level.getGameTime();
        step = Step.COOK;
        if (TRACE) LOG.info("Serfdom trace: {} cooks {} x{} at {} {}", worker.getId(), rule.id(), times, kind, station.toShortString());
    }

    /** effects: waits by the station, collecting as the dish is done: a furnace or smoker once its
     * input is all cooked or it has stalled without fuel (collecting it any sooner takes the
     * uncooked input back out); a campfire's food as it pops out, a pot's meals as they are ready. */
    private void cook(ServerLevel level, Villager worker, long now) {
        worker.getLookControl().setLookAt(Vec3.atCenterOf(station));
        if ((now - since) % POLL != 0) return;
        boolean late = now - since > COOK_LIMIT;
        if (kind.burns() && level.getBlockEntity(station) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity c) {
            var state = level.getBlockState(station);
            boolean lit = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
            boolean done = c.getItem(0).isEmpty(), stalled = !done && !lit && c.getItem(1).isEmpty();
            if (!done && !stalled && !late) return;
            if (!done && !stalled) {
                if (TRACE) LOG.info("Serfdom trace: {} gave up waiting on {} at {}", worker.getId(), dish, station.toShortString());
                step = Step.CHOOSE;
                return;
            }
            got += Stations.collect(level, worker, station, kind, dish, hands);
            worker.swing(InteractionHand.MAIN_HAND);
            step = Step.CHOOSE;
            return;
        }
        got += Stations.collect(level, worker, station, kind, dish, hands);
        if (got >= expect) { worker.swing(InteractionHand.MAIN_HAND); step = Step.CHOOSE; return; }
        if (late) {
            if (TRACE) LOG.info("Serfdom trace: {} gave up waiting on {} at {}", worker.getId(), dish, station.toShortString());
            step = Step.CHOOSE;
        }
    }

    /** effects: up to {@code n} of {@code item} taken out of the hands, in one stack. */
    private ItemStack removeFrom(String item, int n) {
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < hands.getContainerSize() && (out.isEmpty() || out.getCount() < n); i++) {
            var s = hands.getItem(i);
            if (s.isEmpty() || !RecipeBook.key(s.getItem()).equals(item)) continue;
            int take = Math.min(s.getCount(), n - (out.isEmpty() ? 0 : out.getCount()));
            var part = hands.removeItem(i, take);
            if (out.isEmpty()) out = part; else out.grow(part.getCount());
        }
        return out;
    }

    // ---- walking and ending -----------------------------------------------------------------

    private void walkTo(ServerLevel level, Villager worker, BlockPos pos, long now, Runnable then) {
        dest = pos;
        arrive = then;
        since = now;
        if (near(worker, pos)) { step = Step.CHOOSE; then.run(); return; }
        worker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED, 1));
        step = Step.WALK;
    }

    private void walk(ServerLevel level, Villager worker, long now) {
        if (near(worker, dest)) {
            worker.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            worker.getLookControl().setLookAt(Vec3.atCenterOf(dest));
            step = Step.CHOOSE;
            arrive.run();
            return;
        }
        if (now - since > WALK_LIMIT) {
            if (TRACE) LOG.info("Serfdom trace: {} could not reach {} for its meal", worker.getId(), dest.toShortString());
            end(level, worker);
            return;
        }
        if (!worker.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) worker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(dest, SPEED, 1));
    }

    private static boolean near(Villager worker, BlockPos pos) { return worker.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) <= REACH * REACH; }

    private void end(ServerLevel level, Villager worker) {
        note(level, worker);
        ended = true;
        step = Step.DONE;
        if (TRACE) LOG.info("Serfdom trace: {} ends its meal at hunger {} (ate {})", worker.getId(), Appetite.of(worker).hunger().points(), ate);
    }

    private void note(ServerLevel level, Villager worker) {
        var belly = Appetite.of(worker);
        Appetite.set(worker, belly.with(Meals.after(belly.times(), level.getDayTime(), level.getGameTime(), ate)));
    }
}
