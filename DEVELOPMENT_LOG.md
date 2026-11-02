# Ellie / PlayerEngine development log

A chronological record of **observations, decisions, preview artifacts and verification**, not a claim that the current GitHub source contains those binaries. See [Ellie Backlog](docs/ELLIE_BACKLOG.md) for actionable IDs and [Validation Matrix](docs/ELLIE_VALIDATION.md).

## Ground rules
- Experimental Minecraft target: **1.21.1 Fabric, PlayerEngine 1.4.0**. Current GitHub main still advertises **1.20.1 Quilt**; until a source port is committed and rebuilt, treat binary-only previews as external to this repository.
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
