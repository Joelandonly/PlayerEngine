# Ellie / PlayerEngine development backlog

_Last reviewed: 2026-11-02. Tracking IDs are stable within this document. See [Development Log](../DEVELOPMENT_LOG.md) and [Validation Matrix](ELLIE_VALIDATION.md)._

> **Current organization:** The interpreter bundle is now preserved in [`ellie/`](../ellie/README.md), with bridge source, tests and a pinned 1.21.1 binary baseline. The root Gradle source remains 1.20.1. The complete 1.21.1 mod source is still unavailable; importing the bundle does not close any gameplay bug or establish live validation.

> **GitHub Issues are disabled in this repository** (issue creation returned HTTP 410 on 2026-11-02). This is the issue register until Issues are enabled; preserve IDs during any migration.

## Status language

- **OBSERVED**: reproduced or reported symptom, without a verified fix.
- **PROPOSED**: design agreed or suggested; not implemented.
- **PREVIEW / MOCK**: generated preview artifact and/or test-harness claim; no proof of successful in-game execution.
- **LIVE / PARTIAL**: logs or user observations confirm a specific sub-feature, not the complete acceptance criteria.
- **LIVE / PASS**: reproducible in Minecraft with logged evidence and meaningful regression coverage.
- **SOURCE / VERIFIED**: source committed in the correct target branch, can rebuild and passes tests. Only then consider closure.
- **INVESTIGATE**: competing causes or inadequate diagnostics; no confirmed bug diagnosis.

## Register

| ID | Priority | Type | Status | Topic |
|---|---|---|---|---|
| ELLIE-001 | P0 | Bug / navigation | PREVIEW / MOCK | No-vine traversal policy and safe obstacle recovery |
| ELLIE-002 | P0 | Regression / UI | OBSERVED | Teleport-to-Ellie button disappeared |
| ELLIE-003 | P1 | Feature / autonomy | PROPOSED | Stone-or-better tool preflight |
| ELLIE-004 | P1 | Feature / navigation | PROPOSED | Bridge decision shared by all movement tasks |
| ELLIE-005 | P1 | Validation / navigation | PREVIEW / MOCK | Verify short water bridges and rejection reasons |
| ELLIE-006 | P1 | Resource planning | LIVE / PARTIAL | Local wood choice instead of forced oak |
| ELLIE-007 | P1 | Craft / travel | PREVIEW / MOCK | Craft local doors instead of far-away salvage |
| ELLIE-008 | P1 | Autonomy / goals | PROPOSED | Persistent compound goal planner and verification |
| ELLIE-009 | P1 | Reliability | INVESTIGATE | Door/crafting preflight stalls and interruptions |
| ELLIE-010 | P2 | Safety / placement | INVESTIGATE | Underground house build-site selection |
| ELLIE-011 | P2 | Feature / construction | PROPOSED | Visual blueprint picker and saved designs |
| ELLIE-012 | P2 | Feature / navigation | PROPOSED | Boat strategy for wide lakes |
| ELLIE-013 | P1 | Observability | PREVIEW / MOCK | Path stall and resource decision instrumentation |
| ELLIE-014 | P1 | Reliability | INVESTIGATE | Competing survival chains preempt user work |
| ELLIE-015 | P0 | Build / source | OBSERVED | Source parity with 1.21.1 Fabric preview JARs |
| ELLIE-016 | P1 | Testing | PROPOSED | Reproducible live and long-run test suite |
| ELLIE-017 | P1 | Construction | LIVE / PARTIAL | House builder: completion and interruption recovery |
| ELLIE-018 | P2 | Telemetry / performance | INVESTIGATE | Lag warnings, skin/data-fixer noise |
| ELLIE-019 | P2 | Permissions | PROPOSED | Optional owner-controlled native-command bridge |
| ELLIE-020 | P0 | Invariant | KEEP | Preserve retaliation, player control and teleport extension |
| ELLIE-021 | P1 | Feature / crafting | PREVIEW / MOCK | Remember verified crafting tables in EllieGPS and avoid deep detours |
| ELLIE-022 | P1 | Bug / bridge | PREVIEW / MOCK | Bridge reflection-access repair; live crossing still unverified |

### ELLIE-001 — Vines and leaves obstruct navigation
**Evidence:** User reports Ellie stands in jungle vine clusters until specific vines are broken; leaves occasionally behave similarly. The 2026-11-02 20:01 Observatory snapshot did not include collision/block coordinates. **The owner intentionally set a tree on fire**, explaining later survival-chain interventions and burning death; do not attribute that death to vines.

**Approved direction (2026-11-02):** **Do not route Ellie over vines as traversal/climbing infrastructure.** Prefer ordinary terrain and existing solid support-pillar / block-placement movement instead. Mark vines as high-cost / disallowed route nodes in the actual Automatone fork (not just `allowVines=false`, which disables only *advanced* vine moves). When vines obstruct a necessary approach, attempt a safe detour first; only break a verified blocking vine through normal block breaking where doing so cannot drop Ellie, release hazards or destroy a necessary support. Keep leaves handling targeted, not global deforestation. Observe native movement failures/replans and use bounded cooldowns. This replaces the earlier proposal to preserve vine climbing as a route choice; no code is yet changed.

**Exit test:** In vine-heavy jungle, Ellie uses valid walking/pillaring instead of climbing vines; never repeatedly targets the same vine traversal; clears only genuinely blocking vines safely; does not regress ordinary vine-free travel, leaves, building or fall safety. Require `PATH_STALL` / `PATH_RECOVERY` and actual in-game evidence. **Status:** earlier navigation preview mock-tested only; new no-vine-traversal policy is **approved, not implemented**.

### ELLIE-002 — Missing TP-to-Ellie button
**Evidence:** User reported the button missing after a recent preview upgrade. The startup log still showed a `Player2NPC-...-TELEPORT-TO-BUDDY-PREVIEW.jar` rendering class.

**Action:** Compare working vs current UI registration, ownership, GUI state and extension compatibility; verify in single-player, rejoin and after respawn. A vanilla `/tp` workaround is not a fix. **Do not rewrite or remove the independent Player2NPC extension without explicit request.**

### ELLIE-003 — Stone tool preflight
**Proposal:** On substantial build/mining/gathering tasks, check existing equipment. Require a **stone axe or better** for major log work; a **stone pickaxe or better** for stone work; respect ore harvest tiers. Bootstrap a wooden pickaxe only when stone is needed and unavailable. Equip the tool, retain it across jobs, replace on breakage and use bounded fallback. Tiny jobs need no unnecessary provisioning.

**Exit test:** No fist-mining of large wood/stone workloads; no perpetual tool loops; appropriate harvest levels; no downgrade of good gear. Not yet implemented.

### ELLIE-004 — Shared bridge/navigation capability
**Problem:** Existing bridge logic is called from construction resource collection, not normal follow, travel or exploration.

**Proposal:** Reusable traversal service compares walk, detour, swim, construct short safe bridge, later boat. Reuse completed crossings and respect ownership, terrain, placement support and route costs.

**Exit test:** All movement task families can use the same policy; no unwanted structure modification; no persistent routing loops.

### ELLIE-005 — Verify short water bridging
**Evidence:** The old bridge preview never emitted a `WATER_BRIDGE_PLAN` in the 2026-11-02 lake test. A newer jungle run showed explicit `BRIDGE_SKIP: Bridge planner unavailable: IllegalAccessException` (see ELLIE-022). A subsequent preview claimed short straight crossing scans and alternative shore probing, but actual water placement is **not** verified in-game.

**Action:** Live cases for 4–20-block crossings, path detour around lake, curved banks, sparse materials, unsupported water placement and long-lake rejection. Report `BRIDGE_CANDIDATE`, `BRIDGE_SKIP`, `BRIDGE_PLAN`, `BRIDGE_RESULT`. Never report success before a walkable bridge exists.

### ELLIE-006 — Prefer locally available wood
**Evidence:** At 19:29:16 the resolver chose oak for a generic house, although a jungle-wood crafting table was made immediately afterward. At 19:58:04 a later preview selected `jungle` and logged **98 wood substitutions** for the same 5×5 type of house.

**Status:** LIVE / PARTIAL: species selection corrected in one later test; **house completion did not occur in that later test**. Robustness, exact local stock estimation, multiple species and source-reproducibility remain unverified.

### ELLIE-007 — Prefer local door crafting to distant item acquisition
**Evidence:** Early door preflight handed `oak_door` to general acquisition and sent Ellie a long distance; a jungle-wood preview later planned `jungle_door`, built a table and collected matching wood. No later log proves the door was fully crafted and placed.

**Design:** Before salvage, compare nearby material crafting against travel for existing door; craft with available species; cap travel/delay and report `RESOURCE_CHOICE`.

**Exit test:** Start beside jungle trees, produce and place local jungle door without visiting a distant village.

### ELLIE-008 — Persistent, verifiable goals
**Evidence:** An explicit diamond pickaxe request worked, but equipping a full diamond kit required repeated prompts. User deliberately omitted boots from the individual item list to test whether `full diamond armour` implied all four pieces. Boots were not observed in available session logs.

**Design:** Turn high-level intents into tracked required outputs; acquire/craft/equip/verify every component, including implicit boots; keep goals through subtasks/interruptions, respect cancellation, report incomplete requirements.

**Exit test:** One full-armour order equips helmet/chestplate/leggings/boots and verifies slots without repeated user prompts.

### ELLIE-009 — Crafting preflight stalls
**Evidence:** One snapshot showed `0/124` while preparing an oak door, and another showed `0/124` while preparing a jungle door, with crafting-table production completed. Interrupted operations may be unfinished rather than failed. Distinguish waiting from deadlock.

**Action:** Trace inventory deltas, recipe planning, tool possession and task hierarchy; add bounded progress test and resume semantics. Confirm door and house complete under survival conditions.

### ELLIE-010 — Build-site validation
**Evidence:** A house plan was seen at Y=-19, but the user left the world while Ellie fetched materials. **Do not label this a proven failure.**

**Proposal:** Before committing to surface-house intent, assess ground support, ceiling, hazards, cave context, clearance and user-specified placement. Avoid surprising automatic relocation when the user requests an exact site.

### ELLIE-011 — Visual blueprint selection
**Proposal:** Saved building designs with names, preview, footprints, material estimates, anchor/orientation and reproducible blueprint IDs. Keep original layer-aware physical build behavior.

### ELLIE-012 — Long-water traversal by boat
**Proposal:** For crossings too wide or complex to bridge, evaluate safe boating with acquisition, embark, steer, disembark and recovery. Do not deploy until a live navigation test proves it.

### ELLIE-013 — Observatory diagnostics
**Evidence:** Existing HUD accessible with F8/F7 and logs resource/build chain details. New preview claimed `PATH_STALL`/`PATH_RECOVERY`; older bridge candidates rejected silently.

**Requirement:** Include target, position, elapsed stagnant ticks, local obstacle sample, candidate reason, attempted recovery and success/failure. Rate-limit logging. Keep reports free of account IDs, access tokens and full unredacted game prompts.

### ELLIE-014 — Survival-chain preemption
**Evidence:** At 21:00:46, 21:01:00 and 21:01:17, `Misc World Survival Chain` preempted house crafting and routed toward (-1,49,21); Ellie later burned. **Owner confirmed setting the tree on fire**, confounding attribution.

**Action:** Reproduce without fire or manual intervention; only then assess whether priority/hysteresis is defective. Keep safety overrides but prevent unnecessary repeated task churn.

### ELLIE-015 — Source reproducibility
**Evidence:** GitHub `gradle.properties` targets Minecraft 1.20.1 and Quilt, whereas downloadable experimental JARs are 1.21.1 Fabric / PlayerEngine 1.4.0 with bytecode patches.

**Action:** Identify exact 1.21.1 source revision, import reviewed changes as readable source, record build instructions and checksum and compile from clean checkout. **Never claim source updated merely because binary previews were issued.**

### ELLIE-016 — Real-world validation
**Proposal:** Maintain the [Validation Matrix](ELLIE_VALIDATION.md). Require compile, unit and class-reference audits, actual Minecraft traversal/build/crafting tests, repeated-world tests, interrupts and long runs. Tag each result separately; no `PASS` from an unobserved preview alone.

### ELLIE-017 — House construction reliability
**Evidence:** An earlier log recorded **two completed 5×5 houses** with verified all structure blocks placed. Construction sometimes progressed 123/124 before a final door. Later jungle runs did not yet complete. Existing layer-aware builder, persistent plans and structure verification should not be casually replaced.

**Exit test:** Repeated houses with mixed terrain/species, door/window finalization and recoverable interruptions; avoid regressing those two observed successes.

### ELLIE-018 — Performance and noisy diagnostics
**Evidence:** Logs include server tick-delay warnings and nonfatal skin/data-fixer messages. These do not independently prove a PlayerEngine performance bug.

**Action:** Isolate path recompute and local-scan overhead, test sustained follow/build at normal and fast tick loads. Separate known benign warnings from actionable exceptions.

### ELLIE-019 — Optional trusted command bridge
**Background:** AI chat can invoke registered PlayerEngine actions but not arbitrary native Minecraft slash commands. A prior discussion considered an owner-approved allowlisted native-command interface.

**Rule:** Off by default, no blanket operator privileges, no escalation through user-authored text, audit actions. Deferred / low priority.

### ELLIE-020 — Behavioral invariants
**Do not change without explicit request:** NPC combat retaliation toward attackers (including owner), existing teleport extension, F8/F7 Observatory, layer-aware verified house construction, unrelated NPC features. Ensure these survive JAR replacements and source merges.


### ELLIE-021 — Local crafting-table memory and anti-detour policy
**Observation:** User saw Ellie go to a trial-chamber crafting table rather than use/create one locally. Existing task logs previously showed crafting-table targets near (-185,63,-248) and (-181,14,-247); these do not conclusively identify the trial chamber. Existing `CraftingTableLocator` defaults to a 48-block reuse radius and consults a separate block scanner instead of EllieGPS.

**Preview (2026-11-02):** Binary patch from the previous navigation preview. It stores actually verified reachable tables as `crafting_table` in existing per-world EllieGPS, rejects distant candidates (>16 horizontal or >5 vertical blocks, or a smaller configured radius), checks the table still exists in a loaded chunk, and selects nearby remembered vs scanner candidates. It records **observed** tables, including self-placed ones after use, without falsely tagging them as bot-placed. New diagnostics: `CRAFT_TABLE_REMEMBER`, `CRAFT_TABLE_CHOICE`.

**Validation:** Java compilation, policy tests, mock GPS record persistence, deduplication, stale block rejection, and mock test preferring a nearby surface table over a deep table passed; internal JAR audit passed. **Minecraft live and cross-restart persistence have not been tested.** Package: `PlayerEngine-Ellie-Workbench-Memory-Vine-Recovery-1.21.1-PREVIEW.zip` (delivered in chat). SHA-256 JAR: `1f0bc6603ff788a6736387cbe08e773fd21c107b99f980d65e0da985e0263b0c`.

**Acceptance:** Owner-placed or Ellie-verified tables reused locally after reload; no trial chamber detours for local crafting; correct dimension and stale checks; no repeated pathing loops. The patch is **binary-only** and not checked into the 1.20.1 source tree.

### ELLIE-022 — Water bridge planner reflection access error
**Observation:** On 2026-11-02 during a jungle-house run, Observatory repeatedly logged `BRIDGE_SKIP: Bridge planner unavailable: IllegalAccessException` (e.g. 20:31:21 and 20:34:10) along with `No active Baritone path`. This is positive evidence of bridge helper runtime failure, not merely cost-based avoidance.

**Action:** Locate the reflective call throwing `IllegalAccessException` in the bridge coordinator, make mapped method access safe, and add per-failure method/class diagnostics without leaking sensitive data. Confirm live bridge creation/traversal and ensure no repeated errors or expensive scans.

**Status:** OBSERVED, **not fixed** by the workbench/vine preview. Priority high.


## Upstream navigation research: Baritone 1.21.1

2026-11-02 code and issue review: [Baritone research and implementation priorities](BARITONE_RESEARCH_2026-11-02.md). **No code changes.** Significant new evidence for ELLIE-001: Baritone [issue #1773](https://github.com/cabaletta/baritone/issues/1773) reports jungle-log mining stalls on hanging vines until manually broken; [issue #392](https://github.com/cabaletta/baritone/issues/392) reports vine stalls with `allowVines=false`. The advanced `allowVines` setting itself warns of indefinite traps and defaults off, so do not claim one setting is the solution.

For ELLIE-022, Baritone 1.21.1 exposes supported typed `IPathingBehavior.getCurrent/getPath` and `IPath.positions` APIs. Investigate whether the embedded Automatone fork has equivalent public access before fixing the existing bridge `IllegalAccessException`. For ELLIE-004/005, Baritone already considers block placement/bridging in individual movement costs, but custom materials/route policy still needs work. For ELLIE-021, Baritone has unreachable-target blacklisting; remembered crafting-table location is not a guarantee of efficient reachability.


**Architecture decision:** User explicitly rejected vine climbing as a capability because Ellie can build support pillars. This is a navigation policy, **not** a mandate to erase all vine blocks or disable ladders. Keep native Baritone/Automatone single-source path planning. Related agreed priorities remain ELLIE-022 (bridge reflection fix), ELLIE-004 (shared navigation bridges), ELLIE-021 (crafting-table routing/waypoints), and ELLIE-003 (stone tools).

### Backlog implementation Batch 1 — 2026-11-02

**Code produced, not merged into the old GitHub source tree.** Built `PlayerEngine-Fabric-1.21.1-1.4.0-BACKLOG-BATCH1-PREVIEW.jar` from the previous `WORKBENCH-VINE-PREVIEW` binary.

- **ELLIE-001:** Add `VineRoutePolicy` to the general navigation tick: persist `Blocks.VINE` in the NPC-specific Automatone `blocksToAvoid` setting, preserve ladders and other avoidance entries, and log `VINE_POLICY` or an error. Patch native `MovementHelper.canWalkOn` to reject vines as walking supports and `MovementPillar.cost` to reject climbing/pillaring from a vine. Retain conservative safe normal block-breaking and diagnostic stall recovery. This does *not* establish that every special fall/climb interaction is eliminated.
- **ELLIE-022:** Patch `WaterBridgeCoordinator.blockId` cached world-method reflection and general `invoke` to set Java reflective accessibility. Mock test reproduced `IllegalAccessException` on a non-public world implementation and demonstrated the patched helper reads the block. The real bridge planner may have other access failures; no live bridge success claimed.
- **Tests:** Java 21 compile; mock route and safe-clear test suite; non-public-world reflection regression; bytecode inspection, JAR CRC, Fabric metadata and internal reference audit. **PASS** for these checks. **NOT RUN:** live Minecraft jungle, bridge construction, long-run, save/reload.
- **Binary audit:** 1,780 classes, 11,158 internal class refs, zero unresolved; four existing classes changed and one new policy class. No combat, teleport, builder, crafting table or Player2NPC classes altered.
- **SHA-256:** `f02aa1bacd2e462f5f4775608f03184e0722853b84a9671757293e414f102d25`
- **Download artifact:** `PlayerEngine-Ellie-Backlog-Batch1-No-Vines-Bridge-Fix-PREVIEW.zip` in project conversation; includes Java helper sources, bytecode patch tool, package audit, and mock tests. The current GitHub branch still targets Minecraft 1.20.1 Quilt and has **not** gained this binary-only fix.

**Next gates:** Verify T-07, T-09, T-27, T-33 through T-37 in actual survival Minecraft; check `VINE_POLICY` in Observatory, a safe jungle-log collection route without vine climbing, normal ladder behavior, and real bridge placement without reflection errors. Work on shared bridging (ELLIE-004) and crafting station route cost (ELLIE-021) after that evidence.

## Triage rules

1. Every newly observed behavior gets an ID, evidence date/build, clear **observed vs inferred** separation and priority.
2. Every proposed idea gets explicit status; discussing it is not implementation.
3. A generated artifact is recorded as **PREVIEW / MOCK**, not **SOURCE / VERIFIED**. In-game tests are separately evidenced.
4. Before closing, record source commit, test seed/world, actual result, regressions checked and user observation.
5. Prefer minimal patches; do not remove unrelated features. Never publish raw logs, authentication information, UUIDs or local paths in this public repo.
6. If GitHub Issues are enabled, migrate by stable `ELLIE-###` ID and cross-link back here. Do not create duplicate entries.
