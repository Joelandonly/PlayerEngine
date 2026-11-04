# Ellie 1.21.1 implementation reconciliation (2026-11-04)

**Status:** evidence + architecture decisions, **not a source port or release**.
**Repository:** `Joelandonly/PlayerEngine`; `main` currently targets Minecraft 1.20.1 Quilt / PlayerEngine 1.0.0. Experiments target Minecraft 1.21.1 Fabric / PlayerEngine 1.4.0 / Java 21. The exact complete modern Fabric source is not in this repository.

## Evidence classes

- **LIVE / OBSERVED:** user or game-log observation; qualify interventions and specific world/build.
- **PREVIEW / STATIC:** a local binary overlay was produced and tested with mocks/bytecode/packaging; does not establish in-game success.
- **DESIGN / APPROVED:** user-selected architectural direction; no implementation implied.
- **BLOCKED:** needs source parity, compatible build, or in-game verification.

Never claim GitHub's older source embodies newer preview JARs. Do not upload unredacted Minecraft logs, OAuth credentials, local transport secrets, personal identifiers, or private-world content.

## November 3 work missing from GitHub source

| Workstream | Evidence / current result | Remaining gap |
|---|---|---|
| Reconciled backlog, ELLIE-001..042 | November 3 master reconciliation documented 001..022 and proposed 023..042 | Not all proposals implemented or formally tested |
| Goal Supervisor Phase 2 | **PREVIEW** `PlayerEngineController.runUserTaskTracked` delegates to `CoreGoalSupervisor.submit`; per-world/controller historical journal, generation protection from stale callbacks; existing setup/get/build execution left in place. Java unit checks and ASM verification reported | Generic resume/retry, verified predicates for all tasks, reproducible 1.21.1 source and live save/reload testing |
| Acquisition travel-progress + bed | **PREVIEW** travel progress read from native path/position to prevent false gather timeout while moving, with 4,800-tick no-gain bound; bed acquisition uses wool/planks path rather than unconditional remote bed salvage. Mock/ASM tests reported | Minecraft travel, actual material gain, sheep/bed, crafting-table altitude and disconnected session tests |
| ChatGPT interpreter bridge | **PREVIEW** optional `Sign in with ChatGPT` sidecar, local authenticated loopback at 127.0.0.1:38457, OpenAI Responses API, JSON response matching existing Player2 command dispatcher, explicit `provider=player2` fallback selection; no automatic paid-service switch. Node/local HTTP and bytecode tests reported | OAuth/live model testing on user device, correct usage-limit handling, runtime end-to-end, full Java/Node source and dependency provenance |
| Latest user log | **OBSERVED** `CHATGPT_INTERPRETER_REQUEST` followed by bridge HTTP 503; console reported `subscription_sharing_usage_limit_exceeded` in later interaction | Treat usage exhaustion as a provider quota error; distinguish 429 upstream from 503 bridge status. No claim the model successfully responded |
| Remaining survival bugs | **OPEN** distant/inaccessible crafting table, iron/furnace acquisition, path/height stalls, inventory clutter, dropped support/scaffolds | Actual reproducible runtime fixes and regression checks |

ChatGPT preview preserves Player2NPC, character/TTS infrastructure and the existing world-command catalogue; it replaces the chat-completion interpreter, **not** every Player2 desktop service. API plan allowance is separate from ordinary ChatGPT conversation usage. Private keys/tokens must not be committed.

## November 4 agreed construction architecture

**DESIGN / APPROVED. NOT IMPLEMENTED.** One Ellie Building Manager remains authoritative. GPT interprets user goals and supplies strategic reasoning only when needed. The manager persists a project and drives a single active executor; embedded Baritone/Automatone is a subordinate *physical* construction/navigation engine, not a competing planner. Existing layer-aware builder and verifying logic remain as fallback/specialized capabilities.

Simple, strict phases:

1. **BLUEPRINT:** settle full target block states, orientation, footprint and exact material list.
2. **CHESTS:** establish accessible staging containers outside the footprint. Use one chest for small builds and only add more if storage capacity requires it. Do not invent a new warehouse system.
3. **ACQUISITION:** collect/craft **all** build materials before starting construction; use chests, not inventory-only assumptions. Random incidental items must not corrupt reservations or strand the worker with a full inventory.
4. **STOCK VERIFY:** physically count all material items across reserved chests and inventory; advance only when the blueprint's needs are satisfied. If impossible, stay blocked with a precise material/source diagnosis rather than claiming ready.
5. **CONSTRUCTION:** Ellie fetches workable batches from chests; Baritone executes approved sections while the manager owns task priority, progress, retries, path/placement limits, and pause/resume.
6. **INSPECTION:** compare actual world states against desired states, repair missing/wrong blocks, remove temporary scaffolding and recover leftovers before marking complete.

Small and large builds follow **the same phases**. Only the number of chests and required quantities differ. Use the already-bundled `IBuilderProcess` path only after exact 1.21.1 signature, NPC instance, schematic loader and runtime behavior checks. Baritone is an execution tool; do not hand it authority to alter or redefine the blueprint.

### Construction contracts and stop conditions

- Checkpoint the stable blueprint ID, exact target states, build origin, chest coordinates, material deficits, work section and verified block counts. Resume by checking actual world state; never trust historical completion blindly.
- Single movement/build owner: release construction control before acquisition/survival preemption; never run competing path executors simultaneously.
- Pause/retry must have a bounded budget and specific failure reason. Expose phase, target, stock and blocked cause to Observatory and sanitized logs.
- Preserve F8/F7, teleport extension, owner retaliation and all unrelated functionality. Do not delete existing builders or other methods until there is source parity and live regression proof.
- Local `.schematic`/`.schem` and procedural blueprints can be future inputs; do **not** assume Player2's online schematic catalog works without the app. `.litematic` needs separately validated decoding.

## Required implementation order

0. **Source recovery (blocking):** obtain the exact 1.21.1 Fabric 1.4.0 baseline source + our overlays and independent sidecar source. Record file hashes, licensing, version, dependencies and rollback JARs. A reproducible build from a fresh checkout is mandatory before calling GitHub current.
1. Preserve the existing code and write manager contract + state machine unit tests separately from all movement.
2. Blueprint/material inventory and chest-capacity/placement integration with deterministic verification.
3. Baritone builder adapter controlled by the manager; retain current verified builder, compare identical small blueprints.
4. Full stockpile, start/resume, interruptions, site prep, and scaffolding recovery in actual survival worlds.
5. Repeated 5x5 tests, mixed-terrain builds, a multi-thousand-block village, reload/recovery, NPC retaliation, F8/F7 and long-run testing. Explicitly report all interventions.

**Release status 2026-11-04: no new binary, modern source port, or Minecraft runtime validation is claimed by this documentation update.**
