# v18.2 — shared native constructor bindings

- Fix primitive boolean arguments rejected by the shared constructor resolver.
  This repairs the logged foundation and depot-travel task creation failures,
  plus the same binding error in later interaction tasks.
- Verify exact flags/targets/counts against actual packaged task constructors
  and Minecraft 1.21.1 registries in GitHub Actions before publishing artifacts.
  Add primitive/reference/null/exception regressions and retain the full suite.
- Freeze all 1,901 other classes and all other resources against v18.1, excluding
  build identity. All 32 protected setup hashes remain unchanged.
- Live NPC execution and complete survival construction remain unverified.

See [diagnosis, test boundaries and live checks](../docs/ELLIE_SCHEMATIC_NATIVE_BINDINGS_V18_2.md).

# v18.1 — material preparation independent of protected-site clearance

The logged `schematic_prepare` imported successfully, then stopped at a protected
wall torch at 104,106,289 in selection air. Preparation now warns and stages
supplies; building still requires those protected conflicts to be resolved.
Adds separate site telemetry and tests the reported startup failure through the
real coordinator and preflight with substituted game boundaries.

See `docs/ELLIE_SCHEMATIC_PREPARE_V18_1.md`. Setup/native workers are unchanged;
live Minecraft success is not claimed.

# v18 — schematic preparation/building (live Minecraft unverified)

- Add supervised `schematic_prepare` and `schematic_build` commands for `.litematic`
  and modern `.schem` files, with bounded parsing and corrected region indexing.
- Stage remaining finished-item supplies in actual project chests, retaining gear.
- Use native acquisition/crafting/navigation/transfer/build workers, strict world
  postconditions, layered construction, deferred fluids and bounded failure states.
- Scope native property and NPC-facing prediction fixes to imported projects.
- Preserve the 14-stage setup and all unrelated v17 classes; keep live validation
  explicitly false in the revision-stamped JAR.

See `docs/ELLIE_SCHEMATIC_PROJECTS_V18.md` for precise tests and limitations.

# v17 — partial-block navigation (live Minecraft unverified)

- Replace the incorrect slab-only chunk lookup at shared feet calculation with
  non-loading collision-supported foot height, including hops and negative coordinates.
- Recognize ordinary partial floor shapes at the existing support predicate; keep
  thin-overlay, hazard, slab-setting, liquid and climbable rules.
- Request a native jump when a level path cell rises above automatic step height.
  Missing bridge floors, paused/backing/sneaking movements and emergency airborne
  control retain their existing inputs.
- Reject impossible ascents from low supports while retaining native stair ascent.
- Preserve the 14-stage setup, v16 food repairs and all unrelated classes. Protected
  setup hashes, scope hashes and native-body comparisons run in the full suite.

See `docs/ELLIE_PARTIAL_BLOCK_NAVIGATION_V17.md` for exact boundaries and live checks.

# Food reliability v16 — gameplay UNVERIFIED

Fixes the first food/supervision/result-contract portion of the command audit.
Fuel acquisition is retained and bounded; inaccessible fuel switches strategy.
Hunting targets require a new raw item and cooking goals include existing cooked
stock. Food keeps its cooking child until inventory output is verified, uses an
existing furnace where available, and reports a specific blocked dependency.
Automatic food collection yields to an explicit food job and suppresses unchanged
failed retries while preserving emergency eating. Critical unresolved hunger
blocks the owner job explicitly rather than silently repeating food work.

The command lifecycle captures its own task receipt, defers early native callback
completion, and translates worker failure/partial outcomes consistently. Giving
retains a verified successful terminal and sign failures keep their reason.

Validation executes the packaged food/cooking methods with explicit world/combat/
acquisition/navigation boundaries. The repeated-hunt regression fails on v15.
The full pre-existing suite and setup byte-preservation checks remain required.
No live Minecraft gameplay success is claimed. See docs/ELLIE_FOOD_RELIABILITY_V16.md.

# Non-loading water escape v15 — gameplay UNVERIFIED

The supplied long-running world stops at the same Unstuck/Completing-goal boundary
as the earlier freeze. The client launched before v14 existed, so this run cannot
establish v14 failure. Native water-escape goal
callbacks still query live world blocks from A*; use the existing non-loading
loaded-chunk view and exclude unknown/stale-world escape destinations.

Keep native task/recovery orchestration, setup and bridging unchanged. Extend the
existing observatory client-tick diagnostics to capture full JVM thread/monitor
stacks to `logs/ellie-server-stall.txt` after a sustained unpaused server stall.
No new thread, executor or recovery controller. Actual Minecraft stall resolution
remains unverified; tests reproduce the access hazard with substituted worlds.
Record embedded build identity at client startup to disambiguate future runs.
See docs/ELLIE_WATER_ESCAPE_STALL_V15.md.

# Non-loading descent collision view v14 — gameplay UNVERIFIED

Use native CalculationContext.bsi.access for planning collision callbacks;
neighbor lookups must not escape to the live world from a path worker. No descent
policy, native movement hooks, setup, chunk ownership or lifecycle changes.
A regression fails against v13 and covers alternating world contexts and old
context unloading. This demonstrates the access defect, not the exact reported
Minecraft freeze. Real rapid-world-switch testing remains required.
See docs/ELLIE_DESCENT_BLOCK_VIEW_V14.md.

# Pre-commit descent safety v13 — gameplay UNVERIFIED

Preserve outward bridging while rejecting unsafe downward candidates in shared
A* planning and rechecking landings before native movement preparation/inputs.
Allow verified safe drops, gradual stairs, existing source water and continuous
climbables; do not treat a carried bucket as validated deployment protection.
Reuse native replanning and existing bounded shared recovery. No setup changes.

Adds geometry/adapter regressions, execution of the packaged pre-commit gate,
bytecode boundary checks and exact preservation checks for all 32 proven setup
classes and concrete native bridge/movement bodies. These substitute game
boundaries and do not prove live Minecraft navigation or placement success.
See docs/ELLIE_SAFE_DESCENTS_V13.md for the required live cliff/mining checks.

# Independent NPC chunk ticking v12 — gameplay UNVERIFIED

Based on experimental v11 cc9f999. Keeps the proven 14-stage setup source unchanged.
World-scoped, idempotent native chunk ownership and independent global tick maintenance
replace the circular dependency on NPC/controller ticking. The native footprint remains
at most four distinct cells; old cells are released before movement/teleport acquisition.
Vanilla forced entity-ticking tickets are reasserted independently of saved force flags.
Controller stop, death/removal, unregistration, world change, tracker reset and native
clear release owned claims; pre-existing external forced flags remain intact.
No manual NPC/controller tick, new executor, remote command authorization change,
recipe/navigation/recovery rewrite or setup redesign is included.

CI substitutes Minecraft world generation/tick eligibility. It does not establish actual
unloaded-NPC gameplay execution. See docs/ELLIE_REMOTE_EXECUTION_V12.md for the live gate.

# Setup output handoff v7

Build ID: `20261105-setup-output-handoff-v7`. Gameplay UNVERIFIED.

Addresses the supplied v6 failures at workshop crafting-table acquisition and
night-time shelter final verification. Workshop material counts read actual
36 main slots. A native acquisition completion without observed output gets
one bounded 40-tick settling window; no repeated acquisition or false success.
The three placed stations and required kit remain mandatory.

Shelter blueprints explicitly retain all interior/entrance air obligations,
including cells that are clear before construction but can receive temporary
scaffolding during building. A builder that ends with verified cell gain but
incomplete geometry gets one remaining-cell repair; no-gain/repeated failures
block with the first mismatched cell and observed state. Nested mining guards
and compact checkpoint migration are retained. Torch placement remains off.

Adds delayed-output/false-completion handoff tests, an explicit interior-air
blueprint assertion and a partial-shelter repair full-chain scenario. Full
regressions substitute game boundaries; live crafting/placement/repair and
complete survival setup remain unverified. No chat/follow/hunting fix included.

# Survival shelter protection v6

Build ID: `20261105-survival-shelter-protection-v6`. Gameplay UNVERIFIED.

The supplied run verified its shelter, then damaged it while gathering supplies.
Shared construction mining exclusions now retain all active owners per NPC:
workshop, shelter and nested repair blueprints cannot replace each other's zones.
Shelter break guards are restored if native state restoration removes them.

Normal daytime setup places and verifies the table, furnace and chest after
stone tools, before building the shelter. Saved chest coordinates describe a
planned location, never proof of an existing chest. Emergency cover retains
its priority. Required inventory and final world checks remain unchanged.

V5's per-cell material list exceeded the existing journal's 360-character field.
V6 stores a compact position/wood/approach checkpoint, recomputes materials,
and recovers validated chest/shelter coordinates from truncated V2 records.
It does not silently move the base or discard partial construction.

Validation adds nested-zone ownership, actual-journal truncation/migration and
physical-workshop-before-shelter assertions to the full regression suite.
Actual live mining protection, repaired shelter, station placement and complete
survival setup still require a Minecraft retest. Torch placement remains off.

# Survival terrain/platform setup v5

Build ID: `20261105-survival-terraform-platform-v5`. Live gameplay UNVERIFIED.

Fixes the premature flat-5x5-site rejection in the supplied November 5 logs.
The existing builder now receives bounded natural excavation, mixed-material
platform fills, retained existing solids, and a connected bridge/stair approach.
Shared workshop preparation also uses suitable blocks from actual main slots.
Protected blocks, containers, hazards and disconnected sites remain blocked
with specific diagnostics. Saved chest and partial shelter plans are retained;
old shelter journals migrate. Torch placement remains disabled.

Validation: complete existing regression suite, 21 production-entry survival
scenarios, 10 voxel terrain/platform scenarios and four reflective inventory
adapter checks. These substitute game boundaries; real placement, excavation
and navigation require Minecraft testing.

# Chest-anchored survival setup v4

Build ID: `20261105-survival-setup-v4`. Minecraft 1.21.1 Fabric, pinned
PlayerEngine 1.4.0 binary overlay, Java 21. **Gameplay remains UNVERIFIED.**

- Expand the existing setup task with assessment, food, shelter, fuel and
  equipment stages; no second runner, model planner or engine migration.
- Keep the required kit and station verification. Torch placement/light surveys
  remain excluded; all 32 torches stay in inventory.
- Reuse the saved workshop chest and recorded shelter coordinates. Return to a
  remote saved base instead of silently selecting another site.
- Use fresh verified base-chest stock before collecting matching supplies;
  setup cannot withdraw from unrelated remembered storage. Prefer carried or
  stored wood and nearby trees when choosing shelter materials.
- Use the existing native physical builder for a checked 5x5 local-wood
  enclosure, with actual floor/wall/roof/door/interior/entrance verification.
  Refuse foreign/protected obstructions instead of bulldozing them.
- Hungry NPCs obtain/eat food; ordinary work can pause for cover/rest/daylight.
  Native food and defense chains retain priority. Waiting and retry budgets are
  bounded, including worlds with a stopped daylight cycle.
- Require 32 safe carried food nutrition units, eight coal/charcoal fuel items,
  and verified sword/main-hand and shield/off-hand equipment before completion.
  Use charcoal as a fuel option; food/fuel have bounded alternate native routes.
  Beds remain opportunistic information only: no mandatory wool hunt or sleep.
- Correct the old setup help text that still advertised buckets and armor.
- Verify packaged entry points and native ABI boundaries, and run full setup
  orchestration/geometry/storage-scope/lifecycle scenarios in addition to the
  existing regression suite. Substituted worlds do not prove live behavior.

See `docs/ELLIE_SURVIVAL_SETUP.md` for exact stages, limits and fresh-world gates.

---

# Setup11: tools first, no automatic torch placement

Build ID: `20261105-setup11-tools-first-no-torch-placement-v3`.

The owner's live run crafted all 32 torches, then setup stopped because its
surface-light survey found one dark sample with no acceptable torch location.
This revision follows the owner's updated contract: remove automatic torch
placement and surface-light verification from setup entirely. Do not claim the
site is lit or spawn-proof. The three workshop stations are still verified.

Required order: wooden pickaxe, early stone axe, two stone pickaxes, stone shovel,
crafting table, furnace, chest, shield, iron pickaxe, iron sword, then 32 torches
retained in inventory for manual placement. No optional skipping, buckets,
iron armor or diamond acquisition is added. Existing saved station layouts can
be resumed and reused. No new setup_base command or base relocation is added.

Regression checks cover the full setup chain with an inventory of partial tools,
existing stations, uneven terrain, missing iron and placement failures. A light
survey boundary that throws verifies setup never calls it; dark/unplaceable
samples cannot block shield or iron-tool acquisition. Shared recovery, Player2,
storage, command and construction checks remain in the complete suite.

Live survival completion still requires a Minecraft test of this exact revision.
The previous live logs establish the torch-stage failure, not a successful run
of this change.

---

# Setup11 shared survival reliability v2

Build ID: `20261105-setup11-shared-reliability-v2`.
Minecraft 1.21.1 Fabric / PlayerEngine baseline 1.4.0 / Java 21.
**Automated preview; live Minecraft survival behavior remains UNVERIFIED.**

Reconciles the owner's checksum-verified Setup11 source ZIP against
`codex/shared-recovery-crash` at `ae24a84`. No Goodbird engine replacement or
full source migration. Imported storage, native commands, village handling,
observatory and server-tick diagnostics remain covered by regression checks.

- All eleven setup objectives are required, with the early stone axe and stone
  shovel. No bucket, iron-armor, diamond, bed or bread objectives.
- Three verified stations and outdoor-only block-light coverage. Existing placed
  stations and installed torches credit real outputs; sunlight does not count.
- Shared recovery retains progress and retry history across target changes.
  A/B selection, inventory reshuffling, reachability, repeated crafting phases
  and damage lost/regained below the high-water mark do not replenish retries.
- Scanner rejection selects alternative locations. Three recovery attempts and
  a 2,400 active-server-tick no-output limit bound unsuccessful resource jobs.
  Higher-priority safety chains retain control; there is no second craft watchdog.
- Canonical inventory capacity reads the actual 36 main/hotbar stacks, bypassing
  cached AIR counts and slot maps. Unknown layouts are not reported as full.
  No automatic dumping is added.
- `TASK_PROGRESS` logs identify objective, subtask, target, occupied/free slots,
  no-output age and recovery owner/attempt. `TASK_BLOCKED` preserves failure
  evidence through the supervisor even if a native receipt claims success.
- The JAR embeds build ID, exact Git revision, source-input digest and baseline
  checksum. CI verifies these and uploads one revision-named install JAR plus
  checksum, separately from a source bundle containing zero binaries.

See `docs/ELLIE_SETUP11_RELIABILITY.md` for test boundaries and live acceptance.
