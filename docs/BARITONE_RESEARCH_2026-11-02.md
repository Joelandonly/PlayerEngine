# Baritone 1.21.1 research for Ellie / PlayerEngine

Research date: 2026-11-02. Upstream: [cabaletta/baritone](https://github.com/cabaletta/baritone), **branch [1.21.1](https://github.com/cabaletta/baritone/tree/1.21.1)**. Default branch at inspection was `1.21.4`, so **do not** silently copy default-branch APIs into the 1.21.1 Fabric mod.

This is **design / source research**, not code merged into PlayerEngine, not a JAR and not a live test. The connected repository `Joelandonly/PlayerEngine` itself still checks in an older 1.20.1 source tree; experimental Ellie runtime is 1.21.1 Fabric with an Automatone/AltoClef-derived NPC controller. Upstream Baritone interfaces need comparison with the actual embedded fork before use.

## Finding 1 — Native vine bugs closely match Ellie

- [Settings.java: allowVines](https://github.com/cabaletta/baritone/blob/1.21.1/src/api/java/baritone/api/Settings.java#L341-L347) warns that **advanced** vine movements can trap the pathfinder indefinitely; `allowVines` defaults to `false`. This option is **not all vine climbing** and does not prove Ellie's value.
- [Issue #1773: can't mine logs with vines on the bottom](https://github.com/cabaletta/baritone/issues/1773), **open** at inspection, reports that jungle-log mining becomes stuck until a human breaks vines. An issue comment suggests including vines among mining targets may sometimes bypass the problem. Very close to Ellie user reports, but not proof of identical cause.
- [Issue #392](https://github.com/cabaletta/baritone/issues/392) reports vine/water stalls **with `allowVines=false` already**, so simply disabling it is insufficient.
- [Issue #4913: mangrove vines](https://github.com/cabaletta/baritone/issues/4913) (open) has a suggestion to test `blocksToAvoid`, with the caveat that mass avoidance may be impractical in vine-heavy biomes.

**Action (ELLIE-001):** Inspect the NPC's effective setting, get active path/next movement and inspect exact vine collision geometry (head/feet, attached face, liquid, nearby leaves). If path has no progress, inspect whether native movement timeout canceled it and the same route was subsequently recalculated. Replan with *local* short-lived obstacle penalties; only then allow carefully bounded mining of an actually blocking vine. Never remove the supporting vine while attached/climbing. Avoid making all vines forbidden: jungles would become inaccessible.

## Finding 2 — Baritone already implements movement timeouts

[PathExecutor.java](https://github.com/cabaletta/baritone/blob/1.21.1/src/main/java/baritone/pathing/path/PathExecutor.java#L220-L253) checks individual movement status; failed/unreachable movement cancels; a movement taking longer than estimated cost + `movementTimeoutTicks` cancels. [Settings.java](https://github.com/cabaletta/baritone/blob/1.21.1/src/api/java/baritone/api/Settings.java#L587-L592) defaults the extra allowance to **100 ticks**.

**Implication:** A second Ellie watchdog should **not** spam cancel/replan on every slow block-breaking operation. Better to monitor movement index plus physical progress, observe native cancel/retry, and penalize *repeated same-location failures* after timeout. Add a cooldown and logging for the specific movement type, target and path position.

## Finding 3 — Public path API, avoid fragile reflection

Baritone 1.21.1's [IPathingBehavior](https://github.com/cabaletta/baritone/blob/1.21.1/src/api/java/baritone/api/behavior/IPathingBehavior.java) exposes `getCurrent()`, `getNext()`, `getPath()`, `isPathing()`, `ticksRemainingInSegment()` and `estimatedTicksToGoal()`. [IPathExecutor](https://github.com/cabaletta/baritone/blob/1.21.1/src/api/java/baritone/api/pathing/path/IPathExecutor.java) exposes `getPosition()` and `getPath()`. [IPath](https://github.com/cabaletta/baritone/blob/1.21.1/src/api/java/baritone/api/pathing/calc/IPath.java) exposes ordered `positions()` and `movements()`.

This is a cleaner way to inspect route/candidate crossings and support Observatory than reflecting on concrete private classes. **But the runtime is an Automatone-derived fork; verify matching interfaces and access/mappings before modifying it.** The previously observed `BRIDGE_SKIP: Bridge planner unavailable: IllegalAccessException` remains unresolved. A native typed API may fix *that class* of problem after compatibility checks, but source comparison is required.

**Priority (ELLIE-022):** Identify exact reflection target and exception source; if the fork exposes equivalent public accessors, replace with typed path access and handle `getCurrent() == null` without repeated logs. Test bridge code in-game.

## Finding 4 — Native bridging is already a movement candidate

[MovementTraverse.java](https://github.com/cabaletta/baritone/blob/1.21.1/src/main/java/baritone/pathing/movement/movements/MovementTraverse.java#L125-L155) calculates the cost of placing a block under the next step when a normal walking surface is absent, with support and reachability checks. [Settings.java](https://github.com/cabaletta/baritone/blob/1.21.1/src/api/java/baritone/api/Settings.java) defaults `allowPlace=true`, fluid placement flags true and `blockPlacementPenalty=20` (ticks); it restricts disposable materials via `acceptableThrowawayItems`. [FEATURES.md](https://github.com/cabaletta/baritone/blob/1.21.1/FEATURES.md) describes block placing, sneak bridging and path cost tradeoffs.

**Implication:** Do **not** create a competing second pathfinder for every movement. First check whether the NPC's native path context can place disposable blocks, can bridge in/over water, which items are acceptable and whether the route is actually selected. The custom bridge helper should address the higher-level choices absent from pathing, such as preparing materials, persistent shortcuts, explicit bridge policy and wider crossings. Do not globally add construction planks to disposable materials without a budget. See ELLIE-004, ELLIE-005.

## Finding 5 — Unreachable-target blacklist and route ownership exist

[GetToBlockProcess.java](https://github.com/cabaletta/baritone/blob/1.21.1/src/main/java/baritone/process/GetToBlockProcess.java#L41-L154) maintains `knownLocations`, blacklists presumed unreachable nearest locations on calculation failure, scans again and uses a `GoalComposite` across candidate positions. Its API includes `blacklistClosest()`. [PathingControlManager](https://github.com/cabaletta/baritone/blob/1.21.1/src/main/java/baritone/utils/PathingControlManager.java#L86-L151) controls which process owns the route; it can revalidate or cancel a segment when the process/goal changes.

**Implication for trial-chamber crafting-table detour:** An EllieGPS waypoint is memory of a block's *location*, not proof it's cheap to reach. Candidate crafting stations should be validated as loaded/present, require sensible 3D/local constraints, be rejected after failed access, and prefer local crafting if route cost is high. Avoid navigating to the closest XYZ table through caves. Consider route-cost heuristics and existing process blacklisting rather than introducing another independent target-selection loop. See ELLIE-021.

## Finding 6 — Terrain and tool cost are part of the core model

[FEATURES.md](https://github.com/cabaletta/baritone/blob/1.21.1/FEATURES.md) explains that block-breaking cost takes the **current tool inventory** into account, and pathfinding can trade mining through blocks against climbing around them. It also documents segmented long-distance paths, chunk caching and safe block placement.

**Implication for stone-tool preflight (ELLIE-003):** The upstream engine knows a stone axe/pickaxe saves effort when already available. Ellie still needs a high-level bootstrap step that **obtains and equips** stone-tier tools in advance, before the first expensive gathering job. Don't try to solve tool provisioning by rewriting movement-cost math.

## Proposed investigation order

1. **First:** Inspect actual embedded Automatone 1.21.1 API and live `allowVines` state. Reproduce vine-under-log bug without external fire, with exact path movement and collision positions logged.
2. **Next:** Fix `IllegalAccessException` by using the embedded fork's supported path interface where possible. Probe native block placing, water behavior, default throwaway-item policy and route goals before adding extra bridge machinery.
3. **Then:** Add small repeated-failure memory to navigation (movement-index/position/target/cause) and safe local obstacle handling. Respect timeout and process control.
4. **After:** Use validated table waypoints and route failure blacklisting for crafting-table selection. Test surface table vs underground table and reload.
5. **Lastly:** Integrate stone-tool preflight and shared navigation policies, with long-run survival tests.

## Required test evidence

- Vine-climbing, vine-under-log, vine-water intersection, leaves above head, mangrove vines. Record whether `allowVines` is true/false; do both settings without assuming one fixes all.
- Timeout vs immediate `FAILED` vs path cancellation/recalculation, including exact movement index and `IPath` positions. No indefinite replan loop.
- Bridge path computed with/without acceptable blocks, with water and banks, valid support and unloaded chunks. A `BRIDGE_SKIP` event with exception is a failure, not a successful cost decision.
- Nearby surface workbench vs deep dungeon station; verify reachability, fallback, no tunnel-for-one-door behavior.
- Do not touch combat retaliation, independent teleport extension or the existing working house builder. Real Minecraft success is mandatory before marking fixed.

**Conclusion:** Use the embedded navigation engine's path and failure machinery as the *single source of truth*. Ellie-specific additions should manage high-level goals, resources, verified waypoints and bounded recovery, not duplicate Baritone's A* at every layer.
