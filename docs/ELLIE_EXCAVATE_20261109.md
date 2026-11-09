# Explicit native excavation: `ellie excavate x1 y1 z1 x2 y2 z2`

## Scope (separate from automatic inventory storage)

Finite inclusive cuboid selection of up to **32,768 blocks**. The six supplied
integer coordinates are NOT interpreted as offsets; reversed corners normalize
to the same region. Owner can use:

```
ellie excavate 100 64 100 120 74 120
```

This selects 21 × 11 × 21 = 4,851 blocks. The command routes through
the existing native command executor and `ProjectTaskGateway`, not a model.
It starts the existing NPC Baritone `BuilderProcess.clearArea` after a
complete safety preflight. The project is a real native `Task` with bounded
verification, timeout and terminal outcome; it is NOT a second scheduler.

## Destructive safeguards

Before any digging, require world continuity and loaded chunks, then reject
any selected cell containing a real block entity (chests, barrels, signs,
furnaces, storage machines), a protected schematic depot/support, existing
water or lava, bedrock or portal blocks. If a prohibited block is found, **no
excavation starts**. Reject absent NPC/world, wrong height limits, oversized
regions and already-active builder operations. Never drop or delete inventory
items. These are safety checks for an explicitly selected area, not a
guarantee that all player-built decorative blocks are preserved.

Check native builder identity during execution and only cancel our own
builder on explicit stop. Completion requires a *fresh verified sweep*
of actual air blocks; a stopped pathing process or an action return value
cannot indicate success without real-world verification. Keep bounded no-work
and total-work limits and emit `EXCAVATE_PREFLIGHT`,
`EXCAVATE_STARTED`, `EXCAVATE_PROGRESS`, `EXCAVATE_BLOCKED`,
`EXCAVATE_COMPLETED`.

**Limitations:** Unloaded portions of an area are rejected rather than being
silently explored. Building/digging can expose newly flowing water, falling
gravel, mobs or other hazards. A large excavation is not automatically
invisible to ordinary survival physics, and should first be tested on a
backed-up world. Existing resource/food/defense handlers keep their normal
priority. Manual `excavate` is a new owner goal, so issuing it while
`schematic_prepare` is running will supersede that project, exactly as
any other explicit user command would. Do not use it mid-hunt.

## Validation

- Pure geometry/parse tests for explicit coordinates, order, signed values
  and size/height rejection.
- Full GitHub build with original PR25/PR26/PR28 test suite preserved.
- Native 1.21.1 runtime signature verification for Baritone `clearArea`,
  world chunk/block/block entity APIs, and the new owner command.
- Final JAR class-scope verification: exactly two existing class modifications
  (`PlayerEngineCommands`, `NativeCommandText`) and three new tracked
  classes. Prior 14-stage setup and task/combat/depot classes unchanged.
- **Live validation not run:** backed-up game, small rectangle; ensure chest
  and water selections are rejected before any work, and a safe rectangle
  excavates with real air-cell verification and no protected-block damage.

Inventory pressure during spider farming is a distinct P1 task described in
`docs/ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md`.
