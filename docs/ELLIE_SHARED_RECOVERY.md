# Shared target recovery and the November 4 crash

This revision continues the native-command/crafting-recovery lineage. It keeps the
three-station setup, conditional compact supports, native command routes and Player2
interpreter. It does not inject chat commands or change personal Baritone home.

## Findings from the newly supplied logs

The current `latest.log` names `ELLIE-PLAYER2-NATIVE-COMMANDS.jar`; no binary checksum
is supplied in the game log. Times below are game-log times (Observatory UTC is one
hour earlier).

- **20:23:17 crash:** `AxeItem.shouldCancelStripAttempt` dereferences a null vanilla
  player to read the offhand stack. The call comes through PlayerEngine's non-player
  item-usage context. The Player2NPC entity appears lower in the tick stack because
  it owns the controller; the failing boundary is in PlayerEngine.
- **20:14:30–20:15:30 production:** setup requests six iron ingots for two buckets.
  Five are recorded by 20:15:20, then the macro fails at the end of the nominal
  six-item smelting interval. The furnace code can leave smelting mode when its
  timer expires before final output collection. On the next tick it can request a
  fresh material batch before checking existing output. Parent material resolution
  and cached inventory counts also run before the child transfer.
- **20:23:15–20:23:16 build regression:** Mob Defense temporarily suspends the build.
  Our native wrapper treats a stopped physical child with no result as terminal
  failure on resume. This was a regression in the native-command wrapper.

This run stops setup at buckets (step 10), not armour. The pasted armour examples
are earlier observations. The production correction is recipe-independent and is
also tested with seven/eight-ingot requirements; live armour completion is pending.
The older `reasoning_effort`/HTTP 400 report is not reproduced in this new log.

## Corrections

**NPC item use:** non-player axe stripping returns PASS before entering the known
player-only vanilla method. Real-player axe use and NPC block-item placement keep
their original paths. Exceptions from unrelated item behavior are not broadly hidden.

**Production handoff:** a macro retains its existing native furnace child while
matching input/output or an active batch is pending. Pending matching furnace work
keeps the current batch active, and furnace ticks refresh inventory accounting.
Cold input without remaining burn time or fuel does not force an indefinite hold.
The existing native task transfers the actual output; there are no item grants or
new per-recipe stall timers. Native failure/stop handling remains authoritative.

**Build suspension:** an accepted safety/gesture suspension records a resume marker.
The same unfinished physical child is reset for native re-entry, preserving its
blueprint. Explicit stop/failure remains terminal; it is not converted to success.

## One shared movement-recovery owner

The common `Task.tick` boundary observes block interaction/navigation targets and
can pause the user task tree in place while one coordinator owns recovery movement.
Individual crafting tasks no longer run the separate 90-tick watchdog.

Reachability alone is not progress: a visible, reachable bench with no successful
craft still enters recovery, and reaching an approach does not replenish retry
attempts. After about 100 simulation ticks without verified work or novel movement:

1. Try a standable interaction approach with dry clearance, support, world bounds,
   reach and ray checks. Native Baritone computes the route. Actual reach is checked
   again after arrival; a candidate is not advertised as a proven reachable route.
2. On another failed attempt, deliberately clear an eligible blocking cell through
   the native destruction task, if all conservative safety checks pass.
3. Replan the native movement leaf or temporarily reject the target for 120 seconds.
   Scanner selection and table reuse share that rejection. Repeated exhausted
   recovery cannot endlessly reset the same leaf without verified progress.

The original project, inventory and placed blocks remain. Native leaf interruption
and re-entry balance lifecycle cleanup. Higher-priority chains retain control; the
coordinator releases only its own movement goal and respects forced transactions.
Critical air during user work routes toward a nearby breathable cell, considering
sideways exits beneath an overhead obstruction. Native survival chains can preempt.

Breaking progress uses a high-water mark, so slow genuine mining is allowed and
repeated damage loss/regain is not treated as indefinite progress. Back-and-forth
movement over the same cells also stops resetting the progress observation.

Clearing refuses active blueprint footprints, tracked player-built blocks/supports,
workstations/storage, the NPC's supporting floor, adjacent fluid exposure and
falling/support hazards. Only a small ordinary-terrain whitelist is eligible.
Unknown or unsafe geometry is retained. No teleport or direct world-state edit is
used by production recovery.

Bindings cover common movement, destruction, placement and crafting primitives.
For the physical builder, composite goals are matched to an actual native path
endpoint; an unknown builder target is reported rather than guessed. Recovery
cannot guarantee a route through every possible terrain or extension mod.

## Worksite and bench memory

Setup and blueprints register a construction anchor separately from personal home
and the current movement destination. Journals are scoped by world/character and
worksite identity (dimension, origin and blueprint fingerprint). A currently
reachable crafting bench can be recorded and restored. Nearby reuse rechecks the
block, radius, shared rejection and approach geometry. A distant saved location is
not automatically treated as reachable, and remembered coordinates do not guarantee
safe travel. The physical builder retains its original return-to-blueprint task.

Worksite save failures are reported; an in-memory active site can still run. Stops
release the live worksite preference while leaving its saved record for a matching
project. No `sethome` command is sent and the existing personal-home waypoint is
never overwritten. This is bench/worksite memory, not automatic chest withdrawals
or a newly exposed personal-home command.

## Observatory evidence

Exports include target/block position, task, NPC position, interaction reach,
ray-obstructing cell, actual look hit where available, look/input-writer context, inputs
before recovery, recovery
mode, movement owner and attempt count. Outside-task look calls record their caller
(e.g. a native Baritone process). Unknown readings are explicitly unavailable.
`TARGET_RECOVERY`, `RECOVERY_APPROACH`, `RECOVERY_CLEAR`, `RECOVERY_REPATH`,
`RECOVERY_REJECT`, `RECOVERY_AIR`, `RECOVERY_YIELD` and `RECOVERY_RESUME` distinguish
selection, movement ownership and actual world verification.

## Verification and acceptance

Local checks exercise the actual patched `Task.tick` with substituted world,
collision and pathfinder boundaries: ownership, safe resume, defense preemption,
sideways air escape, verified obstruction removal, support/blueprint protection,
finite retries, builder-goal selection and worksite bench reuse. Separate cases
cover the null-player axe, final-ingot handoff, slow mining and oscillation. Packaged
bytecode checks require the common hooks and absence of the competing craft watchdog.
All prior native-command/setup/supervisor/Player2 integrity checks remain required.

These are not live Minecraft navigation or complete armour/build acceptance tests.
For live acceptance, run setup on the same world, inspect bucket/armour output
collection, interrupt a build with defense, and test an obstructed bench/route.
Record the JAR hash and new Observatory exports. A normal lookup/syntax rejection
(e.g. `smith iron_sword`, `get 64 stone`) is separate: use `get iron_sword 1` and
`get stone 64`; smithing operates on upgrade recipes.
