# Shared target recovery, NPC crash and production handoffs

- Guard NPC axe stripping from the null-player vanilla path in the supplied crash.
- Preserve final furnace input/output handoff and refresh material accounting.
- Resume the same physical build after accepted defense/gesture suspension.
- Coordinate stalled navigation below tasks, with one recovery movement owner,
  alternative interaction positions, conservative clearing, finite retries and air priority.
- Delegate table rejection to the shared policy; remove the separate craft watchdog.
- Record a construction worksite and verified nearby bench separately from personal home.
- Add target/reach/blocker/look-writer and recovery-ownership Observatory diagnostics.

Output: `PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar`.
Native commands, original Player2 API and compact setup remain. Production recovery
uses native APIs, with no teleport, item grants or direct world edits. Local
simulations/bytecode checks do not replace live Minecraft acceptance. See
`docs/ELLIE_SHARED_RECOVERY.md` and prior notes below.

---

# Player2 native commands on the crafting-recovery revision

Based on the maintainer's reproducible craft-recovery source/JAR, retaining compact
setup, one table/furnace/chest, conditional supports and table-rejection behavior.

- Exact authenticated-owner commands bypass model selection for all 46 NPC IDs.
- Builds use shipped local templates and the unchanged physical layer builder;
  unsupported requests fail without AI retries. Builder outcomes are verified.
- Agentic pickup/storage plans, chest labels and GPS descriptions use local logic.
- Fix no-argument eating, missing-food silent returns, uppercase scan identifiers,
  and semicolons inside quoted arguments.
- Remove the permanent no-op dummy task. Eight unverified/unregistered commands
  remain unavailable; the speedrun is not described as impossible or tested.
- Keep the original Player2 API binary and companion integration. No ChatGPT
  interpreter hook or Node process is required by this build.

Output: `PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-NATIVE-COMMANDS.jar`.
See `docs/ELLIE_NATIVE_COMMANDS.md` in the portable bundle or the repository docs.

Local command/planner/orchestration simulations, bytecode checks and compatibility
checks are separate from live Minecraft acceptance. Shared navigation recovery is
not implemented by the imported crafting-specific fix and remains pending.
Previous interpreter release notes are retained in `HISTORICAL_RELEASE_NOTES.md`.
