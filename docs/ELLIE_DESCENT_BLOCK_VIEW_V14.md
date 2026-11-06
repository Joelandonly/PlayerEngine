# Non-loading collision view v14

Build ID `20261106-descent-block-view-v14`. Gameplay **UNVERIFIED**.
Based on v13 `2ffabd720bdb85282b55e3b496cbd49e5b3ca79f`.

## Observed failure and limits of diagnosis

The supplied November 6 log starts an integrated world, joins the player, then
shows Ellie entity/controller heartbeats. Server observatory output stops at
tick 340 as `Unstuck Chain` / `Completing goal` starts; background API logging
continues. The owner reports frozen mobs and no chunks beyond the initial area.
There is no fatal exception or thread dump in the supplied files. A subsequent
new world in the same session works. This is consistent with an intermittent
server stall, not proof that world generation failed or that Ellie was ticked
before her entity existed. A quick world switch is a hypothesis, not a confirmed
cause or a required user workaround.

## Confirmed code defect and narrow correction

V13's planning probe fetched states through native `CalculationContext.get`, but
passed `CalculationContext.world` into collision shape callbacks. Such callbacks
may read neighbors, escaping the non-loading block access used by the pathfinder.
This violates the worker boundary and can enter live-world chunk access. It does
not by itself prove the exact deadlock or wait in the supplied Minecraft run.

The planning probe now passes the existing `CalculationContext.bsi.access`.
Its native `BlockStateInterfaceAccessWrapper` serves state/fluid reads through
`BlockStateInterface.get0`, which uses already-available chunks and returns air
when unavailable. No new world cache, navigator, executor, lifecycle hook or
chunk loading policy is introduced. The existing loaded-target check remains.
The grounded pre-movement gate still checks the live world on the movement tick.

All descent policy, same-height outward bridging, safe drops/stairs, native
emergency clutch behavior and the entire 14-stage setup remain unchanged.
The two patched native classes (`AStarPathFinder`, `Movement`) and all other
runtime classes must remain byte-identical to v13 except `SafeDescent.class`.

## Validation

`SafeDescentThreadTest` forces a collision shape to query a neighbor while a
worker plans a safe downward edge. Its raw-world boundary refuses such a read;
the native-shaped `bsi.access` fixture serves it without a live-world query.
This test fails against the published v13 helper and passes after the correction.
It checks 20 alternating world contexts, no direct live reads, the correct view
per context, unloaded old-context rejection and continued new-context planning.

The test substitutes world/shape/chunk scheduling boundaries. It reproduces the
wrong-view access defect, **not the reported Minecraft freeze**. The full native
A* engine, Minecraft server scheduler and real world unload/reload are not run
by this fixture. Existing geometry, actual packaged movement gate, concrete
bridge-body preservation, Player2, setup, recovery, chunk and Node regressions
also run. All 32 setup class hashes must still match the proven setup revision.

Live acceptance requires rapid create/leave/create and existing-world reopen
cycles in Minecraft, with chunks continuing to stream, mobs ticking and Ellie
appearing, followed by the same cliff/ore task and ordinary bridge/staircase tasks.
If the stall recurs, a Java thread dump captured while it is frozen is needed to
identify the actual wait chain; the current log alone cannot establish it.
