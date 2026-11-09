# Codex guardrails for Ellie / PlayerEngine

This repository contains **two distinct code lines**:

- Root Gradle sources are an older Minecraft 1.20.1 project.
- **Active Ellie 1.21.1** is the pinned-binary overlay in `ellie/`, with
  release code and tests built by `bash ellie/build.sh`.

Do NOT use old `main` as the latest active Ellie 1.21.1 implementation.
Before editing, inspect the active series of PRs and their target branches.
As of 2026-11-09: PR #24 owns v18.3 schematic acquisition; PR #25 is
MERGED into that branch. PR #26 (plank deficit and chest protections) remains
DRAFT / unmerged. PR #27 (sheep route) was CLOSED WITHOUT MERGE and must not
be carried into a release. PR #28 is the current active, draft branch
`fix/ellie-v18-3-combat-acquire-resume-20261109`, based on PR #26.
It corrects interruption/resume of an in-flight native `ACQUIRE` child,
not mob combat or recipe choice. New work must preserve the PR #26 and #28
lineage; check live GitHub status for later releases. Do not branch from
old `main` or reintroduce the closed wool-first workaround.

## Mandatory JAR-to-GitHub handoff (every new build, including hotfixes)

The **GitHub commit + CI artifact is the source of truth** for every new Ellie
1.21.1 JAR. Never ship a new JAR as an isolated final deliverable that only
exists in a chat or local filesystem.

Follow this sequence **in the same work session** whenever creating a JAR:

1. Identify the newest **active Ellie 1.21.1 branch and outstanding PR chain**
   rather than assuming `main` is current. Record the actual parent commit.
2. Make the code/ASM patch, relevant tests, build changes and release notes
   **in that branch or in a new PR based on it**. Never replace a reviewed
   hotfix with only a compiled `.class` or `.jar`.
3. **Commit and push the source BEFORE declaring the JAR delivered.** The commit
   must be enough to reproduce the JAR with `bash ellie/build.sh`; for a
   binary-overlay change, include its patcher and required baseline hashes.
4. Prefer the GitHub Actions JAR for that exact commit. Require the
   `verify_build_revision.py` source/revision checks, relevant regressions,
   packaged-class and scope checks, and a passing CI run before calling it
   build-validated. Preserve the separate `UNVERIFIED` label for gameplay.
5. Report a **handoff receipt** in the response, every time:
   - exact **branch and commit SHA**;
   - **PR/commit link** and GitHub Actions run/artifact link if built;
   - JAR filename and SHA-256;
   - concise changes, tests passed/failed, and gameplay status;
   - whether the PR is **draft/open/merged**, and which branch Codex should
     continue from next.
6. Ensure the PR description explains what's new since its base, what was
   verified, which tests are missing, and the **next development branch**.
   Update the relevant release/handoff documentation when behavior changes.

If GitHub write access or CI is unavailable, **do not silently produce a
so-called official release**. Mark the JAR `LOCAL-ONLY / NOT SYNCED`, include
the source/diff and hashes, explicitly tell the owner GitHub is not current,
and record the missing sync action. Do not claim success or invent a commit
or artifact. A local-only emergency preview does not satisfy this policy.

**No exception for "small fixes."** A two-line patch also needs its commit.
Never delete or overwrite older releases simply to make the branch appear
tidy; history is our recovery trail.

See `docs/ELLIE_RELEASE_HANDOFF.md` for the current detailed workflow.

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

Read `docs/ELLIE_V18_3_LIFECYCLE_STORAGE_SYNC.md` and
`docs/ELLIE_P0_CRAFTING_DEPOT_20261108.md` for earlier fixes;
read `docs/ELLIE_COMBAT_ACQUIRE_RESUME_20261109.md` for PR #28's
verified ACQUIRE suspension patch, exact runtime ownership contract,
regressions and live-world limits. Do not silently replace native
combat, either plank patcher, or the depot guard.

For any code change to task shutdown, inventory, native transfers,
schematics, or the release pipeline, run the complete Ellie build and verify
the packaged class/test receipts before proposing a merge.
