# Ellie: Setup11 survival reliability

Current revision: `20261105-setup11-shared-reliability-v2`. **Live survival gameplay is UNVERIFIED.**
See [reliability validation](../docs/ELLIE_SETUP11_RELIABILITY.md) for exact automated coverage, budgets, and the required fresh-world run.

The required sequence is wooden pickaxe, early stone axe, two stone pickaxes, stone shovel, crafting table, furnace, chest, 32 torches, shield, iron pickaxe, iron sword. Every objective is required. Surface block-light coverage is verified around the three-station workshop before iron upgrades; installed verified torches count toward supplies. Setup never requests buckets, iron armor, spare iron tools, bed, bread or diamonds. Existing better tools may satisfy quantities without requesting a downgrade.


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

## Current setup

The fixed setup kit uses native catalogue acquisition tasks, as mining prerequisites
do. It does not opt into the dynamic crafting macro at furnace/material transitions.
Setup requests disable opportunistic mining of placed output (such as torches),
so supplies use native ingredient gathering and crafting. Nearby workbench/furnace
placement requires non-tree support and a usable adjacent standing position.
Failed base safety checks produce safe native repair jobs or identify the unsafe
cell; naturally supported workstation floors are protected during setup too.

The imported revision uses one crafting table, one furnace and one chest in a
spaced line, on natural ground where possible. A safe nearby bench is preferred
as the anchor; a saved layout with placed stations retains its coordinates.
A world bench cannot substitute for a table item explicitly requested for placement. A compact support platform is a
fallback for unsupported terrain, with at most eight load-bearing placements.
The required survival kit remains. It intentionally supersedes the old eight-chest
9x7 deck contract; old layout records are not blindly reused or demolished.
See [setup contract](../docs/ELLIE_SETUP.md) and
[compact-platform changes](../docs/ELLIE_COMPACT_PLATFORM_UPLOAD.md).

The imported crafting-specific watchdog has been superseded by shared task-layer
recovery. Its earlier behavior remains documented as upload history. See
[uploaded recovery notes](../docs/ELLIE_CRAFT_RECOVERY_UPLOAD.md).
Local simulations and binary checks do not establish live gameplay success.

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
