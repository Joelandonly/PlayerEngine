# PlayerEngine development instructions for coding agents

## Version boundary (critical)
- The current source in this fork, including `feature/autonomous-goals`, is **Minecraft 1.20.1 / Java 17 / PlayerEngine 1.0.0**. Verify `gradle.properties` and `build.gradle` before editing it.
- The playable mod being debugged separately is **Minecraft 1.21.1 / Java 21 / Fabric API 0.116.17+1.21.1 / PlayerEngine 1.4.0 / Player2NPC 1.4.0**.
- The complete matching 1.4.0 Java source has **not** been integrated. A legacy `./gradlew build` passing does not prove a patched 1.21.1 JAR works. Do not transplant 1.21.11 or 26.x APIs without checking mappings and version compatibility.
- Do not change `main` or unrelated NPC features. Keep all source changes reviewable and retain source for binary patches.

## Agent skills
Select relevant guidance from `.agents/skills/`:
- `minecraft-modding` for Java and Fabric APIs
- `minecraft-testing` for unit tests, GameTests, and integration cases
- `minecraft-ci-release` for builds, artifact verification, and releases
- `minecraft-multiloader` for Architectury common/Fabric/NeoForge boundaries

Upstream skills are development instructions, not abilities installed inside the NPC. The actual project version always takes precedence over example code.

## Construction requirements
- Preserve existing mining, crafting, combat, inventory, conversation and other commands.
- Build from a bounded blueprint. Obtain and consume genuine materials, navigate to placement points, and use actual Minecraft block interactions rather than silent world editing.
- Count attempted, placed, skipped, failed and already-correct blocks; verify actual world states before claiming completion. A zero-block build cannot succeed unless the full blueprint is already present in the world.
- A gesture may not cancel a long-running construction job; interruption, save/reload, insufficient resources and unreachable positions need explicit recovery or failure.
- Connect progress to the Task Chain display. Do not report "house completed" merely because an executor or script returned.
- Preserve binary compatibility with original call sites, including inner-class constructor descriptors. Before distributing a 1.21.1 JAR, run `bash tools/check-playerengine-abi.sh /path/to/playerengine.jar`.
- Static validation is NOT game validation. In a disposable 1.21.1 Fabric world, test a 5x5 shelter, material gathering, placement, pause/restart, and block-by-block outcome.

See `docs/MINECRAFT_AGENT_SKILLS.md` for provenance and validation details.
