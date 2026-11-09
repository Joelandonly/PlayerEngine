# Planned: safe inventory overflow during autonomous resource gathering

**Status: NOT implemented, NOT in the current JAR.** GitHub Issues are disabled in this repository, so this file is the tracked backlog entry.

## User's live problem (2026-11-09)

During `schematic_prepare` spider hunting for string, unrelated loot quickly fills Ellie's 36 main inventory slots. The material target can be 64 string, but full inventory may block pickup before then. Calling the existing manual `deposit` command can supersede the schematic objective. The user prefers reversible storage, with any destructive disposal strictly opt-in.

## Scope: existing coordinator, no new manager

- Trigger at **five or fewer free main slots** (configurable), before loot pickup stalls. Source: `InventorySpace.inspect`; unknown slot data means no destructive action.
- Preserve current `ACQUIRE` action object, absolute target, inventory progress and bounded retry history across a new *same-goal* storage subaction. Do not use `CoreGoalSupervisor.submit` for an independent `deposit` goal.
- Retain requested loot (e.g. string), all schematic materials, recipe inputs, gear, weapons, food, survival items, rare items and **unknown stacks**. Never use existing broad `deposit`-all logic as an automatic filter.
- Select an existing known reachable chest with verified free space; use the schematic's already protected depot if appropriate. If none is safe, consider building one carefully as a bounded subaction. Never destroy or relocate project chests, alter build selection, or leave supplies exposed.
- Deposit only a vetted surplus item list with exact inventory/chest before-and-after checks. Record where it went in the existing journal, then resume `ACQUIRE` from current real inventory, without restarting gathering from the depot.
- Mob Defense and environmental survival chains still preempt work. A temporary defense interruption retains the storage subaction and acquisition ownership; explicit owner stop, terminal failure, or world change cancels.
- Events: `INVENTORY_PRESSURE`, `INVENTORY_OVERFLOW_STORAGE`, `INVENTORY_OVERFLOW_RESUMED` include project/action ids, before/after free slots and exact material deltas.
- **No automatic lava, drop-on-ground, or deletion.** A later owner-approved whitelist for purely renewable junk can be proposed separately; unfamiliar items are always kept.

## Regressions and live acceptance

1. Simulate 34/36 main slots, 3 string already obtained, and accumulating junk. Safely deposit only surplus to a known chest. Keep the original `ACQUIRE` absolute target and progress.
2. Chest full/unreachable/unloaded or inventory count mismatch: emit specific blocked reason; never pretend a deposit succeeded.
3. Preserve all unknown high-value item stacks and the 14-stage setup tools.
4. Verify repeated combat interrupts during unload and resume don't lose the same parent action or reset its bounded budget.
5. Validate actual chest count deltas and successful continued spider hunting in real Minecraft 1.21.1.

**Do not combine with the explicit `excavate` command**. That is a separate user-requested feature, and this backlog is not a claim that inventory pressure has been solved.
