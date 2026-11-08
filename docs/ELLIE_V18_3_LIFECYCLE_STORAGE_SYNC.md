# Ellie v18.3 task lifecycle and storage reconciliation (synchronized hotfix)

**Active source baseline:** `fix/ellie-schematic-acquisition-20261108` at
`04f96a1b238cca55209e3265aa1c71106a88cfc4` (PR #24).

**Purpose:** Make the two November 8 standalone binary fixes reproducible in
the GitHub build and visible to future Codex sessions. Do not start new Ellie
1.21.1 work from the older `main` branch.

## Why a post-package patch?

The current 1.21.1 Ellie integration is a pinned **binary overlay**, not
complete matching source for all engine classes. In particular, shared
`Task.class` is not editable source in this repository.

The verified patchers are tracked under `ellie/tools/`:
- `StopLifecyclePatch.java`: guards recursive `stop()` callbacks during
  `onTransientResumeAbandoned() -> fail() -> stop()`, with reset and
  exceptional-exit cleanup.
- `StorageRecoveryBytecodePatch.java`: adds two guarded call sites to the
  packaged `SchematicProjectTask`: a pre-transfer chest baseline and
  reconciliation on failed DEPOSIT/WITHDRAW.
- `hotfix-src/com/player2/playerengine/schematic/SchematicStorageReconciler.java`:
  reads the **live chest and inventory**, reports expected versus actual
  quantities and unrelated deltas (such as removed clay), discards a stale
  transfer and allows up to three bounded no-progress replans.

`tools/apply_v18_hotfix.py` runs **after** all pre-existing v18.3 scope
regressions, and rewrites the final JAR atomically. It refuses to patch a class
with a different v18.3 SHA256, rejects changes outside exactly the two
reviewed production classes, and ensures exactly three helper classes are new.
The entire 14-stage setup and Player2 classes are byte-identical to the
upstream v18.3 JAR.

`build.sh` now runs helper behavioral tests plus
`tests/verify_v18_hotfix_packaging.py` on the final JAR. The verifier pins
class bytes to the previously tested standalone hotfix and inspects the
actual packaged call sites. GitHub Actions publishes this post-hotfix JAR.

## Observability events

- `SCHEMATIC_STORAGE_BASELINE`: expected target and actual starting chest stock.
- `SCHEMATIC_STORAGE_RECONCILE`: actual chest/held quantities, slot capacity,
  item deltas since action start, child failure data, bounded retry number.
- `SCHEMATIC_STORAGE_RECOVERY_EXHAUSTED`: repeated no-progress failure.
- `SCHEMATIC_STORAGE_DIAGNOSTIC_ERROR`: failure to scan/reconcile; original
  controlled failure remains active.

**Not proven:** Live Minecraft behavior, the root cause of the clay-dependent
native transfer stall, or completion of `123.litematic`. These fixes reduce
fragile shutdown and stale-storage behavior, and expose more diagnostics.
Reproduction in a backed-up world is still required.

## Live acceptance

1. Save a test-world backup. Keep only one PlayerEngine JAR.
2. Prepare a small schematic using a chest with required material and
   156 unrelated clay balls. Remove the clay while Ellie works.
3. Confirm the next failed transfer reports `minecraft:clay_ball=-156`,
   resynchronizes, and either progresses or reports a bounded failure.
4. Restore/rearrange the clay and repeat. No stale snapshot may be treated
   as proof that the requested cobblestone was deposited.
5. Test partial, full, and missing chests. No infinite retry, false success,
   or recursive stop crash.
6. Run the original 14-stage setup in a fresh world separately.

## Development and merging

This branch is intentionally based on PR #24's head (not `main`). Merge
this hotfix PR **into the v18.3 schematic acquisition branch** first. Later,
merge the already ordered chain of feature branches to main. Until then,
all future changes to 1.21.1 Ellie must branch from the merged hotfix head.
Never substitute an older `main` JAR for the active test build.

The Minecraft integration tests are substituted/native-free checks unless
the log explicitly says a live-world test took place.
