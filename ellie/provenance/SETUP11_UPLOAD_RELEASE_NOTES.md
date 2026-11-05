# Ellie 1.21.1: Required Stone Shovel + Outdoor Starter Base Lighting

**Build revision:** `20261105-setup11-stoneshovel-outdoorlight-v1`  
**SHA-256:** `67a4f2853b5d035f160c5edd41e051f1b73fd141271c662bbca470bd1649fe9c`  
**Minecraft target:** Fabric 1.21.1, Player2NPC 1.4.0, Java 21

## Changes

- Required, **11-step** setup sequence: wooden pickaxe, early stone axe, 2 stone pickaxes, **stone shovel**, crafting table, furnace, chest, 32 torches, shield, iron pickaxe, **iron sword**.
- No optional setup steps or optional-skipping code. Empty buckets, spare iron shovel/axe, iron armor, diamond items, bed and bread are **not** requested by setup. Get/agentic can acquire those separately.
- Physical worksite remains one crafting table, one furnace and one chest; after these are verified, setup lights nearby outdoor surface ground with native real torch placements. Uses Minecraft **BLOCK** light, not sunlight, to assess dark locations. At most 12 new torches are placed per task, and surfaces outside the safe base-height range, hidden from sky, inside caves/underground, or occupying station access lanes are excluded. Existing lights are respected.
- Surface lighting is a required completion check. Remaining dark eligible outdoor cells, exhausted torch budget, or unavailable native placement yields BLOCKED instead of false success. Removing torch coverage after completion invalidates the base's verified success.
- Installed torches are counted alongside inventory torches during final required-item verification so the bot does not chase torches it just placed.
- Setup logs print exact build revision `20261105-setup11-stoneshovel-outdoorlight-v1`. JAR also contains `META-INF/ELLIE_BUILD_REVISION.txt`.
- Retains earlier integrated fixes: real inventory slot check and observatory, agentic quoted resource syntax, chunk-ticket watchdog, bounded A/B mining-target rejection, compact-village generator and native crafting recovery. These earlier fixes were not reimplemented here.

## Tests performed

- Java 21 compilation of all packaged overrides: PASS.
- Existing build suite before packaging reached and passed setup, construction, native commands, full shared recovery, village, observatory distance and inventory fixture tests. The monolithic script was stopped by a 120-second command timeout near the end; remaining inventory bytecode and target-loop checks passed in isolated runs.
- New `SurfaceLightingTest` with actual task scheduling against simulated outdoor terrain: PASS (physical torch counts, 0-dark ground, no under-base lighting, no blocked machine access, false completion rejected).
- `SetupCompletionTest`: 12 PASS including exact 11 required items/ordering and stone-shovel final verification.
- Packaged Java bytecode verifier: 1,268 methods PASS.
- **No live Minecraft/Fabric gameplay run.** Reflection into the real 1.21.1 world lighting API and in-world torch placement still require verification on a test world. Do not confuse these tests with a real NPC playthrough.

## Installation and verification

1. Back up the test world. Remove prior PlayerEngine JARs, keeping only this replacement and the regular Player2NPC/Fabric dependencies.
2. Start game and run `setup`; find `SETUP_START ... Ellie build=20261105-setup11-stoneshovel-outdoorlight-v1` in `latest.log` to verify the *correct* file actually loaded.
3. Confirm the stone axe starts early, the **stone shovel** is obtained, and no iron armor, spare iron shovel, buckets or diamonds are requested by setup.
4. Watch the outdoor perimeter-lighting log events `SETUP_SURFACE_LIGHTING`, `SETUP_OUTDOOR_TORCH`, `SETUP_SURFACE_LIT`. Confirm no torch placement follows a cave or trial chamber.
5. Stress-test iron sword acquisition and earlier A/B mining oscillation; verify the shared target rejection actually reroutes in the game.
6. Use the F8/F7 observatory for blocked reasons and actual inventory capacity; submit logs if anything fails.

## Provenance and limitations

This is the existing **binary-overlay** source workflow against the pinned PlayerEngine 1.21.1 baseline; the review source ZIP is **lean (no JARs)** and is **not a standalone rebuild without that exact baseline**. The source ZIP contains overrides, ASM patch source, tests, build script and manifest, but intentionally excludes the large baseline JAR and any generated binaries. Do not claim a review ZIP is independently buildable unless the pinned baseline is supplied separately.

The user previously experienced unreliable ChatGPT sandbox downloads; this turn provides a verified local sandbox artifact. It does **not** create a GitHub-hosted Actions artifact. GitHub repo remains unchanged by this release.
