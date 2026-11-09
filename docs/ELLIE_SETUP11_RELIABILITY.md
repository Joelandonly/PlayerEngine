# Setup11 integration and shared reliability

> **Historical Setup11 revision, not current `setup` (2026-11-09).**
> Its previous link to the "current expanded setup" is obsolete. The current PR #29 candidate dispatches to 14-stage `BasicSetupTask`; see [command inventory](ELLIE_COMMANDS.md) and [reconciled backlog](ELLIE_BACKLOG_CURRENT_20261109.md).


Historical v3 contract. The subsequently superseded survival setup is documented in
[ELLIE_SURVIVAL_SETUP.md](ELLIE_SURVIVAL_SETUP.md).

Baseline: `codex/shared-recovery-crash`, `ae24a84cc9fde89dda55a23a4ecb53b4c25e9056`.
Owner upload: `Ellie-1.21.1-Setup11-Lean-Source-20261105.zip`, SHA-256
`08597b502d3f6b6fe8a243edd35576b17683907031f4c68a67ffa2458ea5ff20`.
Build ID: `20261105-setup11-tools-first-no-torch-placement-v3`.

Owner update after live testing: automatic torch placement and surface lighting
are removed from setup. Shield and iron tools precede acquiring torch supplies.

This remains the existing binary-overlay project, with source overrides and
audited patches against the checksum-pinned PlayerEngine 1.4.0 baseline. Goodbird
1.1.11 is a reference only. Neither this work nor the upload is a full engine
source migration. The output is not claimed byte-identical to the uploaded JAR.

## Required setup contract

In order: wooden pickaxe; early stone axe; two stone pickaxes; stone shovel;
crafting table; furnace; chest; shield; iron pickaxe; iron sword; 32 torches.
All are required. Better tools already owned may satisfy the corresponding
quantity without downgrading; setup does not acquire diamonds. No optional
skipping, buckets, iron armor, spare iron tools, bed or bread are in this plan.

Verify the three-station workshop before the equipment stages. Acquire the
shield and iron tools before torches. Keep all 32 torches in inventory for manual
placement. **No automatic torch placement or light survey is run by setup.**
Completion verifies the kit and stations; it makes no lighting/spawn-safety claim.
Existing station positions are retained on rerun, including after the live
"No safe surface torch position can cover 1 dark samples" failure.

## Shared progress and ownership

The existing Task.tick recovery gate and scanner rejection remain the shared
boundary for setup, resource collection and material collection for construction.
The upload's pair detector is supplementary: the main stall/retry policy now
retains evidence across **all target switches**, including slow A/B revisits.

- Mining: observe the actual block changing or aggregated main-inventory gains.
  Damage above its per-target high-water mark allows a slow break to finish,
  but never replenishes recovery attempts; damage grace is bounded at 1,800 ticks.
- Navigation: novel positions must reduce distance to the target. Retain best
  distances for every visited target. Unknown-target exploration has a 900-tick
  window. Returning to an old position or changing targets is not progress.
- Crafting: inventory gains or a new monotonic phase milestone count. Repeating
  earlier phases does not count. Existing setup material/phase checks remain.
- Placement: actual target block changes are observed; workshop completion additionally requires the expected station blocks.
  Lighting is excluded from setup.
- Inventory: read exactly 36 main/hotbar stacks directly. Armor/offhand and stale
  tracker maps cannot supply fictitious capacity. Do not dump inventory.

One recovery lease owns movement. Safety chains retain priority. Three shared
attempts use different approach candidates or safe clearance, then reject the
target through the existing scanner for 120 seconds. Reselection never resets
the attempt budget. Genuine output can replenish it. A resource job with no
verified output for 2,400 active server ticks terminates with a specific BLOCKED
reason; this prevents endlessly scanning or waiting for blacklist expiry.
At nominal 20 TPS these limits are 5 seconds for a stall, 2 minutes without
resource output, and 2 minutes of target rejection. They are tick-based where
noted, so wall time grows on a lagging server. Existing setup/station
budgets provide additional bounds. Long travel without resource output can
therefore be reported blocked; move to a suitable resource area and retry.

`TASK_PROGRESS` reports objective, task tree, target, actual slots, no-output
age, mode and attempt every 100 active server ticks. `TARGET_RECOVERY` retains
reach, position, blocker, look/input ownership. `TASK_BLOCKED` and
`SETUP_BLOCKED` preserve specific reasons. A shared blocked outcome overrides a
nominally successful native receipt in the existing supervisor.

## Automated validation

Run from `ellie/` with a Java 21 **JDK** and Python 3:

```sh
bash build.sh
node --test tests/*.test.mjs
python3 make_release.py
```

The full Java suite includes the existing command, supervisor, production,
storage, village, range, recovery, setup and packaged-bytecode checks. Player2's
API service remains byte-identical to the baseline; packaging rejects unintended
class changes. Historical bridge tests compile into a separate test output and
never enter the native Player2 JAR. Node tests check the retained historical
sidecar, not a new runtime dependency.

`SetupChainTest` runs production setup plus workshop/lighting state machines
through all eleven objectives, including fresh and partial inventory, existing
stations, repairable uneven ground, missing iron, blocked stations and false
child placement completion. An unavailable light-survey boundary must never be
called; dark samples cannot block completion. Acquisition, inventory,
placement and light propagation are **substituted game boundaries**.

`SharedRecoveryRuntimeTest` runs the real patched Task.tick against substituted
world/pathfinder boundaries. It includes inaccessible alternating ore, selection
of a third location, successful alternative output, exhausted resource jobs,
obstructed stations, safe clearing, and defense priority/lease cleanup.
The policy regression uses the November 5 target coordinates from the handoff;
original Minecraft logs were not supplied in this task. Canonical inventory
checks execute the patched tracker method against substituted stacks.

These tests can reject looping/false success at the tested boundaries. They do
**not** establish actual Minecraft recipe execution, pathfinder reachability,
NPC/companion compatibility in a running game, or live survival completion.

## Delivery and live gate

The Build workflow produces `Ellie-1.21.1-JAR-only`, containing exactly one
revision-named `UNVERIFIED.jar` and SHA256SUMS.txt. The separate source artifact
contains zero JARs/classes; obtain the documented checksum-pinned baseline from
the repository to rebuild. `META-INF/ELLIE_BUILD.json` records the exact source
revision, source-input digest, baseline checksum and `live_minecraft_validated:
false`. CI verifies the packaged bytes and revision before upload.

Use a backed-up fresh survival world and the existing Player2NPC/companion mods.
Install exactly one PlayerEngine JAR. Check SETUP_START for this build ID, run
the same `setup` command, and retain observatory events through iron sword and the 32-torch inventory check. Test real iron absence/inaccessible ore and an
obstructed table: accept a different successful strategy or a specific BLOCKED
outcome, never repeated A/B mining or completion without outputs. Re-run with
partial inventory and placed stations. Check defense interruptions, commands,
storage/build tasks and remote ticking with vanilla view/simulation ranges.
Only that observed run can establish gameplay success. A successful CI artifact
or sandbox download is a separate delivery result.
