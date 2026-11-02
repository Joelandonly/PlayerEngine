# Ellie Minecraft 1.21.1 Fabric validation matrix

Experimental runtime: Minecraft 1.21.1, Fabric, PlayerEngine 1.4.0 and the existing Player2NPC companion/teleport extension. Current GitHub source remains an older 1.20.1 Quilt configuration. Results below distinguish **documented user sessions** from tests yet to run.

## Evidence levels

| Level | Meaning | Can mark a feature complete? |
|---|---|---|
| S0 | Design proposal or inferred diagnosis | No |
| S1 | Compiles and/or passes mock tests | No |
| S2 | Packaged JAR/class-reference integrity checks | No |
| S3 | Reproduced successfully in actual Minecraft once | Not by itself |
| S4 | Repeated in survival, distinct seeds/biomes, interruptions, and regression checks | Potentially |
| S5 | Rebuildable from this repository plus S4 evidence | Yes, for scoped acceptance criteria |

**Current status across the recent JARs:** Some mock/component and packaging claims exist, and select behaviors were observed in Minecraft, but there is **no current S5 sign-off** for the preview patch set.

## Scenarios

| ID | Scenario | Expected result | Evidence / current status |
|---|---|---|---|
| T-01 | 5×5 generic wood house beside jungle trees | Use jungle logs/planks and matching door, no remote oak trip | Species selection worked once at 19:58; rest unverified |
| T-02 | Two houses sequentially in survival | All 124/124 placements, including door, without manual repair | Earlier session had two completed houses; rerun on current build |
| T-03 | Local door with crafting table but no doors in inventory | Craft matching door locally; return to structure | Jungle door planned; not verified placed |
| T-04 | Short straight water crossing | Check detour vs building; construct supported bridge and traverse | No confirmed live bridge |
| T-05 | Detour around lake | Candidate scan considers crossing where safe; no futile placement | Mock candidate behavior reported only |
| T-06 | Wide/irregular lake or unsafe shore | Reject with reason, detour/swim; do not build unsafe bridge | Live untested |
| T-07 | Vines obstruct route, no fire | Stall logged, controlled replan, eventually moves | Mock recovery reported only |
| T-08 | Leaves obstruct route | Recover without perpetual oscillation or clearing entire tree | Not live tested |
| T-09 | Ellie actively climbing a supporting vine | Never destroy support and cause a fall | Mock safety reported only |
| T-10 | House construction interrupted by **real** survival hazard | Suspends safely, resumes progress when safe, does not lose blueprint | Interruptions observed; user-created fire confounded earlier run |
| T-11 | Building request while underground Y=-19 | Explicit site selection/clear feedback; don't silently infer failure | Incomplete snapshot only |
| T-12 | Large wood gathering, no axe | Preflight obtains/uses stone-or-better axe if affordable | Feature not implemented |
| T-13 | Stone excavation, no pickaxe | Bootstraps from wooden to stone pickaxe; equips it | Feature not implemented |
| T-14 | Full diamond armour from high-level request | Craft/equip all four slots, including omitted boots | Previous session missed boots |
| T-15 | Teleport-to-Ellie UI during restart/respawn | Button appears and safely teleports | Reported missing; untested fix |
| T-16 | Following Ellie through short crossing | Shared traversal policy can be used outside building | Not implemented |
| T-17 | Jungle navigation long-run (>=30 min) | No endless stalls, excess path recomputes, fire hazards or memory leaks | Not tested |
| T-18 | Save/reload, resume persistent goals | No lost blueprint, no retained stale controller, correct goal state | Not tested |
| T-19 | Existing owner retaliation | Preserve intentionally configured behavior | Must not modify |
| T-20 | Observatory F8/F7 + diagnostics | HUD opens; logs causally useful, rate-limited; no private data | HUD observed, new recovery logs not live verified |
| T-21 | Long-path performance | No repeat stall-probe spikes or rising per-tick costs | Not tested |
| T-22 | Fresh checkout source build | Rebuild exact Fabric 1.21.1 preview behavior without binary injection | Source mismatch blocks this |

## Procedure for each live test

1. Record Minecraft version, Fabric loader, full installed PlayerEngine/Player2NPC artifact names and **SHA-256**. Merely seeing `playerengine 1.21.1-1.4.0` in mod list is insufficient to identify which preview JAR ran.
2. Record seed, biome, coordinates or a safe anonymized relative geometry, game mode, and exactly what Ellie was asked. Preserve original instruction for local private debugging but publish only sanitized excerpts.
3. Run normal workload **without manual interference** first. If player breaks vines, sets fire, changes game mode, teleports, or hits Ellie, flag that as an intervention; don't assign subsequent behavior to the baseline code.
4. Capture timestamps of goals, chain changes, `WOOD_RESOLVE`, `CRAFT_REQUEST`, `BRIDGE_SKIP`, `PATH_STALL`, `PATH_RECOVERY`, and completion/failure. Missing events are an instrumentation limitation, not proof that nothing happened.
5. Distinguish movement through a hazard from the decision to enter it. Note whether any block was destroyed by normal mining, not spawned/removed programmatically.
6. Repeat successful case on another world and after save/reload, then attempt one deliberately adverse scenario. Verify intentional combat retaliation, teleport extension and HUD survive.
7. Only mark **LIVE / PASS** when all scoped acceptance checks and negative tests pass. Only mark **SOURCE / VERIFIED** after the exact implementation is checked into this repo and built from clean checkout.

## Release manifest template

```text
Patch name:
Date:
Source commit / source branch:
PlayerEngine JAR SHA-256:
Player2NPC JAR SHA-256:
Minecraft / loader versions:
Test IDs run:
Mock tests: pass/fail/not run
JAR integrity: pass/fail/not run
In-game survival: pass/fail/not run
Long-run: pass/fail/not run
Known limitations:
Rollback JAR:
```

## Security and scope

Public tracking may contain sanitized symptoms and decisions. Never commit raw game/LLM logs, user tokens, UUIDs, account identifiers, local absolute paths or private-world data. Testing should not overwrite the independent Player2NPC extension, change retaliation, or remove existing builder functions by accident.

## Additional scenarios, 2026-11-02

| ID | Scenario | Expected result | Evidence / current status |
|---|---|---|---|
| T-23 | Workbench visible near surface; different workbench directly below in trial chamber | Reuse surface workbench or create a local one; never take deep detour | Mock selection passed; live test pending |
| T-24 | Crafting table verified, then save/reload world | Table is recorded in per-world EllieGPS and reused if still present and close | Mock persistence passed; live restart pending |
| T-25 | Remembered crafting table destroyed or unloaded | Reject stale/unloaded target and craft/place locally | Mock invalidation passed; live pending |
| T-26 | Jungle vine stall while Ellie is pursuing a `Destroying block` target | Monitor progress, log stall and recover safely without breaking support vine | Updated mock recovery passed; live pending |
| T-27 | Construction bridge planner encounters water | No `IllegalAccessException`, actionable diagnostics and real supported crossing | Runtime error observed; not fixed |

The `WORKBENCH-VINE-PREVIEW` binary hash is `1f0bc6603ff788a6736387cbe08e773fd21c107b99f980d65e0da985e0263b0c`. The code exists in an experimental downloadable ZIP, **not in this repository's 1.20.1/Quilt source**. Live success remains unverified.


## Upstream Baritone comparison regression cases

| ID | Case | Required outcome |
|---|---|---|
| T-28 | Jungle-log mining with vines below logs (Baritone issue #1773) | Ellie doesn't stall indefinitely; mine/repath around specific obstruction without unsafe vine removal |
| T-29 | `allowVines=false` vs `true` under the same terrain | Measure exact behavior rather than assume false solves every vine snag |
| T-30 | Movement timeout followed by repeated identical replan | Detect identical-failure cycle, record path index/position and bounded local avoidance |
| T-31 | Native movement placement and custom bridge logic with water | Avoid duplicate path owners; verify allowed throwaway blocks and physical support |
| T-32 | Embedded Automatone path API compatibility | Enumerate supported public interfaces; no IllegalAccessException before shipping |

Source analysis and evidence: [Baritone research](BARITONE_RESEARCH_2026-11-02.md). These scenarios are **not run**.
