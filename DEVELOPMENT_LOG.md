# Ellie / PlayerEngine development log

**2026-11-04 user observation — ELLIE-023:** Ellie sometimes waits on a block edge while most of her visible body overlaps the next block. Feet/grid-cell ownership, collision support, arrival and interaction reach need correlated inspection. Added setup/build movement acceptance checks; root cause and fix remain unverified.

A chronological record of **observations, decisions, preview artifacts and verification**, not a claim that the current GitHub source contains those binaries. See [Ellie Backlog](docs/ELLIE_BACKLOG.md) for actionable IDs and [Validation Matrix](docs/ELLIE_VALIDATION.md).

## Ground rules
- Current target: **1.21.1 Fabric, PlayerEngine 1.4.0**, preserved as an explicit binary-overlay module in `ellie/`. Root source remains 1.20.1; matching complete 1.21.1 mod source is unavailable. Historical external-preview notes below describe their status at the time.
- **OBSERVED** means user/log evidence; **INFERRED** means plausible but not proven; **PREVIEW** means produced an experimental JAR; **LIVE** means actual in-game result. Never equate mock tests or packaging audits with end-to-end survival validation.
- No unredacted log files in this public repository. Local usernames, identifiers, paths, tokens and conversation payloads must not be published.
- Preserve combat retaliation, independent Player2NPC teleport extension, Observatory shortcuts and successful building logic.

## 2026-11-02: House builds, gear and follow-up design

**LIVE / OBSERVED (older session):**
- The user followed Ellie in creative mode to a village and back. Ellie finished **two separate 5×5 houses** in one session, with Observatory `Completed: Verified all structure blocks placed` for each.
- Earlier progress could pause at 123/124 during the structure stage before door completion, but both houses finished.
- Ellie eventually acquired and equipped multiple diamond tools and armour pieces. Full-diamond-armour request still required repeated user prompts; **diamond boots were not observed**.
- The omission of boots from per-item instructions was **intentional** to test whether Ellie could infer all four pieces from “full diamond armour.”
- A later house request was anchored underground at Y=-19, with zero placement progress in its final snapshot; user explicitly explained they **left the world while Ellie travelled for materials**. This snapshot does not demonstrate a failed build-site choice.

**DECISION / PROPOSAL:** Add a persistent compound-goal manager with explicit requirement checking and post-equip verification. Avoid hardcoding only the missing boots (ELLIE-008).

**DECISION / PROPOSAL:** Add stone-or-higher tool preflight for substantial wood and stone work. Existing successful explicit pickaxe crafting shows capabilities exist; builder's tool selection is inadequate. Reuse equipped gear, bootstrap when needed, bound retries (ELLIE-003).

## 2026-11-02, first recreated-world wood/lake test

**LIVE / OBSERVED:**
- The generic 5×5 house plan at spawn logged `WOOD_RESOLVE: Requested=any, chosen=oak, blueprint unchanged` around 19:29:16.
- Ellie nevertheless made a crafting table from **jungle planks** shortly afterwards, indicating local jungle wood was available.
- Door-preflight required oak planks and oak logs; the AI then travelled to remote tree positions in the rough region of (-260, 65, -309). The visible task was oak resource acquisition, not proof that a specific village door was salvaged.
- No `WATER_BRIDGE_PLAN` or bridge rejection reason appeared in the available run. Exact cause of no bridge is **unknown**.

**CODE REVIEW / INFERENCE:** The initial wood selector sampled nearby wood too sparsely and fell back to oak silently. Existing bridge logic was narrow and attached to construction/resource gathering, not general navigation.

**PREVIEW ARTIFACTS:** A “Wood & Water Preview” and then “Local Wood & Bridge Fix Preview” were packaged for Fabric 1.21.1. The latter described improved wood scans, local-crafting preference and detour-aware short bridge candidate searches. Mock/component tests and binary audits were reported. **No successful live bridge was recorded at this stage.** (ELLIE-004, 005, 006, 007, 015.)

## 2026-11-02, later jungle follow-up

**LIVE / OBSERVED:**
- House construction anchored at (6,73,8). Around 19:58:04 the newer wood selector logged `Requested=any, chosen=jungle, substitutions=98`. This directly supports that the local-wood selection change worked **for this world/scenario**.
- The builder prepared a jungle door and successfully crafted a workbench from jungle material. Final status remained at **0/124**, while door/resource preparation was in progress; do not claim completed door or house.
- The user observed that vines sometimes permitted climbing but then caused apparent navigation stalls. Manual breaking of certain vines freed Ellie. Leaves occasionally had similar symptoms, less frequently.
- Around 21:00:46, 21:01:00, 21:01:17, an automatic World Survival Chain interrupted the building task and navigated toward (-1,49,21); Ellie later burned to death, cancelling the build.
- **Correction from user:** The owner intentionally set the tree on fire. Thus the survival-chain activity and burning death are **fire-confounded**, not proof of a vine-induced navigation bug. Preserve this correction in future analyses.

**PREVIEW ARTIFACT:** “Navigation Stall Recovery Preview” was issued with claimed watchdog, rerouting, temporary vine avoidance, controlled obstruction-breaking and `PATH_STALL`/`PATH_RECOVERY` logging. Mock tests were reported; **a successful live vine recovery has not been confirmed**. (ELLIE-001, 009, 013, 014.)

**USER REPORT:** Teleport-to-Ellie button disappeared after a preview update. The latest game log still included a Player2NPC teleport-to-buddy preview JAR name in a rendering stack; missing button cause not established. Vanilla /tp was suggested only as temporary workaround. Do not overwrite Player2NPC while investigating. (ELLIE-002.)

## 2026-11-02, architecture decisions

- Move bridging from construction-only helper into reusable navigation, supporting follow, exploration and resource-gathering tasks; prefer a reasoned walk/swim/bridge/boat decision instead of blindly building everywhere (ELLIE-004).
- Keep short safe bridges separate from eventual long-water boating (ELLIE-005, 012).
- Preserve working layer-aware construction and original NPC retaliation logic; patch surgically rather than rewriting unrelated functions (ELLIE-017, 020).
- Add observable, bounded recovery rather than deleting vines or teleporting the bot past obstructions (ELLIE-001, 013).
- Consider a saved visual blueprint picker, footprint/material estimates and persistent structure IDs as later work (ELLIE-011).
- A prior discussion considered an owner-controlled allowlisted native-command tool. No unrestricted operator-command capability is approved or implemented (ELLIE-019).

## 2026-11-02, repository tracking setup

- Identified the correct connected repository: `Joelandonly/PlayerEngine` (public), distinct from `Joelandonly/Spider-Colony`.
- Verified default-branch `gradle.properties` still targets **Minecraft 1.20.1** with **Quilt**, unlike the separate **1.21.1 Fabric** preview JARs (ELLIE-015).
- Attempting to create GitHub Issues returned **HTTP 410: “Issues has been disabled in this repository.”**
- Created `docs/ELLIE_BACKLOG.md` with stable IDs, observations, caveats, priorities, acceptance criteria and testing status; added this log and a validation matrix.
- Tracking docs are not executable code. **No JAR, source patch, or live test is being claimed as committed by these document changes.**

## Future entry template

### YYYY-MM-DD — short descriptive title
- **Reference ID:** ELLIE-###
- **Build/source:** exact JAR SHA-256 + branch/commit (if known)
- **Observed:** what actually happened, with sanitized timestamps/coordinates
- **Hypothesis:** clearly distinct from observation
- **Change:** code paths or artifacts actually modified
- **Tests:** mock / compile / JAR audit / Minecraft live / long-run, separately
- **Status and next action:** never infer success from a lack of error logs

## 2026-11-02, crafting-table detour and vine-recovery refinement

**USER REPORT:** Ellie travelled down into a trial chamber to use a crafting table instead of using a convenient local workbench. A separate Observatory history documents workbench task targets near (-185,63,-248) and (-181,14,-247); that log does not establish which table was in the trial chamber.

**SOURCE/BINARY AUDIT:** Current binary has `EllieGPSStore` for persistent per-world waypoint records, but `CraftingTableLocator` uses a block scanner and defaults to a **48-block reuse radius**. The selector's geometric proximity can be misleading in caves. The previous navigation watchdog also exempted tasks described as `Destroying block`, hiding stalls while walking to a block.

**PREVIEW, NOT LIVE:** Issued `PlayerEngine-Ellie-Workbench-Memory-Vine-Recovery-1.21.1-PREVIEW.zip`, containing a patched PlayerEngine JAR (SHA-256 `1f0bc6603ff788a6736387cbe08e773fd21c107b99f980d65e0da985e0263b0c`). Verified tables encountered within interaction reach are saved in EllieGPS, including ones Ellie places and then uses, without claiming proven bot origin. Subsequent table selection checks saved locations and scans, rejecting candidates beyond 16 horizontal/5 vertical blocks or a smaller configured radius. Existing vine recovery now monitors active travel toward a mining target, with longer tolerance when the task reports mining.

**TESTS:** Java compile; geometric policy cases; mocked EllieGPS persistence/reuse/stale-block cases; navigation recovery mock suite; binary CRC and class-reference audit (1,779 classes, zero unresolved internal references). **Real Minecraft behavior and persistence across server restarts not tested.** No code changes in GitHub source tree; binary-only preview. See ELLIE-001, ELLIE-021, ELLIE-015.

**CONFIRMED BRIDGE BUG:** Latest jungle run emitted `BRIDGE_SKIP: Bridge planner unavailable: IllegalAccessException` (20:31:21 and 20:34:10). This error is **not fixed** in the preview; tracked separately as ELLIE-022.


## 2026-11-02 — upstream Baritone 1.21.1 architecture review

**Research only; no executable patch.** Compared [cabaletta/baritone 1.21.1](https://github.com/cabaletta/baritone/tree/1.21.1) and [upstream source/issue findings](docs/BARITONE_RESEARCH_2026-11-02.md) against Ellie's present observations. Native Baritone already has explicit movement timeout (original estimated cost + 100 ticks), route path accessors, supported block-placement bridging and per-target blacklisting. Its `allowVines` advanced option warns that climbing manoeuvres can trap indefinitely, but issue #392 reports stalls even with the option off. The still-open issue #1773 closely reproduces the owner's report of getting stuck under jungle logs with vines until manually removed. The 1.21.1 API offers a possible replacement for bridge-path reflection, but the Automatone-derived runtime needs interface compatibility verification. Do not install standalone Baritone over PlayerEngine. No live tests conducted for this research.

## 2026-11-02 — user decision: no vine traversal

**Approved policy, not yet implemented:** The user explicitly chose to **stop using vines as a pathfinding/climbing mechanism**. Ellie already has support-pillar construction, which should be preferred when gaining elevation. Do not confuse upstream `allowVines=false` (disables advanced manoeuvres only) with a full vine-routing prohibition. Adapt the *embedded* Automatone movement/cost policy so routes prefer safe ground and solid supports instead of climbing or traversing vine nodes; do not disable ladders, indiscriminately destroy jungle vines, or cut a vine while Ellie is relying on it. Treat genuinely blocking vines as normal removable obstacles only when doing so is safe, with route revalidation, cooldown and Observatory diagnostics.

**Other earlier research decisions accepted:** Investigate the existing Baritone path interface as replacement for bridge reflection; use native placement and movement cost first; keep EllieGPS crafting-table memory with reachability checks, and continue stone tool preflight. The upstream research remains recorded for context but its earlier recommendation to preserve vine climbing is now superseded.

**Status:** Documentation/decision committed to GitHub. No new JAR or executable source changes; Minecraft regression testing pending. Tracking: ELLIE-001, ELLIE-004, ELLIE-021, ELLIE-022, ELLIE-003.

## 2026-11-02 — Backlog Batch 1 experimental implementation

**PREVIEW / MOCK, not live verified.** Implemented the no-vine-route decision and one concrete bridge reflection defect in the latest 1.21.1 Fabric preview JAR. Native route settings now disallow `Blocks.VINE`, and two movement-cost hooks stop using vines as walkable/pillar support; ordinary ladders and existing support-pillaring remain. Patched `WaterBridgeCoordinator` reflective access for non-public world classes. Existing User Task builder, EllieGPS workbench memory, Observatory and combat/retaliation logic preserved; Player2NPC unchanged.

**Validation:** Java compile, navigation mock, bridge reflection red/green test, ABI and internal-class reference audit passed (1,780 classes, 0 unresolved). **Not run in Minecraft**, so there is no evidence yet of a completed crossing or a resolved jungle path in the real world. Filename `PlayerEngine-Fabric-1.21.1-1.4.0-BACKLOG-BATCH1-PREVIEW.jar`; SHA-256 `f02aa1bacd2e462f5f4775608f03184e0722853b84a9671757293e414f102d25`. ZIP bundle provided in chat with source helpers/tests. Code is **not** in the GitHub main source tree, still 1.20.1 Quilt. Issue register: ELLIE-001, ELLIE-022.

## 2026-11-04: Preserve current interpreter bundle in the repository

Imported the maintainer-supplied `PlayerEngine-Ellie-ChatGPT-Interpreter-STREAM-HOTFIX-FLAT.zip`
as `ellie/`: bridge source, Node/JVM tests, launcher, ASM build tools and the pinned
Acquisition Safety baseline. No interpreter or executor behavior is intentionally
changed. Root source remains available as a clearly identified legacy build.

Repository build changes: verify baseline hash before patching, clean generated
class directories, and produce a portable release ZIP under `ellie/dist/` without
overwriting the original provenance manifest. CI has separate Ellie and legacy jobs.
This is not a full 1.21.1 source port and does not close existing gameplay issues.

Local import validation: 24 Node tests, JVM configuration/fail-closed test and ASM
verification passed. Rebuilt JAR exactly matches the uploaded artifact, SHA-256
`9464cebb201edf6ac720828bb445293195389c9123007a3319c12510857be2c8`.
No live account inference, Windows OAuth or Minecraft gameplay was run.

Upstream discovery: Goodbird-git/PlayerEngine has a public `1.21.1-arch` branch,
commit `aa4ad2d5ec2a834107c76cdb58af732acb192ecc`, advertising version 1.21.1-1.1.11.
It is a source-recovery candidate, not yet verified as equivalent to the custom
Ellie 1.4.0 baseline. See `docs/ELLIE_SOURCE_PROVENANCE.md`.

## 2026-11-04: In-game project supervisor reliability preview

Recovered `CoreGoalSupervisor` and `GoalLifecycle` against the pinned baseline's
API. Initial regression harness reproduced eight failures in twelve scenarios:
receipt races, stale continuations, contradictory success and invalid lifecycle
inputs. Added fixes, a reviewed project gateway for 20 command classes / 22 call
sites, and packaged-bytecode routing verification. Native task, survival-chain,
controller, executor, gesture and unpause classes remain unchanged.

Validation: 15 supervisor scenarios, bridge JVM test, 24 Node tests, ASM and exact
class-change inventory. The artifact is distinctly named PROJECT-SUPERVISOR-PREVIEW;
it intentionally differs from the original interpreter JAR. Full task-graph
persistence, bounded recovery and live building verification remain outstanding.
See `docs/ELLIE_PROJECT_MANAGER.md`; no gameplay backlog items are closed.

## 2026-11-04: Command audit, setup contract, and supplied-log regressions

Audited the supervisor JAR: 46 registered NPC commands and nine unregistered
command classes. `gamer`/BeatMinecraftTask exists but is not registered; lookup
rejects it. Generic `build_structure` uses LLM-generated code. Added a full inventory
and real-dispatcher lookup tests, without claiming any game handler passed live.

The maintainer selected a full starter kit plus placed workstations/storage for
exact `setup`. Recovered setup/progress source; all supplies and iron-or-better gear
are required, final outputs are reverified, and a conservative native placement
phase establishes a crafting table, furnace and eight separated chest blocks.
A per-world/per-character journal records the base anchor; unsafe/unavailable
sites block rather than excavating or duplicating a base.

Supplied logs show skipped armor/food with old inventory-only success, unrelated
cobblestone counted as iron progress, and repeated approach to a table three blocks
above the NPC. Added monotonic relevant-material progress and a targeted crafting
approach-radius correction, plus ELLIE-023 edge/feet/bounds diagnostics. Raw logs
remain outside the repository; findings are in `docs/ELLIE_LOG_FINDINGS_2026-11-04.md`.

Local validation: 15 base/layout/journal scenarios, 11 setup final-output checks,
eight progress checks, 15 supervisor scenarios, bridge JVM test, all 24 Node tests,
46 dispatcher lookups/nine unavailable-ID rejections, ASM and routing inventory.
These substitute game boundaries. Native placement, full acquisition, reload and
observed crafting/edge stalls still require live Minecraft acceptance; no claim
that all existing gameplay defects are fixed.

## 2026-11-04: Immediate setup startup regression and terraforming requirement

New logs reproduce immediate failure at the over-restrictive flat-pad startup gate.
Changed startup to restore-only layout handling, deferred new selection to placement,
and added preparable-site handling using native excavation and cobblestone foundation
tasks. The maintainer explicitly requested clearing/filling ordinary terrain rather
than rejecting it. Dry living space, world limits, unbreakable/protected blocks,
verified native outputs and bounded waits remain enforced. See
`docs/ELLIE_SETUP_STARTUP_HOTFIX.md` for findings, mod versions and test limits.

## 2026-11-04: Protect setup foundation and revalidate layers

Latest supplied events show native acquisition mining cobblestone it had just
placed in the setup foundation; two generations then fail the base safety gate.
Reused the existing village resource-source exclusion through a small setup owner
adapter. New/restored anchors register before acquisition, and protection survives
the base phase until the whole setup stops. Rechecked terrain layers retain matching
stations and repair changed support before placement, with a finite repair budget.
The new damage regression fails against the previous task implementation. The real
shipped guard passes footprint/boundary/character/owner-cleanup checks. Native
Minecraft gathering, placement and full setup still require live acceptance.

## 2026-11-04: Import maintainer's Player2 crafting-recovery revision

Integrated source from `Ellie-Player2-Crafting-Recovery-Source (1).zip` while
retaining prior repository history. The unmodified recovered source rebuilds the
supplied craft-recovery JAR byte-for-byte (SHA-256 `f36e4210a31024c113f7be6d2afa97385c878a15b1ab1d9f590a4ea745980b9f`).
The Acquisition Safety baseline is unchanged. Setup now uses three spaced stations
with one chest, natural ground first, and an eight-support-cell platform fallback.
The original Player2 API binary replaces the optional ChatGPT interpreter overlay;
its source is retained under `ellie/legacy-chatgpt/`. Imported crafting-specific
table rejection and selector hooks pass seven local checks but do not implement
shared navigation recovery. Corrected stale release paths and current guidance;
preserved upload manifest separately. Raw logs and generated artifacts stay ignored.

## 2026-11-04: Deterministic command entry and execution

Extended the owner-verified setup route to all 46 exact command IDs and supported
local farm/build phrases. Preserved argument case and quotes. Replaced building's
model orchestrator with local selection and the original physical layer builder;
typed outcomes reject failed/unverified work. Agentic pickup/storage uses local
plans even if model planning was enabled. Labels and GPS descriptions keep native
templates. Fixed no-argument eat_food, absent-food silent returns, uppercase scan
default/arguments, and command splitting on quoted semicolons. Removed only the
provable no-op dummy class/task, leaving eight unregistered experimental/legacy
commands visible in the audit. None are described as proven impossible to implement.
Player2 API identity and the imported compact setup/table-recovery behavior remain.
Shared navigation deadlock recovery is a separate pending architectural change;
this revision does not claim it was implemented or all gameplay handlers tested.

## 2026-11-04: Supplied NPC crash, final-ingot race and shared target recovery

The new native-command log identifies a null PlayerEntity in vanilla axe stripping,
a five-of-six bucket material failure at the nominal furnace-batch boundary, and
our wrapper incorrectly failing a physical child after defense suspension. Guarded
the known NPC-only item-use boundary, retained in-flight native furnace production,
prevented an expired timer from skipping final output collection, refreshed inventory
accounting, and repaired accepted build resume without changing operator-stop semantics.

Implemented one shared recovery owner at Task.tick: verified progress/novel-position
sampling, alternative interaction poses, conservative native excavation, finite target
rejection, safety-chain yield and critical-air routing. Task look calls are gated
by interaction reach and actual look writers are recorded. Scanner/table selectors
share rejection; the craft-only watchdog is retired. Native re-entry balances leaf
cleanup while preserving the project. Worksites/verified benches are journalled
separately from personal home and revalidated for nearby reuse. Unknown builder
targets and persistence failures remain explicit; no chat-command injection.

Tests run real patched Task.tick with substituted world/pathfinder boundaries,
alongside production/NPC-use regressions and all earlier checks. Live Minecraft
physics, complete armour setup and construction still require acceptance. Raw uploads
and logs stay outside the repository. See docs/ELLIE_SHARED_RECOVERY.md.
