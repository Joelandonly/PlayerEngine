# Explicit native excavation: `ellie excavate x1 y1 z1 x2 y2 z2`

## Live test and verified interruption fix (2026-11-09)

User-tested build: `240dc3101ac5b9f9ee64841f56aa6b2c77c8f498`,
from PR #29. Uploaded files:
`latest(20261109-023728).log`,
`ellie-observatory-events(20261109-023728).jsonl`, and status snapshot.

The user started excavation of `91 80 277` through `120 80 296`
(600 total blocks). Actual air-cell progress was continuous; the native
builder was visibly digging and 15 inventory slots were still free at
the final interruption. The failure was NOT the no-work watchdog and
NOT full inventory.

- **03:34:08 local:** 444 blocks remaining; the first excavation task
  reported `BLOCKED_EXCAVATE_CANCELLED`, immediately as
  `User Tasks -> Mob Defense -> User Tasks`.
- **03:36:28 local:** restarted excavation progressed to 261 blocks
  remaining; it reported the same cancellation as
  `User Tasks -> ForceEquipGearChain -> User Tasks`.
- These events establish an erroneous **transient task lifecycle**.
  A skeleton nearby does not demonstrate a combat or targeting failure.

**Correction:** `ExcavateAreaTask` now implements the existing
`TransientlyResumableTask` contract. On the native runner's accepted
`HIGHER_PRIORITY_CHAIN` or `GESTURE_OVERLAY` suspension it retains the
same owner/task, area, last verified remaining count, cumulative build-work
and no-work history. It releases ONLY its own native builder so that safety
or gear work can run. At resume it checks the same world, determines
whether the old native plan is still owned/active, and either continues or
revalidates the full selected region and creates another `clearArea` plan.
A preflight interrupted before digging is rechecked from the beginning.
The 128-interruption ceiling and total-work watchdog remain bounded.

Explicit user stop/replacement, abandoned resume, world mismatch,
actual builder ownership theft, and unsafe changed blocks are still terminal.
No changes to Mob Defense, ForceEquipGearChain, stored schematics,
combat hunting or automatic inventory disposal.

**Automated evidence:** after the fix, full GitHub run
https://github.com/Joelandonly/PlayerEngine/actions/runs/42552708414
passed 34 excavation geometry/lifecycle tests, 11 real Minecraft
1.21.1 method binding checks, existing P0/PR28 regressions and exact
release identity. These tests substitute native NPC/world execution and
are **not** a successful survival-game excavation.

**Next live acceptance:** install the exact new PR #29 JAR; on a backed-up
world, repeat `ellie excavate 91 80 277 120 80 296`. Capture
`EXCAVATE_SUSPENDED` and `EXCAVATE_RESUMED` with the same
`project` id when combat/equipment takes priority, and verify the block
count continues downward without `BLOCKED_EXCAVATE_CANCELLED`.
Do not issue an explicit stop or another owner command during that test.

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
