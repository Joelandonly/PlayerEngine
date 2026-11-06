# Water escape worker access and stall evidence v15

Build ID `20261106-nonloading-water-escape-v15`. Gameplay **UNVERIFIED**.
Based on v14 `859aef523fb72273e88f882bb09bf98592d6b148`.

## Supplied run

The November 6 log enters the final world at 14:55:41 Europe/Amsterdam and
continues ticking until approximately 16:21:29, around server tick 96,000.
The last task is `get sand 300`, wandering without a source; Unstuck Chain then
takes over with `<> Completing goal.` Server log/observatory updates stop while
Player2 background requests continue. The earlier freeze stopped at the same
Unstuck/Completing-goal boundary shortly after joining a world.

The empty debug name matches GetOutOfWaterTask, which inherits the Completing-goal
state from CustomBaritoneGoalTask. There is no fatal exception or thread dump in
the supplied files. This repeat is not limited to rapid world creation. The logs
establish the repeated stopping boundary, not the exact Java wait/lock chain.

Build identity matters: this client process starts at 14:43:45 Europe/Amsterdam
(HUD_INIT 13:44:06 UTC), before v14's Actions run started at 13:52:14 UTC and
finished at 13:53:23 UTC. There is no client restart in this log. World reloads
do not reload mod classes. This run cannot be evidence that the published v14
artifact failed; its installed revision is not recorded. The unchanged native
water-escape hazard is nevertheless present in both v13 and v14 JARs.

## Confirmed native loading hazard

The actual v14 `GetOutOfWaterTask$EscapeFromWaterGoal.isWater` calls the live
ServerWorld.getBlockState. Both its heuristic and isInGoal callbacks query this
method, including eight neighboring positions. Native A* invokes these callbacks
on its path worker; the calls bypass the non-loading BlockStateInterface used
by movement candidate evaluation. The prior collision-view repair did not cover
these goal callbacks. The source recovered from the 1.4.0 release branch and the
actual installed JAR show the same code.

V15 changes only that goal's constructor/read boundary. Each goal captures a
native BlockStateInterface and its world. isWater consults isLoaded/get0, keeping
the existing water classification and dry-neighborhood goal rules. Unavailable
center/neighbor data, a missing world or a changed world cannot count as a dry
escape. The goal does not generate chunks or synchronously fetch them.

The enclosing GetOutOfWaterTask, UnstuckChain and CustomBaritoneGoalTask remain
byte-identical to the pinned baseline. No new navigator or recovery controller
is added. Same-height bridging, descent safety, the working 14-stage setup,
storage, command routing and chunk ownership policies remain unchanged.

## Evidence capture if the stall recurs

The existing observatory client-tick callback now checks the existing global
server heartbeat once per second, independently of dashboard/HUD visibility.
After at least 30 seconds without server ticks while a world remains loaded and
the client remains unpaused, it attempts one full JVM thread/monitor capture to:

`logs/ellie-server-stall.txt`

This is a read-only addition to existing diagnostics, with no new thread, AI
executor or recovery action. It avoids the synchronized DistanceDiagnostics and
ObservatoryTelemetry monitors: a stalled server might hold either. Normal pause
and menu/world transitions suppress capture; unpausing gets a fresh grace period.
Each resumed heartbeat permits capture of a later independent stall. The file
includes the packaged build revision, full thread stacks and monitor ownership.
It overwrites the previous capture. It cannot capture if the client thread also
stops processing its callbacks. Capture failure never changes game actions.
Startup also emits BUILD_IDENTITY and records buildRevision in observatory status,
so future logs establish the loaded JAR directly instead of relying on wall clocks.

## Automated validation and limits

- Execute the original packaged v14 goal with a substituted live-world service
  that waits for the server: the path worker enters that service and remains
  waiting until released. This reproduces the live-read hazard, **not the exact
  Minecraft deadlock**.
- Execute the actual patched packaged goal, with world/cache/physics boundaries
  substituted: dry exits, water centers, all eight wet neighbor directions,
  unknown centers/neighbors, loaded chunk borders, unloading, world changes,
  fresh goals and missing worlds. The worker returns while live-world servicing
  is deliberately unavailable, with zero direct live-world reads.
- Packaged ASM verifies the native isInGoal/heuristic/neighbor policy is preserved,
  the native BSI ABI exists, the goal uses its cache boundary and the enclosing
  task/recovery orchestration stays unchanged.
- A real JVM thread capture runs while a simulated server holds both existing
  diagnostic monitors. The client completes capture and records full stacks,
  with pause, grace, deduplication, world-transition and resumed-tick scenarios.
  Minecraft lifecycle behavior is substituted.
- Run the full existing regression suite, Node suite, packaged revision audit,
  byte identity of all 32 proven setup classes and concrete native movement/bridge
  preservation checks.

No full Minecraft server/client or exact frozen wait chain was reproduced here.
Live validation must repeat spawning/falling into water, shallow/deep water escape,
chunk-border shorelines, sand collection near water and world reopen/switch cycles.
Confirm ticks, chunk streaming, outward bridging, safe descents and setup continue.
If the world stalls again, retain `ellie-server-stall.txt` with the normal logs;
its stacks should identify the next action from evidence rather than another guess.
