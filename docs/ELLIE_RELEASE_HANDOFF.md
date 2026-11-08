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
- PR #27, `fix/ellie-v18-3-white-carpet-wool-first-20261108`,
  is the current DRAFT development head based on PR #26. The latest live
  run of `620c200` acquired and staged many cobblestone batches, then
  dynamically expanded 48 `white_carpet` into 128 `string`, repeatedly
  approached spiders without verified loot and timed out after 2,400 ticks.
  PR #27 adds a narrowly pinned white-carpet native route:
  shears -> white wool -> actual 2-wool/3-carpet crafting, with no extra
  scheduler or general combat overhaul. See
  `docs/ELLIE_WHITE_CARPET_WOOL_FIRST_20261108.md`.
  General mob-farming reliability remains a separate P1 investigation;
  Minecraft navigation and survival behavior are not validated by CI.
- **Live-world evidence still outstanding:** the 64 oak trapdoors must
  actually craft past the 92-plank loop, and a manual `agentic deposit`
  replacement must not destroy the staged project chests. The prior
  full-chest failure and the general command-replacement/resume behavior
  have not been declared solved by isolated tests.

**Current development starting point:** inspect PR #27 and latest CI first.
Preserve all merged PR #25 fixes and unmerged PR #26 patches; do not start
from old `main`. Active branch:
`fix/ellie-v18-3-white-carpet-wool-first-20261108`.
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
