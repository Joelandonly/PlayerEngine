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
  The last behavior-changing build before this policy passed CI at commit
  `588d363`, run
  https://github.com/Joelandonly/PlayerEngine/actions/runs/42489371512 .
  These PRs were still draft/unmerged when this document was first added.
- **Important live P0 issues:** native 64-oak-trapdoor acquisition stalls
  inside the 92-plank crafting child despite planks in inventory; manual
  storage-assistance replacement interrupts the schematic, after which
  project chest protections are removed and three chest locations become
  unavailable. These live findings are *not resolved* by a green CI result.
  Avoid describing v18.3 as gameplay-validated.

**Starting point for the next P0 patch:** inspect the current PR heads first,
then branch from the newest validated active 1.21.1 integration head.
Do not blindly copy the literal SHA above once newer commits exist.

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
