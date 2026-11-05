# Ellie 1.21.1: November 5 standalone reconciliation and live blockers

> **Status: HANDOFF / NO CODE PORTED / IN-GAME VALIDATION PENDING.** The owner reports the **ChatGPT sandbox download link failed**, NOT that the JAR was installed and crashed or malfunctioned. The distinction matters. Do not label this binary defective without an actual in-game failure report. Do not assume `codex/shared-recovery-crash` includes November 5 changes.

## Baseline and provenance

- GitHub source baseline: PR #9, `codex/shared-recovery-crash`, commit `ae24a84cc9fde89dda55a23a4ecb53b4c25e9056`.
- Later changes were built **outside GitHub** against the 1.21.1 PlayerEngine binary with a mixture of Java overrides and ASM patches. The root repository's Gradle project is not necessarily the matching 1.21.1 source tree; check versions and class identities before porting.
- The most recent claimed output was called `Ellie-1.21.1-Inventory-Range-Recovery-20261105.jar`, SHA-256 `420cc8ed331430d3047949b378a6382d68e344e4551a2ba836f0bcc1c0a172ea`. **CORRECTION: Owner did not report runtime failure of this JAR. Their complaint was that the ChatGPT sandbox download link often did not work. Runtime behavior of this specific build is not verified either way; automated tests passed previously, and an in-game test still needs to happen.**
- The associated source ZIP was bloated: two JARs totaling ~8.7 MB uncompressed and legacy docs. The included output JAR had the **wrong filename**, `PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar`, although its bytes and SHA-256 match the separately named standalone. The other included JAR is the pinned `baseline/playerengine-acquisition-safety.jar`, SHA-256 `8ae4c3da375b45ad60f94ddbd4d4c31328400cce0c4a720b1002dd8dee143d27`. Do not mistake that older baseline for an installable new build.
- **Packaging requirement:** ship one clearly revision-named install JAR in the JAR-only artifact, and a lean source bundle with changed sources, tests, notes and explicit dependency/baseline instructions. Don't duplicate generated JARs in the source bundle or pass the binary-overlay bundle off as a full standalone source tree.

## Live findings from November 5 Minecraft logs

1. **Inventory capacity: false warnings.** User visually inspected Ellie and saw empty slots while `SETUP_INVENTORY_PRESSURE` repeatedly said there was no free main slot. The original `InventorySubTracker.hasEmptySlot` appears to infer empty slots from the *item count* of an empty stack (zero), rather than actual slot occupancy. Fix the canonical tracker and verify by iterating the **36 player main/hotbar slots**, not armor/offhand/crafting slots. Log actual occupied/free counts; compare against in-game inventory. **Do not add automatic dumping in response to these false warnings.**
2. **Setup iron_sword acquisition stall:** at step 14/20, recorded `No material or crafting-phase progress for 1801 ticks`, while attempted mining alternated at `(8,50,-23)` and `(-1,62,-32)` repeatedly. At last failure the owner was **98.8 blocks** away; controller and NPC were both ticking and Ellie's chunk was force-loaded. Therefore the setup failure is a resource/path/target oscillation problem, not proof of remote ticking failure. Need bounded per-target retries with meaningful progress measured by changed block, movement, or inventory increase.
3. **Independent long-distance failure:** diagnostics showed NPC + controller ticking at **520.5 blocks**, then both stalled at **523.1 blocks**, with `requested=4 tracked` but `forced=NO` for the NPC's current chunk. Ticking resumed after owner approached to ~313 blocks. Check if requested tickets are actually present and whether the ticker depends circularly on the NPC. Proposed minimal global-server-tick watchdog verifying actual forced-chunk state and cleaning up old tickets. Keep only a bounded NPC-centered region. User reports increasing global range seemed to help, so prove behavior with vanilla default range too.
4. **Agentic command inconsistency:** quoted inputs such as `ellie agentic "get cobblestone 64"` and `"get minecraft:stone 64"` were rejected as storage-only, while `ellie agentic get diamond 64` started resource acquisition. Normalize quoted/unquoted arguments and unify under the existing resource task executor, not a second agent loop. Verify native command registration as well as parsing.
5. **Early setup tool order:** user requested a **stone axe early**, ideally after initial log/wooden pickaxe and mined cobblestone, before bulk woodcutting. Existing setup had stone axe after two stone pickaxes and shovel. Keep initial material safety and workstations, but don't require much hand-chopping before an axe.
6. **Village generator regression:** prior `compact village` routed to a 47x47 fortress (3,533 cobblestone in one analyzed version). A later standalone test claimed a compact 3-house, plaza-and-roads blueprint (~285 cobblestone, ~474 operations), but **the later build still needs actual Minecraft construction validation**. Cover exact natural-language routing, layout bill of materials, roads connectivity, and physical placement.
7. **Reporting invariants:** physical builder failures must become `FAILED` to core supervisor, never `SUCCEEDED`; setup `COMPLETED` must clearly distinguish required/optional items and unplaced storage chests.
8. **Storage intelligence:** design accepted by owner: use actual inventory first, then fresh known storage and targeted scans before gathering/crafting/building. Automatically discover waypoints and snapshot inventories only after verified scans; invalidate stale/missing/unloaded locations. Preserve manual `scan_storage`, `get`, `idle`, `stop` commands and observatory logging.

## Porting order and acceptance tests (mandatory)

1. **Do not copy the entire overlay blindly.** Compare PR #9 source behavior against current binary and isolate already-present fixes. Port independently with minimal diffs and matching 1.21.1 source. Keep Player2NPC 1.4.0 API identity and existing commands.
2. Fix and live-test actual inventory slot counting with visibly empty slots; log `free / occupied / reserved` consistently. No automatic drops.
3. Test `setup` from fresh world, early axe crafting, iron_sword acquisition, inventory progression and retries. Force the A/B target oscillation and verify fallback before timeout.
4. Verify `agentic get coal 16`, `agentic "get coal 16"`, raw `get coal 16` with/without known storage, and correct quantity.
5. Test owner distances 100 / 250 / 500 / 600 blocks with NPC age, controller heartbeat, actual `forced` state, and *inventory progress*. Repeatedly leave/return. Also verify tickets clean up.
6. Build a compact village end-to-end in survival, not just compile blueprint math. Verify there is no false success or protection-induced mining loop.
7. Run the full regression/build suite and **one real Minecraft smoke test** before marking any release ready. If gameplay is not tested, label output `UNVERIFIED` rather than `WORKING`.
8. Produce one correctly named JAR in an install-only artifact. Keep source package lean; ensure source and JAR revisions/hashes match.

## Owner feedback requiring attention: download delivery, not a reported runtime crash

> **Owner correction:** “no i mean the standalone DOWNLOAD link didnt work (as it does only work 1 out of 5 times anyway, codex uses some direct github link)”

The reported failure is **ChatGPT's sandbox download link / artifact delivery**. It does not establish that the JAR cannot run. The user prefers a **direct GitHub Actions artifact or release download**, as Codex uses. Produce an actual GitHub-hosted artifact for the exact revision and give the workflow/artifact link; do not substitute a pointer to the old GitHub baseline JAR or claim this source archive is itself a compiled new release. Keep separate statuses for (1) build/tests, (2) accessible artifact link, (3) successful in-game behavior.

The source ZIP's duplicate binaries and the mismatched internal JAR filename are separate packaging concerns; neither proves broken runtime behavior. Correct filename/package layout and test actual installation with Minecraft after delivering a working URL.

### Scope of this PR

Documentation only. It contains **no Java source changes, no merged binary, no claim that the standalone works**. The standalone source ZIP resides in the original ChatGPT conversation, not in this GitHub branch; it must be supplied independently if Codex needs its exact source content.
