# Ellie 1.21.1: current reconciled backlog

_Reviewed 2026-11-09 against the draft PR #29 chain at
`4626bf2e724f0b4f05ae5234968680132dc293a8`._

## Authority, scope and evidence

This is the **current triage and forward-work index**, not a claim that all
features are implemented. It reconciles the [historical ELLIE-001–023
register](ELLIE_BACKLOG.md), the **ELLIE-024–029** entries added separately
to `main` on November 6, and newer issues discovered on the PR #26/28/29
development chain. [Legacy backlog](ELLIE_BACKLOG.md) preserves the original
dated hypotheses and acceptance criteria, including all six main-only entries.
The [overflow design](ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md)
remains a separate detailed proposal, now indexed as **ELLIE-032**.

**Do not confuse branches:** `main` currently holds the stable basic HOME
v9 source and original older command inventory. Draft PR #26 adds plank/depot
fixes, PR #28 adds schematic acquisition resume, and PR #29 adds explicit
native excavation and its transient-resume hotfix; these are **stacked,
unmerged experiments**, not releases on `main`. Closed PR #27's unverified
sheep/shears workaround must **not** be reintroduced.

**Observed evidence:** owner-confirmed basic setup v9 in four fresh Minecraft
worlds (preserved 32 setup classes); actual partial cobblestone staging and
spider acquisition failure; actual excavation progressed before two chain
interruptions (Mob Defense and ForceEquipGearChain). Later CI builds
[PR #28](https://github.com/Joelandonly/PlayerEngine/pull/28) and
[PR #29](https://github.com/Joelandonly/PlayerEngine/pull/29) pass
mock/ABI/package tests, **but their fixed interruption recovery is not yet
proven live**. A green GitHub workflow is not an end-to-end NPC game test.

Evidence labels: **PROPOSED** = not implemented; **INVESTIGATE** = needs
causal evidence; **CI PASS** = verified tests/binary, not gameplay;
**LIVE PARTIAL** = a specific in-world sub-behavior observed;
**LIVE TEST OPEN** = candidate fix awaits survival-game acceptance;
**KEEP** = preservation invariant; **DOCS FIX IN REVIEW** = repaired in
this documentation branch, not yet merged.

## Reconciled register: all historic and new IDs

| ID | Priority | Current status | What remains / evidence boundary |
| --- | --- | --- | --- |
| ELLIE-001 | P1 | HISTORICAL PREVIEW / LIVE OPEN | Avoid vine traversal and preserve ordinary pillar/path safety; old preview not assumed packaged |
| ELLIE-002 | P1 | OBSERVED / LIVE OPEN | Verify missing TP-to-Ellie extension control against installed Player2NPC combination |
| ELLIE-003 | P1 | PARTIAL / LIVE OPEN | Shared stone-or-better tool policy; native mine-specific tool acquisition exists, all jobs unverified |
| ELLIE-004 | P1 | PROPOSED | Unified safe bridging route choice across movement task families |
| ELLIE-005 | P1 | PREVIEW / LIVE OPEN | Prove real short-water bridge construction and crossing |
| ELLIE-006 | P1 | LIVE PARTIAL | Local wood substitution observed; mixed-species complete build unverified |
| ELLIE-007 | P1 | PREVIEW / LIVE OPEN | Prefer local door crafting; verify final door placement rather than planning alone |
| ELLIE-008 | P1 | PARTIAL / ARCHITECTURE OPEN | Core supervisor and schematic goals exist; general AGENTIC dependency goals incomplete |
| ELLIE-009 | P1 | PARTIAL / LIVE OPEN | Plank loops patched in PR #26 CI; full crafting and door convergence unverified |
| ELLIE-010 | P2 | INVESTIGATE | Validate house/build placement against cave/terrain and explicit location |
| ELLIE-011 | P2 | PROPOSED | Saved blueprint selection/preview/orientation |
| ELLIE-012 | P2 | PROPOSED | Safe boating for major water crossings, no new parallel navigator |
| ELLIE-013 | P1 | PARTIAL | Observatory F8/logs exist; correlate resource, path and dependency reasons |
| ELLIE-014 | P1 | LIVE REPRO / CI PATCH / LIVE OPEN | Preemptions interrupted schematic and excavation; PR #28/#29 code passes CI, live resume pending |
| ELLIE-015 | P0 | PARTIAL / SOURCE PARITY OPEN | Reproducible 1.21.1 pinned JAR + overlays; full matching custom source not recovered |
| ELLIE-016 | P1 | PARTIAL / LIVE TESTING OPEN | CI mocks/ABI checks present; automated real-world long-run harness missing |
| ELLIE-017 | P1 | LIVE PARTIAL | Two original houses observed complete, later variable-site/species cases unverified |
| ELLIE-018 | P2 | INVESTIGATE | Separate measured performance from cosmetic tick/skin/data-fixer warnings |
| ELLIE-019 | P2 | PROPOSED / OWNER ONLY | Optional explicit allowlisted native bridge, no privilege escalation |
| ELLIE-020 | P0 | KEEP | Preserve retaliation, teleport extension, F8/F7 HUD and unrelated NPC behavior |
| ELLIE-021 | P1 | HISTORICAL PREVIEW / LIVE OPEN | Reachable remembered crafting tables and anti-detour policy need current JAR/live proof |
| ELLIE-022 | P1 | HISTORICAL PREVIEW / LIVE OPEN | Water bridge IllegalAccessException patch claimed in old preview; current binary/live crossing unverified |
| ELLIE-023 | P1 | OBSERVED / REPRO NEEDED | Block-edge visual overlap vs actual foot-cell arrival mismatch |
| ELLIE-024 | P1 | INVESTIGATE | Exception-safe SharedTargetRecovery thread-local cleanup on all exits (main v9 audit) |
| ELLIE-025 | P1 | INVESTIGATE | Classify true stall vs temporary safety/environment unavailability before watchdog BLOCKED |
| ELLIE-026 | P1 | PROPOSED | Atomic staging/publishing to protect last known-good JAR on failures |
| ELLIE-027 | P2 | PROPOSED | External completion callbacks outside supervisor registry lock, without altering ordering |
| ELLIE-028 | P2 | PROPOSED | Pin/migrate 1.21.1 waypoint BlockPos mapping compatibility adapter |
| ELLIE-029 | P2 | PARTIAL / TEST COVERAGE OPEN | Controller fixture ABI parity: some real 1.21.1 bindings checked, not entire runtime |
| ELLIE-030 | P1 | DOCS FIX IN REVIEW | Regenerate 49-ID command inventory; eliminate obsolete active iron-setup description |
| ELLIE-031 | P1 | PROPOSED | Shared AGENTIC capability contracts, resource ledger, reservations and dependency graph |
| ELLIE-032 | P1 | PROPOSED | Safe same-goal inventory overflow deposits during hunting/excavation; no automatic destruction |
| ELLIE-033 | P1 | PROPOSED | End-to-end excavation survey/tools/drop budget/storage/resume/verification under one parent goal |
| ELLIE-034 | P0 | CI PASS / LIVE TEST OPEN | PR #29 temporary excavation interruption: gear/defense resume and native builder replan |
| ELLIE-035 | P0 | CI PASS / LIVE TEST OPEN | PR #28 schematic ACQUIRE interruption: preserve same action and avoid depot round-trip |
| ELLIE-036 | P1 | INVESTIGATE | Mob-farming hit/death/drop/pickup instrumentation; distinguish native combat from parent restart |
| ELLIE-037 | P1 | PROPOSED / DOCS IN REVIEW | Prevent stale command/backlog reports via deterministic parity checks and dated provenance |

## High-priority next work: scoped acceptance contracts

### ELLIE-034: live excavation interruption acceptance

**Existing implementation:** [PR #29](https://github.com/Joelandonly/PlayerEngine/pull/29),
CI run [42553046991](https://github.com/Joelandonly/PlayerEngine/actions/runs/42553046991).
Actual pre-fix live scene: area `91 80 277` through `120 80 296` (600 cells);
the native builder reached 444 then 261 blocks remaining before two
`BLOCKED_EXCAVATE_CANCELLED` events at 03:34:08 (Mob Defense) and 03:36:28
(ForceEquipGearChain). CI validates a patch that treats those chain takeovers
as transient, preserves work budgets and revalidates the region.

**Exit:** same project ID logs `EXCAVATE_SUSPENDED` →
`EXCAVATE_RESUMED`; native area progress continues to verified completion;
no unrequested new owner command, no modified chest/hazard and explicit
owner stop still cancels. CI-only evidence does not close this.

### ELLIE-035: live schematic acquisition resume acceptance

**Existing implementation:** [PR #28](https://github.com/Joelandonly/PlayerEngine/pull/28),
CI run [42540492088](https://github.com/Joelandonly/PlayerEngine/actions/runs/42540492088).
The original 48-carpet dependency followed 128-string spider farming;
Mob Defense and equipment interruptions sometimes sent the project back
to storage. PR #28 preserves the original ACQUIRE action and reissues
the native child with the correct **absolute inventory target**, accounting
for already held items. **No evidence yet proves native loot pickup is
broken**, so don't replace combat with speculative animal availability.

**Exit:** `SCHEMATIC_ACQUIRE_SUSPENDED` →
`SCHEMATIC_ACQUIRE_RESUMED next=ACQUIRE`, same action ID and cumulative
watchdog; verified loot reduces remaining amount; no preemption-only
TRAVEL; explicit cancellation/world change blocks. Test both original
string case and generic material collection.

### ELLIE-031 / 032 / 033: one AGENTIC planner, not more managers

Add shared **capability contracts** to the existing
`CoreGoalSupervisor`/`AgenticPlannerService` that declare:
preconditions, inputs/output units, exact resource reservations,
allowed source candidates, required inventory slots, interrupt behavior,
verified progress, bounded failure reasons and cleanup. Do not
recursively issue top-level `deposit` or `stop` commands.

The shared **resource ledger** must separate live inventory, verified
container stock, reserved project supplies, estimated uncollected drops
and exact additional-material requests. Explicitly preserve
`mine BLOCKS` vs `get ITEMS` and total-inventory vs missing-count
semantics proven problematic in the plank failures.

For overflow, start with **five or fewer free inventory slots** as a
tunable warning, use a verified safe container, store only vetted surplus,
retain rare/unknown and required supplies, check actual chest/inventory
deltas, then resume the *same* AGENTIC goal. If no safe room exists,
give a bounded capacity blocker. **Never throw items into lava
or auto-delete unknown items.**

For excavation, survey the actual area before predicting drop needs,
recheck tools and hazards, use existing native `clearArea`, prepare
chests **only when storage calculations justify them**, unload
when necessary and verify air-cell postconditions. A 600-block
selection does not imply 600 recoverable items or a fixed chest count.

**Exit:** deterministic fake-world scenarios for partial materials,
missing chest, full inventory, interrupted unloading, world change,
no resource source, tool breakage and refusal to delete items, followed
by a supervised long-running live Minecraft case.

### ELLIE-036: separate actual combat farming evidence

Keep native Mob Defense and `KillEntitiesTask` behavior intact until
a trace proves the failing stage. Record target selection, reach,
attack/hit, death, drop spawn, pickup attempt and actual inventory
increase with project/action IDs. A successful direct `attack` kill
is **not** proof of item-farming success. Do not infer a broken attack
from a parent coordinator losing its ACQUIRE action.

### ELLIE-024–029: restored source-audit concerns

These entries originated on `main` in a [2026-11-06 audited follow-up](ELLIE_BACKLOG.md).
They were absent from the PR #29 branch because its source lineage diverged
before the main-only documentation update. The original detailed text
is restored to the legacy register. None should be marked solved merely
because later build/supervisor tests exist: package integrity is not
exception-safe task cleanup; a watchdog is not stall classification;
GitHub artifact upload is not atomic local release staging; single-thread
callback tests are not global-lock concurrent validation; one 1.21.1
method ABI check is not a full mapping compatibility adapter.

### ELLIE-030 / 037: keep source, reports and backlog aligned

The current `setup` owner entry invokes
`BasicSetupTask`, a tested **14-stage** HOME workflow: 12 logs,
one HOME bench, wooden and stone tools, 20 cobblestone, 16 coal,
64 torches, one chest and furnace. The former iron/shield/bucket/bed
survival setup remains historical source/tests, not this command.
The old TSV incorrectly advertised the previous workflow and omitted
the later `schematic_prepare`, `schematic_build` and `excavate`
IDs. [49-ID canonical command audit](ELLIE_COMMANDS.md) now
reconciles these. Mark previous setup pages historical instead of
silently rewriting their revision-specific tests.

**Exit:** source-to-command-report consistency checks on every changed
command; unique 49 registered IDs and 8 legacy unregistered IDs on this
specific PR #29 line; documented tested and live evidence kept separate.
Run the checks on newer commits and update counts rather than treating
49 as a permanent constant for all future releases.

## Release and triage rules

1. New fixes branch from the **latest active development PR head** after
   checking GitHub; never silently revert stacked PR #26/28/29 code.
2. Preserve every original `ELLIE-###` identifier, and distinguish
   historical hypothesis from current confirmation. New entries start
   after 029; no reuse or renumbering.
3. Treat owner reports, CI/ABI tests, actual packaged JAR hashes and
   live-world proof as **different evidence**, with date and build ID.
4. Do not promote a draft as gameplay-verified based on tests using
   substituted controllers/worlds; keep the PR draft until acceptance.
5. Avoid changing existing command behavior in a docs-only cleanup.
   Historical evidence remains intact and is marked superseded where
   appropriate.
6. Keep the last known-good local JAR, validate new output against its
   exact source SHA and current patcher, and respect explicit owner stop.
7. If GitHub Issues are later enabled, migrate using stable IDs rather
   than creating duplicates. The repository currently tracks work in docs.
