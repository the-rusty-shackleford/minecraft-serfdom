# Serfdom 0.8.0 (phase 5, raids and the base's defence): verification

Run on 2026-10-05. 0.8.0 is 0.7.0 (phases 1 to 4, never released; its record is `release-0.7.0.md`)
with phase 5 (D-0007): owned villagers arm from their chests in a raid, fight the raid's raiders, and
put everything back after. Rusty's call: no launcher. D-0007's "Settled in the build" holds what the
build found, the one narrowing first: guns reload loose rounds only, since Ranged Weapons Mod's
magazines aren't the protocol's ammo stores.

`./gradlew clean build` with the booth on Xephyr `:7` (the Gradle daemon stopped first, so the build's
environment was the Xephyr recipe's): `BUILD SUCCESSFUL`. The jar `serfdom-0.8.0.jar` has sha1
`64c9330ef9a80bff99bc1f80752c33d61cbaa5cc`, with Carried 1.0.0 nested and the Ranged Weapons protocol
not (it is compiled against, from mavenLocal's 1.7.0, the version Ranged Weapons Mod 2.12.0 nests).
Phase 5 needs no change to the two siblings (Vanilla Wheels 1.11.0, Village Law 1.1.0), nor to RWM.

## JUnit: 290 tests, 0 failures

The 274 of 0.7.0, plus:

| Suite | Tests | What it covers |
|---|---|---|
| `ArmouryTest` | 6 | the choice: nothing, melee only (the best, a tie), ranged with and without ammo, both, ranged first whatever the sword does; never a launcher, a bow over a better launcher; the most damage a second among a pistol, a crossbow, a bow and a rifle without rounds; damage a second of a sword and an axe, a full draw and a crossbow's charge, a pistol's and a shotgun's magazine, no reload; ammo carried under, at and over the most; refused inputs |
| `DefenceTest` | 7 | who musters: hired or captive adults, never free, children, in chains or on their way home; the next step: arm (raider or not), hide when tried and empty-handed, fight or stand with a ready bow, a sword, out of arrows with and without a sword, put back with no raid or no longer mustering, nothing; the hand; reach at and past its edges; a reload empty, part full, full, from nothing, less than room; a blow's cooldown |
| `LineOfFireTest` | 3 | a friend on the line, within the margin, past it, no margin, behind the target, behind the shooter, overhead; a line along an axis; refused margins and boxes |

## GameTests: 99 passed

The 87 of 0.7.0 and 12 in `DefenceGameTests`. A raid is started as vanilla starts one (a player with
the Raid Omen at a base whose beds are taken), and raiders joined to it by hand, which holds its own
waves back while they live; most stand frozen, so what is tested is the defender, not a fight's luck.
Each stands alone in its batch (raids within 96 blocks are one raid) and stops its raid; every batch's
setup stops any a failed test left.

- **Melee:** of an iron and a stone sword at home the worker takes the iron, kills a vindicator, a
  player can't trade with it meanwhile, and after the raid the sword goes back and its helmet stays on.
  In diamond with a diamond sword it kills a live vindicator, and never panics.
- **A bow:** it carries out the bow, all 16 arrows and the sword, shoots a pillager dead, and puts the
  bow, the sword and the arrows left back. With two arrows against a ravager it shoots both, then draws
  the sword, carrying the bow.
- **A gun:** a Ranged Weapons Mod pistol, empty, and 20 small rounds: it loads from what it carried,
  shoots a pillager dead, and puts the pistol (with what it still holds) and the loose rounds back,
  fewer than 20 between them.
- **Nothing to fight with:** a bow without arrows and a launcher with rockets: nothing taken; it hides
  as vanilla's do. A bow and one arrow against a ravager: one shot, then it hides carrying the bow, and
  after the raid it comes out and puts the bow back.
- **Who:** a captive arms; a free villager beside it, a sword in its own chest, doesn't. At night a
  zombie stands by a worker with a sword at home: nothing taken.
- **The line of fire:** an archer with a villager standing between it and a raider shoots nothing for
  120 ticks; the villager steps aside and the raider is hit.
- **Leaving:** a defender killed drops its bow, its sword and its arrows; one set free mid-raid drops
  its sword where it stands and carries nothing after.

Eight runs of the GameTests in a row passed at the final code (99 of 99 each), and the gate's build
made a ninth. **One failure is not explained:** in the first run of mutation batch `b`, before the
put-back had a trace, the hiding test's bow was not in its chest when the test timed out, though the
defence logged its put-back finished carrying nothing. No mutation of that batch touches that path. It
has not come back in the 13 runs since (batch `b` again, and the loops), and the put-back now logs what
it puts where, so a recurrence will say where the bow went.

## Booth: 110 checks, 55 photographs

| Photo | Shows | Checks in code |
|---|---|---|
| `51-armed-at-the-chest` | from the side: three workers by their chests on the left, the gunner in front with the pistol across its folded arms, the archer behind with its bow; a vindicator and two pillagers facing them on the right | the client sees the archer with its bow by its chest |
| `52-swordsman` | the farmer face to face with the vindicator, which flashes red from the blow; the sword a thin line at its hands | the client sees the vindicator hurt and the swordsman within 3.5 blocks with its sword |
| `53-arrow-in-flight` | an arrow just leaving the archer, fletching first, toward the raiders | the client sees an arrow moving fast |
| `54-gunner` | both pillagers flashing red, the gunner's pistol at its hands | the client sees its pillager hurt and the gunner holding its gun |
| `55-put-back` | the archer's chest: the bow (its wear bar showing) and 23 of 24 arrows | the client sees the bow in the chest |

And after the photos: every defender's hands empty and nothing carried; the sword back; the bow back with
arrows spent. The raid stops the moment the four photos are in, its raiders gone: with its last raider
dead, a raid spawns a wave of its own 300 ticks later, at a survival camera.

**Judged by eye** (crops at three times):
- The first staging failed. From 15 to 18 blocks the defenders were a few pixels tall, the frozen
  raiders faced the camera, and the camera player's Raid Omen particles (the effect is not taken away by
  creating a raid by hand) swarmed across 51, 52 and 54. It was staged again from the side, 6 to 15
  blocks off, raiders facing their defenders, the omen removed.
- **A villager draws a held weapon lying across its folded arms,** as vanilla draws anything a villager
  holds: the pistol and the bow read, the sword is a thin line. That is the villager model, untouched;
  whether it will do is Rusty's call.
- A few small blue specks by the gunner and the archer in 53 and 54, which I could not name.

## Mutation pass: 23 of 23 caught at the final code

`uv run --no-project python devtools/verification/mutate-0.8.0.py domain-each | a | b | c | d | e`.
Left out on purpose: villagers without attack damage, which crashes the server at the first blow.

| Mutation | Caught by |
|---|---|
| D1 a launcher taken | `ArmouryTest` |
| D2 a ranged weapon without ammo taken | `ArmouryTest` |
| D3 the worst melee weapon | `ArmouryTest` |
| D4 a gun's reload ignored | `ArmouryTest` |
| D5 a free villager musters | `DefenceTest` |
| D6 it arms again every look | `DefenceTest` |
| D7 it never hides | `DefenceTest` |
| D8 it keeps what it took | `DefenceTest` |
| D9 melee before a ready bow | `DefenceTest` |
| D10 no reach from home | `DefenceTest` |
| D11 a reload past its capacity | `DefenceTest` |
| D12 it shoots through friends | `LineOfFireTest` |
| D13 no margin | `LineOfFireTest` |
| G2 the work-tool stash takes the weapon | the swordsman, the live vindicator, the archer, the gunner |
| G3 no line of fire | the friend in the line |
| G4 a skeleton's aim | the archer |
| G5 a defender set free keeps what it took | set free mid-raid |
| G6 a launcher is a gun | nothing to fight with |
| G7 nothing put back | the one that hid |
| G8 a defender trades | the swordsman |
| G9 vanilla's panic takes the defender | the live vindicator |
| G10 rounds never leave what it carries | the gunner |
| G11 the dead drop nothing they took | killed |

**Two got past the first pass.**
- **G5, then "a free villager musters"** (the owned flag in the defence's mustering): equivalent. A free
  villager's brain has no defence at all, so the flag can't be reached by one. Asking where it could
  matter found a defender set free mid-raid would have walked off with its owner's weapons: `free` now
  drops them, a test covers it, and G5 is aimed there (batch `e`).
- **G7 "nothing put back":** a defender still defending puts things back by itself, so the sword test
  never needed the core's call. That call is for one that left the defence, one that hid out of
  ammunition: the hiding test covers it (batch `e`).

Under batch `b` of the first pass two workshop tests failed too: a mutated raid test failed before
stopping its raid, which went on over its neighbours. Every batch now stops leftover raids; batch `b`
again failed only its targets.

## Found by the gate

- **`KeepBed` took the defender's sword** a few ticks after it armed: it puts a worker's hand away
  whenever it isn't at work, the tool's rule since 1a. `Workers.stash` leaves the defence's weapon
  alone. Six of the ten first defence tests failed on it.
- **Arrows flew over every head:** the skeleton's aim lifts a fifth of the distance, for its slow
  arrows; at a full draw's speed a defender lifts by gravity's drop over the flight.
- **A defender set free mid-raid kept its owner's weapons** (the mutation pass, above).
- **A failed raid test raided the next batches** (above).

## Not verified

- **Play on the box:** nothing of phase 5, nor of 4a and 4b, has run on the server. No real raid with
  waves of its own has been fought; the tests' raiders are joined by hand and mostly frozen, and one
  live vindicator fought a worker in diamond.
- **Crossbows** are covered by the rule and the domain's numbers, not by a GameTest of their own.
- **Several defenders sharing one chest:** first come, first served is how taking works (a weapon taken
  is gone), not a tested race.
- **RWM's other guns:** the pistol is tested; the shotgun, rifles, revolver and machine gun go through
  the same protocol calls.
- **Rusty's shaders and GPU:** the photographs are software-rendered.
- **Rusty has not seen** photos 51 to 55.
