# Exact setup contract: full kit and placed starter base

This preview implements the maintainer's chosen target: a full starter kit plus
placed workstations/storage. It is not a claim of live Minecraft acceptance.
Invoke the existing exact `Ellie, setup` route; supplied logs show that this route
can dispatch without calling the conversation model. Generic `build_structure`
is a different, LLM-assisted operation.

## Required outputs

- Iron-or-better pickaxe, shovel, axe and sword.
- Iron-or-better helmet, chestplate, leggings and boots, held or worn. Existing
  diamond/netherite equivalents count; setup should not demand an iron downgrade.
- One shield, two buckets, 32 torches, 16 bread and one bed item.
- A placed crafting table, a placed furnace and **eight placed single chest blocks**.
  Chests are separated to preserve walk space; no double-chest orientation guarantee
  is made. Bed/torches remain supplies; this does not place a house, bed or farm.

Wooden/stone tools are bootstrap steps, not final reserve requirements once the
iron kit is obtained. The old optional-item skips cannot satisfy this contract.

## Workstation/storage phase

The original setup starting feet position and dimension are captured before
resource gathering. A bounded nearby search at that height looks for a clear,
supported workshop pad. Only ordinary full-block ground is accepted. It does not
excavate terrain, clear buildings, break occupied blocks, fill water, or descend
into a cave to find a site. An unsuitable site blocks setup with an explanation.
The NPC's initial feet cell and immediate neighboring cells are excluded as
placement targets, accounting conservatively for body overlap at block edges.

The layout occupies these relative cells (all at the same Y):

```text
T . F . . . .     T = crafting table, F = furnace, C = chest
. . . . . . .
C . C . C . C
. . . . . . .
C . C . C . C
```

The complete pad includes a one-block margin, solid support and head/lid clearance.
The selected anchor is saved to a separate per-world/per-character goal journal
before placement. Rerunning setup reuses and rechecks that recorded layout instead
of placing another base. A corrupted, unavailable or dimension-mismatched journal
blocks placement rather than silently choosing a duplicate site.

Workstations/storage are established after bootstrap and before iron upgrades.
The native `PlaceItemBlockAtPosTask` performs interactions and checks the resulting
block. Native catalogue/crafting tasks acquire missing station materials. There
are no direct world edits or LLM blueprint requests in this phase.

Placed station/chest blocks count toward the requirement after their item stacks
are consumed, and only when the expected block is actually observed. Missing kit
items trigger at most two final repair passes. Placement has a finite active-work
budget and does not treat a stopped child as successful placement.

## Fixes motivated by the supplied logs

- All kit objectives are required; missing armor/food cannot be optional skips.
- Recheck final kit and placed blocks rather than only trusting past subtask success.
- Ignore unrelated cobblestone changes during iron acquisition. Material progress
  requires a new per-item high-water mark, not a decrease/regain cycle.
- Narrow the crafting macro's approach goal from radius three to one while retaining
  its actual native interaction-reach check. This targets the observed vertical
  arrival/reach mismatch; it is not a global pathfinding rewrite or verified cure.
- Stall diagnostics include feet cell, cell below feet and collision bounds to
  investigate ELLIE-023 at positive/negative block boundaries.

See [sanitized log findings](ELLIE_LOG_FINDINGS_2026-11-04.md).

## Verification and remaining gates

The build runs the existing supervisor/bridge/dispatcher checks plus:

- setup final-verification tests with inventory boundaries;
- conservative layout and task-handoff tests with a simulated world;
- monotonic/relevant material-progress tests, including the supplied-log regression;
- ASM verification and an exact allowed-class-change inventory.

The native reflection symbols were checked against the actual baseline method
signatures and Fabric Yarn 1.21.1+build.3 mappings. This is static API evidence,
not a Minecraft integration test. The complete mod source remains unrecovered.

**Not yet verified live:** bootstrap/gathering every required resource, the actual
placement interactions, safe-site selection on the real terrain, interruption and
world reload, chest reuse, and the narrowed crafting-table approach. Native
resource macros can still fail or lack reachable materials. Such a failure must
report BLOCKED rather than completing setup. A prepared test pad, exact JAR hash,
matching Player2NPC version and actual in-game logs are needed for acceptance.
