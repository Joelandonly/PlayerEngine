# Ellie setup: compact workstations, conditional local platform

> **Historical setup design, not the current `setup` command (2026-11-09).**
> This page describes the former compact-platform / 20-milestone survival implementation. The current PR #29 candidate dispatches the owner-facing `setup` to the verified 14-stage `BasicSetupTask`: 12 logs, HOME bench, wooden and stone tools, 20 cobblestone, 16 coal, 64 torches, one chest and one furnace. It does **not** demand iron armor, shield, buckets, bed or a house. See [current command inventory](ELLIE_COMMANDS.md) and [current backlog](ELLIE_BACKLOG_CURRENT_20261109.md). This historical implementation is retained for provenance only.


`Ellie, setup` follows the existing 20-milestone survival equipment plan and now acquires **one chest**, **one furnace**, and **one crafting table**, then verifies their real placement.

## Layout

```
T . F . C       T table, F furnace, C chest
= = = = =       One-block standing/service aisle (only when platform needed)
```

## Site selection

1. Prefer a directly usable existing surface within 12 horizontal blocks of Ellie.
2. Otherwise select an existing supported surface that only needs obstruction clearing.
3. Only if neither exists, try native compact support construction at a nearby supported/replaceable water or void site. Search vertical offsets 0, +1, -1, +2, +3, protecting Ellie's immediate body/aisle from work.

For ordinary land, no cobblestone support jobs are generated. For the water/unsupported fallback, the site consists of **three equipment support cells and five aisle support cells** (maximum eight cobblestone foundation jobs), plus at most 16 native clearance checks. Existing solid supports are reused, and terrain jobs never contain an air-clear instruction at the same coordinates as a foundation cell. Only air/fluid support cells can be filled; protected/occupied obstructions are not blindly excavated.

Every operation is delegated to a native Minecraft task, then checked against the actual world; no block-state writes, teleport, free inventory or generated villages. The original `build_structure` command remains separate. Baritone's `AdditionalBaritoneSettings` receives a temporary *exact-Y* predicate protecting platform support coordinates against path-mining during setup, in addition to the existing resource-source exclusion on the 7×3 footprint. Both guards are removed on terminal completion or stop.

Saved journal markers are `BASE_LAYOUT_COMPACT_V3` for natural ground and `BASE_LAYOUT_PLATFORM_V3` for the supported fallback. Older V2 or 9×7 layout journals are not reused; existing placed structures are not automatically torn down.

## Verification and limitations

`bash build.sh` runs bytecode validation, original Player2 interpreter byte equality, command and supervisor tests, platform generation tests, simulated water/supported surfaces, foundation damage recovery, and release packaging checks.

**Game behavior remains unverified.** Baritone must be able to reach/place support cells physically; unsupported remote aerial builds, deep submerged starts, protected areas or distant elevations are not guaranteed reachable. Repeated ineffective native tasks fail with a precise status instead of silently reporting setup complete.
