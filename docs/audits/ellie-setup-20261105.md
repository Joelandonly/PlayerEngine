# Ellie setup audit — 5 November 2026

Reviewed runtime source: `5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7` (v7).
Baseline JAR SHA-256: `8ae4c3da375b45ad60f94ddbd4d4c31328400cce0c4a720b1002dd8dee143d27`.

## Verdict

Setup has accumulated incompatible contracts at its component boundaries. It
is increasingly strict about recognizing failure, without reliably making the
underlying native actions satisfy the required postconditions. More timeout
patches will not resolve those disagreements. V7's live failure demonstrates
that the v6 failure was not fixed by waiting for output.

The recommended next patch is a narrow workshop acquisition/placement and
lifecycle repair, proven through the real native task path. Retain the mining
protection and checkpoint repairs. Do not expand setup, migrate the whole
engine, or declare another build reliable based on the current full-chain
simulations.

## What the latest evidence establishes

Times below are Europe/Amsterdam (UTC+1). The evidence JSON retains UTC.

- 22:09:57: v7 setup starts.
- The bootstrap creates/uses a crafting table near `72,121,47`.
- 22:11:04: the stone shovel is verified. All four bootstrap objectives passed.
- The recorded workshop table is `64,131,37`; its chest is `68,131,37`.
- 22:11:05: workshop reports it is waiting for a carried crafting table.
- 22:11:07: it blocks after 40 ticks without that item.
- 22:11:23: rerunning credits the already owned bootstrap tools.
- 22:11:24–26: it repeats exactly the same table failure.
- NPC/controller ticking, forced chunk and 28 free main slots are reported.
  There is no evidence here that range, full inventory, missing iron, or a
  shelter action caused this particular failure.

The event file includes older runs. They must not be treated as this run's
current state. The current failure occurs before the normal shelter stage.

## Audit scope and responsibility map

| Layer reviewed | Actual responsibility | Audit result |
| --- | --- | --- |
| ConversationManager / SetupDirectRoute / NativeCommandText | Chat eligibility, authenticated owner bypass, parsing | Named owner bypass occurs after the 64-block chat filter; unrelated to this near-owner failure. |
| EllieSetupCommand / controller submission | Instantiate root and report tracked outcome | Correctly reports BLOCKED; command acknowledgement is not completion. |
| CoreGoalSupervisor / GoalLifecycle / ProjectTaskGateway | Goal generations and receipts | Stale callback protection is valuable. These layers cannot repair a bad leaf-action contract. |
| TaskStepExecutorAdapter / UserTaskChain / Task / TaskRunner (pinned bytecode) | Native task execution, replacement, suspension, completion | Multiple lifecycle states must be distinguished; unit fixtures do not reproduce this path. |
| EllieSetupTask | Required kit, root stage ordering, final checks | Overlaps workshop verification and survival scheduling; v6 moved a failing boundary earlier. |
| StarterBaseTask / StarterBaseWorld / StarterBaseLayout | Site intent, preparation, acquisition and actual placement | Present blocking boundary; vertical approach and acquisition-outcome handling are incomplete. |
| SetupSurvival / SetupSurvivalWorld / SetupShelter | Hunger, cover, shelter, reserves, final survival checks | Mandatory scope grew substantially; clear geometry verification is useful but is a second coordinator inside root setup. |
| Native TaskCatalogue / ResourceTask / CraftInInventoryTask / StorageHelper / InventorySubTracker / SlotHandler (pinned bytecode) | Recipes, ingredient collection, native completion and inventory | Native ownership includes cursor; workshop requires main slots. Actual cause of current native termination still needs instrumentation. |
| SharedTargetRecovery / RecoveryWorld / CoreAcquisitionProgress | Progress, resource watchdog, movement recovery | Several progress ledgers/budgets coexist; entity tasks and planned waiting need explicit treatment. |
| WorksiteAnchor / crafting table recovery and placement hooks | Preferred/reachable crafting station | Project worksite has a single owner slot; nested construction can replace workshop context. |
| StorageIntelligence / ProductionHandoff / smelting hooks | Verified storage, withdrawal, in-flight production | Useful shared responsibilities, but inventory and outcome semantics are not unified across consumers. |
| SetupBuildZone / VillageSiteGuard / native break guards | Resource-source and path-break protection | Multi-owner mining exclusion repair should be retained; resource exclusion and movement breaking are separate checks. |
| CoreGoalJournal / observatory / build/package checks | Persistence, explanations, artifact identity | Artifact identity is verifiable; telemetry still omits decisive leaf lifecycle/count distinctions. |

This is a source/bytecode boundary audit, not an exhaustive proof of every
Minecraft method, companion-mod behavior or reachable task interleaving.

## Findings, ordered by repair priority

### F1 — Critical: a bounded failure was delivered as a repaired action

**Confirmed by live evidence.** `StarterBaseTask.onTick` changes a terminal
acquisition with no carried table into a 40-tick wait, then BLOCKED. It does
not find out why the acquisition ended, move an existing cursor item, choose
a usable existing station, or invoke a different production strategy. Rerun
repeats the same failure. V7 therefore improved the timeout message without
fixing the demonstrated table handoff. The earlier output-delay diagnosis was
unproven and is contradicted as a sufficient explanation by this run.

**Repair:** instrument the actual returned native task and its child tree,
then repair the concrete native producer/transfer boundary. Do not increase
the wait or add blind reacquisition retries.

### F2 — High: inventory ownership has incompatible definitions

**Confirmed static defect; trigger in this run unproven.** Pinned
`ResourceTask.isFinished -> StorageHelper.itemTargetsMet ->
ItemStorageTracker.getItemCount -> InventorySubTracker.getItemCount` includes
`SlotHandler.getCursorStack`. `getItemCountInventoryOnly` delegates to the same
count in this baseline. `CraftInInventoryTask.onResourceTick` also uses that
count to decide not to craft. In contrast, v7 `StarterBaseWorld.count` reads
only the 36 main slots. Root kit counting still uses the tracker path.

A table on the cursor can satisfy native acquisition forever while the
workshop observes zero usable carried tables. It is not valid to call the
cursor item nonexistent, count it twice, or manufacture a second item.

**Repair:** define carried/transferable/placed/stored/potential observations
separately in the existing inventory/production helpers. The action that owns
the cursor must complete a verified native transfer before reporting usable
output. Add a real-native-path test with main=0, cursor=1 and another with
both=0. Log main, cursor and native count together. This audit does not prove
that the player's current cursor contained the table.

### F3 — High: workshop return can succeed ten blocks below the workshop

**Confirmed static defect, relevant geometry in logs.**
`StarterBaseWorld.approach` accepts horizontal distance <=12 plus a loaded
chest chunk. It never tests vertical distance, a standing interaction location
or a completed reachable route. The logs show the bootstrap bench at Y=121
and the workshop at Y=131. That bench position is not proof of the NPC's exact
position at failure, but the gate cannot distinguish the two elevations.

**Repair:** shared navigation establishes a reachable standing position near
the specific workstation. Loading a chunk or being close in X/Z cannot mean
arrival. Do not make setup itself invent another movement controller.

### F4 — High: two workshop contracts compete

**Confirmed design conflict.** Root setup accepts a verified nearby crafting
table for its kit; `StarterBaseTask` requires a table at a fixed planned
coordinate. Native recipe tasks may place/use a temporary bench elsewhere.
The workshop plan is persisted before bootstrap resource gathering moves the
NPC to an actual workable area. V6 moved full workshop establishment from
index >=7 to >=4; that exposes this boundary before later kit acquisition.

**Repair:** retain saved coordinates when they exist, but explicitly distinguish
a temporary crafting bench from the permanent base. For a new project, prefer
a verified usable bench/site when establishing the base after bootstrap.
For an existing project, deliberately acquire/transfer/place what is missing
at its saved site; do not silently relocate it or credit a remote bench as the
saved workshop. Restore the old ordering only if tested as containment; it
would defer this defect, not solve it.

### F5 — High: interruption contracts differ inside the same root

**Confirmed code risk; not demonstrated as this run's trigger.**
`EllieSetupTask` explicitly interrupts its kit `active` when survival takes
over, but its workshop `baseTask` is a separate reference. `StarterBaseTask`
blocks on ordinary `onStop`, while transient resume depends on correct native
suspension cause and `afterChildrenStopped`. Root and survival each reset or
reconstruct their own children. Moving to emergency shelter during workshop
placement must not look like a failed or completed station acquisition.

**Repair:** use the existing Task/UserTaskChain suspension protocol consistently
for the one active setup child, including workshop preparation. Keep intent,
resource reservations and retry budgets across suspension. Test a night/food/
defense interruption during each workshop substage, not only during shelter.

### F6 — High: worksite context is not nested like mining protection

**Confirmed static mechanism; contribution to latest failure unproven.**
`WorksiteAnchor.open` replaces one site per controller; closing the nested owner
removes it rather than restoring the workshop site. `worksiteRegistered` in
`StarterBaseWorld` can remain true after another builder replaced its context.
The multi-owner VillageSiteGuard repair does not repair this different map.

**Repair:** explicit owner-scoped project context, with deterministic restoration
of the parent worksite; reuse the existing helper. Test workshop -> shelter ->
repair -> workshop and verify the same bench policy/protection after return.

### F7 — High: completion and failure information is lost at handoffs

**Confirmed code issue.** Workshop branches collapse `active.isFinished()` and
`active.stopped()` into an ended acquisition; unlike root kit acquisition,
they do not preserve the underlying recipe failure reason. An unstarted,
suspended, failed, completed, and output-pending task cannot all be represented
by an ended-task test followed by a timer. Root, executor and supervisor also
present different status names: latest root BLOCKED is emitted as supervisor
FAILED because the executor receipt is FAILED. That is honest failure, but
loses the actionable category.

**Repair:** translate existing tracked outcomes and native failure reasons at
one shared boundary. Supervisor owns the goal receipt; leaf action owns its
physical postcondition. Preserve both executor status and actionable cause.

### F8 — High: tests replace the exact boundary that keeps failing

**Confirmed validation gap.** `tests/fixtures/.../Task.java` makes `interrupt`
and `reset` no-ops and `stopped()` always false. Fixture TaskCatalogue returns
a symbolic request, and full-chain tests directly apply inventory/placement
effects. The delayed-output test starts with an artificial successful producer.
They do not exercise the native recipe, cursor, Task.tick replacement,
forced-child behavior or return-to-workshop path. Counts of passing setup
scenarios cannot establish that this integration works.

**Repair:** keep cheap state-machine tests, add a narrow real native scheduler +
producer + inventory/transfer integration harness, and explicitly test the
current supplied failure. Then require a live fresh-world run and a same-world
rerun before claiming gameplay reliability. Existing build/ABI/package checks
remain valuable and should not be discarded.

### F9 — Medium: progress and time budgets have several owners

**Confirmed design risk.** Root kit tracks stagnant/phase/material milestones;
workshop tracks stage/access/total ticks; survival tracks high water/idle/total;
shared recovery has a 2400-tick no-output watchdog and its own movement lease.
Shared inventory signatures can renew a job watchdog independently of the
current required output. Root navigation diagnostics also coexist with shared
recovery. These are not all competing movement writers, but they do not form
one consistent action outcome/progress contract.

**Repair:** action-specific evidence and one recovery owner per running child.
Pause budgets during actual safety suspension; neither target switches nor
busy inventory rearrangement are work. Station placement and shelter output
must report verified world changes through the same existing progress hook.

### F10 — Medium: scope expansion has made setup much harder to finish

**Confirmed history/design tradeoff.** The earlier kit/stations command now
requires an enclosed shelter, usable approach, food reserve, fuel reserve,
equipping, entering and closing a door, with a possible night wait. Each is a
new mandatory failure gate. Night can trigger shelter before bootstrap tools.
V4 introduced most of this survival expansion; v5–v7 added constraints and
repair behavior. Some useful protections are new, but passing the simpler old
command was never evidence that the expanded contract works.

**Repair:** keep the agreed survival goals, but stage them explicitly and expose
verified checkpoints. Do not add new mandatory ambitions or silently drop
shield/iron tools to chase shelter work. A future split into kit/base commands
is a product choice, not something to impose during this repair.

### F11 — Medium: essential diagnostics are missing or truncated

**Confirmed.** Current telemetry cannot identify which native task claimed the
table, whether it ever started, whether it stopped with a recipe error, or
where a cursor/output item was. Several task-progress strings are truncated.
Child builder `phase=Completed` also describes the child, not full setup.

**Repair:** one structured handoff event containing root generation, stage,
parent/child IDs and classes, isActive/isFinished/stopped/outcome/failure,
main/cursor/tracker counts, planned and observed station positions, current
NPC coordinates, movement owner, progress evidence and attempts. Keep complete
JSONL detail even when the HUD abbreviates it. Do not log tokens or chat history.

## What I would design differently from scratch

Keep the existing native executor and task tree. Setup should be a small,
ordered coordinator of verified postconditions, not another inventory tracker,
recipe engine, navigator or safety controller.

| Responsibility | Owner |
| --- | --- |
| User intent, required goals and checkpoint progression | EllieSetupTask |
| Authoritative carried/cursor observations and safe transfer | Existing inventory/slot helpers |
| Recipe execution and output delivery | Native acquisition/crafting/smelting tasks |
| Permanent site, station positions, placed-state verification | StarterBaseTask/World |
| Shelter geometry, excavation, placement and final clearance | Existing construction task + SetupShelter |
| Hunger/defense emergency arbitration and suspension | Existing safety chains; SetupSurvival requests cover/reserves |
| Reachability and arrival | Existing native navigation |
| Stalls and bounded alternate strategy | SharedTargetRecovery, with action-specific targets/evidence |
| Owner-scoped reservations/worksite restoration | Existing protection/worksite helpers |
| Goal generations and terminal receipt | CoreGoalSupervisor + native executor adapter |
| Saved intent and explanations | Existing journals/observatory; never proof of present ownership |

Each child starts with a concrete target and ends in one of: verified output,
explicitly suspended, bounded recovery using a different feasible strategy,
or BLOCKED with evidence. A callback never substitutes for its postcondition.
There should be one active child and one movement/recovery owner at a time.

## Repair order and acceptance gates

1. Capture and reproduce the exact native crafting-table handoff. Determine
   whether completion comes from cursor count, stale cache, failure, or stopped
   lifecycle. Do not guess or extend the timer.
2. Repair the common acquisition/transfer boundary and 3D workshop arrival.
   Demonstrate an actual table in main slots or verified station at its intended
   site, then place/verify furnace and chest. Preserve partial inventory.
3. Repair nested worksite restoration and test safety suspension at each station
   action. Keep the already fixed multi-owner mining protections.
4. Exercise shelter completion/clearance using the actual builder and shared
   resource collector, including retained natural walls and temporary scaffold.
5. Validate the whole required kit and base, then final reserves/equipment. Keep
   torches unplaced and exclude buckets, armor and diamonds as agreed.

Required cases: current main=0/native-ended table failure; cursor-only output;
no output/recipe failure; real output delayed by one tick; existing usable
bench below the saved site; steep/obstructed return; partial stations; interruption
while crafting, carrying, placing, excavating and smelting; missing/inaccessible
iron; material gathering beside an existing shelter; same-world rerun after
failure/reload; completed base altered later. Every case must terminate honestly
or preserve a bounded suspended job. A successful artifact build is not the
live acceptance gate.

## Retain, defer and uncertainty

Retain exact revision/checksum checks, owner authentication, supervisor generation
checks, real slot-capacity checks, multi-owner mining exclusions, compact journal
migration, terrain preparation, no automatic torch placement, and actual world
verification. Do not return to the old destructive/unbounded behavior wholesale.

Remote owner communication, entity-follow stalls and interrupted hunting remain
unfixed. They need shared lifecycle/navigation repairs but did not trigger this
latest near-owner crafting-table failure. Single-player cannot continue ticking
while the integrated server is stopped or genuinely paused.

No runtime changes or replacement JAR are part of this audit. No Minecraft world
was available for reproduction. The native cursor mismatch and approach defect
are established code paths; the precise triggering cause of this native table
termination is still not proven from the supplied telemetry. Read this report
as a repair specification and evidence assessment, not a claim of gameplay success.

## Review references

Pinned source links for the reviewed implementation:

- [Root ordering and verification](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/tasks/setup/EllieSetupTask.java#L132)
- [Workshop output lifecycle](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/tasks/setup/StarterBaseTask.java#L61)
- [Workshop arrival](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/tasks/setup/StarterBaseWorld.java#L115)
- [Workshop actual-slot count](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/tasks/setup/StarterBaseWorld.java#L208)
- [Survival coordinator](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/tasks/setup/SetupSurvival.java#L41)
- [Worksite ownership](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/reliability/WorksiteAnchor.java#L12)
- [Shared watchdog](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/reliability/SharedTargetRecovery.java#L39)
- [Goal status translation](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/src/com/player2/playerengine/supervisor/CoreGoalSupervisor.java#L90)
- [Full-chain acquisition substitute](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/tests/fixtures/com/player2/playerengine/TaskCatalogue.java#L7)
- [Full-chain task lifecycle substitute](https://github.com/Joelandonly/PlayerEngine/blob/5ff6cbde8c9a512e02453ff8223ef78e0eed3ec7/ellie/tests/fixtures/com/player2/playerengine/tasks/base/Task.java#L3)

[Filtered live-event evidence](ellie-setup-evidence-20261105.json). Baseline-only paths were inspected with Java 21 `javap -c -p` against the pinned JAR, rather than assuming upstream 1.1.11 source matches custom 1.4.0.
