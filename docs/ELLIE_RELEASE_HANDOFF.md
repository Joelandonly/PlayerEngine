# Ellie release handoff: one JAR, one reproducible GitHub commit

**Rule:** Every Ellie 1.21.1 JAR delivered to the owner must correspond to a
GitHub commit containing the source/ASM patch, build logic and test coverage
needed to rebuild that exact binary. The canonical installable file should be
the GitHub Actions artifact from that commit. This applies to tiny patches,
experiments and emergency hotfixes.

## Current development lineage (2026-11-08)

- Minecraft target: 1.21.1 Fabric, Java 21, `ellie/` pinned binary overlay.
- Historical `main` is **not** the active source of the tested NPC.
- PR #24, `fix/ellie-schematic-acquisition-20261108`, provides v18.3
  schematic acquisition (head `04f96a1`).
- PR #25, `fix/ellie-v18-3-storage-lifecycle-sync-20261108`,
  adds stop-loop and storage reconciliation fixes on top of PR #24.
  **Merged** into the v18.3 schematic branch as commit
  `56af538c12b58f2a90ea3087a8fae837743952e4`.
- PR #26, `fix/ellie-v18-3-p0-crafting-protected-storage-20261108`,
  contains the reviewed P0 repair of the native plank-shortfall loop and a
  persistent per-world schematic depot break guard. The source, exact
  production-class hashes, and test cases are in GitHub. The first
  code-changing candidate passed automated tests at `76facf3`
  (https://github.com/Joelandonly/PlayerEngine/actions/runs/42506916254).
  A live run of the later `b3531a45` candidate exposed a second precise
  native plank-target defect: 188 held oak planks + one log, yet a repeated
  `Crafting 1 planks` child; blocked after 2,400 ticks. The updated PR #26
  also patches `CraftWithMatchingPlanksTask.getSpecificSameResourceTask()`,
  whose absolute count=1 is already satisfied while 188 planks are held.
  Regression includes 188 + one log -> target 189 -> four produced -> 192.
  A test build with BOTH native handoffs corrected passed
  (https://github.com/Joelandonly/PlayerEngine/actions/runs/42525515617).
  **Draft/unmerged.** Confirm final-head CI and live Minecraft before
  declaring the game fix validated.
- PR #27 (sheep/wool-first workaround) is CLOSED WITHOUT MERGE.
  The user requested a fix for the native combat/resource-gathering
  interruption conflict, not speculative sheep availability. Never promote
  that withdrawn build as the canonical Ellie release.
- PR #28, `fix/ellie-v18-3-combat-acquire-resume-20261109`, is the
  ACTIVE DRAFT based on PR #26. A higher-priority Mob Defense chain had
  interrupted spider-string acquisition, and
  `SchematicProjectTask.afterChildrenStopped()` erased its active ACQUIRE
  action. PR #28 preserves the same parent Action, its absolute material
  target, inventory high-water and retry/timeout counters, then creates a
  fresh stopped native acquisition child on resume. This deliberately
  changes no combat strategy, loot table, navigation or safety chain.
  Its first CI run passed 52 fake-world regression checks; exact final
  released class SHA and full CI must pass before claiming build validity.
  See `docs/ELLIE_COMBAT_ACQUIRE_RESUME_20261109.md`.
- PR #28's final source `4db5eb5b0b2f4dfd88b5c0c12df4356efd0efdbf`
  passed full CI (https://github.com/Joelandonly/PlayerEngine/actions/runs/42540492088).
  Its executable Mob Defense/schematic ownership handoff remains
  **gameplay-unverified**.
- PR #29, `feat/ellie-excavate-region-20261109`, is the ACTIVE DRAFT
  based on PR #28, introducing native bounded `ellie excavate` using the
  existing Baritone builder with full preflight checks. Live build `240dc31`
  successfully excavated until temporary high-priority chains interrupted:
  Mob Defense at 03:34:08 (444 blocks remaining), and ForceEquipGearChain
  at 03:36:28 (261 remaining). Both were incorrectly treated as terminal.
  The PR now implements the packaged `TransientlyResumableTask` contract,
  preserving work and resuming/revalidating the native builder after chain
  yields. The 34-test regression and final Minecraft 1.21.1 API checks passed
  in https://github.com/Joelandonly/PlayerEngine/actions/runs/42552708414.
  **This specific correction still needs live gameplay validation.**
  Keep PR #29 draft. See `docs/ELLIE_EXCAVATE_20261109.md`.
- Automatic overflow storage for the current spider-loot inventory pressure
  is **future work, not yet implemented**. Its reversible same-goal policy
  and safety acceptance are at
  `docs/ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md`. GitHub Issues are
  disabled in this repository; this tracked file is the persistent backlog.
- **Live-world evidence still outstanding:** the 64 oak trapdoors must
  actually craft past the 92-plank loop, and a manual `agentic deposit`
  replacement must not destroy the staged project chests. The prior
  full-chest failure and the general command-replacement/resume behavior
  have not been declared solved by isolated tests.

**Current development starting point:** inspect PR #29's current CI and
PR head, preserving PR #28 and PR #26 as its dependent base. Do not
start from old `main` or merge withdrawn PR #27. The active branch is
`feat/ellie-excavate-region-20261109`. Inventory overflow is a separate
unimplemented backlog item, not a feature of the excavation JAR.
Future commits and releases must update their exact SHA and artifact link.

## How to create a release

1. Inspect open Ellie PRs and previous release receipt; choose the correct
   active branch. Record parent SHA and preserve all earlier changes.
2. Implement in reviewable tracked sources. For missing 1.21.1 source,
   use a pinned binary patcher with strict input hashes. Do not keep the only
   editable copy in a chatbot attachment.
3. Commit every code, test, verifier and build-script change to GitHub. Open
   or update a PR with explicit parent/target and change scope.
4. Build from the committed SHA with `bash ellie/build.sh` and the existing
   GitHub Actions workflow. Tests must exercise the final packaged output,
   not just intermediate source. Keep exact setup and Player2 integrity
   checks active; never remove failures simply to pass CI.
5. Use the GitHub Actions `Ellie-1.21.1-JAR-only` artifact for the commit.
   Preserve `META-INF/ELLIE_BUILD.json`,
   `META-INF/ELLIE_BUILD_REVISION.txt` and `SHA256SUMS.txt`.
   The latter should match the actual JAR being installed.
6. Give the owner the release receipt below in the **same reply** as the
   binary. Keep the PR draft until all required automated checks pass.
   Keep gameplay unverified until an actual Minecraft test passes.

### Mandatory release receipt (fill with real values)

```text
Ellie Minecraft target: 1.21.1 Fabric
Build/change name:
Active base/parent commit:
Source branch:
Source commit SHA:
PR/commit URL:
GitHub Actions run URL:
Installable artifact URL:
JAR name:
JAR SHA-256:
Automated tests: PASSED / FAILED / NOT RUN
Live Minecraft tests: PASSED / FAILED / NOT RUN
PR status: draft / open / merged
Known unresolved issues:
Next Codex/ChatGPT development branch:
GitHub sync: SYNCED / LOCAL-ONLY-NOT-SYNCED
```

A missing link, uncommitted patch, mismatched JAR hash, or unknown source SHA
means the artifact is **not** a verified synchronized release.

## If GitHub is unavailable

A local candidate can be shared solely as an explicitly named
`LOCAL-ONLY-NOT-SYNCED` preview. Include a source/diff bundle and hashes,
write that GitHub is **not** up to date, and do not claim that Codex will see
it. When GitHub becomes available, perform the commit, build and release
receipt before treating it as the canonical Ellie version.

## Scope and workflow discipline

- Do not add another task scheduler just to track releases.
- Do not merge draft PRs merely to consolidate files. Merge in validated
  dependency order, with the owner's existing project history preserved.
- Do not create a new "release" by manually swapping classes inside a JAR
  and omitting a reproducible patcher.
- **One authoritative code lineage and one exact commit per installable JAR.**
