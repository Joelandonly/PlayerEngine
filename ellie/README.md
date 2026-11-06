# Food reliability v16 (experimental)

Build ID: `20261106-food-reliability-v16`. **Gameplay UNVERIFIED.**
Repairs the existing food workers: repeated hunts, already-owned cooked food,
in-flight fuel/cooking retention, bounded alternative fuel strategies, existing
furnace reuse, and automatic food collection that otherwise starves owner work.
Command completion follows its own executor receipt, including deferred native
callbacks and partial/failure outcomes. No new scheduler or recovery controller.

All **32 setup classes** remain byte-identical to the owner-confirmed 14-stage
setup. Navigation/bridging and the previous descent/water safeguards are retained.
See [changes, tests and live acceptance checks](../docs/ELLIE_FOOD_RELIABILITY_V16.md).
The later navigation, deposit-counting, pickup, fishing and resolver repairs are
separate work; this candidate does not claim to resolve every audit finding.

# Non-loading water escape v15 (experimental)

Build ID: `20261106-nonloading-water-escape-v15`. **Gameplay UNVERIFIED.**
Repairs live-world block queries in native water-escape goal callbacks, a separate
path-worker access hazard missed by v14. Keeps native goal policy, setup and
bridging. Existing observatory client ticks attempt a full thread capture after
a sustained unpaused server stall: `logs/ellie-server-stall.txt`.

See [supplied freeze evidence, patch scope and live validation](../docs/ELLIE_WATER_ESCAPE_STALL_V15.md).
The exact frozen Minecraft wait chain remains unconfirmed.

# Non-loading descent collision view v14 (experimental)

Build ID: `20261106-descent-block-view-v14`. **Gameplay UNVERIFIED.**
Corrects v13's planning collision callbacks to use native `bsi.access` instead
of the live world. A worker/view regression fails against v13 and passes with
this change. The reported intermittent Minecraft freeze is not reproduced or
conclusively explained by the supplied log. Setup and bridging remain unchanged.

See [evidence, correction and live validation](../docs/ELLIE_DESCENT_BLOCK_VIEW_V14.md).

# Pre-commit descent safety v13 (experimental)

Build ID: `20261106-safe-descents-v13`. **Gameplay UNVERIFIED.**
Based on v12 `45b1a02`. Preserves outward bridging and the owner-confirmed 14-stage
setup. Shared planning and pre-input gates reject unsupported dangerous descents;
a carried bucket grants no speculative fall protection.

See [behavior, automated checks and live acceptance steps](../docs/ELLIE_SAFE_DESCENTS_V13.md).

The following notes retain previous build provenance.

# Independent NPC chunk ticking v12 (experimental)

Build ID: `20261106-independent-npc-chunks-v12`. **Gameplay UNVERIFIED.**
Based on v11 `cc9f9994e2d686e8ac7945fe2f1ee31106a0d0eb`; intended for review
against `feature/ellie-friendly-commands-remote-owner-20261106`, not main.
The owner-reported four-world proven baseline is `6290ef647051bc4f1be1898b795c031d80295252`.
Its 14-stage setup implementation is unchanged by this patch.

See [chunk execution investigation, CI scope and live acceptance steps](../docs/ELLIE_REMOTE_EXECUTION_V12.md).

The following retained notes describe older overlay provenance and survival experiments;
they are not a new setup contract for this patch.

# Ellie: chest-anchored survival setup

Current build: `20261105-setup-output-handoff-v7`. **Live Minecraft survival completion remains unverified.**
See [survival setup behavior and validation](../docs/ELLIE_SURVIVAL_SETUP.md).

`setup` now assesses hunger, health, daylight and nearby hostiles; returns to the
saved workshop chest; reuses verified supplies; prepares an enclosed shelter;
verifies the required kit and stations; and finishes with food, fuel and equipped
sword/shield. Automatic lighting stays excluded. Existing native task execution,
Player2, storage, supervisor, observatory and shared recovery remain in use.

Current target: **Minecraft 1.21.1 Fabric, PlayerEngine 1.4.0, Java 21**.
Keep the existing Player2NPC and companion Fabric mods. This is a binary overlay;
complete matching upstream source remains unavailable. The root Gradle project is
legacy Minecraft 1.20.1 source, not the current Ellie build.

The maintainer supplied `Ellie-Player2-Crafting-Recovery-Source (1).zip` containing
`PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-CRAFT-RECOVERY.jar`.
Its source rebuilds byte-for-byte to SHA-256
`f36e4210a31024c113f7be6d2afa97385c878a15b1ab1d9f590a4ea745980b9f`.
The pinned Acquisition Safety baseline is unchanged. Original import provenance is
in `RELEASE_MANIFEST.json`; the latest supplied manifest is preserved separately
in `provenance/CRAFT_RECOVERY_UPLOAD.json`.

## Build and install

With Java 21 JDK tools and Python 3 on PATH:

```sh
bash build.sh
python3 make_release.py
```

The current build uses native Player2. No ChatGPT sidecar or Node process is needed.
Older ChatGPT launcher/sidecar files and `legacy-chatgpt/` are historical material,
not part of the current runtime. The build verifies Player2 API byte identity,
exact class changes, ASM, command routing, supervisor, setup and crafting recovery.
Generated JAR/portable ZIP files are in `dist/`. For installation, download the
GitHub Actions artifact **`Ellie-1.21.1-JAR-only`**: it contains exactly one mod JAR,
with its source revision and `UNVERIFIED` in the filename, plus a checksum receipt. GitHub wraps artifact downloads in a ZIP;
extract that one JAR into `mods`.

The separate **`Ellie-1.21.1-source-bundle`** is a lean review/rebuild source bundle with zero JARs or class files. Rebuilding requires the pinned baseline from this repository, with its SHA-256 verified by `build.sh`. It is not a full upstream engine source tree.

Install only the replacement PlayerEngine JAR in Minecraft's `mods` folder.
Remove the previous engine JAR and keep the companion mods. Run `Ellie, setup` in
a test world and observe actual placement/navigation before accepting a release.

## Current owner-facing setup (basic batched HOME, v9)

\`setup\` now dispatches \`BasicSetupTask\`. Older \`EllieSetupTask\` and
\`SetupSurvival\` classes are retained for compatibility/history, **not
the current owner-facing setup behavior**.

The task deliberately completes one bounded material batch at a time:

1. Gather 12 logs for the full wooden crafting bill, then place/verify one
   crafting bench and register it as **Ellie Setup Home** in the existing
   native Baritone \`HOME\` waypoint collection. The first verified table
   is the workshop anchor for later chest/furnace placement.
2. Craft one wooden axe and one wooden pickaxe.
3. Gather 20 cobblestone (four stone tools, one furnace and a margin),
   then craft one each stone pickaxe, axe, shovel and sword.
4. Gather 16 coal and craft **64 torches**. Keep them in inventory.
5. Craft one chest and furnace, then place both at the recorded bench
   using the existing native StarterBaseTask/StarterBaseWorld structures.
6. Verify real world blocks, HOME waypoint and stone equipment/64 torches.

No iron or diamond gear, shield, empty buckets, food/fuel reserves, shelter
construction, automatic lighting or extra commands in this scoped setup.
All failures are bounded and explicit; no optional-skipping path.

Previous Nov 5 game logs showed a furnace creation subtask ending without
an actual furnace in inventory. This revision separates raw material gathering,
furnace crafting and verified physical placement, with at most two missing-output
acquisition attempts. Native Minecraft gameplay validation is still pending.
Build/tests and packaging passing are **not** proof of a successful live game.

## Native commands on this revision

The integrated build writes `ELLIE-PLAYER2-NATIVE-COMMANDS.jar` and
`PlayerEngine-Ellie-Player2-Native-Commands.zip`. Exact owner commands such as
`Ellie, get cobblestone 64`, `Ellie, mine iron_ore 8`, `Ellie, setup_farm` and
`Ellie, build a huge cobblestone village` use local routing. Supported blueprints,
agentic drop/storage plans, labels and waypoint descriptions have no AI fallback.
See [command changes and test limits](../docs/ELLIE_NATIVE_COMMANDS.md).

## Current shared-recovery test build

Install `ELLIE-PLAYER2-SHARED-RECOVERY.jar` from the current CI artifact, replacing the
previous engine JAR and keeping Player2NPC. This adds the NPC axe-use crash guard,
production/furnace handoffs, defense-safe build resume, common movement recovery,
look diagnostics and a separate persistent worksite/bench record. Personal home
is untouched. See [findings, architecture and live gates](../docs/ELLIE_SHARED_RECOVERY.md).
The portable bundle is `PlayerEngine-Ellie-Player2-Shared-Recovery.zip`.

V6 verifies the three placed workshop stations before normal daytime shelter
construction, retains nested mining protection zones, and repairs truncated V5
shelter checkpoints without relocating the saved base.
