# Schematic ACQUIRE ownership across Mob Defense interruptions

**Parent:** PR #26, exact pre-patch source `620c200841244c3a25c6874ec836e36f5448b3d5`. **NOT** closed sheep workaround PR #27.

## Root cause and scope

The 2026-11-08 23:44–23:49 game logs show `white_carpet` dynamic recipe -> `string x128` -> spider hunt; a higher-priority `Mob Defense` chain took control against zombies/skeletons. The schematic's `afterChildrenStopped()` discarded the active `ACQUIRE` action. On returning to the coordinator, `prepare()` required travel back to the original storage location before creating another native acquisition child. No log evidence isolates a failure to damage spiders or pick up string, and this patch does **not** change combat targeting, mob drops, recipes or resource strategy.

## Exact fix

The single modified packaged class is `SchematicProjectTask.class`; helper `SchematicAcquireResume` is in tracked source. The pinned input SHA is `f605c5275c746f93e6c5fdaefd632c17f36b13fa2d0832426c5f366b71cf7c5f`.

- On native accepted transient suspension, preserve **the same Action object**, its ACQUIRE kind, absolute item target, inventory high-water and cumulative tick/idle counters. The native task runner is still allowed to stop its child normally. Pause physical builder **only if actually active**.
- Record a project-identity/action-identity lease with the inventory count at suspension. Multiple structurally identical schematic goals cannot share a lease.
- Before the next `pollAction()`, validate the exact action identity, query actual live inventory, then either verify already-satisfied stock or construct a **new** native acquisition task with the *original absolute total* (not a newly invented missing count). Existing 1.21.1 `SchematicWorld.acquire` accepts an absolute inventory target; its delegate calculates the missing amount. The v18.3 plank patches remain unchanged.
- `pollAction()` still checks the actual material count, enforces its existing bounded idle/total tick limits and updates the same high-water history. A Mob Defense interruption alone cannot cause a new `TRAVEL`.
- Explicit stop, abandoned suspension, terminal block, completion and world change discard pending leases and follow the original terminal behavior. Other action kinds (travel, transfer, placing, post-processing) retain original suspension cleanup.
- No second command scheduler, no workaround using sheep, and no weakening of hazard/descent protection.

## Observable events

`SCHEMATIC_ACQUIRE_SUSPENDED`: `project`, `action`, `item`, `absoluteTarget`, `carriedBefore`, `remaining`, `actionTicks`, `idle`.

`SCHEMATIC_ACQUIRE_RESUMED`: same identifiers, `carriedBefore`, `carriedAfter`, `remaining`, and `next=ACQUIRE|VERIFY`. The log includes an explicit `native_child_recreated` indication.

## Verification

`CombatAcquireResumeRegression` uses the real patched coordinator bytecode with the existing `SchematicProjectTest` substituted world/native execution to assert: repeated Mob Defense interruptions preserve action identity and watchdog counters; 3 then 5 string picked up is credited; new workers receive absolute inventory target 8 and continue at current position, with 0 premature depot travels; a completed 8-string target is verified without restarting the hunt; later depot travel is allowed once acquisition is finished; explicit user stop, abandoned resume, and world change remain terminal.

Final packaged verifier validates the exact hook, two helper classes, single existing class scope, previous Task stop reentrancy fix, crafting fixes, depot protection, and 14-stage setup unchanged.

**Gameplay unverified.** The next survival test must show a real `SCHEMATIC_ACQUIRE_SUSPENDED` followed by `SCHEMATIC_ACQUIRE_RESUMED next=ACQUIRE` on the same action id, without an intervening depot travel. Further hit/death/loot evidence should be collected before treating native combat farming as defective.
