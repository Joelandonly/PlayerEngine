# Ellie: chest-anchored survival setup

Current build: `20261105-survival-setup-v4`. **Live Minecraft survival completion remains unverified.**
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

## Current setup

The required kit remains: wooden pickaxe when needed, early stone axe, two stone
pickaxes, stone shovel, crafting table, furnace, chest, shield, iron pickaxe,
iron sword and 32 unplaced torches. No bucket, armor or diamond goals are added.

The command also requires an enclosed, accessible shelter near the saved chest,
a carried reserve of 32 safe food nutrition units (about four steaks or seven
bread), eight coal/charcoal fuel items, and an equipped sword and shield. Bed
acquisition is not required; a carried bed is reported as carried, not placed.
Food may include bread through the existing native food/recipe tasks.

Low hunger interrupts ordinary kit work. Darkness, nearby threats or low health
can cause return to shelter and a bounded wait; native defense/food chains retain
priority. Shelter uses a small local-wood 5x5 native blueprint on a checked site.
Foreign structures are not cleared to force a fit. Placement, food, fuel,
movement and equipment failures produce specific blocked outcomes.

Saved station and shelter positions are persistent intent, even while unloaded.
Reruns verify actual inventory and world blocks; they do not blindly trust old
completion receipts, relocate the base, or duplicate already owned tools.
Setup storage use is restricted to fresh verified stock in its own saved chest.
The final report does not claim lighting, spawn-proofing, bed placement or a farm.

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
