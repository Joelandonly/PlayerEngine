# Independent NPC chunk execution — v12 candidate

Status: gameplay-unverified. Exact source/JAR identity is embedded in META-INF/ELLIE_BUILD.json.
Base: `cc9f9994e2d686e8ac7945fe2f1ee31106a0d0eb` on experimental
`feature/ellie-friendly-commands-remote-owner-20261106` (PR #15).
Proven setup baseline: `6290ef647051bc4f1be1898b795c031d80295252`; owner reports
14-stage completion in four fresh survival worlds. This patch preserves that setup source.
The old unfinished workshop experiment remains in a separate worktree and is not included.

## Evidence and scope

The supplied report describes a roughly 398-block owner teleport, controller and NPC
STALLED, current chunk unforced despite four tracker entries, a successfully dispatched
remote follow command, and execution resuming when the owner returned. The raw log for
that timestamp was not attached to the pasted brief, so its exact initiating ticket
removal cannot be attributed to one actor from this investigation.

The pinned binary was inspected directly, not inferred from old upstream source:

- PlayerEngineController.serverTick refreshes ChunkLoadingTracker, but staticServerTick
  only advances the global clock/chat/pruning. It does not execute NPC tasks.
- The native tracker requests the floor/ceil Cartesian footprint around NPC X/Z, up to
  four distinct chunks (its four-entry list can contain duplicates at exact boundaries).
- Native ChunkController keeps a coordinate-only map to UUID lists, not keyed by world;
  duplicate loads append the same owner repeatedly. Force is set only on first claim.
- Native clear forgets the map without releasing tickets. Tracker reset unloads only
  the current cell before clearing the list, leaving the other claimed cells behind.
- v11 independently restores missing flags, but refuses a stale tracked footprint,
  cannot refresh the tracker without entity ticking, and has no complete claim cleanup.

These are established code defects, not proof that each caused the reported teleport.

Cached Minecraft 1.21.1 intermediary bytecode was also inspected: ServerWorld.method_17988
(setChunkForced) changes persisted force flags and notifies ServerChunkManager.method_12124
only when that flag changes. Therefore an already-present flag is not a general reassertion
of a missing effective ticket. ChunkTicketManager.method_14036 uses the native forced
ENTITY_TICKING level (31, derived via class_8563 from class_3194.field_13877). This patch
uses that existing API rather than inventing another level or manually ticking entities.

## Change

ChunkTicketWatchdog now backs the existing native ChunkController load/unload/clear/size
methods through exact bytecode delegation. Claims are deduplicated per world/chunk/UUID.
A pre-existing external force flag is recorded and preserved when our final claim ends.
Native callers and the global maintainer share that accounting; there is no competing
second chunk-owner implementation or second controller/executor.

The global callback reconciles current NPC coordinates every server tick, including when
the tracker starts empty or has stale coordinates. It releases obsolete claims first,
requests the same native footprint, and updates the native tracker list. It restores
missing vanilla force flags and reasserts effective native forced tickets every 20 ticks.
It never forces the owner's destination or loads a path corridor.

Explicit controller stop/unregister and tracker reset have release hooks. Global cleanup
also handles absent controllers, dead/removed NPCs, disabled task runners, changed worlds
and orphaned UUID claims. An accepted new task can re-enable the existing runner, allowing
chunk maintenance to resume. Nothing turns chunk unavailability into task completion.
Errors are rate-limited in CHUNK_TICKETS_UNAVAILABLE and visible in chunkTicketsStatus.

DistanceDiagnostics still measures actual controller callbacks and NPC age separately.
Restored force flags are explicitly logged as awaiting heartbeat evidence. A forced flag
or successful command receipt is never presented as successful gameplay execution.

## CI tests and limits

The existing regression suite and bytecode/packaging checks remain required, including
basic setup, native routing, authenticated remote gate, storage, recipes/recovery and
legacy sidecar tests. New tests execute the packaged native ChunkController methods and
the real global DistanceDiagnostics callback against substituted Minecraft boundaries.
They cover missing flags; saved flags with missing effective tickets; stale footprints;
negative/exact-boundary coordinates; repeated moves without a ticket trail; world-specific
shared and duplicate owners; stop/re-enable; death/despawn; registry loss; reset and clear;
external flags; force rejection and visible diagnostics. Modeled owners teleport 100,
250 and 400 blocks, while NPC ticket creation is tested with the NPC footprint
already generated or absent in the fixture. This is not an emulation of generating
the owner's remote destination or of generation stalling the real server. NPC age/controller
heartbeats advance only after the modeled world's ticket eligibility is restored.

These cases test maintenance/control flow. The modeled world is not Minecraft's actual
chunk generator, entity manager or NPC implementation. Existing remote-route tests verify
command eligibility, while this patch tests chunk eligibility after re-enabling work;
it does not prove that a real remote task progresses while its NPC was unloaded.
No actual Minecraft client/server + matching Player2NPC gameplay run was available.

## Required live acceptance before merge to a proven gameplay branch

Use copies of survival saves with the real matching Fabric/Player2NPC installation.
Keep ordinary chat local and issue an explicit authenticated owner command remotely.

1. Begin a supported resource job and record its remaining target, NPC UUID/age,
   controller heartbeat, current/tracked chunk coordinates and inventory.
2. Teleport the owner 100, 250 and 400 blocks away without moving the NPC. Repeat toward
   explored terrain and toward terrain not previously generated. Keep the server running.
3. Check NPC age and controller callbacks keep advancing, and demonstrate actual mined
   blocks, collected inventory or meaningful path progress. A force flag alone fails this
   gate. Issue remote follow while distant and verify actual movement, not only acceptance.
4. Repeat teleports and NPC chunk crossings; owned footprint must remain <=4 distinct
   cells per NPC. Verify old cells no longer carry this NPC's claims. Existing unrelated
   spawn/player/external forced chunks are not counted as this mod's new claims.
5. Test two NPCs sharing a chunk and identical chunk coordinates in two dimensions.
   Stopping one owner must preserve the other's claims. Test death, dismissal, explicit
   stop, dimension transfer, reconnect and server shutdown without claim leaks.
6. If an NPC has already been genuinely removed/unloaded, do not resurrect its stale
   object or clone a controller. Verify native reload/registration resumes the pending
   task honestly, or records unavailability without fabricated completion.
7. Re-run the proven 14-stage setup scenario on this candidate as a regression test.

An owner teleport into new terrain may stall the entire server during generation; no
NPC maintenance can run while the global server itself is paused/stalled/stopped. The
live experiment must compare server ticks, NPC ticks and controller ticks to distinguish
that from NPC chunk eligibility. The initiating actor in the reported disappearance and
real execution recovery remain gameplay-unverified until this test is performed.
