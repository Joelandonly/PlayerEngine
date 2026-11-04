# Ellie 1.21.1: crafting table navigation recovery (2026-11-04)

## Evidence from supplied 18:54-18:58 game logs

- New setup run reaches stone-pickaxe step 2/20, after completing one wooden pickaxe.
- A crafting table is placed and remembered at (120, 68, 4) at 17:55:36 UTC.
- Following a cobblestone-gathering detour, the character is at roughly (118.51, 65, 6.29) while trying to return to the table.
- Baritone repeatedly targets a break at (119, 65, 5) during attempts to approach the table; the Observatory reports no meaningful movement for 120 ticks.
- The sample log does NOT show repeated cobblestone placement during this loop. It shows repeated breaking/movement. Do not infer the exact block was actually removed each time.
- The operator interrupts the run at 17:58:30. This is NOT a terminal setup failure caused by the patch.

## Patch

- Patch `CraftInTableTask.onResourceTick` to monitor active table approach, clear an invalid/stalled target, and reselect safely.
- Patch both `CraftingTableLocator.findReachable` and `findReusableNearby` to reject inaccessible candidate tables, including GPS-memory candidates, rather than immediately reselecting the same failed table.
- Disallow reuse when target vertical level differs by more than 2 blocks from current feet Y. This intentionally favors local table placement in ravines/cliffs; it may choose an extra table even where a longer stair path exists.
- Detect under 0.6 blocks of progress for 90 active crafting ticks, check actual interaction reach, temporarily suppress that table for 120 seconds, then retry elsewhere.
- Preserve the table block in the world: do NOT mine/destroy an inaccessible table just to craft a pickaxe.
- Emit `CRAFT_TABLE_REJECTED` and `CRAFT_TABLE_RECOVERY_WARNING` into observatory event stream.

## Explicitly unchanged

- Native Player2 interpreter remains bit-identical to pinned baseline. No ChatGPT interpreter bridge.
- Compact 1-chest, 1-furnace, 1-crafting-table setup and conditional water/elevation support platform remain.
- Other tasks, intentional break/place, general Baritone navigation, village construction remain unchanged.

## Validation

- `bash build.sh` passed: all existing project tests, new `CraftTableRecoveryTest`, ASM analyzer, player2 byte-identical check, command routing audit.
- Simulated tests validate the selector and watchdog logic. No real in-game run or full Minecraft navigation has been executed.
- The remaining place/break failure in native Baritone navigation is NOT independently proven fixed; this patch addresses the repeated crafting-table target observed in this particular log.
