# Codex guardrails for Ellie / PlayerEngine

This repository contains **two distinct code lines**:

- Root Gradle sources are an older Minecraft 1.20.1 project.
- **Active Ellie 1.21.1** is the pinned-binary overlay in `ellie/`, with
  release code and tests built by `bash ellie/build.sh`.

Do NOT use old `main` as the latest active Ellie 1.21.1 implementation.
Before editing, inspect the active series of PRs and their target branches,
starting with PR #24 (v18.3 schematic acquisition) and PR #25 (lifecycle +
storage reconciliation). New Ellie work must preserve the combined lineage
or explicitly rebase it and verify the same runtime class outputs.

## Non-negotiable preservation rules

1. Keep the existing owner-confirmed **14-stage setup** untouched unless
   a task explicitly requests setup changes. Do not silently substitute the
   old source or a reconstructed setup implementation.
2. The v18.3 stop/abandon crash is fixed by a re-entrancy guard in the
   packaged `com.player2.playerengine.tasks.base.Task`. The matching
   source is not available as an editable engine class here. Patch source:
   `ellie/tools/StopLifecyclePatch.java`.
3. A failed schematic DEPOSIT/WITHDRAW is reconciled against *fresh, actual*
   chest/inventory contents, with bounded retries and diagnostic item deltas.
   Removing unrelated clay from project storage must not count as a missing
   schematic requirement. Patch source:
   `ellie/tools/StorageRecoveryBytecodePatch.java` and
   `ellie/tools/hotfix-src/com/player2/playerengine/schematic/SchematicStorageReconciler.java`.
4. `ellie/build.sh` first executes historical freeze tests, then applies
   these two reviewed bytecode patches to the **final published JAR** using
   `ellie/tools/apply_v18_hotfix.py`. Never remove/reorder that final step
   or ship the pre-hotfix intermediate JAR.
5. The build refuses unknown input class hashes. If the upstream binary
   changes, review and port the patches deliberately rather than bypassing
   hash checks or commenting out tests.
6. Tests that substitute Minecraft world or native actions do not establish
   in-game success. Keep the hotfix PR in draft until CI passes; keep live
   gameplay claims separate.

Read `docs/ELLIE_V18_3_LIFECYCLE_STORAGE_SYNC.md` for the active baseline,
dependency/merge order, known limitations, and clay-removal reproduction.

For any code change to task shutdown, inventory, native transfers,
schematics, or the release pipeline, run the complete Ellie build and verify
the packaged class/test receipts before proposing a merge.
