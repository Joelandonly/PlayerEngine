# Codex guardrails for Ellie / PlayerEngine

This repository contains **two distinct code lines**:

- Root Gradle sources are an older Minecraft 1.20.1 project.
- **Active Ellie 1.21.1** is the pinned-binary overlay in `ellie/`, with
  release code and tests built by `bash ellie/build.sh`.

Do NOT use old `main` as the latest active Ellie 1.21.1 implementation.
Before editing, inspect the active series of PRs and their target branches.
As of 2026-11-09: PR #24 owns v18.3 schematic acquisition; PR #25 is
MERGED into that branch. PR #26 (plank deficits/depot guard) and PR #28
(Mob Defense ACQUIRE-resume) remain DRAFT / unmerged. PR #27 (sheep
workaround) was CLOSED WITHOUT MERGE; never reintroduce it.
PR #29 is the active, DRAFT native excavation feature on branch
`feat/ellie-excavate-region-20261109`, based on PR #28.
It adds ONLY explicit bounded `excavate x1 y1 z1 x2 y2 z2`, no mob,
schematic, or inventory change. A separate reversible storage backlog for
spider-hunt overflow is documented at
`docs/ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md` and NOT IMPLEMENTED.
Preserve the entire PR #26 -> PR #28 -> PR #29 lineage; check current
GitHub PRs before modifying newer code. Never branch from stale `main`.

## Command documentation and backlog source of truth (2026-11-09)

See the full current-vs-historical [documentation audit](docs/ELLIE_DOCUMENTATION_AUDIT_20261109.md) before interpreting older setup or release notes.\n\nFor **current draft PR #29** code/command planning, use
`docs/ELLIE_COMMANDS.md` and `docs/ELLIE_COMMANDS.tsv` (49 registered,
8 unregistered), plus `docs/ELLIE_BACKLOG_CURRENT_20261109.md`
(ELLIE-001 through ELLIE-037). These are the reconciled current
readme/triage references. `docs/ELLIE_BACKLOG.md` is the **historical
evidence archive**, now including main-only ELLIE-024–029, not the
authoritative current status table. Previous setup/survival/native-command
documents remain as explicitly labeled historical versions.

**Current owner `setup` is v9 14-stage basic HOME**, with wood/stone
tools and 64 torches, one crafting bench HOME, chest and furnace; it is
NOT the earlier iron/shield/bucket/bed/shelter setup. Do not rename the
old survival setup as the currently dispatched command. The current
1.21.1 JAR preserves 32 protected setup classes; source/CI tests and
owner's four-world test evidence are separate facts.

When adding/removing commands or changing their contract, update BOTH
the source/native routing and the 49-ID TSV/Markdown inventory.
When an issue's status changes, update the current backlog with evidence
and preserve historical ELLIE-### IDs. The `docs/verify_ellie_consistency.py`
CI gate checks command registry, setup bills, 29 historical IDs and 37
current IDs at this source revision. Update the gate alongside real
future registry/issue changes, not by weakening its checks.

This documentation-only review is based on PR #29's head. It does not
add AGENTIC functionality or validate the latest suspension fix in-game.

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
ACQUIRE suspension patch and native task contract. PR #29's explicit
excavation and safety limits are documented in
`docs/ELLIE_EXCAVATE_20261109.md`; overflow storage is separately
documented at `docs/ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md`. Do not silently replace native
combat, either plank patcher, or the depot guard.

For any code change to task shutdown, inventory, native transfers,
schematics, or the release pipeline, run the complete Ellie build and verify
the packaged class/test receipts before proposing a merge.
